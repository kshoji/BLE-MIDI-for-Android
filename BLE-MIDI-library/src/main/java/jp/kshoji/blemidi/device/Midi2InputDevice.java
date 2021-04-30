package jp.kshoji.blemidi.device;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import jp.kshoji.blemidi.listener.OnMidi2InputEventListener;
import jp.kshoji.blemidi.util.MidiCapabilityNegotiator;

/**
 * Represents BLE MIDI 2.0 Input Device
 *
 * @author K.Shoji
 */
public abstract class Midi2InputDevice {
    private MidiCapabilityNegotiator.MidiProtocol midiProtocol;
    private MidiCapabilityNegotiator.MidiExtension midiExtension;

    /**
     * Attaches {@link OnMidi2InputEventListener}
     *
     * @param midiInputEventListener the listener
     */
    public abstract void setOnMidiInputEventListener(@Nullable OnMidi2InputEventListener midiInputEventListener);

    /**
     * Obtains the device name
     *
     * @return device name
     */
    @NonNull
    public abstract String getDeviceName();

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

    /**
     * Sets capable MIDI Protocol, and Extension
     *
     * @param midiProtocol  the MidiProtocol
     * @param midiExtension the MidiExtension
     */
    public final void setProtocolInformation(MidiCapabilityNegotiator.MidiProtocol midiProtocol, MidiCapabilityNegotiator.MidiExtension midiExtension) {
        this.midiProtocol = midiProtocol;
        this.midiExtension = midiExtension;
    }

    /**
     * Obtains using MIDI Protocol version
     * @return the MidiProtocol
     */
    public final MidiCapabilityNegotiator.MidiProtocol getMidiProtocol() {
        return midiProtocol;
    }

    /**
     * Obtains using MIDI Extension specification
     * @return the MidiExtension
     */
    public final MidiCapabilityNegotiator.MidiExtension getMidiExtension() {
        return midiExtension;
    }
}
