package jp.kshoji.blemidi.central;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.BleMidiDeviceUtils;
import jp.kshoji.blemidi.util.BleMidiParser;
import jp.kshoji.blemidi.util.BleUuidUtils;
import jp.kshoji.blemidi.util.Constants;

/**
 * BluetoothGattCallback implementation for BLE MIDI devices.
 *
 * @author K.Shoji
 */
public final class BleMidiCallback extends BluetoothGattCallback {
    private final Map<String, Set<MidiInputDevice>> midiInputDevicesMap = new HashMap<>();
    private final Map<String, Set<MidiOutputDevice>> midiOutputDevicesMap = new HashMap<>();
    private final Map<String, List<BluetoothGatt>> deviceAddressGattMap = new HashMap<>();
    private final Context context;

    private OnMidiDeviceAttachedListener midiDeviceAttachedListener;
    private OnMidiDeviceDetachedListener midiDeviceDetachedListener;

    private boolean needsBonding = false;

    /**
     * Constructor
     *
     * @param context the context
     */
    public BleMidiCallback(@NonNull final Context context) {
        super();
        this.context = context;
    }

    /**
     * Checks if the specified device is already connected
     *
     * @param device the device
     * @return true if already connected
     */
    boolean isConnected(@NonNull BluetoothDevice device) {
        synchronized (deviceAddressGattMap) {
            return deviceAddressGattMap.containsKey(device.getAddress());
        }
    }

