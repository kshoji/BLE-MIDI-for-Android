package jp.kshoji.blemidi.central.callback;

import android.annotation.TargetApi;
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
import android.util.Log;

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
import jp.kshoji.blemidi.util.Constants;

/**
 * BluetoothGattCallback implementation for BLE MIDI devices.
 *
 * @author K.Shoji
 */
public final class BleMidiCallback extends BluetoothGattCallback {
    final Map<String, Set<MidiInputDevice>> midiInputDevicesMap = new HashMap<String, Set<MidiInputDevice>>();
    final Map<String, Set<MidiOutputDevice>> midiOutputDevicesMap = new HashMap<String, Set<MidiOutputDevice>>();
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
                Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt.getDevice().getAddress());
                if (midiInputDevices != null) {
                    for (MidiInputDevice midiInputDevice : midiInputDevices) {// java.lang.NullPointerException: Attempt to invoke interface method 'java.util.Iterator java.util.Set.iterator()' on a null object reference
                        Log.d(Constants.TAG, "midiInputDevice disconnected: " + midiInputDevice.getDeviceName());

                        midiInputDevice.close();
                        midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                    }
                    midiInputDevices.clear();
                    midiInputDevicesMap.remove(gatt.getDevice().getAddress());
                }

                Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gatt.getDevice().getAddress());
                if (midiOutputDevices != null) {
                    for (MidiOutputDevice midiOutputDevice : midiOutputDevices) {
                        Log.d(Constants.TAG, "midiOutputDevice disconnected: " + midiOutputDevice.getDeviceName());

                        midiOutputDevice.close();
                        midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                    }
                    midiOutputDevices.clear();
                    midiOutputDevicesMap.remove(gatt.getDevice().getAddress());
                }
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Create bond and configure Gatt
            BluetoothDevice bluetoothDevice = gatt.getDevice();
            if (bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                bluetoothDevice.createBond();
                bluetoothDevice.setPairingConfirmation(true);

                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                context.registerReceiver(bondingBroadcastReceiver, filter);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // default is 23: maximum data length is 20 bytes
                // max is 512: maximum data length is 509 bytes
                gatt.requestMtu(23); // default value

                // Set the connection priority to high(for low latency)
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            }

            // find MIDI Input device
            if (midiInputDevicesMap.containsKey(gatt.getDevice().getAddress())) {
                synchronized (midiInputDevicesMap) {
                    Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt.getDevice().getAddress());
                    for (MidiInputDevice midiInputDevice : midiInputDevices) {
                        midiInputDevice.close();
                    }
                    midiInputDevicesMap.remove(gatt.getDevice().getAddress());
                }
            }

            MidiInputDevice midiInputDevice = MidiInputDevice.getInstance(context, gatt);
            if (midiInputDevice != null) {
                synchronized (midiInputDevicesMap) {
                    Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt.getDevice().getAddress());
                    if (midiInputDevices == null) {
                        midiInputDevices = new HashSet<MidiInputDevice>();
                        midiInputDevicesMap.put(gatt.getDevice().getAddress(), midiInputDevices);
                    }

                    midiInputDevices.add(midiInputDevice);
                }

                if (midiDeviceAttachedListener != null) {
                    midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                }
            }

            // find MIDI Output device
            if (midiOutputDevicesMap.containsKey(gatt.getDevice().getAddress())) {
                synchronized (midiOutputDevicesMap) {
                    Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gatt.getDevice().getAddress());
                    for (MidiOutputDevice midiOutputDevice : midiOutputDevices) {
                        midiOutputDevice.close();
                    }
                    midiOutputDevicesMap.remove(gatt.getDevice().getAddress());
                }
            }

            MidiOutputDevice midiOutputDevice = MidiOutputDevice.getInstance(context, gatt);
            if (midiOutputDevice != null) {
                synchronized (midiOutputDevicesMap) {
                    Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gatt.getDevice().getAddress());
                    if (midiOutputDevices == null) {
                        midiOutputDevices = new HashSet<MidiOutputDevice>();
                        midiOutputDevicesMap.put(gatt.getDevice().getAddress(), midiOutputDevices);
                    }

                    midiOutputDevices.add(midiOutputDevice);
                }

                if (midiDeviceAttachedListener != null) {
                    midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.i(Constants.TAG, "onMtuChanged mtu:" + mtu);
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
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);

        Log.i(Constants.TAG, "onCharacteristicRead");
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);

        Log.i(Constants.TAG, "onDescriptorWrite: " + Arrays.toString(descriptor.getValue()) + ", status: " + status);
    }

    private final BroadcastReceiver bondingBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

            Log.d(Constants.TAG, "Bond state(10:bond_none, 11: bonding, 12: bonded) changed for: " + device.getAddress() + " new state: " + bondState + " previous: " + previousBondState);

            final String action = intent.getAction();

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDING) {
                    // Bonding...
                } else if (state == BluetoothDevice.BOND_BONDED) {
                    // Bonded...
                    context.unregisterReceiver(this);
                } else if (state == BluetoothDevice.BOND_NONE) {
                    // Not bonded...
                }
            }
        }
    };

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

        Set<MidiOutputDevice> result = new HashSet<MidiOutputDevice>();
        for (Set<MidiOutputDevice> value: values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Set the event listener to listen the Device attachment event
     * @param midiDeviceAttachedListener
     */
    public void setOnMidiDeviceAttachedListener(OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiDeviceAttachedListener = midiDeviceAttachedListener;
    }

    /**
     * Set the event listener to listen the Device attachment event
     * @param midiDeviceDetachedListener
     */
    public void setOnMidiDeviceDetachedListener(OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiDeviceDetachedListener = midiDeviceDetachedListener;
    }
}
