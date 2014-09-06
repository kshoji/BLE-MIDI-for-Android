package jp.kshoji.blemidi.listener;

import jp.kshoji.blemidi.device.BleMidiInputDevice;
import jp.kshoji.blemidi.device.BleMidiOutputDevice;

/**
 * Listener for MIDI detached events
 *
 * @author K.Shoji
 */
public interface OnMidiDeviceDetachedListener {

    /**
     * MIDI input device has been attached
     *
     * @param midiInputDevice
     */
    void onMidiInputDeviceDetached(BleMidiInputDevice midiInputDevice);

    /**
     * MIDI output device has been attached
     *
     * @param midiOutputDevice
     */
    void onMidiOutputDeviceDetached(BleMidiOutputDevice midiOutputDevice);
}
