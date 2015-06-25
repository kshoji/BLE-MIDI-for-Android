package jp.kshoji.blemidi.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;

/**
 * BLE MIDI Parser<br />
 * The protocol compatible with Apple's `MIDI over Bluetooth LE` specification.
 *
 * @author K.Shoji
 */
public final class BleMidiParser {
    // MIDI event message
    private int midiEventKind;
    private int midiEventNote;
    private int midiEventVelocity;

    // for RPN/NRPN messages
    private static final int PARAMETER_MODE_NONE = 0;
    private static final int PARAMETER_MODE_RPN = 1;
    private static final int PARAMETER_MODE_NRPN = 2;
    private int parameterMode = PARAMETER_MODE_NONE;
    private int parameterNumber = 0x3fff;
    private int parameterValue = 0x3fff;

    // for SysEx messages
    private final Object systemExclusiveLock = new Object();
    private final ReusableByteArrayOutputStream systemExclusiveStream = new ReusableByteArrayOutputStream();
    private final ReusableByteArrayOutputStream systemExclusiveRecoveryStream = new ReusableByteArrayOutputStream();

    // states
    private static final int MIDI_STATE_TIMESTAMP = 0;
    private static final int MIDI_STATE_WAIT = 1;
    private static final int MIDI_STATE_SIGNAL_2BYTES_2 = 21;
    private static final int MIDI_STATE_SIGNAL_3BYTES_2 = 31;
    private static final int MIDI_STATE_SIGNAL_3BYTES_3 = 32;
    private static final int MIDI_STATE_SIGNAL_SYSEX = 41;
    private int midiState;

    // for Timestamp
    private static final int MAX_TIMESTAMP = 8192;
    private static final int BUFFER_LENGTH_MILLIS = 10;
    private int timestamp = 0;
    private int lastTimestamp;
    private long lastTimestampRecorded = 0;
    private int zeroTimestampCount = 0;
    final Timer timer;

    OnMidiInputEventListener midiInputEventListener;
    final MidiInputDevice sender;

    final EventDequeueRunnable eventDequeueRunnable;

    /**
     * Constructor
     *
     * @param sender the sender
     */
    public BleMidiParser(@NonNull MidiInputDevice sender) {
        this.sender = sender;
        timer = new Timer();

        midiState = MIDI_STATE_TIMESTAMP;
        midiEventKind = 0;
        midiEventNote = 0;
        midiEventVelocity = 0;

        eventDequeueRunnable = new EventDequeueRunnable();
        new Thread(eventDequeueRunnable, "EventDequeueThread").start();
    }

    /**
     * Sets {@link jp.kshoji.blemidi.listener.OnMidiInputEventListener}
     *
     * @param midiInputEventListener the listener for MIDI events
     */
    public void setMidiInputEventListener(@Nullable OnMidiInputEventListener midiInputEventListener) {
        this.midiInputEventListener = midiInputEventListener;
    }

    /**
     * Calculate `time to wait` for the event's timestamp
     *
     * @param timestamp the event's timestamp
     * @return time to wait
     */
    private long calculateTimeToWait(int timestamp) {
        long currentTimeMillis = System.currentTimeMillis();
        if (lastTimestampRecorded == 0) {
            // first time
            lastTimestamp = timestamp;
            lastTimestampRecorded = currentTimeMillis;
            return 0;
        }

        if (currentTimeMillis - lastTimestampRecorded > MAX_TIMESTAMP) {
            // the event comes after long pause
            lastTimestamp = timestamp;
            lastTimestampRecorded = currentTimeMillis;
            zeroTimestampCount = 0;
            return 0;
        }

        if (timestamp == 0) {
            zeroTimestampCount++;
            if (zeroTimestampCount >= 3) {
                // decides timestamp is always zero: event fires immediately
                return 0;
            }
        } else {
            zeroTimestampCount = 0;
        }

        int originalTimestamp = timestamp;
        if (timestamp < lastTimestamp) {
            timestamp += MAX_TIMESTAMP;
        }

        long result = BUFFER_LENGTH_MILLIS + timestamp - lastTimestamp + lastTimestampRecorded;

        lastTimestamp = originalTimestamp;
        lastTimestampRecorded = currentTimeMillis;
        return result;
    }

    /**
     * Stops the internal Thread
     */
    public void stop() {
        if (eventDequeueRunnable != null) {
            eventDequeueRunnable.isRunning = false;
        }
    }

    /**
     * {@link Runnable} with MIDI event data, and firing timing
     */
    private abstract class MidiEventWithTiming implements Runnable {
        static final int INVALID = -1;

