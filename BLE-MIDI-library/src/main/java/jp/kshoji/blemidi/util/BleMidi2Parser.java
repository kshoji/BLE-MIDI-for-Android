package jp.kshoji.blemidi.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import jp.kshoji.blemidi.device.Midi2InputDevice;
import jp.kshoji.blemidi.listener.OnMidi2InputEventListener;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;

/**
 * BLE MIDI 2.0 Parser<br />
 * The protocol compatible with Apple's `MIDI over Bluetooth LE` specification.<br />
 * One BleMidiParser instance belongs to one MidiInputDevice instance.
 *
 * @author K.Shoji
 */
public final class BleMidi2Parser {
    // for Universal MIDI Packet
    int waitingMessageIndex = 0;
    final byte[] waitingMessage = new byte[16];
    int waitingMessageType = 0;
    int waitingMessageReadBytes = 0;

    // read buffer
    private final byte[] readBuffer;
    private final byte[] read;
    private int readBufferSize = 0;

    private OnMidi2InputEventListener midiEventListener = null;
    private final Midi2InputDevice sender;

    private static final int GROUP_COUNT = 16;
    private static final int STREAM_COUNT = 256;

    // for SysEx
    final int[] sysexLength = new int[GROUP_COUNT];
    final int[][] sysex2Length = new int[GROUP_COUNT][];
    int sysexIndex = 0;
    final ReusableByteArrayOutputStream[] systemExclusive = new ReusableByteArrayOutputStream[GROUP_COUNT];
    final ReusableByteArrayOutputStream[][] systemExclusive2 = new ReusableByteArrayOutputStream[GROUP_COUNT][];

    // for Mixed Data Set
    final byte[][] mixedDataSetHeader = new byte[GROUP_COUNT][];
    final byte[][] mixedDataSetPayload = new byte[GROUP_COUNT][];

    /**
     * Constructor
     *
     * @param sender the sender
     */
    public BleMidi2Parser(@NonNull final Midi2InputDevice sender) {
        this.sender = sender;

        readBuffer = new byte[8192];
        read = new byte[8192];

        for (int i = 0; i < GROUP_COUNT; i++) {
            sysexLength[i] = 0;
            sysex2Length[i] = new int[STREAM_COUNT];
            systemExclusive[i] = new ReusableByteArrayOutputStream();
            systemExclusive2[i] = new ReusableByteArrayOutputStream[STREAM_COUNT];
            mixedDataSetHeader[i] = new byte[14];
            mixedDataSetPayload[i] = new byte[14];
        }
    }

    /**
     * Sets {@link OnMidiInputEventListener}
     *
     * @param midiEventListener the listener for MIDI events
     */
    public void setMidiInputEventListener(@Nullable OnMidi2InputEventListener midiEventListener) {
        this.midiEventListener = midiEventListener;
    }

    /**
     * Stops the internal Thread
     */
    public void stop() {
    }

