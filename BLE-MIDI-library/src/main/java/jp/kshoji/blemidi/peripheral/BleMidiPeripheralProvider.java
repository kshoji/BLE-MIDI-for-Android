package jp.kshoji.blemidi.peripheral;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
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
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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

    /**
     * Gatt Services
     */
    private static final UUID SERVICE_DEVICE_INFORMATION = BleUuidUtils.fromShortValue(0x180A);
    private static final UUID SERVICE_BLE_MIDI = UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700");

    /**
     * Gatt Characteristics
     */
    private static final short MANUFACTURER_NAME = 0x2A29;
    private static final short MODEL_NUMBER = 0x2A24;
    private static final UUID CHARACTERISTIC_MANUFACTURER_NAME = BleUuidUtils.fromShortValue(MANUFACTURER_NAME);
    private static final UUID CHARACTERISTIC_MODEL_NUMBER = BleUuidUtils.fromShortValue(MODEL_NUMBER);
    private static final UUID CHARACTERISTIC_BLE_MIDI = UUID.fromString("7772e5db-3868-4112-a1a9-f2669d106bf3");

    /**
     * Gatt Characteristic Descriptor
     */
    private static final UUID DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = BleUuidUtils.fromShortValue(0x2902);

    private static final int DEVICE_NAME_MAX_LENGTH = 100;

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private final BluetoothGattService informationGattService;
    private final BluetoothGattService midiGattService;
    private final BluetoothGattCharacteristic midiCharacteristic;
    private BluetoothGattServer gattServer;
    private boolean gattServiceInitialized = false;

    private final Map<String, MidiInputDevice> midiInputDevicesMap = new HashMap<>();
    private final Map<String, MidiOutputDevice> midiOutputDevicesMap = new HashMap<>();
    private final Map<String, BluetoothDevice> bluetoothDevicesMap = new HashMap<>();

    private OnMidiDeviceAttachedListener midiDeviceAttachedListener;
    private OnMidiDeviceDetachedListener midiDeviceDetachedListener;
    private boolean autoStartDevice = true;

    private String manufacturer = "kshoji.jp";
    private String deviceName = "BLE MIDI";

    /**
     * Constructor<br />
     * Before constructing the instance, check the Bluetooth availability.
     *
     * @param context the context
     */
    public BleMidiPeripheralProvider(final Context context) throws UnsupportedOperationException {
        this.context = context.getApplicationContext();

        bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);

        final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            throw new UnsupportedOperationException("Bluetooth is not available.");
        }

        if (bluetoothAdapter.isEnabled() == false) {
            throw new UnsupportedOperationException("Bluetooth is disabled.");
        }

        Log.d(Constants.TAG, "isMultipleAdvertisementSupported:" + bluetoothAdapter.isMultipleAdvertisementSupported());
        if (bluetoothAdapter.isMultipleAdvertisementSupported() == false) {
            throw new UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.");
        }

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        Log.d(Constants.TAG, "bluetoothLeAdvertiser: " + bluetoothLeAdvertiser);
        if (bluetoothLeAdvertiser == null) {
            throw new UnsupportedOperationException("Bluetooth LE Advertising not supported on this device.");
        }

        // Device information service
        informationGattService = new BluetoothGattService(SERVICE_DEVICE_INFORMATION, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        informationGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_MANUFACTURER_NAME, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        informationGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_MODEL_NUMBER, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));

        // MIDI service
        midiCharacteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_BLE_MIDI, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        midiCharacteristic.addDescriptor(descriptor);
        midiCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        midiGattService = new BluetoothGattService(SERVICE_BLE_MIDI, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        midiGattService.addCharacteristic(midiCharacteristic);
    }

    /**
     * Starts advertising
     */
    public void startAdvertising() throws SecurityException {
        // register Gatt service to Gatt server
        if (gattServer == null) {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        }

        if (gattServer == null) {
            Log.d(Constants.TAG, "gattServer is null, check Bluetooth is ON.");
            return;
        }

        // these service will be listened.
        while (!gattServiceInitialized) {
            gattServer.clearServices();
            try {
                gattServer.addService(informationGattService);
                while (gattServer.getService(informationGattService.getUuid()) == null) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
                gattServer.addService(midiGattService);// NullPointerException, DeadObjectException thrown here
                while (gattServer.getService(midiGattService.getUuid()) == null) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
                gattServiceInitialized = true;
            } catch (Exception e) {
                Log.d(Constants.TAG, "Adding Service failed, retrying..");

                try {
                    gattServer.clearServices();
                } catch (Throwable ignored) {
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }
        }

        // set up advertising setting
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build();

        // set up advertising data
        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .setIncludeDeviceName(true)
                .build();

        // set up scan result
        AdvertiseData scanResult = new AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid.fromString(SERVICE_DEVICE_INFORMATION.toString()))
                .addServiceUuid(ParcelUuid.fromString(SERVICE_BLE_MIDI.toString()))
                .build();

        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, scanResult, advertiseCallback);
    }

    /**
     * Stops advertising
     */
    public void stopAdvertising() throws SecurityException {
        try {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        } catch (IllegalStateException ignored) {
            // BT Adapter is not turned ON
        }
    }

    /**
     * Callback for BLE connection<br />
     * nothing to do.
     */
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {};

    /**
     * Disconnects the specified device
     *
     * @param midiInputDevice the device
     */
    public void disconnectDevice(@NonNull MidiInputDevice midiInputDevice) {
        if (!(midiInputDevice instanceof InternalMidiInputDevice)) {
            return;
        }

        disconnectByDeviceAddress(midiInputDevice.getDeviceAddress());
    }

    /**
     * Disconnects the specified device
     *
     * @param midiOutputDevice the device
     */
    public void disconnectDevice(@NonNull MidiOutputDevice midiOutputDevice) {
        if (!(midiOutputDevice instanceof InternalMidiOutputDevice)) {
            return;
        }

        disconnectByDeviceAddress(midiOutputDevice.getDeviceAddress());
    }

    /**
     * callback for disconnecting a bluetooth device
     */
    private final BluetoothGattCallback disconnectCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) throws SecurityException {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(Constants.TAG, "onConnectionStateChange status: " + status + ", newState: " + newState);
            // disconnect the device
            if (gatt != null) {
                gatt.disconnect();
            }
        }
    };

    /**
     * Disconnects the device by its address
     *
     * @param deviceAddress the device address from {@link android.bluetooth.BluetoothGatt}
     */
    private void disconnectByDeviceAddress(@NonNull String deviceAddress) throws SecurityException {
        synchronized (bluetoothDevicesMap) {
            BluetoothDevice bluetoothDevice = bluetoothDevicesMap.get(deviceAddress);
            if (bluetoothDevice != null) {
                gattServer.cancelConnection(bluetoothDevice);
                bluetoothDevice.connectGatt(context, true, disconnectCallback);
            }
        }
    }

    /**
     * Terminates provider
     */
    public void terminate() throws SecurityException {
        stopAdvertising();

        synchronized (bluetoothDevicesMap) {
            for (BluetoothDevice bluetoothDevice : bluetoothDevicesMap.values()) {
                gattServer.cancelConnection(bluetoothDevice);
                bluetoothDevice.connectGatt(context, true, disconnectCallback);
            }
            bluetoothDevicesMap.clear();
        }

        if (gattServer != null) {
            try {
                gattServer.clearServices();
                gattServiceInitialized = false;
            } catch (Throwable ignored) {
                // android.os.DeadObjectException
            }
            try {
                gattServer.close();
            } catch (Throwable ignored) {
                // android.os.DeadObjectException
            }
            gattServer = null;
        }

        synchronized (midiInputDevicesMap) {
            for (MidiInputDevice midiInputDevice : midiInputDevicesMap.values()) {
                ((InternalMidiInputDevice) midiInputDevice).stop();
                midiInputDevice.setOnMidiInputEventListener(null);
            }
            midiInputDevicesMap.clear();
        }

        synchronized (midiOutputDevicesMap) {
            midiOutputDevicesMap.clear();
        }
    }

    /**
     * Callback for BLE data transfer
     */
    final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);

            synchronized (midiOutputDevicesMap) {
                MidiOutputDevice midiOutputDevice = midiOutputDevicesMap.get(device.getAddress());
                if (midiOutputDevice != null) {
                    ((InternalMidiOutputDevice)midiOutputDevice).setBufferSize(mtu < 23 ? 20 : mtu - 3);
                }
            }
            Log.d(Constants.TAG, "Peripheral onMtuChanged address: " + device.getAddress() + ", mtu: " + mtu);
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    connectMidiDevice(device);
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
                    String deviceAddress = device.getAddress();

                    synchronized (midiInputDevicesMap) {
                        MidiInputDevice midiInputDevice = midiInputDevicesMap.get(deviceAddress);
                        if (midiInputDevice != null) {
                            midiInputDevicesMap.remove(deviceAddress);

                            midiInputDevice.terminate();
                            midiInputDevice.setOnMidiInputEventListener(null);
                            if (midiDeviceDetachedListener != null) {
                                midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                            }
                        }
                    }

                    synchronized (midiOutputDevicesMap) {
                        MidiOutputDevice midiOutputDevice = midiOutputDevicesMap.get(deviceAddress);
                        if (midiOutputDevice != null) {
                            midiOutputDevicesMap.remove(deviceAddress);

                            if (midiDeviceDetachedListener != null) {
                                midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                            }
                        }
                    }

                    synchronized (bluetoothDevicesMap) {
                        bluetoothDevicesMap.remove(deviceAddress);
                    }
                    break;
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) throws SecurityException {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            UUID characteristicUuid = characteristic.getUuid();

            if (BleUuidUtils.matches(CHARACTERISTIC_BLE_MIDI, characteristicUuid)) {
                // send empty
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {});
            } else {
                switch (BleUuidUtils.toShortValue(characteristicUuid)) {
                    case MODEL_NUMBER:
                        // the running device's MODEL
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Build.MODEL.substring(0, Math.min(Build.MODEL.length(), 20)).getBytes(StandardCharsets.UTF_8));
                        break;
                    case MANUFACTURER_NAME:
                        // the running device's MANUFACTURER
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Build.MANUFACTURER.substring(0, Math.min(Build.MANUFACTURER.length(), 20)).getBytes(StandardCharsets.UTF_8));
                        break;
                    default:
                        // send empty
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {});
                        break;
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) throws SecurityException {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            if (BleUuidUtils.matches(characteristic.getUuid(), CHARACTERISTIC_BLE_MIDI)) {
                MidiInputDevice midiInputDevice = midiInputDevicesMap.get(device.getAddress());

                if (midiInputDevice != null) {
                    ((InternalMidiInputDevice)midiInputDevice).incomingData(value);
                }

                if (responseNeeded) {
                    // send empty
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {});
                }
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) throws SecurityException {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            byte[] descriptorValue = descriptor.getValue();
            try {
                System.arraycopy(value, 0, descriptorValue, offset, value.length);
                descriptor.setValue(descriptorValue);
            } catch (IndexOutOfBoundsException ignored) {
            }

            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {});
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) throws SecurityException {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);

            if (offset == 0) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, descriptor.getValue());
            } else {
                final byte[] value = descriptor.getValue();
                byte[] result = new byte[value.length - offset];
                try {
                    System.arraycopy(value, offset, result, 0, result.length);
                } catch (IndexOutOfBoundsException ignored) {
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, result);
            }
        }
    };

    /**
     * Connect as BLE MIDI device with specified {@link android.bluetooth.BluetoothDevice}
     *
     * @param device the device
     */
    private void connectMidiDevice(@NonNull BluetoothDevice device) {
        MidiInputDevice midiInputDevice = new InternalMidiInputDevice(device);
        MidiOutputDevice midiOutputDevice = new InternalMidiOutputDevice(device, gattServer, midiCharacteristic);

        String deviceAddress = device.getAddress();

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

        if (autoStartDevice) {
            midiInputDevice.start();
            midiOutputDevice.start();
        }
    }

    /**
     * Obtains the set of {@link jp.kshoji.blemidi.device.MidiInputDevice} that is currently connected
     *
     * @return the set contains all connected devices
     */
    @NonNull
    public Set<MidiInputDevice> getMidiInputDevices() {
        Set<MidiInputDevice> result = new HashSet<>(midiInputDevicesMap.values());
        return Collections.unmodifiableSet(result);
    }

    /**
     * Obtains the set of {@link jp.kshoji.blemidi.device.MidiOutputDevice} that is currently connected
     *
     * @return the set contains all connected devices
     */
    @NonNull
    public Set<MidiOutputDevice> getMidiOutputDevices() {
        Set<MidiOutputDevice> result = new HashSet<>(midiOutputDevicesMap.values());
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
     * Set the manufacturer name
     *
     * @param manufacturer the name
     */
    public void setManufacturer(@NonNull String manufacturer) {
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
     *
     * @param deviceName the name
     */
    public void setDeviceName(@NonNull String deviceName) {
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
     * Sets MidiInputDevice to start automatically at being connected
     * @param enable true to enable, default: true
     */
    public void setAutoStartDevice(boolean enable) {
        autoStartDevice = enable;
    }

    /**
     * {@link jp.kshoji.blemidi.device.MidiInputDevice} for Peripheral
     *
     * @author K.Shoji
     */
    private static final class InternalMidiInputDevice extends MidiInputDevice {
        private final BluetoothDevice bluetoothDevice;

        private final BleMidiParser midiParser = new BleMidiParser(this);

        /**
         * Constructor for Peripheral
         *
         * @param bluetoothDevice the device
         *
         */
        public InternalMidiInputDevice(@NonNull BluetoothDevice bluetoothDevice) {
            super();
            this.bluetoothDevice = bluetoothDevice;
        }

        /**
         * Starts parser's thread
         */
        @Override
        public void start() {
            midiParser.start();
        }

        /**
         * Stops parser's thread
         */
        @Override
        public void stop() {
            midiParser.stop();
        }

        /**
         * Terminates parser's thread
         */
        @Override
        public void terminate() {
            midiParser.terminate();
        }

        @Override
        public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
            midiParser.setMidiInputEventListener(midiInputEventListener);
        }

        @NonNull
        @Override
        public String getDeviceName() throws SecurityException {
            return "";
        }

        @NonNull
        @Override
        public String getManufacturer() {
            return "";
        }

        @NonNull
        @Override
        public String getModel() {
            return "";
        }

        private void incomingData(@NonNull byte[] data) {
            midiParser.parse(data);
        }

        /**
         * Obtains device address
         *
         * @return device address
         */
        @NonNull
        public String getDeviceAddress() {
            return bluetoothDevice.getAddress();
        }
    }

    /**
     * {@link jp.kshoji.blemidi.device.MidiOutputDevice} for Peripheral
     *
     * @author K.Shoji
     */
    private static final class InternalMidiOutputDevice extends MidiOutputDevice {
        private final BluetoothGattServer bluetoothGattServer;
        private final BluetoothDevice bluetoothDevice;
        private final BluetoothGattCharacteristic midiOutputCharacteristic;
        private int bufferSize = 20;

        /**
         * Constructor for Peripheral
         *
         * @param bluetoothDevice the device
         * @param bluetoothGattServer the gatt server
         * @param midiCharacteristic the characteristic of device
         */
        public InternalMidiOutputDevice(@NonNull final BluetoothDevice bluetoothDevice, @NonNull final BluetoothGattServer bluetoothGattServer, @NonNull final BluetoothGattCharacteristic midiCharacteristic) {
            super();
            this.bluetoothDevice = bluetoothDevice;
            this.bluetoothGattServer = bluetoothGattServer;
            this.midiOutputCharacteristic = midiCharacteristic;
        }

        @NonNull
        @Override
        public String getDeviceName() throws SecurityException {
            return "";
        }

        @NonNull
        @Override
        public String getManufacturer() {
            return "";
        }

        @NonNull
        @Override
        public String getModel() {
            return "";
        }

        @Override
        public void transferData(@NonNull byte[] writeBuffer) throws SecurityException {
            midiOutputCharacteristic.setValue(writeBuffer);

            try {
                bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, midiOutputCharacteristic, false);
            } catch (Throwable ignored) {
                // ignore it
            }
        }

        /**
         * Obtains device address
         *
         * @return device address
         */
        public @NonNull String getDeviceAddress() {
            return bluetoothDevice.getAddress();
        }

        @Override
        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }
}