        final Long timing;
        final int arg1;
        final int arg2;
        final int arg3;
        final byte[] array;

        private MidiEventWithTiming(int arg1, int arg2, int arg3, byte[] array, @NonNull Long timing) {
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
            this.array = array;
            this.timing = timing;
        }

        /**
         * Constructor with no arguments
         *
         * @param timing the event firing timing
         */
        MidiEventWithTiming(@NonNull Long timing) {
            this(INVALID, INVALID, INVALID, null, timing);
        }

        /**
         * Constructor with 1 argument
         *
         * @param arg1 argument 1
         * @param timing the event firing timing
         */
        MidiEventWithTiming(int arg1, @NonNull Long timing) {
            this(arg1, INVALID, INVALID, null, timing);
        }

        /**
         * Constructor with 2 arguments
         *
         * @param arg1 argument 1
         * @param arg2 argument 2
         * @param timing the event firing timing
         */
        MidiEventWithTiming(int arg1, int arg2, @NonNull Long timing) {
            this(arg1, arg2, INVALID, null, timing);
        }

        /**
         * Constructor with 3 arguments
         *
         * @param arg1 argument 1
         * @param arg2 argument 2
         * @param arg3 argument 3
         * @param timing the event firing timing
         */
        MidiEventWithTiming(int arg1, int arg2, int arg3, @NonNull Long timing) {
            this (arg1, arg2, arg3, null, timing);
        }

        /**
         * Constructor with array
         *
         * @param array data
         * @param timing the event firing timing
         */
        MidiEventWithTiming(@NonNull byte[] array, @NonNull Long timing) {
            this(INVALID, INVALID, INVALID, array, timing);
        }
    }

