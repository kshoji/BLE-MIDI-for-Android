package jp.kshoji.blemidi.device;

/**
 * Represents BLE MIDI Output Device
 *
 * @author K.Shoji
 */
public abstract class MidiOutputDevice {

    public static final int MAX_TIMESTAMP = 8192;

    /**
     * Transfer data
     *
     * @param writeBuffer byte array to write
     */
    protected abstract void transferData(byte[] writeBuffer);

    /**
     * Obtains the device name
     *
     * @return device name
     */
    public abstract String getDeviceName();

    /**
     * Obtains the device address
     *
     * @return device address
     */
    public abstract String getDeviceAddress();

    @Override
    public final String toString() {
        return getDeviceName();
    }

    /**
     * Sends MIDI message to output device.
     *
     * @param byte1 the first byte
     */
    private void sendMidiMessage(int byte1) {
        long timestamp = System.currentTimeMillis() % MAX_TIMESTAMP;
        byte[] writeBuffer = new byte[] { (byte) (0x80 | ((timestamp >> 7) & 0x3f)), (byte) (0x80 | (timestamp & 0x7f)), (byte) byte1 };

        transferData(writeBuffer);
    }

    /**
     * Sends MIDI message to output device.
     *
     * @param byte1 the first byte
     * @param byte2 the second byte
     */
    private void sendMidiMessage(int byte1, int byte2) {
        byte[] writeBuffer = new byte[4];
        long timestamp = System.currentTimeMillis() % MAX_TIMESTAMP;

        writeBuffer[0] = (byte) (0x80 | ((timestamp >> 7) & 0x3f));
        writeBuffer[1] = (byte) (0x80 | (timestamp & 0x7f));
        writeBuffer[2] = (byte) byte1;
        writeBuffer[3] = (byte) byte2;

        transferData(writeBuffer);
    }

    /**
     * Sends MIDI message to output device.
     *
     * @param byte1 the first byte
     * @param byte2 the second byte
     * @param byte3 the third byte
     */
    private void sendMidiMessage(int byte1, int byte2, int byte3) {
        byte[] writeBuffer = new byte[5];
        long timestamp = System.currentTimeMillis() % MAX_TIMESTAMP;

        writeBuffer[0] = (byte) (0x80 | ((timestamp >> 7) & 0x3f));
        writeBuffer[1] = (byte) (0x80 | (timestamp & 0x7f));
        writeBuffer[2] = (byte) byte1;
        writeBuffer[3] = (byte) byte2;
        writeBuffer[4] = (byte) byte3;

        transferData(writeBuffer);
    }

    /**
     * SysEx
     *
     * @param systemExclusive : start with 'F0', and end with 'F7'
     */
    public final void sendMidiSystemExclusive(byte[] systemExclusive) {
        byte[] timestampAddedSystemExclusive = new byte[systemExclusive.length + 2];
        System.arraycopy(systemExclusive, 0, timestampAddedSystemExclusive, 1, systemExclusive.length);

        // split into 20 bytes. BLE can't send more than 20 bytes by default MTU.
        byte[] writeBuffer = new byte[20];

        long timestamp = System.currentTimeMillis() % MAX_TIMESTAMP;
        timestampAddedSystemExclusive[0] = (byte) (0x80 | ((timestamp >> 7) & 0x3f));

        for (int i = 0; i < timestampAddedSystemExclusive.length; i += 19) {
            writeBuffer[0] = (byte) (0x80 | ((timestamp >> 7) & 0x3f));
            timestampAddedSystemExclusive[systemExclusive.length] = (byte) (0x80 | (timestamp & 0x7f));

            if (i + 20 <= timestampAddedSystemExclusive.length) {
                System.arraycopy(timestampAddedSystemExclusive, i, writeBuffer, 1, 19);
            } else {
                // last message
                writeBuffer = new byte[timestampAddedSystemExclusive.length - i + 1];

                System.arraycopy(timestampAddedSystemExclusive, i, writeBuffer, 1, timestampAddedSystemExclusive.length - i);
            }

            transferData(writeBuffer);

            timestamp = System.currentTimeMillis() % MAX_TIMESTAMP;
        }
    }

    /**
     * Note-off
     *
     * @param channel 0-15
     * @param note 0-127
     * @param velocity 0-127
     */
    public final void sendMidiNoteOff(int channel, int note, int velocity) {
        sendMidiMessage(0x80 | (channel & 0xf), note, velocity);
    }

    /**
     * Note-on
     *
     * @param channel 0-15
     * @param note 0-127
     * @param velocity 0-127
     */
    public final void sendMidiNoteOn(int channel, int note, int velocity) {
        sendMidiMessage(0x90 | (channel & 0xf), note, velocity);
    }

    /**
     * Poly-KeyPress
     *
     * @param channel 0-15
     * @param note 0-127
     * @param pressure 0-127
     */
    public final void sendMidiPolyphonicAftertouch(int channel, int note, int pressure) {
        sendMidiMessage(0xa0 | (channel & 0xf), note, pressure);
    }

