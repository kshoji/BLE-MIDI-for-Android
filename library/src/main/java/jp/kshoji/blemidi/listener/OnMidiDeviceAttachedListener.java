package jp.kshoji.blemidi.listener;

import android.hardware.usb.UsbDevice;

import jp.kshoji.blemidi.device.BleMidiInputDevice;
import jp.kshoji.blemidi.device.BleMidiOutputDevice;

/**
 * Listener for MIDI attached events
 * 
 * @author K.Shoji
 */
public interface OnMidiDeviceAttachedListener {

    /**
     * MIDI input device has been attached
     *
     * @param midiInputDevice
     */
    void onMidiInputDeviceAttached(BleMidiInputDevice midiInputDevice);

    /**
     * MIDI output device has been attached
     *
     * @param midiOutputDevice
     */
    void onMidiOutputDeviceAttached(BleMidiOutputDevice midiOutputDevice);
}
