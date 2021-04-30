package jp.kshoji.javax.sound.midi.ble;

import android.support.annotation.NonNull;

import jp.kshoji.blemidi.device.Midi2OutputDevice;
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
public final class BleMidi2Receiver implements MidiDeviceReceiver {
    private final BleMidi2Device bleMidiDevice;
    private int group;

    /**
     * Constructor
     *
     * @param bleMidiDevice the device
     */
    public BleMidi2Receiver(@NonNull BleMidi2Device bleMidiDevice) {
        this.bleMidiDevice = bleMidiDevice;
        group = 0;
    }

    @NonNull
    @Override
    public MidiDevice getMidiDevice() {
        return bleMidiDevice;
    }

    @Override
    public void send(@NonNull MidiMessage message, long l) {
        Midi2OutputDevice outputDevice = bleMidiDevice.getMidiOutputDevice();

        if (outputDevice == null) {
            // already closed
            return;
        }

        if (message instanceof MetaMessage) {
            final MetaMessage metaMessage = (MetaMessage) message;
            byte[] metaMessageData = metaMessage.getData();
            if (metaMessageData.length > 0) {
                switch (metaMessageData[0] & 0xff) {
                    case 0xf1:
                        if (metaMessageData.length > 1) {
                            outputDevice.sendMidiTimeCodeQuarterFrame(group, metaMessageData[1] & 0x7f);
                        }
                        break;
                    case 0xf2:
                        if (metaMessageData.length > 1) {
                            outputDevice.sendMidiSongPositionPointer(group, metaMessageData[1] & 0x7f);
                        }
                        break;
                    case 0xf3:
                        if (metaMessageData.length > 1) {
                            outputDevice.sendMidiSongSelect(group, metaMessageData[1] & 0x7f);
                        }
                        break;
                    case 0xf6:
                        outputDevice.sendMidiTuneRequest(group);
                        break;
                    case 0xf8:
                        outputDevice.sendMidiTimingClock(group);
                        break;
                    case 0xfa:
                        outputDevice.sendMidiStart(group);
                        break;
                    case 0xfb:
                        outputDevice.sendMidiContinue(group);
                        break;
                    case 0xfc:
                        outputDevice.sendMidiStop(group);
                        break;
                    case 0xfe:
                        outputDevice.sendMidiActiveSensing(group);
                        break;
                    case 0xff:
                        outputDevice.sendMidiReset(group);
                        break;
                }
            }
        } else if (message instanceof SysexMessage) {
            final SysexMessage sysexMessage = (SysexMessage) message;
            outputDevice.sendMidi1SystemExclusive(group, sysexMessage.getData());
        } else if (message instanceof ShortMessage) {
            final ShortMessage shortMessage = (ShortMessage) message;
            switch (shortMessage.getCommand()) {
                case ShortMessage.CHANNEL_PRESSURE:
                    outputDevice.sendMidi1ChannelAftertouch(group, shortMessage.getChannel(), shortMessage.getData1());
                    break;
                case ShortMessage.CONTROL_CHANGE:
                    outputDevice.sendMidi1ControlChange(group, shortMessage.getChannel(), shortMessage.getData1(), shortMessage.getData2());
                    break;
                case ShortMessage.NOTE_OFF:
                    outputDevice.sendMidi1NoteOff(group, shortMessage.getChannel(), shortMessage.getData1(), shortMessage.getData2());
                    break;
                case ShortMessage.NOTE_ON:
                    outputDevice.sendMidi1NoteOn(group, shortMessage.getChannel(), shortMessage.getData1(), shortMessage.getData2());
                    break;
                case ShortMessage.PITCH_BEND:
                    outputDevice.sendMidi1PitchWheel(group, shortMessage.getChannel(), shortMessage.getData1() | (shortMessage.getData2() << 7));
                    break;
                case ShortMessage.POLY_PRESSURE:
                    outputDevice.sendMidi1PolyphonicAftertouch(group, shortMessage.getChannel(), shortMessage.getData1(), shortMessage.getData2());
                    break;
                case ShortMessage.PROGRAM_CHANGE:
                    outputDevice.sendMidi1ProgramChange(group, shortMessage.getChannel(), shortMessage.getData1());
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


    /**
     * Get the group of this MidiReceiver
     *
     * @return the group 0-15
     */
    public int getGroup() {
        return group;
    }

    /**
     * Set the group of this MidiReceiver
     *
     * @param group the group 0-15
     */
    public void setGroup(int group) {
        this.group = group;
    }
}
