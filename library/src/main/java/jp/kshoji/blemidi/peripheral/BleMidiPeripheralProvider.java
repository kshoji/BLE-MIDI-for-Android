package jp.kshoji.blemidi.peripheral;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.BleMidiParser;
import jp.kshoji.blemidi.util.BleUuidUtils;
import jp.kshoji.blemidi.util.Constants;

/**
 * Represents BLE MIDI Peripheral functions<br />
 * Supported with Android Lollipop or newer.
 *
 * @author K.Shoji
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class BleMidiPeripheralProvider {
    public static final UUID SERVICE_DEVICE_INFORMATION = BleUuidUtils.fromShortValue(0x180A);
    public static final UUID CHARACTERISTIC_MANUFACTURER_NAME = BleUuidUtils.fromShortValue(0x2A29);
    public static final UUID CHARACTERISTIC_MODEL_NUMBER = BleUuidUtils.fromShortValue(0x2A24);
    public static final UUID SERVICE_BLE_MIDI = UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700");
    public static final UUID CHARACTERISTIC_BLE_MIDI = UUID.fromString("7772e5db-3868-4112-a1a9-f2669d106bf3");

    private static final int DEVICE_NAME_MAX_LENGTH = 100;

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private final BluetoothGattService informationGattService;
    private final BluetoothGattService midiGattService;
    private final BluetoothGattCharacteristic midiCharacteristic;
    private BluetoothGattServer gattServer;

    private final Map<String, MidiInputDevice> midiInputDevicesMap = new HashMap<>();
    private final Map<String, MidiOutputDevice> midiOutputDevicesMap = new HashMap<>();
    private final Map<String, BluetoothDevice> bluetoothDevicesMap = new HashMap<>();

    private OnMidiDeviceAttachedListener midiDeviceAttachedListener;
    private OnMidiDeviceDetachedListener midiDeviceDetachedListener;

    private String manufacturer = "kshoji.jp";
    private String deviceName = "BLE MIDI";

    /**
     * Constructor
     *
     * @param context the context
     */
    public BleMidiPeripheralProvider(final Context context) {
        this.context = context.getApplicationContext();

        bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);

        final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        Log.i(Constants.TAG, "isMultipleAdvertisementSupported:" + bluetoothAdapter.isMultipleAdvertisementSupported());
        if (bluetoothAdapter.isMultipleAdvertisementSupported() == false) {
            throw new UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.");
        }

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        Log.i(Constants.TAG, "bluetoothLeAdvertiser: " + bluetoothLeAdvertiser);
        if (bluetoothLeAdvertiser == null) {
            throw new UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.");
        }

        // Device information service
        informationGattService = new BluetoothGattService(SERVICE_DEVICE_INFORMATION, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        informationGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_MANUFACTURER_NAME, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        informationGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_MODEL_NUMBER, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));

        // MIDI service
        midiGattService = new BluetoothGattService(SERVICE_BLE_MIDI, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        midiCharacteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_BLE_MIDI, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        midiGattService.addCharacteristic(midiCharacteristic);
    }

    /**
     * Starts advertising
     */
    public void startAdvertising() {
        // register Gatt service to Gatt server
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Log.i(Constants.TAG, "gattServer is null, check Bluetooth is ON.");
            return;
        }

        gattServer.addService(informationGattService);
        gattServer.addService(midiGattService);

        // set up advertising setting
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700")) // BLE MIDI Service
                .setIncludeDeviceName(true)
                .build();

        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
    }

    /**
     * Stops advertising
     */
    public void stopAdvertising() {
        try {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        } catch (IllegalStateException ignored) {
            // BT Adapter is not turned ON
        }

        if (gattServer != null) {
            gattServer.clearServices();
        }
    }

    /**
     * Callback for BLE connection<br />
     * nothing to do.
     */
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {};

    /**
     * Disconnects the specified device
     * @param midiInputDevice the device
     */
    public void disconnectDevice(MidiInputDevice midiInputDevice) {
        if (!(midiInputDevice instanceof InternalMidiInputDevice)) {
            return;
        }

        disconnectByDeviceAddress(((InternalMidiInputDevice) midiInputDevice).getDeviceAddress());
    }

    /**
     * Disconnects the specified device.
     * @param midiOutputDevice the device
     */
    public void disconnectDevice(MidiOutputDevice midiOutputDevice) {
        if (!(midiOutputDevice instanceof InternalMidiOutputDevice)) {
            return;
        }

        disconnectByDeviceAddress(((InternalMidiOutputDevice)midiOutputDevice).getDeviceAddress());
    }

    /**
     * Disconnects the device by its address
     * @param deviceAddress the device address from {@link android.bluetooth.BluetoothGatt}
     */
    private void disconnectByDeviceAddress(String deviceAddress) {
        synchronized (bluetoothDevicesMap) {
            BluetoothDevice bluetoothDevice = bluetoothDevicesMap.get(deviceAddress);
            gattServer.cancelConnection(bluetoothDevice);

            bluetoothDevicesMap.remove(deviceAddress);
        }

        synchronized (midiInputDevicesMap) {
            MidiInputDevice midiInputDevice = midiInputDevicesMap.get(deviceAddress);
            if (midiInputDevice != null) {
                midiInputDevice.setOnMidiInputEventListener(null);

                if (midiDeviceDetachedListener != null) {
                    midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                }
            }
            midiInputDevicesMap.remove(deviceAddress);
        }

        synchronized (midiOutputDevicesMap) {
            MidiOutputDevice midiOutputDevice = midiOutputDevicesMap.get(deviceAddress);
            if (midiOutputDevice != null) {
                if (midiDeviceDetachedListener != null) {
                    midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                }
            }
            midiOutputDevicesMap.remove(deviceAddress);
        }
    }

    /**
     * Terminates instance
     */
    public void terminate() {
        stopAdvertising();

        gattServer.close();
        gattServer = null;

        synchronized (bluetoothDevicesMap) {
            for (BluetoothDevice bluetoothDevice : bluetoothDevicesMap.values()) {
                gattServer.cancelConnection(bluetoothDevice);
            }
            bluetoothDevicesMap.clear();
        }

        synchronized (midiInputDevicesMap) {
            for (MidiInputDevice midiInputDevice : midiInputDevicesMap.values()) {
                midiInputDevice.setOnMidiInputEventListener(null);
            }
            midiInputDevicesMap.clear();
        }

        synchronized (midiOutputDevicesMap) {
            midiOutputDevicesMap.clear();
        }
    }

    /**
     * BroadcastReceiver for BLE Bonding
     *
     * @author K.Shoji
     */
    private class BondingBroadcastReceiver extends BroadcastReceiver {
        final BluetoothDevice device;

        BondingBroadcastReceiver(BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDED) {
                    // successfully bonded
                    context.unregisterReceiver(this);

                    gattServer.connect(device, true);
                    MidiInputDevice midiInputDevice = new InternalMidiInputDevice(device);
                    MidiOutputDevice midiOutputDevice = new InternalMidiOutputDevice(device, gattServer, midiCharacteristic);

                    String deviceAddress = device.getAddress();

                    synchronized (midiInputDevicesMap) {
                        boolean isNewDevice = midiInputDevicesMap.get(deviceAddress) == null;
                        midiInputDevicesMap.put(deviceAddress, midiInputDevice);

                        // don't notify if the same device already connected
                        if (isNewDevice) {
                            if (midiDeviceAttachedListener != null) {
                                midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                            }
                        }
                    }

                    synchronized (midiOutputDevicesMap) {
                        boolean isNewDevice = midiOutputDevicesMap.get(deviceAddress) == null;
                        midiOutputDevicesMap.put(deviceAddress, midiOutputDevice);

                        // don't notify if the same device already connected
                        if (isNewDevice) {
                            if (midiDeviceAttachedListener != null) {
                                midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
                            }
                        }
                    }

                    synchronized (bluetoothDevicesMap) {
                        bluetoothDevicesMap.put(deviceAddress, device);
                    }
                }
            }
        }
    }

    /**
     * Callback for BLE data transfer
     */
    final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            String deviceAddress = device.getAddress();

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    // TODO check bond status
                    // TODO create bond