    /**
     * Control Change
     *
     * @param channel 0-15
     * @param function 0-127
     * @param value 0-127
     */
    public final void sendMidiControlChange(int channel, int function, int value) {
        sendMidiMessage(0xb0 | (channel & 0xf), function, value);
    }

    /**
     * Program Change
     *
     * @param channel 0-15
     * @param program 0-127
     */
    public final void sendMidiProgramChange(int channel, int program) {
        sendMidiMessage(0xc0 | (channel & 0xf), program);
    }

    /**
     * Channel Pressure
     *
     * @param channel 0-15
     * @param pressure 0-127
     */
    public final void sendMidiChannelAftertouch(int channel, int pressure) {
        sendMidiMessage(0xd0 | (channel & 0xf), pressure);
    }

    /**
     * PitchBend Change
     *
     * @param channel 0-15
     * @param amount 0(low)-8192(center)-16383(high)
     */
    public final void sendMidiPitchWheel(int channel, int amount) {
        sendMidiMessage(0xe0 | (channel & 0xf), amount & 0x7f, (amount >> 7) & 0x7f);
    }

    /**
     * MIDI Time Code(MTC) Quarter Frame
     *
     * @param timing 0-127
     */
    public final void sendMidiTimeCodeQuarterFrame(int timing) {
        sendMidiMessage(0xf1, timing & 0x7f);
    }

    /**
     * Song Select
     *
     * @param song 0-127
     */
    public final void sendMidiSongSelect(int song) {
        sendMidiMessage(0xf3, song & 0x7f);
    }

    /**
     * Song Position Pointer
     *
     * @param position 0-16383
     */
    public final void sendMidiSongPositionPointer(int position) {
        sendMidiMessage(0xf2, position & 0x7f, (position >> 7) & 0x7f);
    }

    /**
     * Tune Request
     */
    public final void sendMidiTuneRequest() {
        sendMidiMessage(0xf6);
    }

    /**
     * Timing Clock
     */
    public final void sendMidiTimingClock() {
        sendMidiMessage(0xf8);
    }

    /**
     * Start Playing
     */
    public final void sendMidiStart() {
        sendMidiMessage(0xfa);
    }

    /**
     * Continue Playing
     */
    public final void sendMidiContinue() {
        sendMidiMessage(0xfb);
    }

    /**
     * Stop Playing
     */
    public final void sendMidiStop() {
        sendMidiMessage(0xfc);
    }

    /**
     * Active Sensing
     */
    public final void sendMidiActiveSensing() {
        sendMidiMessage(0xfe);
    }

    /**
     * Reset Device
     */
    public final void sendMidiReset() {
        sendMidiMessage(0xff);
    }

    /**
     * RPN message
     *
     * @param channel 0-15
     * @param function 14bits
     * @param value 7bits or 14bits
     */
    public final void sendRPNMessage(int channel, int function, int value) {
        sendRPNMessage(channel, (function >> 7) & 0x7f, function & 0x7f, value);
    }

    /**
     * RPN message
     *
     * @param channel 0-15
     * @param functionMSB higher 7bits
     * @param functionLSB lower 7bits
     * @param value 7bits or 14bits
     */
    public final void sendRPNMessage(int channel, int functionMSB, int functionLSB, int value) {
        // send the function
        sendMidiControlChange(channel, 101, functionMSB & 0x7f);
        sendMidiControlChange(channel, 100, functionLSB & 0x7f);

        // send the value
        if ((value >> 7) > 0) {
            sendMidiControlChange(channel, 6, (value >> 7) & 0x7f);
            sendMidiControlChange(channel, 38, value & 0x7f);
        } else {
            sendMidiControlChange(channel, 6, value & 0x7f);
        }

        // send the NULL function
        sendMidiControlChange(channel, 101, 0x7f);
        sendMidiControlChange(channel, 100, 0x7f);
    }

    /**
     * NRPN message
     *
     * @param channel 0-15
     * @param function 14bits
     * @param value 7bits or 14bits
     */
    public final void sendNRPNMessage(int channel, int function, int value) {
        sendNRPNMessage(channel, (function >> 7) & 0x7f, function & 0x7f, value);
    }

    /**
     * NRPN message
     *
     * @param channel 0-15
     * @param functionMSB higher 7bits
     * @param functionLSB lower 7bits
     * @param value 7bits or 14bits
     */
    public final void sendNRPNMessage(int channel, int functionMSB, int functionLSB, int value) {
        // send the function
        sendMidiControlChange(channel, 99, functionMSB & 0x7f);
        sendMidiControlChange(channel, 98, functionLSB & 0x7f);

        // send the value
        if ((value >> 7) > 0) {
            sendMidiControlChange(channel, 6, (value >> 7) & 0x7f);
            sendMidiControlChange(channel, 38, value & 0x7f);
        } else {
            sendMidiControlChange(channel, 6, value & 0x7f);
        }

        // send the NULL function
        sendMidiControlChange(channel, 101, 0x7f);
        sendMidiControlChange(channel, 100, 0x7f);
    }
}
