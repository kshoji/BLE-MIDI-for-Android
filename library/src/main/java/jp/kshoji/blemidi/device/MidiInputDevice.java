package jp.kshoji.blemidi.device;

import jp.kshoji.blemidi.listener.OnMidiInputEventListener;

/**
 * Represents BLE MIDI Input Device
 *
 * @author K.Shoji
 */
public abstract class MidiInputDevice {

    /**
     * Attaches {@link jp.kshoji.blemidi.listener.OnMidiInputEventListener}
     *
     * @param midiInputEventListener the listener
     */
    public abstract void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener);

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
}