    /**
     * Parses MIDI events
     *
     * @param header the header bits
     * @param event the event byte
     */
    private void parseMidiEvent(int header, byte event) {
        int midiEvent = event & 0xff;
        long eventFireTime;

        if (midiState == MIDI_STATE_TIMESTAMP) {
            if ((midiEvent & 0x80) == 0) {
                // running status
                midiState = MIDI_STATE_WAIT;
            }

            if (midiEvent == 0xf7) {
                // is this end of SysEx???
                synchronized (systemExclusiveLock) {
                    if (systemExclusiveRecoveryStream.size() > 0) {
                        // previous SysEx has been failed, due to timestamp was 0xF7
                        // process SysEx again

                        // last written byte is for timestamp
                        int removed = systemExclusiveRecoveryStream.replaceLastByte(midiEvent);
                        if (removed >= 0) {
                            timestamp = ((header & 0x3f) << 7) | (removed & 0x7f);

                            eventFireTime = calculateTimeToWait(timestamp) + System.currentTimeMillis();
                            addEventToQueue(new MidiEventWithTiming(systemExclusiveRecoveryStream.toByteArray(), eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiSystemExclusive(sender, array);
                                    }
                                }
                            });
                        }

                        systemExclusiveRecoveryStream.reset();
                    }

                    // process next byte with state: MIDI_STATE_TIMESTAMP
                    midiState = MIDI_STATE_TIMESTAMP;
                    return;
                }
            } else {
                // there is no error. reset the stream for recovery
                synchronized (systemExclusiveLock) {
                    if (systemExclusiveRecoveryStream.size() > 0) {
                        systemExclusiveRecoveryStream.reset();
                    }
                }
            }
        }

        if (midiState == MIDI_STATE_TIMESTAMP) {
            timestamp = ((header & 0x3f) << 7) | (midiEvent & 0x7f);
            midiState = MIDI_STATE_WAIT;
        } else if (midiState == MIDI_STATE_WAIT) {
            switch (midiEvent & 0xf0) {
                case 0xf0: {
                    switch (midiEvent) {
                        case 0xf0:
                            synchronized (systemExclusiveLock) {
                                systemExclusiveStream.reset();
                                systemExclusiveStream.write(midiEvent);
                                systemExclusiveRecoveryStream.reset();

                                midiState = MIDI_STATE_SIGNAL_SYSEX;
                            }
                            break;

                        case 0xf1:
                        case 0xf3:
                            // 0xf1 MIDI Time Code Quarter Frame. : 2bytes
                            // 0xf3 Song Select. : 2bytes
                            midiEventKind = midiEvent;
                            midiState = MIDI_STATE_SIGNAL_2BYTES_2;
                            break;

                        case 0xf2:
                            // 0xf2 Song Position Pointer. : 3bytes
                            midiEventKind = midiEvent;
                            midiState = MIDI_STATE_SIGNAL_3BYTES_2;
                            break;

                        case 0xf6:
                            // 0xf6 Tune Request : 1byte
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiTuneRequest(sender);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xf8:
                            // 0xf8 Timing Clock : 1byte
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiTimingClock(sender);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xfa:
                            // 0xfa Start : 1byte
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiStart(sender);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xfb:
                            // 0xfb Continue : 1byte
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiContinue(sender);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xfc:
                            // 0xfc Stop : 1byte
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiStop(sender);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xfe:
                            // 0xfe Active Sensing : 1byte
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiActiveSensing(sender);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xff:
                            // 0xff Reset : 1byte
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiReset(sender);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;

                        default:
                            break;
                    }
                }
                break;
                case 0x80:
                case 0x90:
                case 0xa0:
                case 0xb0:
                case 0xe0:
                    // 3bytes pattern
                    midiEventKind = midiEvent;
                    midiState = MIDI_STATE_SIGNAL_3BYTES_2;
                    break;
                case 0xc0: // program change
                case 0xd0: // channel after-touch
                    // 2bytes pattern
                    midiEventKind = midiEvent;
                    midiState = MIDI_STATE_SIGNAL_2BYTES_2;
                    break;
                default:
                    // 0x00 - 0x70: running status
                    if ((midiEventKind & 0xf0) != 0xf0) {
                        // previous event kind is multi-bytes pattern
                        midiEventNote = midiEvent;
                        midiState = MIDI_STATE_SIGNAL_3BYTES_3;
                    }
                    break;
            }
        } else if (midiState == MIDI_STATE_SIGNAL_2BYTES_2) {
            switch (midiEventKind & 0xf0) {
                // 2bytes pattern
                case 0xc0: // program change
                    midiEventNote = midiEvent;
                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(midiEventKind, midiEventNote, eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiProgramChange(sender, arg1 & 0xf, arg2);
                            }
                        }
                    });
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xd0: // channel after-touch
                    midiEventNote = midiEvent;
                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(midiEventKind, midiEventNote, eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiChannelAftertouch(sender, arg1 & 0xf, arg2);
                            }
                        }
                    });
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xf0: {
                    switch (midiEventKind) {
                        case 0xf1:
                            // 0xf1 MIDI Time Code Quarter Frame. : 2bytes
                            midiEventNote = midiEvent;
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(midiEventNote, eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiTimeCodeQuarterFrame(sender, arg1);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xf3:
                            // 0xf3 Song Select. : 2bytes
                            midiEventNote = midiEvent;
                            eventFireTime = calculateTimeToWait(timestamp);
                            addEventToQueue(new MidiEventWithTiming(midiEventNote, eventFireTime) {
                                @Override
                                public void run() {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onMidiSongSelect(sender, arg1);
                                    }
                                }
                            });
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        default:
                            // illegal state
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                    }
                }
                    break;
                default:
                    // illegal state
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
            }
        } else if (midiState == MIDI_STATE_SIGNAL_3BYTES_2) {
            switch (midiEventKind & 0xf0) {
                case 0x80:
                case 0x90:
                case 0xa0:
                case 0xb0:
                case 0xe0:
                case 0xf0:
                    // 3bytes pattern
                    midiEventNote = midiEvent;
                    midiState = MIDI_STATE_SIGNAL_3BYTES_3;
                    break;
                default:
                    // illegal state
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
            }
        } else if (midiState == MIDI_STATE_SIGNAL_3BYTES_3) {
            switch (midiEventKind & 0xf0) {
                // 3bytes pattern
                case 0x80: // note off
                    midiEventVelocity = midiEvent;
                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(midiEventKind, midiEventNote, midiEventVelocity, eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiNoteOff(sender, arg1 & 0xf, arg2, arg3);
                            }
                        }
                    });
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0x90: // note on
                    midiEventVelocity = midiEvent;
                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(midiEventKind, midiEventNote, midiEventVelocity, eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                if (midiEventVelocity == 0) {
                                    midiInputEventListener.onMidiNoteOff(sender, arg1 & 0xf, arg2, arg3);
                                } else {
                                    midiInputEventListener.onMidiNoteOn(sender, arg1 & 0xf, arg2, arg3);
                                }
                            }
                        }
                    });
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xa0: // control polyphonic key pressure
                    midiEventVelocity = midiEvent;
                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(midiEventKind, midiEventNote, midiEventVelocity, eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiPolyphonicAftertouch(sender, arg1 & 0xf, arg2, arg3);
                            }
                        }
                    });
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xb0: // control change
                    midiEventVelocity = midiEvent;
                    switch (midiEventNote & 0x7f) {
                        case 98:
                            // NRPN LSB
                            parameterNumber &= 0x3f80;
                            parameterNumber |= midiEventVelocity & 0x7f;
                            parameterMode = PARAMETER_MODE_NRPN;
                            break;
                        case 99:
                            // NRPN MSB
                            parameterNumber &= 0x007f;
                            parameterNumber |= (midiEventVelocity & 0x7f) << 7;
                            parameterMode = PARAMETER_MODE_NRPN;
                            break;
                        case 100:
                            // RPN LSB
                            parameterNumber &= 0x3f80;
                            parameterNumber |= midiEventVelocity & 0x7f;
                            parameterMode = PARAMETER_MODE_RPN;
                            break;
                        case 101:
                            // RPN MSB
                            parameterNumber &= 0x007f;
                            parameterNumber |= (midiEventVelocity & 0x7f) << 7;
                            parameterMode = PARAMETER_MODE_RPN;
                            break;
                        case 38:
                            // data LSB
                            parameterValue &= 0x3f80;
                            parameterValue |= midiEventVelocity & 0x7f;

                            if (parameterNumber != 0x3fff) {
                                if (parameterMode == PARAMETER_MODE_RPN) {
                                    eventFireTime = calculateTimeToWait(timestamp);
                                    addEventToQueue(new MidiEventWithTiming(midiEventKind, parameterNumber, parameterValue, eventFireTime) {
                                        @Override
                                        public void run() {
                                            if (midiInputEventListener != null) {
                                                midiInputEventListener.onRPNMessage(sender, arg1 & 0xf, arg2 & 0x3fff, arg3 & 0x3fff);
                                            }
                                        }
                                    });
                                } else if (parameterMode == PARAMETER_MODE_NRPN) {
                                    eventFireTime = calculateTimeToWait(timestamp);
                                    addEventToQueue(new MidiEventWithTiming(midiEventKind, parameterNumber, parameterValue, eventFireTime) {
                                        @Override
                                        public void run() {
                                            if (midiInputEventListener != null) {
                                                midiInputEventListener.onNRPNMessage(sender, arg1 & 0xf, arg2 & 0x3fff, arg3 & 0x3fff);
                                            }
                                        }
                                    });
                                }
                            }
                            break;
                        case 6:
                            // data MSB
                            parameterValue &= 0x007f;
                            parameterValue |= (midiEventVelocity & 0x7f) << 7;

                            if (parameterNumber != 0x3fff) {
                                if (parameterMode == PARAMETER_MODE_RPN) {
                                    eventFireTime = calculateTimeToWait(timestamp);
                                    addEventToQueue(new MidiEventWithTiming(midiEventKind, parameterNumber, parameterValue, eventFireTime) {
                                        @Override
                                        public void run() {
                                            if (midiInputEventListener != null) {
                                                midiInputEventListener.onRPNMessage(sender, arg1 & 0xf, arg2 & 0x3fff, arg3 & 0x3fff);
                                            }
                                        }
                                    });
                                } else if (parameterMode == PARAMETER_MODE_NRPN) {
                                    eventFireTime = calculateTimeToWait(timestamp);
                                    addEventToQueue(new MidiEventWithTiming(midiEventKind, parameterNumber, parameterValue, eventFireTime) {
                                        @Override
                                        public void run() {
                                            if (midiInputEventListener != null) {
                                                midiInputEventListener.onNRPNMessage(sender, arg1 & 0xf, arg2 & 0x3fff, arg3 & 0x3fff);
                                            }
                                        }
                                    });
                                }
                            }
                            break;
                        default:
                            // do nothing
                            break;
                    }

                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(midiEventKind, midiEventNote, midiEventVelocity, eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiControlChange(sender, arg1 & 0xf, arg2, arg3);
                            }
                        }
                    });
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xe0: // pitch bend
                    midiEventVelocity = midiEvent;
                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(midiEventKind, midiEventNote, midiEventVelocity, eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiPitchWheel(sender, arg1 & 0xf, (arg2 & 0x7f) | ((arg3 & 0x7f) << 7));
                            }
                        }
                    });
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xf0: // Song Position Pointer.
                    midiEventVelocity = midiEvent;
                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(midiEventNote, midiEventVelocity, eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiSongPositionPointer(sender, (arg1 & 0x7f) | ((arg2 & 0x7f) << 7));
                            }
                        }
                    });
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                default:
                    // illegal state
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
            }
        } else if (midiState == MIDI_STATE_SIGNAL_SYSEX) {
            if (midiEvent == 0xf7) {
                // the end of message
                synchronized (systemExclusiveLock) {
                    // last written byte is for timestamp
                    int replacedEvent = systemExclusiveStream.replaceLastByte(midiEvent);
                    if (replacedEvent >= 0) {
                        timestamp = ((header & 0x3f) << 7) | (replacedEvent & 0x7f);
                    }
                    eventFireTime = calculateTimeToWait(timestamp);
                    addEventToQueue(new MidiEventWithTiming(systemExclusiveStream.toByteArray(), eventFireTime) {
                        @Override
                        public void run() {
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiSystemExclusive(sender, array);
                            }
                        }
                    });

                    // for error recovery
                    systemExclusiveRecoveryStream.reset();
                    try {
                        systemExclusiveStream.writeTo(systemExclusiveRecoveryStream);
                    } catch (IOException ignored) {
                    }
                    systemExclusiveRecoveryStream.replaceLastByte(replacedEvent);
                    systemExclusiveRecoveryStream.write(midiEvent);
                }
                midiState = MIDI_STATE_TIMESTAMP;
            } else {
                synchronized (systemExclusiveLock) {
                    systemExclusiveStream.write(midiEvent);
                }
            }
        }
    }

    /**
     * Updates incoming data
     *
     * @param data incoming data
     */
    public void parse(@NonNull byte[] data) {
        if (data.length > 1) {
            int header = data[0] & 0xff;
            for (int i = 1; i < data.length; i++) {
                parseMidiEvent(header, data[i]);
            }
        }
    }

    private final List<MidiEventWithTiming> queuedEventList = new ArrayList<>();

    /**
     * Add a event to event queue
     * @param event the MIDI Event
     */
    private void addEventToQueue(MidiEventWithTiming event) {
        synchronized (queuedEventList) {
            queuedEventList.add(event);
        }
    }

    /**
     * Runnable for MIDI event queueing
     */
    class EventDequeueRunnable implements Runnable {
        boolean isRunning = true;
        private List<MidiEventWithTiming> dequeuedEvents = new ArrayList<>();

        private final Comparator<MidiEventWithTiming> midiTimerTaskComparator = new Comparator<MidiEventWithTiming>() {
            @Override
            public int compare(MidiEventWithTiming lhs, MidiEventWithTiming rhs) {
                // sort by tick
                int tickDifference = (int) (lhs.timing - rhs.timing);
                if (tickDifference != 0) {
                    return tickDifference * 256;
                }

                int lhsMessage = lhs.arg1;
                int rhsMessage = rhs.arg1;

                // apply zero if message is empty
                if (lhsMessage == MidiEventWithTiming.INVALID) {
                    if (lhs.array == null || lhs.array.length < 1) {
                        lhsMessage = 0;
                    } else {
                        lhsMessage = lhs.array[0];
                    }
                }
                if (rhsMessage == MidiEventWithTiming.INVALID) {
                    if (rhs.array == null || rhs.array.length < 1) {
                        rhsMessage = 0;
                    } else {
                        rhsMessage = rhs.array[0];
                    }
                }

                // same timing
                // sort by the MIDI data priority order, as:
                // system message > control messages > note on > note off
                // swap the priority of note on, and note off
                int lhsInt = (lhsMessage & 0xf0);
                int rhsInt = (rhsMessage & 0xf0);

                if ((lhsInt & 0x90) == 0x80) {
                    lhsInt |= 0x10;
                } else {
                    lhsInt &= ~0x10;
                }
                if ((rhsInt & 0x90) == 0x80) {
                    rhsInt |= 0x10;
                } else {
                    rhsInt &= ~0x10;
                }

                return -(lhsInt - rhsInt);
            }
        };

        @Override
        public void run() {
            while (isRunning) {
                dequeuedEvents.clear();

                // deque events
                long currentTime = System.currentTimeMillis();
                synchronized (queuedEventList) {
                    for (MidiEventWithTiming event : queuedEventList) {
                        if (event.timing <= currentTime) {
                            // collect past events
                            dequeuedEvents.add(event);
                        }
                    }
                    queuedEventList.removeAll(dequeuedEvents);
                }

                if (dequeuedEvents.size() > 0) {
                    // sort event order
                    Collections.sort(dequeuedEvents, midiTimerTaskComparator);

                    // fire events
                    if (midiInputEventListener != null) {
                        for (MidiEventWithTiming event : dequeuedEvents) {
                            // wait for timing
                            while (event.timing < System.currentTimeMillis()) {
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException ignored) {
                                }
                            }
                            event.run();
                        }
                    }
                }

                try {
                    Thread.sleep(BUFFER_LENGTH_MILLIS);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
}
