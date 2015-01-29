package jp.kshoji.javax.sound.midi;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;
import jp.kshoji.blemidi.util.BleUtils;
import jp.kshoji.javax.sound.midi.ble.BleMidiDevice;

/**
 * {@link jp.kshoji.javax.sound.midi.MidiSystem} initializer for BLE MIDI
 *
 * @author K.Shoji
 */
public final class BleMidiSystem implements OnMidiDeviceAttachedListener, OnMidiDeviceDetachedListener {
    private static BleMidiPeripheralProvider peripheralProvider;
    private static BleMidiCentralProvider centralProvider;

    private static final Map<String, BleMidiDevice> midiDeviceMap = new HashMap<>();

    /**
     * Initializes {@link jp.kshoji.javax.sound.midi.MidiSystem}
     *
     * @param context the context
     */
    public void initialize(Context context) {
        if (BleUtils.isBleSupported(context)) {
            if (BleUtils.isBlePeripheralSupported(context)) {
                peripheralProvider = new BleMidiPeripheralProvider(context);

                peripheralProvider.setOnMidiDeviceAttachedListener(this);
            }

            centralProvider = new BleMidiCentralProvider(context);
            centralProvider.setOnMidiDeviceAttachedListener(this);
        }
    }

    /**
     * Terminates {@link jp.kshoji.javax.sound.midi.MidiSystem}
     */
    public void terminate() {
        stopAdvertising();
        stopScanDevice();

        if (centralProvider != null) {
            centralProvider.terminate();
            centralProvider = null;
        }

        if (peripheralProvider != null) {
            peripheralProvider.terminate();
            peripheralProvider = null;
        }

        synchronized (midiDeviceMap) {
            for (BleMidiDevice bleMidiDevice : midiDeviceMap.values()) {
                bleMidiDevice.close();
            }

            midiDeviceMap.clear();
        }
    }

    /**
     * Start to scan BLE MIDI devices
     */
    public void startScanDevice() {
        if (centralProvider != null) {
            centralProvider.startScanDevice(0);
        }
    }

    /**
     * Stop to scan BLE MIDI devices
     */
    public void stopScanDevice() {
        if (centralProvider != null) {
            centralProvider.stopScanDevice();
        }
    }

    /**
     * Starts BLE MIDI advertising
     */
    public void startAdvertising() {
        if (peripheralProvider != null) {
            peripheralProvider.startAdvertising();
        }
    }

    /**
     * Stops BLE MIDI advertising
     */
    public void stopAdvertising() {
        if (peripheralProvider != null) {
            peripheralProvider.stopAdvertising();
        }
    }

    @Override
    public void onMidiInputDeviceAttached(MidiInputDevice midiInputDevice) {
        synchronized (midiDeviceMap) {
            BleMidiDevice existingDevice = midiDeviceMap.get(midiInputDevice.getDeviceAddress());
            if (existingDevice != null) {
                existingDevice.setMidiInputDevice(midiInputDevice);
                MidiSystem.addMidiDevice(existingDevice);
            } else {
                BleMidiDevice midiDevice = new BleMidiDevice(midiInputDevice, null);
                midiDeviceMap.put(midiInputDevice.getDeviceAddress(), midiDevice);
                MidiSystem.addMidiDevice(midiDevice);
            }
        }
    }

    @Override
    public void onMidiOutputDeviceAttached(MidiOutputDevice midiOutputDevice) {
        synchronized (midiDeviceMap) {
            BleMidiDevice existingDevice = midiDeviceMap.get(midiOutputDevice.getDeviceAddress());
            if (existingDevice != null) {
                existingDevice.setMidiOutputDevice(midiOutputDevice);
                MidiSystem.addMidiDevice(existingDevice);
            } else {
                BleMidiDevice midiDevice = new BleMidiDevice(null, midiOutputDevice);
                midiDeviceMap.put(midiOutputDevice.getDeviceAddress(), midiDevice);
                MidiSystem.addMidiDevice(midiDevice);
            }
        }
    }

    @Override
    public void onMidiInputDeviceDetached(MidiInputDevice midiInputDevice) {
        synchronized (midiDeviceMap) {
            BleMidiDevice existingDevice = midiDeviceMap.get(midiInputDevice.getDeviceAddress());
            if (existingDevice != null) {
                midiDeviceMap.remove(midiInputDevice.getDeviceAddress());
                MidiSystem.removeMidiDevice(existingDevice);
            }
        }
    }

    @Override
    public void onMidiOutputDeviceDetached(MidiOutputDevice midiOutputDevice) {
        synchronized (midiDeviceMap) {
            BleMidiDevice existingDevice = midiDeviceMap.get(midiOutputDevice.getDeviceAddress());
            if (existingDevice != null) {
                midiDeviceMap.remove(midiOutputDevice.getDeviceAddress());
                MidiSystem.removeMidiDevice(existingDevice);
            }
        }
    }
}
