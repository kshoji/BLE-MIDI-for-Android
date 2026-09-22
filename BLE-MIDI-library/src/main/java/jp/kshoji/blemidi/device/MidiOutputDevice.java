package jp.kshoji.blemidi.device;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.LinkedList;

import jp.kshoji.blemidi.util.BleMidiPacketEncoder;

/**
 * Represents BLE MIDI Output Device
 *
 * @author K.Shoji
 */
public abstract class MidiOutputDevice {

    public static final int MAX_TIMESTAMP = 8192;

    /**
     * Default maximum MIDI messages packed into one BLE write.
     *
     * @see #setMaxMessagesPerPacket(int)
     */
    public static final int DEFAULT_MAX_MESSAGES_PER_PACKET = BleMidiPacketEncoder.DEFAULT_MAX_MESSAGES_PER_PACKET;

    private final LinkedList<BleMidiPacketEncoder.PendingMidiMessage> pendingMessages = new LinkedList<>();
    private volatile int maxMessagesPerPacket = DEFAULT_MAX_MESSAGES_PER_PACKET;

    /**
     * Transfer data
     *
     * @param writeBuffer byte array to write
     * @return true if transfer succeed
     */
    protected abstract boolean transferData(@NonNull byte[] writeBuffer);

    /**
     * Obtains the device name
     *
     * @return device name
     */
    @NonNull
    public abstract String getDeviceName();

    /**
     * Obtains the manufacturer name
     *
     * @return manufacturer name
     */
    @NonNull
    public abstract String getManufacturer();

    /**
     * Obtains the model name
     *
     * @return model name
     */
    @NonNull
    public abstract String getModel();

    /**
     * Obtains the device address
     *
     * @return device address
     */
    @NonNull
    public abstract String getDeviceAddress();

    /**
     * Obtains buffer size
     * @return buffer size
     */
    public abstract int getBufferSize();

    /**
     * Obtains the maximum number of MIDI messages packed into one BLE write.
     *
     * @return max messages per packet
     */
    public final int getMaxMessagesPerPacket() {
        return maxMessagesPerPacket;
    }

    /**
     * Sets the maximum number of MIDI messages packed into one BLE write.
     * Smaller values reduce loss under congestion and increase latency.
     *
     * @param maxMessagesPerPacket must be at least 1
     */
    public final void setMaxMessagesPerPacket(int maxMessagesPerPacket) {
        if (maxMessagesPerPacket < 1) {
            throw new IllegalArgumentException("maxMessagesPerPacket must be >= 1");
        }
        this.maxMessagesPerPacket = maxMessagesPerPacket;
    }

    @NonNull
    @Override
    public final String toString() {
        return getDeviceName();
    }

    volatile boolean transferDataThreadAlive;
    volatile boolean isRunning;
    final Thread transferDataThread = new Thread(new Runnable() {
        @Override
        public void run() {
            transferDataThreadAlive = true;
            byte[] pendingPacket = null;
            byte[] pendingSysEx = null;

            while (true) {
                // running
                while (transferDataThreadAlive && isRunning) {
                    synchronized (pendingMessages) {
                        if (pendingPacket == null && pendingSysEx == null && !pendingMessages.isEmpty()) {
                            if (pendingMessages.getFirst().isSystemExclusive()) {
                                pendingSysEx = pendingMessages.removeFirst().data;
                            } else {
                                byte[] packed = BleMidiPacketEncoder.packChannelMessages(
                                        pendingMessages, getBufferSize(), maxMessagesPerPacket);
                                if (packed.length > 0) {
                                    pendingPacket = packed;
                                }
                            }
                        }
                    }

                    if (pendingSysEx != null) {
                        transferSystemExclusive(pendingSysEx);
                        pendingSysEx = null;
                    } else if (pendingPacket != null) {
                        if (transferData(pendingPacket)) {
                            pendingPacket = null;
                        }
                    }

                    try {
                        Thread.sleep(10); // BluetoothGatt.WRITE_CHARACTERISTIC_TIME_TO_WAIT
                    } catch (InterruptedException ignored) {
                    }
                }

                if (!transferDataThreadAlive) {
                    break;
                }

                // stopping
                while (!transferDataThreadAlive && !isRunning) {
                    // sleep until interrupt
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }

                if (!transferDataThreadAlive) {
                    break;
                }
            }
        }
    });

    protected MidiOutputDevice() {
        transferDataThread.start();
    }

    /**
     * Starts using the device
     */
    public final void start() {
        if (!transferDataThreadAlive) {
            return;
        }
        isRunning = true;
        transferDataThread.interrupt();
    }

