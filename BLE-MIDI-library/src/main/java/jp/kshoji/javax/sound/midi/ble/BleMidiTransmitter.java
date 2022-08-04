package jp.kshoji.javax.sound.midi.ble;

import android.util.Log;

import androidx.annotation.NonNull;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.Constants;
import jp.kshoji.javax.sound.midi.InvalidMidiDataException;
import jp.kshoji.javax.sound.midi.MidiDevice;
import jp.kshoji.javax.sound.midi.MidiDeviceTransmitter;
import jp.kshoji.javax.sound.midi.Receiver;
import jp.kshoji.javax.sound.midi.ShortMessage;
import jp.kshoji.javax.sound.midi.SysexMessage;

/**
 * {@link jp.kshoji.javax.sound.midi.Transmitter} implementation
 *
 * @author K.Shoji
 */
public final class BleMidiTransmitter implements MidiDeviceTransmitter, OnMidiInputEventListener {
    private final BleMidiDevice bleMidiDevice;
    private Receiver receiver;

    /**
     * Constructor
     *
     * @param bleMidiDevice the device
     */
    public BleMidiTransmitter(@NonNull BleMidiDevice bleMidiDevice) {
        this.bleMidiDevice = bleMidiDevice;
        open();
    }

    @NonNull
    @Override
    public MidiDevice getMidiDevice() {
        return bleMidiDevice;
    }

    @Override
    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public Receiver getReceiver() {
        return receiver;
    }

    public void open() {
        MidiInputDevice midiInputDevice = bleMidiDevice.getMidiInputDevice();
        if (midiInputDevice != null) {
            midiInputDevice.setOnMidiInputEventListener(this);
        }
    }

    @Override
    public void close() {
        MidiInputDevice midiInputDevice = bleMidiDevice.getMidiInputDevice();
        if (midiInputDevice != null) {
            midiInputDevice.setOnMidiInputEventListener(null);
        }
    }

    @Override
    public void onMidiSystemExclusive(@NonNull MidiInputDevice sender, @NonNull byte[] systemExclusive) {
        if (receiver != null) {
            try {
                final SysexMessage message = new SysexMessage();
                message.setMessage(systemExclusive, systemExclusive.length);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiNoteOff(@NonNull MidiInputDevice sender, int channel, int note, int velocity) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.NOTE_OFF, channel, note, velocity);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiNoteOn(@NonNull MidiInputDevice sender, int channel, int note, int velocity) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiPolyphonicAftertouch(@NonNull MidiInputDevice sender, int channel, int note, int pressure) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.POLY_PRESSURE, channel, note, pressure);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiControlChange(@NonNull MidiInputDevice sender, int channel, int function, int value) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.CONTROL_CHANGE, channel, function, value);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiProgramChange(@NonNull MidiInputDevice sender, int channel, int program) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.PROGRAM_CHANGE, channel, program, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiChannelAftertouch(@NonNull MidiInputDevice sender, int channel, int pressure) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.CHANNEL_PRESSURE, channel, pressure, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiPitchWheel(@NonNull MidiInputDevice sender, int channel, int amount) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.PITCH_BEND, channel, amount & 0x7f, (amount >> 7) & 0x7f);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiTimeCodeQuarterFrame(@NonNull MidiInputDevice sender, int timing) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.MIDI_TIME_CODE, timing, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiSongSelect(@NonNull MidiInputDevice sender, int song) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.SONG_SELECT, song, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiSongPositionPointer(@NonNull MidiInputDevice sender, int position) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.SONG_POSITION_POINTER, (position >> 7) & 0x7f, position & 0x7f);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiTuneRequest(@NonNull MidiInputDevice sender) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.TUNE_REQUEST, 0, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiTimingClock(@NonNull MidiInputDevice sender) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.TIMING_CLOCK, 0, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiStart(@NonNull MidiInputDevice sender) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.START, 0, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiContinue(@NonNull MidiInputDevice sender) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.CONTINUE, 0, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiStop(@NonNull MidiInputDevice sender) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.STOP, 0, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiActiveSensing(@NonNull MidiInputDevice sender) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.ACTIVE_SENSING, 0, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onMidiReset(@NonNull MidiInputDevice sender) {
        if (receiver != null) {
            try {
                final ShortMessage message = new ShortMessage();
                message.setMessage(ShortMessage.SYSTEM_RESET, 0, 0);
                receiver.send(message, -1);
            } catch (final InvalidMidiDataException e) {
                Log.d(Constants.TAG, "InvalidMidiDataException", e);
            }
        }
    }

    @Override
    public void onRPNMessage(@NonNull MidiInputDevice sender, int channel, int function, int value) {
        // do nothing in this implementation
    }

    @Override
    public void onNRPNMessage(@NonNull MidiInputDevice sender, int channel, int function, int value) {
        // do nothing in this implementation
    }
}