package jp.kshoji.javax.sound.midi.ble;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.kshoji.blemidi.device.Midi2InputDevice;
import jp.kshoji.blemidi.device.Midi2OutputDevice;
import jp.kshoji.javax.sound.midi.MidiDevice;
import jp.kshoji.javax.sound.midi.MidiUnavailableException;
import jp.kshoji.javax.sound.midi.Receiver;
import jp.kshoji.javax.sound.midi.Transmitter;

/**
 * {@link MidiDevice} implementation
 *
 * @author K.Shoji
 */
public final class BleMidi2Device implements MidiDevice {
    @Nullable
    private BleMidi2Receiver receiver;
    @Nullable
    private BleMidi2Transmitter transmitter;

    private boolean isOpened;

    private Midi2InputDevice midiInputDevice;
    private Midi2OutputDevice midiOutputDevice;

    /**
     * Constructor
     *
     * @param midiInputDevice the input device
     * @param midiOutputDevice the output device
     */
    public BleMidi2Device(@Nullable final Midi2InputDevice midiInputDevice, @Nullable final Midi2OutputDevice midiOutputDevice) {
        this.midiInputDevice = midiInputDevice;
        this.midiOutputDevice = midiOutputDevice;

        if (midiInputDevice == null && midiOutputDevice == null) {
            throw new NullPointerException("Both of MidiInputDevice and MidiOutputDevice are null.");
        }

        if (midiOutputDevice != null) {
            receiver = new BleMidi2Receiver(this);
        }

        if (midiInputDevice != null) {
            transmitter = new BleMidi2Transmitter(this);
        }
    }

    @NonNull
    @Override
    public Info getDeviceInfo() {
        String deviceName = "";
        if (midiInputDevice != null) {
            deviceName = midiInputDevice.getDeviceName();
        } else if (midiOutputDevice != null) {
            deviceName = midiOutputDevice.getDeviceName();
        }

        return new Info(deviceName, //
                "(vendor)", //
                "(description)", //
                "(version)");
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public void open() throws MidiUnavailableException {
        if (isOpened) {
            return;
        }

        if (receiver != null) {
            receiver.open();
        }

        if (transmitter != null) {
            transmitter.open();
        }

        isOpened = true;
    }

    @Override
    public void close() {
        if (!isOpened) {
            return;
        }

        if (transmitter != null) {
            transmitter.close();
            transmitter = null;
        }

        if (receiver != null) {
            receiver.close();
            receiver = null;
        }

        isOpened = false;
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }

    @Override
    public long getMicrosecondPosition() {
        // time-stamping is not supported
        return -1;
    }

    @Override
    public int getMaxReceivers() {
        return receiver == null ? 0 : 1;
    }

    @Override
    public int getMaxTransmitters() {
        return transmitter == null ? 0 : 1;
    }

    @NonNull
    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        if (receiver == null) {
            throw new MidiUnavailableException("Receiver not found");
        }
        return receiver;
    }

    @NonNull
    @Override
    public List<Receiver> getReceivers() {
        final List<Receiver> receivers = new ArrayList<>();
        if (receiver != null) {
            receivers.add(receiver);
        }
        return Collections.unmodifiableList(receivers);
    }

    @NonNull
    @Override
    public Transmitter getTransmitter() throws MidiUnavailableException {
        if (transmitter == null) {
            throw new MidiUnavailableException("Transmitter not found");
        }
        return transmitter;
    }

    @NonNull
    @Override
    public List<Transmitter> getTransmitters() {
        final List<Transmitter> transmitters = new ArrayList<>();
        if (transmitter != null) {
            transmitters.add(transmitter);
        }
        return Collections.unmodifiableList(transmitters);
    }

    public void setMidiInputDevice(@Nullable final Midi2InputDevice midiInputDevice) {
        this.midiInputDevice = midiInputDevice;
        if (transmitter != null) {
            transmitter.close();
        }

        if (midiInputDevice == null) {
            transmitter = null;
        } else {
            transmitter = new BleMidi2Transmitter(this);
        }
    }

    public Midi2InputDevice getMidiInputDevice() {
        return midiInputDevice;
    }

    public void setMidiOutputDevice(@Nullable final Midi2OutputDevice midiOutputDevice) {
        this.midiOutputDevice = midiOutputDevice;
        if (receiver != null) {
            receiver.close();
        }

        if (this.midiOutputDevice == null) {
            receiver = null;
        } else {
            receiver = new BleMidi2Receiver(this);
        }
    }

    public Midi2OutputDevice getMidiOutputDevice() {
        return midiOutputDevice;
    }

}
