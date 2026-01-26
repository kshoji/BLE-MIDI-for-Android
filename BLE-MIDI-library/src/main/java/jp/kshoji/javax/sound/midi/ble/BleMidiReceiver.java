package jp.kshoji.javax.sound.midi.ble;

import androidx.annotation.NonNull;

import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.javax.sound.midi.MetaMessage;
import jp.kshoji.javax.sound.midi.MidiDevice;
import jp.kshoji.javax.sound.midi.MidiDeviceReceiver;
import jp.kshoji.javax.sound.midi.MidiMessage;
import jp.kshoji.javax.sound.midi.ShortMessage;
import jp.kshoji.javax.sound.midi.SysexMessage;

/**
 * {@link jp.kshoji.javax.sound.midi.Receiver} implementation
 *
 * @author K.Shoji
 */
public final class BleMidiReceiver implements MidiDeviceReceiver {
    private final BleMidiDevice bleMidiDevice;

    /**
     * Constructor
     *
     * @param bleMidiDevice the device
     */
    public BleMidiReceiver(@NonNull BleMidiDevice bleMidiDevice) {
        this.bleMidiDevice = bleMidiDevice;
    }

    @NonNull
    @Override
    public MidiDevice getMidiDevice() {
        return bleMidiDevice;
    }

    @Override
    public void send(@NonNull MidiMessage message, long l) {
        MidiOutputDevice outputDevice = bleMidiDevice.getMidiOutputDevice();

        if (outputDevice == null) {
            // already closed
            return;
        }

        if (message instanceof MetaMessage) {
            // ignored
        } else if (message instanceof SysexMessage) {
            final SysexMessage sysexMessage = (SysexMessage) message;
            outputDevice.sendMidiSystemExclusive(sysexMessage.getData());
        } else if (message instanceof ShortMessage) {
            final ShortMessage shortMessage = (ShortMessage) message;
            switch (shortMessage.getCommand()) {
                case ShortMessage.ACTIVE_SENSING:
                    outputDevice.sendMidiActiveSensing();
                    break;
                case ShortMessage.CONTINUE:
                    outputDevice.sendMidiContinue();
                    break;
                case ShortMessage.MIDI_TIME_CODE:
                    outputDevice.sendMidiTimeCodeQuarterFrame(shortMessage.getData1());
                    break;
                case ShortMessage.SONG_POSITION_POINTER:
                    outputDevice.sendMidiSongPositionPointer(shortMessage.getData1() | (shortMessage.getData2() << 7));
                    break;
                case ShortMessage.SONG_SELECT:
                    outputDevice.sendMidiSongSelect(shortMessage.getData1());
                    break;
                case ShortMessage.START:
                    outputDevice.sendMidiStart();
                    break;
                case ShortMessage.STOP:
                    outputDevice.sendMidiStop();
                    break;
                case ShortMessage.SYSTEM_RESET:
                    outputDevice.sendMidiReset();
                    break;
                case ShortMessage.TIMING_CLOCK:
                    outputDevice.sendMidiTimingClock();
                    break;
                case ShortMessage.TUNE_REQUEST:
                    outputDevice.sendMidiTuneRequest();
                    break;
                case ShortMessage.CHANNEL_PRESSURE:
                    outputDevice.sendMidiChannelAftertouch(shortMessage.getChannel(), shortMessage.getData1());
                    break;
                case ShortMessage.CONTROL_CHANGE:
                    outputDevice.sendMidiControlChange(shortMessage.getChannel(), shortMessage.getData1(), shortMessage.getData2());
                    break;
                case ShortMessage.NOTE_OFF:
                    outputDevice.sendMidiNoteOff(shortMessage.getChannel(), shortMessage.getData1(), shortMessage.getData2());
                    break;
                case ShortMessage.NOTE_ON:
                    outputDevice.sendMidiNoteOn(shortMessage.getChannel(), shortMessage.getData1(), shortMessage.getData2());
                    break;
                case ShortMessage.PITCH_BEND:
                    outputDevice.sendMidiPitchWheel(shortMessage.getChannel(), shortMessage.getData1() | (shortMessage.getData2() << 7));
                    break;
                case ShortMessage.POLY_PRESSURE:
                    outputDevice.sendMidiPolyphonicAftertouch(shortMessage.getChannel(), shortMessage.getData1(), shortMessage.getData2());
                    break;
                case ShortMessage.PROGRAM_CHANGE:
                    outputDevice.sendMidiProgramChange(shortMessage.getChannel(), shortMessage.getData1());
                    break;
                default:
            }
        }
    }

    public void open() {
        // do nothing
    }

    @Override
    public void close() {
        // do nothing
    }
}
