package jp.kshoji.javax.sound.midi;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.device.Midi2InputDevice;
import jp.kshoji.blemidi.device.Midi2OutputDevice;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;
import jp.kshoji.blemidi.util.BleUtils;
import jp.kshoji.javax.sound.midi.ble.BleMidi2Device;
import jp.kshoji.javax.sound.midi.ble.BleMidiDevice;
import jp.kshoji.javax.sound.midi.ble.BleMidiSynthesizer;

/**
 * {@link jp.kshoji.javax.sound.midi.MidiSystem} initializer for BLE MIDI
 *
 * @author K.Shoji
 */
public final class BleMidiSystem implements OnMidiDeviceAttachedListener, OnMidiDeviceDetachedListener {
    private static BleMidiPeripheralProvider peripheralProvider;
    private static BleMidiCentralProvider centralProvider;

    private final Map<String, BleMidiDevice> midiDeviceMap = new HashMap<>();
    private final Map<String, BleMidi2Device> midi2DeviceMap = new HashMap<>();
    private final Map<String, BleMidiSynthesizer> midiSynthesizerMap = new HashMap<>();
    private final Context context;

    /**
     * Constructor
     *
     * @param context the context
     */
    public BleMidiSystem(@NonNull final Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Initializes {@link jp.kshoji.javax.sound.midi.MidiSystem}
     */
    public void initialize() {
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
            for (final BleMidiDevice bleMidiDevice : midiDeviceMap.values()) {
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
    public void onMidiInputDeviceAttached(@NonNull final MidiInputDevice midiInputDevice) {
        final BleMidiDevice addedDevice;
        synchronized (midiDeviceMap) {
            final BleMidiDevice existingDevice = midiDeviceMap.get(midiInputDevice.getDeviceAddress());
            if (existingDevice != null) {
                addedDevice = existingDevice;
                existingDevice.setMidiInputDevice(midiInputDevice);
                MidiSystem.addMidiDevice(existingDevice);
            } else {
                final BleMidiDevice midiDevice = new BleMidiDevice(midiInputDevice, null);
                addedDevice = midiDevice;
                midiDeviceMap.put(midiInputDevice.getDeviceAddress(), midiDevice);
                MidiSystem.addMidiDevice(midiDevice);
            }
        }

        synchronized (midiSynthesizerMap) {
            final BleMidiSynthesizer existingSynthesizer = midiSynthesizerMap.get(midiInputDevice.getDeviceAddress());
            if (existingSynthesizer == null) {
                final BleMidiSynthesizer synthesizer = new BleMidiSynthesizer(addedDevice);
                MidiSystem.addSynthesizer(synthesizer);
                midiSynthesizerMap.put(midiInputDevice.getDeviceAddress(), synthesizer);
            }
        }
    }

    @Override
    public void onMidiOutputDeviceAttached(@NonNull final MidiOutputDevice midiOutputDevice) {
        final BleMidiDevice addedDevice;
        synchronized (midiDeviceMap) {
            final BleMidiDevice existingDevice = midiDeviceMap.get(midiOutputDevice.getDeviceAddress());
            if (existingDevice == null) {
                final BleMidiDevice midiDevice = new BleMidiDevice(null, midiOutputDevice);
                addedDevice = midiDevice;
                midiDeviceMap.put(midiOutputDevice.getDeviceAddress(), midiDevice);
                MidiSystem.addMidiDevice(midiDevice);
            } else {
                addedDevice = existingDevice;
                existingDevice.setMidiOutputDevice(midiOutputDevice);
                MidiSystem.addMidiDevice(existingDevice);
            }
        }

        synchronized (midiSynthesizerMap) {
            final BleMidiSynthesizer existingSynthesizer = midiSynthesizerMap.get(midiOutputDevice.getDeviceAddress());
            if (existingSynthesizer == null) {
                final BleMidiSynthesizer synthesizer = new BleMidiSynthesizer(addedDevice);
                midiSynthesizerMap.put(midiOutputDevice.getDeviceAddress(), synthesizer);
                MidiSystem.addSynthesizer(synthesizer);
            } else {
                try {
                    existingSynthesizer.setReceiver(addedDevice.getReceiver());
                } catch (final MidiUnavailableException ignored) {
                }
            }
        }
    }

    @Override
    public void onMidiInputDeviceDetached(@NonNull final MidiInputDevice midiInputDevice) {
        String removedDeviceAddress = null;
        synchronized (midiDeviceMap) {
            final BleMidiDevice existingDevice = midiDeviceMap.get(midiInputDevice.getDeviceAddress());
            if (existingDevice != null) {
                existingDevice.setMidiInputDevice(null);

                if (existingDevice.getMidiOutputDevice() == null) {
                    // both of devices are disconnected
                    removedDeviceAddress = midiInputDevice.getDeviceAddress();
                    midiDeviceMap.remove(midiInputDevice.getDeviceAddress());
                    MidiSystem.removeMidiDevice(existingDevice);
                }
            }
        }

        if (removedDeviceAddress != null) {
            synchronized (midiSynthesizerMap) {
                MidiSystem.removeSynthesizer(midiSynthesizerMap.get(removedDeviceAddress));
                midiSynthesizerMap.remove(removedDeviceAddress);
            }
        }
    }

    @Override
    public void onMidiOutputDeviceDetached(@NonNull final MidiOutputDevice midiOutputDevice) {
        String removedDeviceAddress = null;
        synchronized (midiDeviceMap) {
            final BleMidiDevice existingDevice = midiDeviceMap.get(midiOutputDevice.getDeviceAddress());
            if (existingDevice != null) {
                existingDevice.setMidiOutputDevice(null);

                if (existingDevice.getMidiInputDevice() == null) {
                    // both of devices are disconnected
                    removedDeviceAddress = midiOutputDevice.getDeviceAddress();
                    midiDeviceMap.remove(midiOutputDevice.getDeviceAddress());
                    MidiSystem.removeMidiDevice(existingDevice);
                }
            }
        }

        if (removedDeviceAddress != null) {
            synchronized (midiSynthesizerMap) {
                MidiSystem.removeSynthesizer(midiSynthesizerMap.get(removedDeviceAddress));
                midiSynthesizerMap.remove(removedDeviceAddress);
            }
        }
    }

    @Override
    public void onMidi2InputDeviceAttached(@NonNull final Midi2InputDevice midiInputDevice) {
        final BleMidi2Device addedDevice;
        synchronized (midiDeviceMap) {
            final BleMidi2Device existingDevice = midi2DeviceMap.get(midiInputDevice.getDeviceAddress());
            if (existingDevice != null) {
                addedDevice = existingDevice;
                existingDevice.setMidiInputDevice(midiInputDevice);
                MidiSystem.addMidiDevice(existingDevice);
            } else {
                final BleMidi2Device midiDevice = new BleMidi2Device(midiInputDevice, null);
                addedDevice = midiDevice;
                midi2DeviceMap.put(midiInputDevice.getDeviceAddress(), midiDevice);
                MidiSystem.addMidiDevice(midiDevice);
            }
        }

        synchronized (midiSynthesizerMap) {
            final BleMidiSynthesizer existingSynthesizer = midiSynthesizerMap.get(midiInputDevice.getDeviceAddress());
            if (existingSynthesizer == null) {
                final BleMidiSynthesizer synthesizer = new BleMidiSynthesizer(addedDevice);
                MidiSystem.addSynthesizer(synthesizer);
                midiSynthesizerMap.put(midiInputDevice.getDeviceAddress(), synthesizer);
            }
        }
    }

    @Override
    public void onMidi2OutputDeviceAttached(@NonNull final Midi2OutputDevice midiOutputDevice) {
        final BleMidi2Device addedDevice;
        synchronized (midiDeviceMap) {
            final BleMidi2Device existingDevice = midi2DeviceMap.get(midiOutputDevice.getDeviceAddress());
            if (existingDevice == null) {
                final BleMidi2Device midiDevice = new BleMidi2Device(null, midiOutputDevice);
                addedDevice = midiDevice;
                midi2DeviceMap.put(midiOutputDevice.getDeviceAddress(), midiDevice);
                MidiSystem.addMidiDevice(midiDevice);
            } else {
                addedDevice = existingDevice;
                existingDevice.setMidiOutputDevice(midiOutputDevice);
                MidiSystem.addMidiDevice(existingDevice);
            }
        }

        synchronized (midiSynthesizerMap) {
            final BleMidiSynthesizer existingSynthesizer = midiSynthesizerMap.get(midiOutputDevice.getDeviceAddress());
            if (existingSynthesizer == null) {
                final BleMidiSynthesizer synthesizer = new BleMidiSynthesizer(addedDevice);
                midiSynthesizerMap.put(midiOutputDevice.getDeviceAddress(), synthesizer);
                MidiSystem.addSynthesizer(synthesizer);
            } else {
                try {
                    existingSynthesizer.setReceiver(addedDevice.getReceiver());
                } catch (final MidiUnavailableException ignored) {
                }
            }
        }
    }

    @Override
    public void onMidi2InputDeviceDetached(@NonNull final Midi2InputDevice midiInputDevice) {
        String removedDeviceAddress = null;
        synchronized (midi2DeviceMap) {
            final BleMidi2Device existingDevice = midi2DeviceMap.get(midiInputDevice.getDeviceAddress());
            if (existingDevice != null) {
                existingDevice.setMidiInputDevice(null);

                if (existingDevice.getMidiOutputDevice() == null) {
                    // both of devices are disconnected
                    removedDeviceAddress = midiInputDevice.getDeviceAddress();
                    midi2DeviceMap.remove(midiInputDevice.getDeviceAddress());
                    MidiSystem.removeMidiDevice(existingDevice);
                }
            }
        }

        if (removedDeviceAddress != null) {
            synchronized (midiSynthesizerMap) {
                MidiSystem.removeSynthesizer(midiSynthesizerMap.get(removedDeviceAddress));
                midiSynthesizerMap.remove(removedDeviceAddress);
            }
        }
    }

    @Override
    public void onMidi2OutputDeviceDetached(@NonNull final Midi2OutputDevice midiOutputDevice) {
        String removedDeviceAddress = null;
        synchronized (midi2DeviceMap) {
            final BleMidi2Device existingDevice = midi2DeviceMap.get(midiOutputDevice.getDeviceAddress());
            if (existingDevice != null) {
                existingDevice.setMidiOutputDevice(null);

                if (existingDevice.getMidiInputDevice() == null) {
                    // both of devices are disconnected
                    removedDeviceAddress = midiOutputDevice.getDeviceAddress();
                    midi2DeviceMap.remove(midiOutputDevice.getDeviceAddress());
                    MidiSystem.removeMidiDevice(existingDevice);
                }
            }
        }

        if (removedDeviceAddress != null) {
            synchronized (midiSynthesizerMap) {
                MidiSystem.removeSynthesizer(midiSynthesizerMap.get(removedDeviceAddress));
                midiSynthesizerMap.remove(removedDeviceAddress);
            }
        }
    }
}
