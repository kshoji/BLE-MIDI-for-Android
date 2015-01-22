package jp.kshoji.blemidi.util;

import java.util.Timer;
import java.util.TimerTask;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;

/**
 * MIDI Parser<br />
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
    private final ReusableByteArrayOutputStream systemExclusiveStream = new ReusableByteArrayOutputStream();

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
    private boolean useTimestamp = true;
    private int timestamp = 0;
    private int lastTimestamp;
    private long lastTimestampRecorded = 0;
    private int zeroTimestampCount = 0;
    final Timer timer;

    OnMidiInputEventListener midiInputEventListener;
    final MidiInputDevice sender;

    /**
     * Constructor
     * @param sender the sender
     */
    public BleMidiParser(MidiInputDevice sender) {
        this.sender = sender;
        timer = new Timer();

        midiState = MIDI_STATE_TIMESTAMP;
        midiEventKind = 0;
        midiEventNote = 0;
        midiEventVelocity = 0;
    }

    /**
     * Sets {@link jp.kshoji.blemidi.listener.OnMidiInputEventListener}
     * @param midiInputEventListener the listener for MIDI events
     */
    public void setMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
        this.midiInputEventListener = midiInputEventListener;
    }

    /**
     * Calculate `time to wait` for the event's timestamp
     *
     * @param timestamp the event's timestamp
     * @return time to wait
     */
    private int calculateTimeToWait(int timestamp) {
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
                useTimestamp = false;
                return 0;
            }
        } else {
            zeroTimestampCount = 0;
        }

        int originalTimestamp = timestamp;
        if (timestamp < lastTimestamp) {
            timestamp += MAX_TIMESTAMP;
        }

        int result = BUFFER_LENGTH_MILLIS + timestamp - lastTimestamp - (int)(currentTimeMillis - lastTimestampRecorded);
