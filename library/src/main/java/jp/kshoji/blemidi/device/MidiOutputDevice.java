package jp.kshoji.blemidi.device;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import jp.kshoji.blemidi.util.BleMidiDeviceUtils;
import jp.kshoji.blemidi.util.Constants;

/**
 * Represents BLE MIDI Output Device
 *
 * @author K.Shoji
 */
public class MidiOutputDevice {

    private BluetoothGattCharacteristic midiOutputCharacteristic;
    private final BluetoothGatt bluetoothGatt;

    /**
     * Obtains MidiOutputDevice instance if available from specified BluetoothGatt
     *
     * @param context
     * @param bluetoothGatt
     * @return null if the device doesn't contain BLE MIDI service
     */
    public static MidiOutputDevice getInstance(final Context context, final BluetoothGatt bluetoothGatt) {
        // create instance if available
        try {
            return new MidiOutputDevice(context, bluetoothGatt);
        } catch (IllegalArgumentException e) {
            Log.i(Constants.TAG, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Constructor
     *
     * @param context
     * @param bluetoothGatt
     * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
     */
    private MidiOutputDevice(final Context context, final BluetoothGatt bluetoothGatt) throws IllegalArgumentException {
        this.bluetoothGatt = bluetoothGatt;

        BluetoothGattService midiService = BleMidiDeviceUtils.getMidiService(context, bluetoothGatt);
        if (midiService == null) {
            throw new IllegalArgumentException("MIDI GattService not found.");
        }

        midiOutputCharacteristic = BleMidiDeviceUtils.getMidiOutputCharacteristic(context, midiService);
        if (midiOutputCharacteristic == null) {
            throw new IllegalArgumentException("MIDI GattCharacteristic not found.");
        }

        midiOutputCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
    }

    @Override
    public void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            // ignore it
        }

        bluetoothGatt.disconnect();
        bluetoothGatt.close();
    }

    /**
     * Obtains the device name
     *
     * @return device name + ".output"
     */
    public String getDeviceName() {
        return bluetoothGatt.getDevice().getName() + ".output";
    }

    @Override
    public String toString() {
        return getDeviceName();
    }

    /**
     * Sends MIDI message to output device.
     *
     * @param byte1
     */
    private void sendMidiMessage(int byte1) {
        if (midiOutputCharacteristic == null) {
            return;
        }

        byte[] writeBuffer = new byte[] { (byte) byte1 };

        midiOutputCharacteristic.setValue(writeBuffer);
        try {
            bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
        } catch (Throwable t) {
            // android.os.DeadObjectException will be thrown
            // ignore it
        }
    }

    /**
     * Sends MIDI message to output device.
     *
     * @param byte1
     * @param byte2
     * @param byte3
     */
    private void sendMidiMessage(int byte1, int byte2, int byte3) {
        if (midiOutputCharacteristic == null) {
            return;
        }

        byte[] writeBuffer = new byte[3];

        writeBuffer[0] = (byte) byte1;
        writeBuffer[1] = (byte) byte2;
        writeBuffer[2] = (byte) byte3;

        midiOutputCharacteristic.setValue(writeBuffer);
        try {
            bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
        } catch (Throwable t) {
            // android.os.DeadObjectException will be thrown
            // ignore it
        }
    }

    /**
     * SysEx
     *
     * @param systemExclusive : start with 'F0', and end with 'F7'
     */
    public void sendMidiSystemExclusive(byte[] systemExclusive) {
        if (midiOutputCharacteristic == null) {
            return;
        }

        // split into 20 bytes. BLE can't send more than 20 bytes.
        byte []buffer = new byte[20];
        for (int i = 0; i < systemExclusive.length; i += 20) {
            if (i + 20 <= systemExclusive.length) {
                System.arraycopy(systemExclusive, i, buffer, 0, 20);
                midiOutputCharacteristic.setValue(buffer);
                try {
                    bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
                } catch (Throwable t) {
                    // android.os.DeadObjectException will be thrown
                    // ignore it
                }
            } else {
                // last message
                buffer = new byte[systemExclusive.length - i];
                System.arraycopy(systemExclusive, i, buffer, 0, systemExclusive.length - i);
                midiOutputCharacteristic.setValue(buffer);
                try {
                    bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
                } catch (Throwable t) {
                    // android.os.DeadObjectException will be thrown
                    // ignore it
                }
            }
        }
    }

    /**
     * Note-off
     *
     * @param channel 0-15
     * @param note 0-127
     * @param velocity 0-127
     */
    public void sendMidiNoteOff(int channel, int note, int velocity) {
        sendMidiMessage(0x80 | (channel & 0xf), note, velocity);
    }

    /**
     * Note-on
     *
     * @param channel 0-15
     * @param note 0-127
     * @param velocity 0-127
     */
    public void sendMidiNoteOn(int channel, int note, int velocity) {
        sendMidiMessage(0x90 | (channel & 0xf), note, velocity);
    }

    /**
     * Poly-KeyPress
     *
     * @param channel 0-15
     * @param note 0-127
     * @param pressure 0-127
     */
    public void sendMidiPolyphonicAftertouch(int channel, int note, int pressure) {
        sendMidiMessage(0xa0 | (channel & 0xf), note, pressure);
    }

    /**
     * Control Change
     *
     * @param channel 0-15
     * @param function 0-127
     * @param value 0-127
     */
    public void sendMidiControlChange(int channel, int function, int value) {
        sendMidiMessage(0xb0 | (channel & 0xf), function, value);
    }

    /**
     * Program Change
     *
     * @param channel 0-15
     * @param program 0-127
     */
    public void sendMidiProgramChange(int channel, int program) {
        sendMidiMessage(0xc0 | (channel & 0xf), program, 0);
    }

    /**
     * Channel Pressure
     *
     * @param channel 0-15
     * @param pressure 0-127
     */
    public void sendMidiChannelAftertouch(int channel, int pressure) {
        sendMidiMessage(0xd0 | (channel & 0xf), pressure, 0);
    }

    /**
     * PitchBend Change
     *
     * @param channel 0-15
     * @param amount 0(low)-8192(center)-16383(high)
     */
    public void sendMidiPitchWheel(int channel, int amount) {
        sendMidiMessage(0xe0 | (channel & 0xf), amount & 0x7f, (amount >> 7) & 0x7f);
    }

    /**
     * MIDI Time Code(MTC) Quarter Frame
     * @param timing 0-127
     */
    public void sendMidiTimeCodeQuarterFrame(int timing) {
        if (midiOutputCharacteristic == null) {
            return;
        }

        byte[] writeBuffer = new byte[2];

        writeBuffer[0] = (byte) 0xf1;
        writeBuffer[1] = (byte) (timing & 0x7f);

        midiOutputCharacteristic.setValue(writeBuffer);
        try {
            bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
        } catch (Throwable t) {
            // android.os.DeadObjectException will be thrown
            // ignore it
        }
    }

    /**
     * Song Select
     *
     * @param song 0-127
     */
    public void sendMidiSongSelect(int song) {
        if (midiOutputCharacteristic == null) {
            return;
        }

        byte[] writeBuffer = new byte[2];

        writeBuffer[0] = (byte) 0xf3;
        writeBuffer[1] = (byte) (song & 0x7f);

        midiOutputCharacteristic.setValue(writeBuffer);
        try {
            bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
        } catch (Throwable t) {
            // android.os.DeadObjectException will be thrown
            // ignore it
        }
    }

    /**
     * Song Position Pointer
     *
     * @param position 0-16383
     */
    public void sendMidiSongPositionPointer(int position) {
        sendMidiMessage(0xf2, position & 0x7f, (position >> 7) & 0x7f);
    }

    /**
     * Tune Request
     */
    public void sendMidiTuneRequest() {
        sendMidiMessage(0xf6);
    }

    /**
     * Timing Clock
     */
    public void sendMidiTimingClock() {
        sendMidiMessage(0xf8);
    }

    /**
     * Start Playing
     */
    public void sendMidiStart() {
        sendMidiMessage(0xfa);
    }

    /**
     * Continue Playing
     */
    public void sendMidiContinue() {
        sendMidiMessage(0xfb);
    }

    /**
     * Stop Playing
     */
    public void sendMidiStop() {
        sendMidiMessage(0xfc);
    }

    /**
     * Active Sensing
     */
    public void sendMidiActiveSensing() {
        sendMidiMessage(0xfe);
    }

    /**
     * Reset Device
     */
    public void sendMidiReset() {
        sendMidiMessage(0xff);
    }

    /**
     * RPN message
     *
     * @param channel 0-15
     * @param function 14bits
     * @param value 7bits or 14bits
     */
    public void sendRPNMessage(int channel, int function, int value) {
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
    public void sendRPNMessage(int channel, int functionMSB, int functionLSB, int value) {
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
    public void sendNRPNMessage(int channel, int function, int value) {
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
    public void sendNRPNMessage(int channel, int functionMSB, int functionLSB, int value) {
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
