package jp.kshoji.blemidi.central.callback;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.util.Constants;

/**
 * BluetoothGattCallback implementation for BLE MIDI devices.
 *
 * @author K.Shoji
 */
public class BleMidiCallback extends BluetoothGattCallback {
    final Map<BluetoothGatt, Set<MidiInputDevice>> midiInputDevicesMap = new HashMap<BluetoothGatt, Set<MidiInputDevice>>();
    final Map<BluetoothGatt, Set<MidiOutputDevice>> midiOutputDevicesMap = new HashMap<BluetoothGatt, Set<MidiOutputDevice>>();
    final Context context;

    OnMidiDeviceAttachedListener midiDeviceAttachedListener;
    OnMidiDeviceDetachedListener midiDeviceDetachedListener;

    /**
     * Constructor
     *
     * @param context
     */
    public BleMidiCallback(final Context context) {
        super();
        this.context = context;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (gatt.discoverServices()) {
                // successfully started discovering
            } else {
                // already disconnected
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // invoke event listener
            if (midiDeviceDetachedListener != null) {
                Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt);
                if (midiInputDevices != null) {
                    for (MidiInputDevice midiInputDevice : midiInputDevices) {// java.lang.NullPointerException: Attempt to invoke interface method 'java.util.Iterator java.util.Set.iterator()' on a null object reference
                        midiInputDevice.finalize();
                        midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                    }
                    midiInputDevices.clear();
                    midiInputDevicesMap.remove(gatt);
                }

                Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gatt);
                if (midiOutputDevices != null) {
                    for (MidiOutputDevice midiOutputDevice : midiOutputDevices) {
                        midiOutputDevice.finalize();
                        midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                    }
                    midiOutputDevices.clear();
                    midiOutputDevicesMap.remove(gatt);
                }
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // find MIDI Input device
            MidiInputDevice midiInputDevice = MidiInputDevice.getInstance(context, gatt);
            if (midiInputDevice != null) {
                if (!midiInputDevicesMap.containsKey(gatt)) {
                    Log.d(Constants.TAG, "midiInputDevice connected: " + midiInputDevice.getDeviceName());

                    synchronized (midiInputDevicesMap) {
                        Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt);
                        if (midiInputDevices == null) {
                            midiInputDevices = new HashSet<MidiInputDevice>();
                            midiInputDevicesMap.put(gatt, midiInputDevices);
                        }

                        midiInputDevices.add(midiInputDevice);
                    }

                    if (midiDeviceAttachedListener != null) {
                        midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                    }
                }
            }

            // find MIDI Output device
            MidiOutputDevice midiOutputDevice = MidiOutputDevice.getInstance(context, gatt);
            if (midiOutputDevice != null) {
                if (!midiOutputDevicesMap.containsKey(gatt)) {
                    Log.d(Constants.TAG, "midiOutputDevice connected: " + midiOutputDevice.getDeviceName());

                    synchronized (midiOutputDevicesMap) {
                        Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gatt);
                        if (midiOutputDevices == null) {
                            midiOutputDevices = new HashSet<MidiOutputDevice>();
                            midiOutputDevicesMap.put(gatt, midiOutputDevices);
                        }

                        midiOutputDevices.add(midiOutputDevice);
                    }

                    if (midiDeviceAttachedListener != null) {
                        midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
                    }
                }
            }
        }
    }

    // FIXME not called at frequent data transfer, up to 6 times per a second.
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt);
        for (MidiInputDevice midiInputDevice : midiInputDevices) {
            if (midiInputDevice.getCharacteristic().equals(characteristic)) {
                midiInputDevice.incomingData(characteristic.getValue());
            }
        }
    }

    public Set<MidiInputDevice> getMidiInputDevices() {
        Collection<Set<MidiInputDevice>> values = midiInputDevicesMap.values();

        Set<MidiInputDevice> result = new HashSet<MidiInputDevice>();
        for (Set<MidiInputDevice> value: values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    public Set<MidiOutputDevice> getMidiOutputDevices() {
        Collection<Set<MidiOutputDevice>> values = midiOutputDevicesMap.values();

        Set<MidiOutputDevice> result = new HashSet<MidiOutputDevice>();
        for (Set<MidiOutputDevice> value: values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    public void setOnMidiDeviceAttachedListener(OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiDeviceAttachedListener = midiDeviceAttachedListener;
    }

    public void setOnMidiDeviceDetachedListener(OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiDeviceDetachedListener = midiDeviceDetachedListener;
    }
}