//        Log.i(Constants.TAG, "timestamp: " + timestamp + ", lastTimestamp: " + lastTimestamp + ", currentTimeMillis: " + currentTimeMillis + ", lastTimestampRecorded:" + lastTimestampRecorded + ", wait: " + result);
        lastTimestamp = originalTimestamp;
        lastTimestampRecorded = currentTimeMillis;
        return result;
    }

    /**
     * {@link java.util.TimerTask} with MIDI event data
     */
    private abstract class MidiTimerTask extends TimerTask {
        private static final int INVALID = -1;
        final int arg1;
        final int arg2;
        final int arg3;
        final byte[] array;

        private MidiTimerTask(int arg1, int arg2, int arg3, byte[] array) {
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
            this.array = array;
        }

        /**
         * Constructor with no arguments
         */
        MidiTimerTask() {
            this(INVALID, INVALID, INVALID, null);
        }

        /**
         * Constructor with 1 argument
         * @param arg1 argument 1
         */
        MidiTimerTask(int arg1) {
            this(arg1, INVALID, INVALID, null);
        }

        /**
         * Constructor with 2 arguments
         * @param arg1 argument 1
         * @param arg2 argument 2
         */
        MidiTimerTask(int arg1, int arg2) {
            this(arg1, arg2, INVALID, null);
        }

        /**
         * Constructor with 3 arguments
         * @param arg1 argument 1
         * @param arg2 argument 2
         * @param arg3 argument 3
         */
        MidiTimerTask(int arg1, int arg2, int arg3) {
            this (arg1, arg2, arg3, null);
        }

        /**
         * Constructor with array
         * @param array data
         */
        MidiTimerTask(byte[] array) {
            this(INVALID, INVALID, INVALID, array);
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

        if (midiState == MIDI_STATE_TIMESTAMP) {
            if ((midiEvent & 0x80) == 0) {
                // running status
                midiState = MIDI_STATE_WAIT;
            }
        }

        if (midiState == MIDI_STATE_TIMESTAMP) {
            timestamp = ((header & 0x3f) << 7) | (event & 0x7f);
            midiState = MIDI_STATE_WAIT;
        } else if (midiState == MIDI_STATE_WAIT) {
            switch (midiEvent & 0xf0) {
                case 0xf0: {
                    switch (midiEvent) {
                        case 0xf0:
                            synchronized (systemExclusiveStream) {
                                systemExclusiveStream.reset();
                                systemExclusiveStream.write(midiEvent);
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
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask() {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiTuneRequest(sender);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiTuneRequest(sender);
                                }
                            }
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xf8:
                            // 0xf8 Timing Clock : 1byte
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask() {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiTimingClock(sender);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiTimingClock(sender);
                                }
                            }
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xfa:
                            // 0xfa Start : 1byte
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask() {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiStart(sender);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiStart(sender);
                                }
                            }
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xfb:
                            // 0xfb Continue : 1byte
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask() {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiContinue(sender);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiContinue(sender);
                                }
                            }
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xfc:
                            // 0xfc Stop : 1byte
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask() {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiStop(sender);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiStop(sender);
                                }
                            }
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xfe:
                            // 0xfe Active Sensing : 1byte
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask() {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiActiveSensing(sender);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiActiveSensing(sender);
                                }
                            }
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xff:
                            // 0xff Reset : 1byte
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask() {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiReset(sender);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiReset(sender);
                                }
                            }
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
                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(midiEventKind, midiEventNote) {
                                @Override
                                public void run() {
                                    midiInputEventListener.onMidiProgramChange(sender, arg1 & 0xf, arg2);
                                }
                            }, timeToWait);
                        } else {
                            midiInputEventListener.onMidiProgramChange(sender, midiEventKind & 0xf, midiEventNote);
                        }
                    }
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xd0: // channel after-touch
                    midiEventNote = midiEvent;
                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(midiEventKind, midiEventNote) {
                                @Override
                                public void run() {
                                    midiInputEventListener.onMidiChannelAftertouch(sender, arg1 & 0xf, arg2);
                                }
                            }, timeToWait);
                        } else {
                            midiInputEventListener.onMidiChannelAftertouch(sender, midiEventKind & 0xf, midiEventNote);
                        }
                    }
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xf0: {
                    switch (midiEventKind) {
                        case 0xf1:
                            // 0xf1 MIDI Time Code Quarter Frame. : 2bytes
                            midiEventNote = midiEvent;
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask(midiEventNote) {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiTimeCodeQuarterFrame(sender, arg1);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiTimeCodeQuarterFrame(sender, midiEventNote);
                                }
                            }
                            midiState = MIDI_STATE_TIMESTAMP;
                            break;
                        case 0xf3:
                            // 0xf3 Song Select. : 2bytes
                            midiEventNote = midiEvent;
                            if (midiInputEventListener != null) {
                                int timeToWait = calculateTimeToWait(timestamp);
                                if (useTimestamp && timeToWait > 0) {
                                    timer.schedule(new MidiTimerTask(midiEventNote) {
                                        @Override
                                        public void run() {
                                            midiInputEventListener.onMidiSongSelect(sender, arg1);
                                        }
                                    }, timeToWait);
                                } else {
                                    midiInputEventListener.onMidiSongSelect(sender, midiEventNote);
                                }
                            }
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
                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(midiEventKind, midiEventNote, midiEventVelocity) {
                                @Override
                                public void run() {
                                    midiInputEventListener.onMidiNoteOff(sender, arg1 & 0xf, arg2, arg3);
                                }
                            }, timeToWait);
                        } else {
                            midiInputEventListener.onMidiNoteOff(sender, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                        }
                    }
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0x90: // note on
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(midiEventKind, midiEventNote, midiEventVelocity) {
                                @Override
                                public void run() {
                                    if (midiEventVelocity == 0) {
                                        midiInputEventListener.onMidiNoteOff(sender, arg1 & 0xf, arg2, arg3);
                                    } else {
                                        midiInputEventListener.onMidiNoteOn(sender, arg1 & 0xf, arg2, arg3);
                                    }
                                }
                            }, timeToWait);
                        } else {
                            if (midiEventVelocity == 0) {
                                midiInputEventListener.onMidiNoteOff(sender, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                            } else {
                                midiInputEventListener.onMidiNoteOn(sender, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                            }
                        }
                    }
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xa0: // control polyphonic key pressure
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(midiEventKind, midiEventNote, midiEventVelocity) {
                                @Override
                                public void run() {
                                    midiInputEventListener.onMidiPolyphonicAftertouch(sender, arg1 & 0xf, arg2, arg3);
                                }
                            }, timeToWait);
                        } else {
                            midiInputEventListener.onMidiPolyphonicAftertouch(sender, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                        }
                    }
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
                                    if (midiInputEventListener != null) {
                                        int timeToWait = calculateTimeToWait(timestamp);
                                        if (useTimestamp && timeToWait > 0) {
                                            timer.schedule(new MidiTimerTask(midiEventKind, parameterNumber, parameterValue) {
                                                @Override
                                                public void run() {
                                                    midiInputEventListener.onRPNMessage(sender, arg1 & 0xf, arg2 & 0x3fff, arg3 & 0x3fff);
                                                }
                                            }, timeToWait);
                                        } else {
                                            midiInputEventListener.onRPNMessage(sender, midiEventKind & 0xf, parameterNumber & 0x3fff, parameterValue & 0x3fff);
                                        }
                                    }
                                } else if (parameterMode == PARAMETER_MODE_NRPN) {
                                    if (midiInputEventListener != null) {
                                        int timeToWait = calculateTimeToWait(timestamp);
                                        if (useTimestamp && timeToWait > 0) {
                                            timer.schedule(new MidiTimerTask(midiEventKind, parameterNumber, parameterValue) {
                                                @Override
                                                public void run() {
                                                    midiInputEventListener.onNRPNMessage(sender, arg1 & 0xf, arg2 & 0x3fff, arg3 & 0x3fff);
                                                }
                                            }, timeToWait);
                                        } else {
                                            midiInputEventListener.onNRPNMessage(sender, midiEventKind & 0xf, parameterNumber & 0x3fff, parameterValue & 0x3fff);
                                        }
                                    }
                                }
                            }
                            break;
                        case 6:
                            // data MSB
                            parameterValue &= 0x007f;
                            parameterValue |= (midiEventVelocity & 0x7f) << 7;

                            if (parameterNumber != 0x3fff) {
                                if (parameterMode == PARAMETER_MODE_RPN) {
                                    if (midiInputEventListener != null) {
                                        int timeToWait = calculateTimeToWait(timestamp);
                                        if (useTimestamp && timeToWait > 0) {
                                            timer.schedule(new MidiTimerTask(midiEventKind, parameterNumber, parameterValue) {
                                                @Override
                                                public void run() {
                                                    midiInputEventListener.onRPNMessage(sender, arg1 & 0xf, arg2 & 0x3fff, arg3 & 0x3fff);
                                                }
                                            }, timeToWait);
                                        } else {
                                            midiInputEventListener.onRPNMessage(sender, midiEventKind & 0xf, parameterNumber & 0x3fff, parameterValue & 0x3fff);
                                        }
                                    }
                                } else if (parameterMode == PARAMETER_MODE_NRPN) {
                                    if (midiInputEventListener != null) {
                                        int timeToWait = calculateTimeToWait(timestamp);
                                        if (useTimestamp && timeToWait > 0) {
                                            timer.schedule(new MidiTimerTask(midiEventKind, parameterNumber, parameterValue) {
                                                @Override
                                                public void run() {
                                                    midiInputEventListener.onNRPNMessage(sender, arg1 & 0xf, arg2 & 0x3fff, arg3 & 0x3fff);
                                                }
                                            }, timeToWait);
                                        } else {
                                            midiInputEventListener.onNRPNMessage(sender, midiEventKind & 0xf, parameterNumber & 0x3fff, parameterValue & 0x3fff);
                                        }
                                    }
                                }
                            }
                            break;
                        default:
                            // do nothing
                            break;
                    }

                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(midiEventKind, midiEventNote, midiEventVelocity) {
                                @Override
                                public void run() {
                                    midiInputEventListener.onMidiControlChange(sender, arg1 & 0xf, arg2, arg3);
                                }
                            }, timeToWait);
                        } else {
                            midiInputEventListener.onMidiControlChange(sender, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                        }
                    }
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xe0: // pitch bend
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(midiEventKind, midiEventNote, midiEventVelocity) {
                                @Override
                                public void run() {
                                    midiInputEventListener.onMidiPitchWheel(sender, arg1 & 0xf, (arg2 & 0x7f) | ((arg3 & 0x7f) << 7));
                                }
                            }, timeToWait);
                        } else {
                            midiInputEventListener.onMidiPitchWheel(sender, midiEventKind & 0xf, (midiEventNote & 0x7f) | ((midiEventVelocity & 0x7f) << 7));
                        }
                    }
                    midiState = MIDI_STATE_TIMESTAMP;
                    break;
                case 0xf0: // Song Position Pointer.
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(midiEventNote, midiEventVelocity) {
                                @Override
                                public void run() {
                                    midiInputEventListener.onMidiSongPositionPointer(sender, (arg1 & 0x7f) | ((arg2 & 0x7f) << 7));
                                }
                            }, timeToWait);
                        } else {
                            midiInputEventListener.onMidiSongPositionPointer(sender, (midiEventNote & 0x7f) | ((midiEventVelocity & 0x7f) << 7));
                        }
                    }
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
                synchronized (systemExclusiveStream) {
                    systemExclusiveStream.write(midiEvent);
                    if (midiInputEventListener != null) {
                        int timeToWait = calculateTimeToWait(timestamp);
                        if (useTimestamp && timeToWait > 0) {
                            timer.schedule(new MidiTimerTask(systemExclusiveStream.toByteArray()) {
                                @Override
                                public void run() {
                                    midiInputEventListener.onMidiSystemExclusive(sender, array);
                                }
                            }, timeToWait);
                        } else {
                            midiInputEventListener.onMidiSystemExclusive(sender, systemExclusiveStream.toByteArray());
                        }
                    }
                }
                midiState = MIDI_STATE_TIMESTAMP;
            } else {
                synchronized (systemExclusiveStream) {
                    systemExclusiveStream.write(midiEvent);
                }
            }
        }
    }

    /**
     * Updates incoming data
     * @param data incoming data
     */
    public void parse(byte[] data) {
        if (data.length > 1) {
            int header = data[0] & 0xff;
            for (int i = 1; i < data.length; i++) {
                parseMidiEvent(header, data[i]);
            }
        }
    }
}
