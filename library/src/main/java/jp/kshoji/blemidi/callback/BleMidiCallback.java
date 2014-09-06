package jp.kshoji.blemidi.callback;

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

import jp.kshoji.blemidi.device.BleMidiInputDevice;
import jp.kshoji.blemidi.device.BleMidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.util.Constants;

/**
 * BluetoothGattCallback implementation for BLE MIDI devices.
 *
 * @author K.Shoji
 */
public class BleMidiCallback extends BluetoothGattCallback {
    final Map<BluetoothGatt, Set<BleMidiInputDevice>> midiInputDevicesMap = new HashMap<BluetoothGatt, Set<BleMidiInputDevice>>();
    final Map<BluetoothGatt, Set<BleMidiOutputDevice>> midiOutputDevicesMap = new HashMap<BluetoothGatt, Set<BleMidiOutputDevice>>();
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
                Set<BleMidiInputDevice> bleMidiInputDevices = midiInputDevicesMap.get(gatt);
                for (BleMidiInputDevice bleMidiInputDevice : bleMidiInputDevices) {
                    bleMidiInputDevice.finalize();
                    midiDeviceDetachedListener.onMidiInputDeviceDetached(bleMidiInputDevice);
                }
                bleMidiInputDevices.clear();
                midiInputDevicesMap.remove(gatt);

                Set<BleMidiOutputDevice> bleMidiOutputDevices = midiOutputDevicesMap.get(gatt);
                for (BleMidiOutputDevice bleMidiOutputDevice : bleMidiOutputDevices) {
                    bleMidiOutputDevice.finalize();
                    midiDeviceDetachedListener.onMidiOutputDeviceDetached(bleMidiOutputDevice);
                }
                bleMidiOutputDevices.clear();
                midiOutputDevicesMap.remove(gatt);
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // find MIDI Input device
            BleMidiInputDevice midiInputDevice = BleMidiInputDevice.getInstance(context, gatt);
            if (midiInputDevice != null) {
                synchronized (midiInputDevicesMap) {
                    Set<BleMidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt);
                    if (midiInputDevices == null) {
                        midiInputDevices = new HashSet<BleMidiInputDevice>();
                        midiInputDevicesMap.put(gatt, midiInputDevices);
                    }

                    midiInputDevices.add(midiInputDevice);
                }

                if (midiDeviceAttachedListener != null) {
                    midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                }
            }

            // find MIDI Output device
            BleMidiOutputDevice midiOutputDevice = BleMidiOutputDevice.getInstance(context, gatt);
            if (midiOutputDevice != null) {
                synchronized (midiOutputDevicesMap) {
                    Set<BleMidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gatt);
                    if (midiOutputDevices == null) {
                        midiOutputDevices = new HashSet<BleMidiOutputDevice>();
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

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        Set<BleMidiInputDevice> bleMidiInputDevices = midiInputDevicesMap.get(gatt);
        for (BleMidiInputDevice bleMidiInputDevice : bleMidiInputDevices) {
            if (bleMidiInputDevice.getCharacteristic().equals(characteristic)) {
                bleMidiInputDevice.incomingData(characteristic.getValue());
            }
        }
    }

    public Set<BleMidiInputDevice> getMidiInputDevices() {
        Collection<Set<BleMidiInputDevice>> values = midiInputDevicesMap.values();

        Set<BleMidiInputDevice> result = new HashSet<BleMidiInputDevice>();
        for (Set<BleMidiInputDevice> value: values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    public Set<BleMidiOutputDevice> getMidiOutputDevices() {
        Collection<Set<BleMidiOutputDevice>> values = midiOutputDevicesMap.values();

        Set<BleMidiOutputDevice> result = new HashSet<BleMidiOutputDevice>();
        for (Set<BleMidiOutputDevice> value: values) {
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
