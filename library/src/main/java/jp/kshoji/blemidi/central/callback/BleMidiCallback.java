package jp.kshoji.blemidi.central.callback;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

import java.util.Arrays;
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

/**
 * BluetoothGattCallback implementation for BLE MIDI devices.
 *
 * @author K.Shoji
 */
public final class BleMidiCallback extends BluetoothGattCallback {
    final Map<String, Set<MidiInputDevice>> midiInputDevicesMap = new HashMap<String, Set<MidiInputDevice>>();
    final Map<String, Set<MidiOutputDevice>> midiOutputDevicesMap = new HashMap<String, Set<MidiOutputDevice>>();
    final Context context;
    final Handler handler;

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
        this.handler = new Handler(context.getMainLooper());
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        // In this method, the `status` parameter shall be ignored.
        // so, look `newState` parameter only.

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (gatt.discoverServices()) {
                // successfully started discovering
            } else {
                // already disconnected
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            String gattDeviceAddress = gatt.getDevice().getAddress();

            // invoke event listeners
            synchronized (midiInputDevicesMap) {
                Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gattDeviceAddress);
                if (midiInputDevices != null) {
                    for (MidiInputDevice midiInputDevice : midiInputDevices) {// java.lang.NullPointerException: Attempt to invoke interface method 'java.util.Iterator java.util.Set.iterator()' on a null object reference
                        midiInputDevice.close();
                        if (midiDeviceDetachedListener != null) {
                            midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                        }
                    }
                    midiInputDevices.clear();
                    midiInputDevicesMap.remove(gattDeviceAddress);
                }
            }

            synchronized (midiOutputDevicesMap) {
                Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gattDeviceAddress);
                if (midiOutputDevices != null) {
                    for (MidiOutputDevice midiOutputDevice : midiOutputDevices) {
                        midiOutputDevice.close();
                        if (midiDeviceDetachedListener != null) {
                            midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                        }
                    }
                    midiOutputDevices.clear();
                    midiOutputDevicesMap.remove(gattDeviceAddress);
                }
            }
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        if (status != BluetoothGatt.GATT_SUCCESS) {
            return;
        }

        final String gattDeviceAddress = gatt.getDevice().getAddress();

        // find MIDI Input device
        if (midiInputDevicesMap.containsKey(gattDeviceAddress)) {
            synchronized (midiInputDevicesMap) {
                Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gattDeviceAddress);
                for (MidiInputDevice midiInputDevice : midiInputDevices) {
                    midiInputDevice.close();
                }
                midiInputDevicesMap.remove(gattDeviceAddress);
            }
        }

        MidiInputDevice midiInputDevice = MidiInputDevice.getInstance(context, gatt);
        if (midiInputDevice != null) {
            synchronized (midiInputDevicesMap) {
                Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gattDeviceAddress);
                if (midiInputDevices == null) {
                    midiInputDevices = new HashSet<>();
                    midiInputDevicesMap.put(gattDeviceAddress, midiInputDevices);
                }

                midiInputDevices.add(midiInputDevice);
            }

            if (midiDeviceAttachedListener != null) {
                midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
            }
        }

        // find MIDI Output device
        if (midiOutputDevicesMap.containsKey(gattDeviceAddress)) {
            synchronized (midiOutputDevicesMap) {
                Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gattDeviceAddress);
                for (MidiOutputDevice midiOutputDevice : midiOutputDevices) {
                    midiOutputDevice.close();
                }
                midiOutputDevicesMap.remove(gattDeviceAddress);
            }
        }

        MidiOutputDevice midiOutputDevice = MidiOutputDevice.getInstance(context, gatt);
        if (midiOutputDevice != null) {
            synchronized (midiOutputDevicesMap) {
                Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gattDeviceAddress);
                if (midiOutputDevices == null) {
                    midiOutputDevices = new HashSet<MidiOutputDevice>();
                    midiOutputDevicesMap.put(gattDeviceAddress, midiOutputDevices);
                }

                midiOutputDevices.add(midiOutputDevice);
            }

            if (midiDeviceAttachedListener != null) {
                midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
            }
        }

        if (midiInputDevice != null || midiOutputDevice != null) {
            // Create bond and configure Gatt, if this is BLE MIDI device
            BluetoothDevice bluetoothDevice = gatt.getDevice();
            if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                bluetoothDevice.createBond();
                bluetoothDevice.setPairingConfirmation(true);

                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                context.registerReceiver(new BondingBroadcastReceiver(midiInputDevice, midiOutputDevice), filter);
            } else {
                if (midiInputDevice != null) {
                    midiInputDevice.open();
                }
                if (midiOutputDevice != null) {
                    midiOutputDevice.open();
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // default is 23: maximum data length is 20 bytes
                // max is 512: maximum data length is 509 bytes
                gatt.requestMtu(23); // default value

                // Set the connection priority to high(for low latency)
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt.getDevice().getAddress());
        for (MidiInputDevice midiInputDevice : midiInputDevices) {
            if (midiInputDevice.getCharacteristic().equals(characteristic)) {
                midiInputDevice.incomingData(characteristic.getValue());
                break;
            }
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);

        if (descriptor != null) {
            if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, descriptor.getValue())) {
                gatt.setCharacteristicNotification(descriptor.getCharacteristic(), true);
            }
        }
    }

    /**
     * BroadcastReceiver for BLE Bonding
     *
     * @author K.Shoji
     */
    public static class BondingBroadcastReceiver extends BroadcastReceiver {
        final MidiInputDevice midiInputDevice;
        final MidiOutputDevice midiOutputDevice;

        BondingBroadcastReceiver(MidiInputDevice midiInputDevice, MidiOutputDevice midiOutputDevice) {
            this.midiInputDevice = midiInputDevice;
            this.midiOutputDevice = midiOutputDevice;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDED) {
                    // successfully bonded
                    context.unregisterReceiver(this);

                    midiInputDevice.open();
                    midiOutputDevice.open();
                }
            }
        }
    }

    /**
     * Obtains connected input devices
     * @return Set of {@link jp.kshoji.blemidi.device.MidiInputDevice}
     */
    public Set<MidiInputDevice> getMidiInputDevices() {
        Collection<Set<MidiInputDevice>> values = midiInputDevicesMap.values();

        Set<MidiInputDevice> result = new HashSet<MidiInputDevice>();
        for (Set<MidiInputDevice> value: values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Obtains connected output devices
     * @return Set of {@link jp.kshoji.blemidi.device.MidiOutputDevice}
     */
    public Set<MidiOutputDevice> getMidiOutputDevices() {
        Collection<Set<MidiOutputDevice>> values = midiOutputDevicesMap.values();

        Set<MidiOutputDevice> result = new HashSet<>();
        for (Set<MidiOutputDevice> value: values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Set the event listener to listen the Device attachment event
     * @param midiDeviceAttachedListener the listener
     */
    public void setOnMidiDeviceAttachedListener(OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiDeviceAttachedListener = midiDeviceAttachedListener;
    }

    /**
     * Set the event listener to listen the Device attachment event
     * @param midiDeviceDetachedListener the listener
     */
    public void setOnMidiDeviceDetachedListener(OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiDeviceDetachedListener = midiDeviceDetachedListener;
    }
}
