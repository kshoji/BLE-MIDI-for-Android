package jp.kshoji.blemidi.device;

import android.bluetooth.BluetoothGattCharacteristic;

import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.MidiParser;

/**
 * Represents BLE MIDI Input Device
 *
 * @author K.Shoji
 */
public abstract class MidiInputDevice {

    protected BluetoothGattCharacteristic midiInputCharacteristic;

    private final MidiParser midiParser = new MidiParser(this);

    /**
     * Attaches {@link jp.kshoji.blemidi.listener.OnMidiInputEventListener}
     * @param midiInputEventListener the listener
     */
    public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
        midiParser.setMidiInputEventListener(midiInputEventListener);
    }

    /**
     * Close the device
     */
    public void close() {
        midiParser.setMidiInputEventListener(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MidiInputDevice that = (MidiInputDevice) o;

        if (midiInputCharacteristic == null && that.midiInputCharacteristic == null) {
            return true;
        }

        if (midiInputCharacteristic == null || that.midiInputCharacteristic == null) {
            return false;
        }

        return midiInputCharacteristic.getUuid().equals(that.midiInputCharacteristic.getUuid());
    }

    @Override
    public int hashCode() {
        return midiInputCharacteristic.getUuid().hashCode();
    }

    /**
     * Obtains the device name
     *
     * @return device name
     */
    public abstract String getDeviceName();

    @Override
    public String toString() {
        return getDeviceName();
    }

    /**
     * Notifies MIDI data
     * @param data the midi data
     */
    protected void incomingData(byte[] data) {
        midiParser.parse(data);
    }
}
