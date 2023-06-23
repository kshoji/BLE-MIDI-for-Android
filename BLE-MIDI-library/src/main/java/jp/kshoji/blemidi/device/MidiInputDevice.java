package jp.kshoji.blemidi.device;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
    public abstract void setOnMidiInputEventListener(@Nullable OnMidiInputEventListener midiInputEventListener);

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

    @NonNull
    @Override
    public final String toString() {
        return getDeviceName();
    }
}
