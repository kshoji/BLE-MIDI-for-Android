package jp.kshoji.blemidi.listener;

import android.support.annotation.NonNull;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;

/**
 * Listener for MIDI attached events
 * 
 * @author K.Shoji
 */
public interface OnMidiDeviceAttachedListener {

    /**
     * MIDI input device has been attached
     *
     * @param midiInputDevice attached MIDI Input device
     */
    void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice);

    /**
     * MIDI output device has been attached
     *
     * @param midiOutputDevice attached MIDI Output device
     */
    void onMidiOutputDeviceAttached(@NonNull MidiOutputDevice midiOutputDevice);
}