    private volatile static Object gattDiscoverServicesLock = null;
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        // In this method, the `status` parameter shall be ignored.
        // so, look `newState` parameter only.

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (deviceAddressGattMap.containsKey(gatt.getDevice().getAddress())) {
                return;
            }
            // process a device for the same time
            while (gattDiscoverServicesLock != null) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
            if (deviceAddressGattMap.containsKey(gatt.getDevice().getAddress())) {
                // same device has already registered
                return;
            }
            gattDiscoverServicesLock = gatt;
            if (gatt.discoverServices()) {
                // successfully started discovering
            } else {
                // already disconnected
                disconnectByDeviceAddress(gatt.getDevice().getAddress());
                gattDiscoverServicesLock = null;
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            disconnectByDeviceAddress(gatt.getDevice().getAddress());
            gattDiscoverServicesLock = null;
        }
    }

    @SuppressLint({"NewApi", "MissingPermission"})
    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        if (status != BluetoothGatt.GATT_SUCCESS) {
            gattDiscoverServicesLock = null;
            return;
        }

        final String gattDeviceAddress = gatt.getDevice().getAddress();

        // find MIDI Input device
        synchronized (midiInputDevicesMap) {
            if (midiInputDevicesMap.containsKey(gattDeviceAddress)) {
                Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gattDeviceAddress);
                if (midiInputDevices != null) {
                    for (MidiInputDevice midiInputDevice : midiInputDevices) {
                        ((InternalMidiInputDevice) midiInputDevice).stop();
                        midiInputDevice.setOnMidiInputEventListener(null);
                    }
                }
                midiInputDevicesMap.remove(gattDeviceAddress);
            }
        }

        MidiInputDevice midiInputDevice = null;
        try {
            midiInputDevice = new InternalMidiInputDevice(context, gatt);
        } catch (IllegalArgumentException iae) {
            Log.d(Constants.TAG, iae.getMessage());
        }
        if (midiInputDevice != null) {
            synchronized (midiInputDevicesMap) {
                Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gattDeviceAddress);
                if (midiInputDevices == null) {
                    midiInputDevices = new HashSet<>();
                    midiInputDevicesMap.put(gattDeviceAddress, midiInputDevices);
                }

                midiInputDevices.add(midiInputDevice);
            }

            // don't notify if the same device already connected
            if (!deviceAddressGattMap.containsKey(gattDeviceAddress))
            {
                if (midiDeviceAttachedListener != null) {
                    midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                }
            }
        }

        // find MIDI Output device
        synchronized (midiOutputDevicesMap) {
            midiOutputDevicesMap.remove(gattDeviceAddress);
        }

        MidiOutputDevice midiOutputDevice = null;
        try {
            midiOutputDevice = new InternalMidiOutputDevice(context, gatt);
        } catch (IllegalArgumentException iae) {
            Log.d(Constants.TAG, iae.getMessage());
        }
        if (midiOutputDevice != null) {
            synchronized (midiOutputDevicesMap) {
                Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(gattDeviceAddress);
                if (midiOutputDevices == null) {
                    midiOutputDevices = new HashSet<>();
                    midiOutputDevicesMap.put(gattDeviceAddress, midiOutputDevices);
                }

                midiOutputDevices.add(midiOutputDevice);
            }

            // don't notify if the same device already connected
            if (!deviceAddressGattMap.containsKey(gattDeviceAddress)) {
                if (midiDeviceAttachedListener != null) {
                    midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
                }
            }
        }

        if (midiInputDevice != null || midiOutputDevice != null) {
            synchronized (deviceAddressGattMap) {
                List<BluetoothGatt> bluetoothGatts = deviceAddressGattMap.get(gattDeviceAddress);
                if (bluetoothGatts == null) {
                    bluetoothGatts = new ArrayList<>();
                    deviceAddressGattMap.put(gattDeviceAddress, bluetoothGatts);
                }
                bluetoothGatts.add(gatt);
            }

            if (needsBonding && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Create bond and configure Gatt, if this is BLE MIDI device
                BluetoothDevice bluetoothDevice = gatt.getDevice();
                if (bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                    bluetoothDevice.createBond();
                    try {
                        bluetoothDevice.setPairingConfirmation(true);
                    } catch (Throwable t) {
                        // SecurityException if android.permission.BLUETOOTH_PRIVILEGED not available
                        Log.d(Constants.TAG, t.getMessage());
                    }

                    if (bondingBroadcastReceiver != null) {
                        context.unregisterReceiver(bondingBroadcastReceiver);
                    }
                    bondingBroadcastReceiver = new BondingBroadcastReceiver(midiInputDevice, midiOutputDevice);
                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                    context.registerReceiver(bondingBroadcastReceiver, filter);
                }
            } else {
                if (midiInputDevice != null) {
                    ((InternalMidiInputDevice)midiInputDevice).configureAsCentralDevice();
                }
                if (midiOutputDevice != null) {
                    ((InternalMidiOutputDevice)midiOutputDevice).configureAsCentralDevice();
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

        // all finished
        gattDiscoverServicesLock = null;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(gatt.getDevice().getAddress());
        if (midiInputDevices != null) {
            for (MidiInputDevice midiInputDevice : midiInputDevices) {
                ((InternalMidiInputDevice)midiInputDevice).incomingData(characteristic.getValue());
            }
        }
    }

    /**
     * Disconnect the specified device
     *
     * @param midiInputDevice the device
     */
    void disconnectDevice(@NonNull MidiInputDevice midiInputDevice) {
        if (!(midiInputDevice instanceof InternalMidiInputDevice)) {
            return;
        }

        disconnectByDeviceAddress(midiInputDevice.getDeviceAddress());
    }

    /**
     * Disconnect the specified device
     *
     * @param midiOutputDevice the device
     */
    void disconnectDevice(@NonNull MidiOutputDevice midiOutputDevice) {
        if (!(midiOutputDevice instanceof InternalMidiOutputDevice)) {
            return;
        }

        disconnectByDeviceAddress(midiOutputDevice.getDeviceAddress());
    }

    /**
     * Disconnects the device by its address
     *
     * @param deviceAddress the device address from {@link android.bluetooth.BluetoothGatt}
     */
    private void disconnectByDeviceAddress(@NonNull String deviceAddress) {
        synchronized (deviceAddressGattMap) {
            List<BluetoothGatt> bluetoothGatts = deviceAddressGattMap.get(deviceAddress);

            if (bluetoothGatts != null) {
                for (BluetoothGatt bluetoothGatt : bluetoothGatts) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                }

                deviceAddressGattMap.remove(deviceAddress);
            }
        }

        synchronized (midiInputDevicesMap) {
            Set<MidiInputDevice> midiInputDevices = midiInputDevicesMap.get(deviceAddress);
            if (midiInputDevices != null) {
                midiInputDevicesMap.remove(deviceAddress);

                for (MidiInputDevice midiInputDevice : midiInputDevices) {
                    ((InternalMidiInputDevice) midiInputDevice).stop();
                    midiInputDevice.setOnMidiInputEventListener(null);

                    if (midiDeviceDetachedListener != null) {
                        midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                    }

                }
                midiInputDevices.clear();
            }
        }

        synchronized (midiOutputDevicesMap) {
            Set<MidiOutputDevice> midiOutputDevices = midiOutputDevicesMap.get(deviceAddress);
            if (midiOutputDevices != null) {
                midiOutputDevicesMap.remove(deviceAddress);

                for (MidiOutputDevice midiOutputDevice : midiOutputDevices) {
                    if (midiDeviceDetachedListener != null) {
                        midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                    }
                }
                midiOutputDevices.clear();
            }
        }
    }

    /**
     * Terminates callback
     */
    public void terminate() {
        synchronized (deviceAddressGattMap) {
            for (List<BluetoothGatt> bluetoothGatts : deviceAddressGattMap.values()) {
                if (bluetoothGatts != null) {
                    for (BluetoothGatt bluetoothGatt : bluetoothGatts) {
                        bluetoothGatt.disconnect();
                        bluetoothGatt.close();
                    }
                }
            }
            deviceAddressGattMap.clear();
        }

        synchronized (midiInputDevicesMap) {
            for (Set<MidiInputDevice> midiInputDevices : midiInputDevicesMap.values()) {
                for (MidiInputDevice midiInputDevice : midiInputDevices) {
                    ((InternalMidiInputDevice) midiInputDevice).stop();
                    midiInputDevice.setOnMidiInputEventListener(null);
                }

                midiInputDevices.clear();
            }
            midiInputDevicesMap.clear();
        }

        synchronized (midiOutputDevicesMap) {
            midiOutputDevicesMap.clear();
        }

        if (bondingBroadcastReceiver != null) {
            context.unregisterReceiver(bondingBroadcastReceiver);
            bondingBroadcastReceiver = null;
        }
    }

    private BondingBroadcastReceiver bondingBroadcastReceiver;

    /**
     * Set if the Bluetooth LE device need `Pairing`
     *
     * @param needsBonding if true, request paring with the connecting device
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void setNeedsBonding(boolean needsBonding) {
        this.needsBonding = needsBonding;
    }

    /**
     * {@link android.content.BroadcastReceiver} for BLE Bonding
     *
     * @author K.Shoji
     */
    private class BondingBroadcastReceiver extends BroadcastReceiver {
        final MidiInputDevice midiInputDevice;
        final MidiOutputDevice midiOutputDevice;

        /**
         * Constructor
         *
         * @param midiInputDevice input device
         * @param midiOutputDevice output device
         */
        BondingBroadcastReceiver(@Nullable MidiInputDevice midiInputDevice, @Nullable MidiOutputDevice midiOutputDevice) {
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
                    bondingBroadcastReceiver = null;

                    if (midiInputDevice != null) {
                        ((InternalMidiInputDevice) midiInputDevice).configureAsCentralDevice();
                    }
                    if (midiOutputDevice != null) {
                        ((InternalMidiOutputDevice) midiOutputDevice).configureAsCentralDevice();
                    }
                }
            }
        }
    }

    /**
     * Obtains connected input devices
     *
     * @return Set of {@link jp.kshoji.blemidi.device.MidiInputDevice}
     */
    @NonNull
    public Set<MidiInputDevice> getMidiInputDevices() {
        Collection<Set<MidiInputDevice>> values = midiInputDevicesMap.values();

        Set<MidiInputDevice> result = new HashSet<>();
        for (Set<MidiInputDevice> value: values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Obtains connected output devices
     *
     * @return Set of {@link jp.kshoji.blemidi.device.MidiOutputDevice}
     */
    @NonNull
    public Set<MidiOutputDevice> getMidiOutputDevices() {
        Collection<Set<MidiOutputDevice>> values = midiOutputDevicesMap.values();

        Set<MidiOutputDevice> result = new HashSet<>();
        for (Set<MidiOutputDevice> value: values) {
            result.addAll(value);
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Set the listener for attaching devices
     *
     * @param midiDeviceAttachedListener the listener
     */
    public void setOnMidiDeviceAttachedListener(@Nullable OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiDeviceAttachedListener = midiDeviceAttachedListener;
    }

    /**
     * Set the listener for detaching devices
     *
     * @param midiDeviceDetachedListener the listener
     */
    public void setOnMidiDeviceDetachedListener(@Nullable OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiDeviceDetachedListener = midiDeviceDetachedListener;
    }

    /**
     * {@link MidiInputDevice} for Central
     *
     * @author K.Shoji
     */
    private static final class InternalMidiInputDevice extends MidiInputDevice {
        private final BluetoothGatt bluetoothGatt;
        private final BluetoothGattCharacteristic midiInputCharacteristic;

        private final BleMidiParser midiParser = new BleMidiParser(this);

        /**
         * Constructor for Central
         *
         * @param context the context
         * @param bluetoothGatt the gatt of device
         * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
         */
        public InternalMidiInputDevice(@NonNull final Context context, @NonNull final BluetoothGatt bluetoothGatt) throws IllegalArgumentException {
            super();
            this.bluetoothGatt = bluetoothGatt;

            BluetoothGattService midiService = BleMidiDeviceUtils.getMidiService(context, bluetoothGatt);
            if (midiService == null) {
                List<UUID> uuidList = new ArrayList<>();
                for (BluetoothGattService service : bluetoothGatt.getServices()) {
                    uuidList.add(service.getUuid());
                }
                throw new IllegalArgumentException("MIDI GattService not found from '" + bluetoothGatt.getDevice().getName() + "'. Service UUIDs:" + Arrays.toString(uuidList.toArray()));
            }

            midiInputCharacteristic = BleMidiDeviceUtils.getMidiInputCharacteristic(context, midiService);
            if (midiInputCharacteristic == null) {
                throw new IllegalArgumentException("MIDI Input GattCharacteristic not found. Service UUID:" + midiService.getUuid());
            }
        }

        /**
         * Stops parser's thread
         */
        void stop() {
            midiParser.stop();
        }

        /**
         * Configure the device as BLE Central
         */
        public void configureAsCentralDevice() {
            bluetoothGatt.setCharacteristicNotification(midiInputCharacteristic, true);

            List<BluetoothGattDescriptor> descriptors = midiInputCharacteristic.getDescriptors();
            for (BluetoothGattDescriptor descriptor : descriptors) {
                if (BleUuidUtils.matches(BleUuidUtils.fromShortValue(0x2902), descriptor.getUuid())) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }
            }

            bluetoothGatt.readCharacteristic(midiInputCharacteristic);
        }

        @Override
        public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
            midiParser.setMidiInputEventListener(midiInputEventListener);
        }

        @NonNull
        @Override
        public String getDeviceName() {
            return bluetoothGatt.getDevice().getName();
        }

        /**
         * Obtains device address
         *
         * @return device address
         */
        @NonNull
        public String getDeviceAddress() {
            return bluetoothGatt.getDevice().getAddress();
        }

        /**
         * Parse the MIDI data
         *
         * @param data the MIDI data
         */
        private void incomingData(@NonNull byte[] data) {
            midiParser.parse(data);
        }
    }

    /**
     * {@link jp.kshoji.blemidi.device.MidiOutputDevice} for Central
     *
     * @author K.Shoji
     */
    private static final class InternalMidiOutputDevice extends MidiOutputDevice {
        private final BluetoothGatt bluetoothGatt;
        private final BluetoothGattCharacteristic midiOutputCharacteristic;

        /**
         * Constructor for Central
         *
         * @param context the context
         * @param bluetoothGatt the gatt of device
         * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
         */
        public InternalMidiOutputDevice(@NonNull final Context context, @NonNull final BluetoothGatt bluetoothGatt) throws IllegalArgumentException {
            super();
            this.bluetoothGatt = bluetoothGatt;

            BluetoothGattService midiService = BleMidiDeviceUtils.getMidiService(context, bluetoothGatt);
            if (midiService == null) {
                List<UUID> uuidList = new ArrayList<>();
                for (BluetoothGattService service : bluetoothGatt.getServices()) {
                    uuidList.add(service.getUuid());
                }
                throw new IllegalArgumentException("MIDI GattService not found from '" + bluetoothGatt.getDevice().getName() + "'. Service UUIDs:" + Arrays.toString(uuidList.toArray()));
            }

            midiOutputCharacteristic = BleMidiDeviceUtils.getMidiOutputCharacteristic(context, midiService);
            if (midiOutputCharacteristic == null) {
                throw new IllegalArgumentException("MIDI Output GattCharacteristic not found. Service UUID:" + midiService.getUuid());
            }
        }

        /**
         * Configure the device as BLE Central
         */
        public void configureAsCentralDevice() {
            midiOutputCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        }

        @Override
        public void transferData(@NonNull byte[] writeBuffer) {
            midiOutputCharacteristic.setValue(writeBuffer);

            try {
                bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
            } catch (Throwable ignored) {
                // android.os.DeadObjectException will be thrown
                // ignore it
            }
        }

        @NonNull
        @Override
        public String getDeviceName() {
            return bluetoothGatt.getDevice().getName();
        }

        /**
         * Obtains device address
         *
         * @return device address
         */
        @NonNull
        public String getDeviceAddress() {
            return bluetoothGatt.getDevice().getAddress();
        }
    }
}
