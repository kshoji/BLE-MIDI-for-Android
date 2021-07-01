package jp.kshoji.blemidi.listener;

import android.support.annotation.NonNull;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;

/**
 * Listener for MIDI detached events
 *
 * @author K.Shoji
 */
public interface OnMidiDeviceDetachedListener {

    /**
     * MIDI input device has been detached
     *
     * @param midiInputDevice detached MIDI Input device
     */
    void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice);

    /**
     * MIDI output device has been detached
     *
     * @param midiOutputDevice detached MIDI Output device
     */
    void onMidiOutputDeviceDetached(@NonNull MidiOutputDevice midiOutputDevice);
}