    /**
     * Updates incoming data
     *
     * @param data incoming data
     */
    public synchronized void parse(@NonNull byte[] data) {
        if (data.length > 0) {
            System.arraycopy(data, 0, readBuffer, readBufferSize, data.length);
            readBufferSize += data.length;

            if (readBufferSize < 4) {
                // more data needed
                return;
            }

            // Universal MIDI packet data stream: 4 bytes boundary
            int readSize = readBufferSize / 4 * 4;
            System.arraycopy(readBuffer, 0, read, 0, readSize); // fill the read array

            // keep unread bytes
            int unreadSize = readBufferSize - readSize;
            if (unreadSize > 0) {
                System.arraycopy(readBuffer, readSize, readBuffer, 0, unreadSize);
                readBufferSize = unreadSize;
            } else {
                readBufferSize = 0;
            }

            int group = 0;
            int messageType;
            int byte1;
            int byte2;
            int byte3;
            int i;
            for (i = 0; i < readSize; i += 4) {
                if (waitingMessageType == 0) {
                    messageType = (read[i] >> 4) & 0xf;
                    group = read[i] & 0xf;
                } else {
                    messageType = waitingMessageType;
                }
                byte1 = read[i + 1] & 0xff;
                byte2 = read[i + 2] & 0xff;
                byte3 = read[i + 3] & 0xff;

                for (waitingMessageIndex = 0; waitingMessageIndex < 4; waitingMessageIndex++) {
                    waitingMessage[waitingMessageReadBytes + waitingMessageIndex] = read[i + waitingMessageIndex];
                }

                switch (messageType) {
                    case 0: // region messageType 0
                        switch ((byte1 >> 4) & 0xf) {
                            case 0:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiNoop(sender, group);
                                }
                                break;
                            case 1:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiJitterReductionClock(sender, group, ((byte2 & 0xff) << 8) | (byte3 & 0xff));
                                }
                                break;
                            case 2:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiJitterReductionTimestamp(sender, group, ((byte2 & 0xff) << 8) | (byte3 & 0xff));
                                }
                                break;
                        }
                        // endregion
                        break;
                    case 1: // region messageType 1
                        switch (byte1 & 0xff) {
                            case 0xf1:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiTimeCodeQuarterFrame(sender, group, byte2 & 0x7f);
                                }
                                break;
                            case 0xf2:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiSongPositionPointer(sender, group, (byte2 & 0x7f) | ((byte3 & 0x7f) << 7));
                                }
                                break;
                            case 0xf3:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiSongSelect(sender, group, byte2 & 0x7f);
                                }
                                break;
                            case 0xf6:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiTuneRequest(sender, group);
                                }
                                break;
                            case 0xf8:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiTimingClock(sender, group);
                                }
                                break;
                            case 0xfa:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiStart(sender, group);
                                }
                                break;
                            case 0xfb:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiContinue(sender, group);
                                }
                                break;
                            case 0xfc:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiStop(sender, group);
                                }
                                break;
                            case 0xfe:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiActiveSensing(sender, group);
                                }
                                break;
                            case 0xff:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidiReset(sender, group);
                                }
                                break;
                        }
                        // endregion
                        break;
                    case 2: // region messageType 2
                        switch (byte1 & 0xf0) {
                            case 0x80:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidi1NoteOff(sender, group, byte1 & 0xf, byte2 & 0x7f, byte3 & 0x7f);
                                }
                                break;
                            case 0x90:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidi1NoteOn(sender, group, byte1 & 0xf, byte2 & 0x7f, byte3 & 0x7f);
                                }
                                break;
                            case 0xa0:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidi1PolyphonicAftertouch(sender, group, byte1 & 0xf, byte2 & 0x7f, byte3 & 0x7f);
                                }
                                break;
                            case 0xb0:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidi1ControlChange(sender, group, byte1 & 0xf, byte2 & 0x7f, byte3 & 0x7f);
                                }
                                break;
                            case 0xc0:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidi1ProgramChange(sender, group, byte1 & 0xf, byte2 & 0x7f);
                                }
                                break;
                            case 0xd0:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidi1ChannelAftertouch(sender, group, byte1 & 0xf, byte2 & 0x7f);
                                }
                                break;
                            case 0xe0:
                                if (midiEventListener != null) {
                                    midiEventListener.onMidi1PitchWheel(sender, group, byte1 & 0xf, (byte2 & 0x7f) | ((byte3 & 0x7f) << 7));
                                }
                                break;
                        }
                        // endregion
                        break;
                    case 3: // region messageType 3
                        switch (byte1 & 0xf0) {
                            case 0x00:
                                if (waitingMessageReadBytes == 0) {
                                    systemExclusive[group].reset();
                                    sysexLength[group] = byte1 & 0xf;
                                }
                                waitingMessageType = 3;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 8) {
                                    for (sysexIndex = 0; sysexIndex < sysexLength[group]; sysexIndex++) {
                                        systemExclusive[group].write(waitingMessage[3 + sysexIndex]);
                                    }
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi1SystemExclusive(sender, group, systemExclusive[group].toByteArray());
                                    }
                                    systemExclusive[group].reset();
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                            case 0x10:
                                if (waitingMessageReadBytes == 0) {
                                    systemExclusive[group].reset();
                                    sysexLength[group] = byte1 & 0xf;
                                }
                                waitingMessageType = 3;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 8) {
                                    for (sysexIndex = 0; sysexIndex < sysexLength[group]; sysexIndex++) {
                                        systemExclusive[group].write(waitingMessage[3 + sysexIndex]);
                                    }
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                            case 0x20:
                                if (waitingMessageReadBytes == 0) {
                                    sysexLength[group] = byte1 & 0xf;
                                }
                                waitingMessageType = 3;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 8) {
                                    for (sysexIndex = 0; sysexIndex < sysexLength[group]; sysexIndex++) {
                                        systemExclusive[group].write(waitingMessage[3 + sysexIndex]);
                                    }
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                            case 0x30:
                                if (waitingMessageReadBytes == 0) {
                                    sysexLength[group] = byte1 & 0xf;
                                }
                                waitingMessageType = 3;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 8) {
                                    for (sysexIndex = 0; sysexIndex < sysexLength[group]; sysexIndex++) {
                                        systemExclusive[group].write(waitingMessage[3 + sysexIndex]);
                                    }
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi1SystemExclusive(sender, group, systemExclusive[group].toByteArray());
                                    }
                                    systemExclusive[group].reset();
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                        }
                        // endregion
                        break;
                    case 4: // region messageType 4
                        waitingMessageType = 4;
                        waitingMessageReadBytes += 4;
                        if (waitingMessageReadBytes == 8) {
                            switch (waitingMessage[1] & 0xf0) {
                                case 0x00:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiRegisteredPerNoteController(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, waitingMessage[3] & 0xff, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0x10:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiAssignablePerNoteController(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, waitingMessage[3] & 0xff, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0x20:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiRegisteredController(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, waitingMessage[3] & 0x7f, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0x30:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiAssignableController(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, waitingMessage[3] & 0x7f, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0x40:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiRelativeRegisteredController(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, waitingMessage[3] & 0x7f, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0x50:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiRelativeAssignableController(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, waitingMessage[3] & 0x7f, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0x60:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiPerNotePitchWheel(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0x80:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2NoteOff(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, ((waitingMessage[4] & 0xff) << 8) | (waitingMessage[5] & 0xff), waitingMessage[3] & 0xff, ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0x90:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2NoteOn(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, ((waitingMessage[4] & 0xff) << 8) | (waitingMessage[5] & 0xff), waitingMessage[3] & 0xff, ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0xa0:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2PolyphonicAftertouch(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0xb0:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2ControlChange(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0xc0:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2ProgramChange(sender, group, waitingMessage[1] & 0xf, waitingMessage[3] & 0xff, waitingMessage[4] & 0x7f, ((waitingMessage[6] & 0x7f) << 7) | (waitingMessage[7] & 0x7f));
                                    }
                                    break;
                                case 0xd0:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2ChannelAftertouch(sender, group, waitingMessage[1] & 0xf, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0xe0:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2PitchWheel(sender, group, waitingMessage[1] & 0xf, ((waitingMessage[4] & 0xff) << 24) | ((waitingMessage[5] & 0xff) << 16) | ((waitingMessage[6] & 0xff) << 8) | (waitingMessage[7] & 0xff));
                                    }
                                    break;
                                case 0xf0:
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiPerNoteManagement(sender, group, waitingMessage[1] & 0xf, waitingMessage[2] & 0x7f, waitingMessage[3] & 0xff);
                                    }
                                    break;
                            }
                            waitingMessageType = 0;
                            waitingMessageReadBytes = 0;
                        }
                        // endregion
                        break;
                    case 5: // region messageType 5
                        switch (byte1 & 0xf0) {
                            case 0x00:
                                if (waitingMessageReadBytes == 0) {
                                    if (systemExclusive2[group][waitingMessage[2] & 0xff] == null) {
                                        systemExclusive2[group][waitingMessage[2] & 0xff] = new ReusableByteArrayOutputStream();
                                    }
                                    systemExclusive2[group][waitingMessage[2] & 0xff].reset();
                                    sysex2Length[group][waitingMessage[2] & 0xff] = waitingMessage[1] & 0xf;
                                }
                                waitingMessageType = 5;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 16) {
                                    for (sysexIndex = 0; sysexIndex < sysex2Length[group][waitingMessage[2] & 0xff]; sysexIndex++) {
                                        systemExclusive2[group][waitingMessage[2] & 0xff].write(waitingMessage[3 + sysexIndex]);
                                    }
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2SystemExclusive(sender, group, waitingMessage[2] & 0xff, systemExclusive2[group][waitingMessage[2] & 0xff].toByteArray());
                                    }
                                    systemExclusive2[group][waitingMessage[2] & 0xff].reset();
                                    sysex2Length[group][waitingMessage[2] & 0xff] = 0;
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                            case 0x10:
                                if (waitingMessageReadBytes == 0) {
                                    if (systemExclusive2[group][waitingMessage[2] & 0xff] == null) {
                                        systemExclusive2[group][waitingMessage[2] & 0xff] = new ReusableByteArrayOutputStream();
                                    }
                                    systemExclusive2[group][waitingMessage[2] & 0xff].reset();
                                    sysex2Length[group][waitingMessage[2] & 0xff] = byte1 & 0xf;
                                }
                                waitingMessageType = 5;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 16) {
                                    for (sysexIndex = 0; sysexIndex < sysex2Length[group][waitingMessage[2] & 0xff]; sysexIndex++) {
                                        systemExclusive2[group][waitingMessage[2] & 0xff].write(waitingMessage[3 + sysexIndex]);
                                    }
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                            case 0x20:
                                if (waitingMessageReadBytes == 0) {
                                    sysex2Length[group][waitingMessage[2] & 0xff] = byte1 & 0xf;
                                }
                                waitingMessageType = 5;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 16) {
                                    for (sysexIndex = 0; sysexIndex < sysex2Length[group][waitingMessage[2] & 0xff]; sysexIndex++) {
                                        systemExclusive2[group][waitingMessage[2] & 0xff].write(waitingMessage[3 + sysexIndex]);
                                    }
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                            case 0x30:
                                if (waitingMessageReadBytes == 0) {
                                    sysex2Length[group][waitingMessage[2] & 0xff] = waitingMessage[1] & 0xf;
                                }
                                waitingMessageType = 5;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 16) {
                                    for (sysexIndex = 0; sysexIndex < sysex2Length[group][waitingMessage[2] & 0xff]; sysexIndex++) {
                                        systemExclusive2[group][waitingMessage[2] & 0xff].write(waitingMessage[3 + sysexIndex]);
                                    }
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidi2SystemExclusive(sender, group, waitingMessage[2] & 0xff, systemExclusive2[group][waitingMessage[2] & 0xff].toByteArray());
                                    }
                                    systemExclusive2[group][waitingMessage[2] & 0xff].reset();
                                    sysex2Length[group][waitingMessage[2] & 0xff] = 0;
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                            case 0x80:
                                waitingMessageType = 5;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 16) {
                                    System.arraycopy(waitingMessage, 2, mixedDataSetHeader[group], 0, 14);
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiMixedDataSetHeader(sender, group, waitingMessage[1] & 0xf, mixedDataSetHeader[group]);
                                    }
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                            case 0x90:
                                waitingMessageType = 5;
                                waitingMessageReadBytes += 4;
                                if (waitingMessageReadBytes == 16) {
                                    System.arraycopy(waitingMessage, 2, mixedDataSetPayload[group], 0, 14);
                                    if (midiEventListener != null) {
                                        midiEventListener.onMidiMixedDataSetPayload(sender, group, waitingMessage[1] & 0xf, mixedDataSetPayload[group]);
                                    }
                                    waitingMessageType = 0;
                                    waitingMessageReadBytes = 0;
                                }
                                break;
                        }
                        // endregion
                        break;
                    default:
                        // do nothing.
                        break;
                }
            }
        }
    }
}
