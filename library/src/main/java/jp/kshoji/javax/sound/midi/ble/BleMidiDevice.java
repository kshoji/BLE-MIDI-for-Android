package jp.kshoji.javax.sound.midi.ble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.javax.sound.midi.MidiDevice;
import jp.kshoji.javax.sound.midi.MidiUnavailableException;
import jp.kshoji.javax.sound.midi.Receiver;
import jp.kshoji.javax.sound.midi.Transmitter;

/**
 * {@link jp.kshoji.javax.sound.midi.MidiDevice} implementation
 *
 * @author K.Shoji
 */
public final class BleMidiDevice implements MidiDevice {
    private final List<Receiver> receivers = new ArrayList<Receiver>();
    private final List<Transmitter> transmitters = new ArrayList<Transmitter>();

    private boolean isOpened;

    private MidiInputDevice midiInputDevice;
    private MidiOutputDevice midiOutputDevice;

    /**
     * Constructor
     *
     * @param midiInputDevice the input device
     * @param midiOutputDevice the output device
     */
    public BleMidiDevice(MidiInputDevice midiInputDevice, MidiOutputDevice midiOutputDevice) {
        this.midiInputDevice = midiInputDevice;
        this.midiOutputDevice = midiOutputDevice;

        if (midiInputDevice == null && midiOutputDevice == null) {
            throw new NullPointerException("Both of MidiInputDevice and MidiOutputDevice are null.");
        }

        if (midiOutputDevice != null) {
            receivers.add(new BleMidiReceiver(this));
        }

        if (midiInputDevice != null) {
            transmitters.add(new BleMidiTransmitter(this));
        }
    }

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

    @Override
    public void open() throws MidiUnavailableException {
        if (isOpened) {
            return;
        }

        for (final Receiver receiver : receivers) {
            if (receiver instanceof BleMidiReceiver) {
                final BleMidiReceiver bleMidiReceiver = (BleMidiReceiver) receiver;
                bleMidiReceiver.open();
            }
        }
        for (final Transmitter transmitter : transmitters) {
            if (transmitter instanceof BleMidiTransmitter) {
                final BleMidiTransmitter bleMidiTransmitter = (BleMidiTransmitter) transmitter;
                bleMidiTransmitter.open();
            }
        }
        isOpened = true;
    }

    @Override
    public void close() {
        if (!isOpened) {
            return;
        }

        for (final Transmitter transmitter : transmitters) {
            transmitter.close();
        }
        transmitters.clear();

        for (final Receiver receiver : receivers) {
            receiver.close();
        }
        receivers.clear();

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
        return receivers.size();
    }

    @Override
    public int getMaxTransmitters() {
        return transmitters.size();
    }

    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        if (receivers.size() < 1) {
            return null;
        }

        return receivers.get(0);
    }

    @Override
    public List<Receiver> getReceivers() {
        return Collections.unmodifiableList(receivers);
    }

    @Override
    public Transmitter getTransmitter() throws MidiUnavailableException {
        if (transmitters.size() < 1) {
            return null;
        }

        return transmitters.get(0);
    }

    @Override
    public List<Transmitter> getTransmitters() {
        return Collections.unmodifiableList(transmitters);
    }

    public void setMidiInputDevice(MidiInputDevice midiInputDevice) {
        this.midiInputDevice = midiInputDevice;
    }

    public MidiInputDevice getMidiInputDevice() {
        return midiInputDevice;
    }

    public void setMidiOutputDevice(MidiOutputDevice midiOutputDevice) {
        this.midiOutputDevice = midiOutputDevice;
    }

    public MidiOutputDevice getMidiOutputDevice() {
        return midiOutputDevice;
    }

}