//                    if (device.getBondState() == BluetoothDevice.BOND_NONE) {
//                        Log.i(Constants.TAG, "creating Bond with: " + device.getName());
//                        device.createBond();
//                        device.setPairingConfirmation(true);
//
//                        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
//                        context.registerReceiver(new BondingBroadcastReceiver(device), filter);
//                    } else
                    {
                        // connecting to the device
                        gattServer.connect(device, true);

                        MidiInputDevice midiInputDevice = new InternalMidiInputDevice(device);
                        MidiOutputDevice midiOutputDevice = new InternalMidiOutputDevice(device, gattServer, midiCharacteristic);

                        synchronized (midiInputDevicesMap) {
                            midiInputDevicesMap.put(deviceAddress, midiInputDevice);
                        }

                        synchronized (midiOutputDevicesMap) {
                            midiOutputDevicesMap.put(deviceAddress, midiOutputDevice);
                        }

                        synchronized (bluetoothDevicesMap) {
                            bluetoothDevicesMap.put(deviceAddress, device);
                        }

                        if (midiDeviceAttachedListener != null) {
                            midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                            midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
                        }
                    }
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
                    if (midiDeviceAttachedListener != null) {

                        synchronized (midiInputDevicesMap) {
                            MidiInputDevice midiInputDevice = midiInputDevicesMap.get(deviceAddress);
                            if (midiInputDevice != null) {
                                midiInputDevice.setOnMidiInputEventListener(null);
                                midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                                midiInputDevicesMap.remove(deviceAddress);
                            }
                        }

                        synchronized (midiOutputDevicesMap) {
                            MidiOutputDevice midiOutputDevice = midiOutputDevicesMap.get(deviceAddress);
                            if (midiOutputDevice != null) {
                                midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                                midiOutputDevicesMap.remove(deviceAddress);
                            }
                        }

                        synchronized (bluetoothDevicesMap) {
                            bluetoothDevicesMap.remove(deviceAddress);
                        }
                    }
                    break;
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            UUID characteristicUuid = characteristic.getUuid();

            if (BleUuidUtils.matches(CHARACTERISTIC_BLE_MIDI, characteristicUuid)) {
                // send empty
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {});
            } else {
                switch (BleUuidUtils.toShortValue(characteristicUuid)) {
                    case 0x2A24:
                        // Model number
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, deviceName.getBytes(StandardCharsets.UTF_8));
                        break;
                    case 0x2A29:
                        // Manufacturer
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, manufacturer.getBytes(StandardCharsets.UTF_8));
                        break;
                    default:
                        // send empty
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {});
                        break;
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            if (BleUuidUtils.matches(characteristic.getUuid(), CHARACTERISTIC_BLE_MIDI)) {
                MidiInputDevice midiInputDevice = midiInputDevicesMap.get(device.getAddress());

                if (midiInputDevice != null) {
                    ((InternalMidiInputDevice)midiInputDevice).incomingData(value);
                }

                if (responseNeeded) {
                    // return empty data
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
                }
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, new byte[] {});
        }
    };

    /**
     * Obtains connected {@link jp.kshoji.blemidi.device.MidiInputDevice}
     * @return the set contains all connected devices
     */
    public Set<MidiInputDevice> getMidiInputDevices() {
        Set<MidiInputDevice> result = new HashSet<>();
        result.addAll(midiInputDevicesMap.values());
        return Collections.unmodifiableSet(result);
    }

    /**
     * Obtains connected {@link jp.kshoji.blemidi.device.MidiOutputDevice}
     * @return the set contains all connected devices
     */
    public Set<MidiOutputDevice> getMidiOutputDevices() {
        Set<MidiOutputDevice> result = new HashSet<>();
        result.addAll(midiOutputDevicesMap.values());
        return Collections.unmodifiableSet(result);
    }

    public void setOnMidiDeviceAttachedListener(OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiDeviceAttachedListener = midiDeviceAttachedListener;
    }

    public void setOnMidiDeviceDetachedListener(OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiDeviceDetachedListener = midiDeviceDetachedListener;
    }

    /**
     * Set the manufacturer name
     * @param manufacturer the name
     */
    public void setManufacturer(String manufacturer) {
        // length check
        byte[] manufacturerBytes = manufacturer.getBytes(StandardCharsets.UTF_8);
        if (manufacturerBytes.length > DEVICE_NAME_MAX_LENGTH) {
            // shorten
            byte[] bytes = new byte[DEVICE_NAME_MAX_LENGTH];
            System.arraycopy(manufacturerBytes, 0, bytes, 0, DEVICE_NAME_MAX_LENGTH);
            this.manufacturer = new String(bytes, StandardCharsets.UTF_8);
        } else {
            this.manufacturer = manufacturer;
        }
    }

    /**
     * Set the device name
     * @param deviceName the name
     */
    public void setDeviceName(String deviceName) {
        // length check
        byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
        if (deviceNameBytes.length > DEVICE_NAME_MAX_LENGTH) {
            // shorten
            byte[] bytes = new byte[DEVICE_NAME_MAX_LENGTH];
            System.arraycopy(deviceNameBytes, 0, bytes, 0, DEVICE_NAME_MAX_LENGTH);
            this.deviceName = new String(bytes, StandardCharsets.UTF_8);
        } else {
            this.deviceName = deviceName;
        }
    }

    /**
     * MidiInputDevice for Peripheral
     * @author K.Shoji
     */
    private final class InternalMidiInputDevice extends MidiInputDevice {
        private final BluetoothDevice bluetoothDevice;

        private final BleMidiParser midiParser = new BleMidiParser(this);

        /**
         * Constructor for Peripheral
         * @param bluetoothDevice the device
         *
         */
        public InternalMidiInputDevice(BluetoothDevice bluetoothDevice) {
            super();
            this.bluetoothDevice = bluetoothDevice;
        }

        @Override
        public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
            midiParser.setMidiInputEventListener(midiInputEventListener);
        }

        @Override
        public String getDeviceName() {
            if (TextUtils.isEmpty(bluetoothDevice.getName())) {
                return bluetoothDevice.getAddress();
            }
            return bluetoothDevice.getName();
        }

        void incomingData(byte[] data) {
            midiParser.parse(data);
        }

        /**
         * Obtains device address
         * @return device address
         */
        public final String getDeviceAddress() {
            return bluetoothDevice.getAddress();
        }
    }

    /**
     * MidiOutputDevice for Peripheral
     * @author K.Shoji
     */
    private final class InternalMidiOutputDevice extends MidiOutputDevice {
        private final BluetoothGattServer bluetoothGattServer;
        private final BluetoothDevice bluetoothDevice;
        private final BluetoothGattCharacteristic midiOutputCharacteristic;

        /**
         * Constructor for Peripheral
         * @param bluetoothDevice the device
         * @param bluetoothGattServer the gatt server
         * @param midiCharacteristic the characteristic of device
         */
        public InternalMidiOutputDevice(final BluetoothDevice bluetoothDevice, final BluetoothGattServer bluetoothGattServer, final BluetoothGattCharacteristic midiCharacteristic) {
            super();
            this.bluetoothDevice = bluetoothDevice;
            this.bluetoothGattServer = bluetoothGattServer;

            this.midiOutputCharacteristic = midiCharacteristic;
        }

        @Override
        public String getDeviceName() {
            if (TextUtils.isEmpty(bluetoothDevice.getName())) {
                return bluetoothDevice.getAddress();
            }
            return bluetoothDevice.getName();
        }

        @Override
        public void transferData(byte[] writeBuffer) {
            if (midiOutputCharacteristic == null) {
                return;
            }

            midiOutputCharacteristic.setValue(writeBuffer);

            try {
                bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, midiOutputCharacteristic, false);
            } catch (Throwable ignored) {
                // ignore it
            }
        }

        /**
         * Obtains device address
         * @return device address
         */
        public final String getDeviceAddress() {
            return bluetoothDevice.getAddress();
        }
    }
}