    /**
     * Stops using the device
     */
    public final void stop() {
        if (!transferDataThreadAlive) {
            return;
        }
        isRunning = false;
        transferDataThread.interrupt();
    }

    /**
     * Terminates the device instance
     */
    public final void terminate() {
        transferDataThreadAlive = false;
        isRunning = false;
        synchronized (pendingMessages) {
            pendingMessages.clear();
        }
        transferDataThread.interrupt();
    }

    private void storeTransferData(byte[] data) {
        if (!transferDataThreadAlive || !isRunning) {
            return;
        }

        long timestamp = System.currentTimeMillis() % MAX_TIMESTAMP;
        byte[] copy = Arrays.copyOf(data, data.length);
        synchronized (pendingMessages) {
            pendingMessages.add(new BleMidiPacketEncoder.PendingMidiMessage(copy, timestamp));
        }
        transferDataThread.interrupt();
    }

    /**
     * Sends MIDI message to output device.
     *
     * @param byte1 the first byte
     */
    private void sendMidiMessage(int byte1) {
        storeTransferData(new byte[] { (byte) byte1 });
    }

    /**
     * Sends MIDI message to output device.
     *
     * @param byte1 the first byte
     * @param byte2 the second byte
     */
    private void sendMidiMessage(int byte1, int byte2) {
        storeTransferData(new byte[] { (byte) byte1, (byte) byte2 });
    }

    /**
     * Sends MIDI message to output device.
     *
     * @param byte1 the first byte
     * @param byte2 the second byte
     * @param byte3 the third byte
     */
    private void sendMidiMessage(int byte1, int byte2, int byte3) {
        storeTransferData(new byte[] { (byte) byte1, (byte) byte2, (byte) byte3 });
    }

    /**
     * SysEx
     *
     * @param systemExclusive : start with 'F0', and end with 'F7'
     */
    public final void sendMidiSystemExclusive(@NonNull byte[] systemExclusive) {
        storeTransferData(systemExclusive);
    }

    /**
     * Encodes and writes a SysEx message as its own BLE packets (never mixed with channel messages).
     *
     * @param systemExclusive start with 'F0', and end with 'F7'
     */
    private void transferSystemExclusive(@NonNull byte[] systemExclusive) {
        if (systemExclusive.length == 0) {
            return;
        }

        byte[] timestampAddedSystemExclusive = new byte[systemExclusive.length + 2];
        System.arraycopy(systemExclusive, 0, timestampAddedSystemExclusive, 1, systemExclusive.length);

        long timestamp = System.currentTimeMillis() % MAX_TIMESTAMP;

        // extend a byte for timestamp LSB, before the last byte('F7')
        timestampAddedSystemExclusive[systemExclusive.length + 1] = systemExclusive[systemExclusive.length - 1];
        // set first byte to timestamp LSB
        timestampAddedSystemExclusive[0] = (byte) (0x80 | (timestamp & 0x7f));

        // split into bufferSize bytes. BLE can't send more than (bufferSize: MTU - 3) bytes.
        int bufferSize = getBufferSize();
        if (bufferSize <= 1) {
            return;
        }
        byte[] writeBuffer = new byte[bufferSize];
        for (int i = 0; i < timestampAddedSystemExclusive.length; i += (bufferSize - 1)) {
            // Don't send 0xF7 timestamp LSB inside of SysEx(MIDI parser will fail) 0x7f -> 0x7e
            timestampAddedSystemExclusive[systemExclusive.length] = (byte) (0x80 | (timestamp & 0x7e));

            if (i + (bufferSize - 1) <= timestampAddedSystemExclusive.length) {
                System.arraycopy(timestampAddedSystemExclusive, i, writeBuffer, 1, (bufferSize - 1));
            } else {
                // last message
                writeBuffer = new byte[timestampAddedSystemExclusive.length - i + 1];

                System.arraycopy(timestampAddedSystemExclusive, i, writeBuffer, 1, timestampAddedSystemExclusive.length - i);
            }

            // timestamp MSB
            writeBuffer[0] = (byte) (0x80 | ((timestamp >> 7) & 0x3f));

            // immediately transfer data
            while (transferDataThreadAlive) {
                if (transferData(writeBuffer)) {
                    break;
                }

                try {
                    Thread.sleep(10); // BluetoothGatt.WRITE_CHARACTERISTIC_TIME_TO_WAIT
                } catch (InterruptedException ignored) {
                }
            }
            if (!transferDataThreadAlive) {
                return;
            }

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
