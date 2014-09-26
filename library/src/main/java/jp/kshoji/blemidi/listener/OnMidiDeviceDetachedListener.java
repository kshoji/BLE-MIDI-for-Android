package jp.kshoji.blemidi.listener;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;

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
    void onMidiInputDeviceDetached(MidiInputDevice midiInputDevice);

    /**
     * MIDI output device has been attached
     *
     * @param midiOutputDevice
     */
    void onMidiOutputDeviceDetached(MidiOutputDevice midiOutputDevice);
}
