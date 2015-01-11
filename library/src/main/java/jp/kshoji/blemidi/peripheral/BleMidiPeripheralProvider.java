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
import android.util.Log;

import java.util.UUID;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
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

    final Context context;
    final BluetoothManager bluetoothManager;
    final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    final BluetoothGattService informationGattService;
    final BluetoothGattService midiGattService;
    final BluetoothGattCharacteristic midiCharacteristic;
    BluetoothGattServer gattServer;

    MidiInputDevice midiInputDevice;
    MidiOutputDevice midiOutputDevice;

    OnMidiDeviceAttachedListener midiDeviceAttachedListener;
    OnMidiDeviceDetachedListener midiDeviceDetachedListener;

    String manufacturer = "kshoji.jp01234567890"; // 20 bytes
    String deviceName = "BLE MIDI";

    /**
     * Check if Bluetooth LE Peripheral mode supported on the running environment.
     *
     * @param context the context
     * @return true if supported
     */
    public static boolean isBlePeripheralSupported(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        final BluetoothAdapter bluetoothAdapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        } else {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (bluetoothAdapter == null) {
            return false;
        }

        return bluetoothAdapter.isMultipleAdvertisementSupported();
    }

    /**
     * Constructor
     *
     * @param context the context
     */
    public BleMidiPeripheralProvider(final Context context) {
        this.context = context.getApplicationContext();

        bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);

        final BluetoothAdapter bluetoothAdapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        } else {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

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
                .addServiceUuid(ParcelUuid.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700")) // Service for BLE MIDI
                .setIncludeDeviceName(true)
                .build();

        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseData, advertiseCallback);
    }

    /**
     * Stops advertising
     */
    public void stopAdvertising() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        }
        if (gattServer != null) {
            gattServer.clearServices();
            gattServer.close();
            gattServer = null;
        }
    }

    /**
     * Callback for BLE connection<br />
     * nothing to do.
     */
    final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {};

    /**
     * BroadcastReceiver for BLE Bonding
     *
     * @author K.Shoji
     */
    public class BondingBroadcastReceiver extends BroadcastReceiver {
        final BluetoothDevice device;

        BondingBroadcastReceiver(BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                Log.i(Constants.TAG, "BondingBroadcastReceiver.onReceive state(12:bonded):" + state);

                if (state == BluetoothDevice.BOND_BONDED) {
                    // successfully bonded
                    context.unregisterReceiver(this);

                    gattServer.connect(device, true);
                    midiInputDevice = MidiInputDevice.getPeripheralInstance(gattServer);
                    midiOutputDevice = MidiOutputDevice.getPeripheralInstance(device, gattServer, midiCharacteristic);

                    if (midiDeviceAttachedListener != null) {
                        midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                        midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
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
                        midiInputDevice = MidiInputDevice.getPeripheralInstance(gattServer);
                        midiOutputDevice = MidiOutputDevice.getPeripheralInstance(device, gattServer, midiCharacteristic);

                        if (midiDeviceAttachedListener != null) {
                            if (midiInputDevice != null) {
                                midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
                            }
                            if (midiOutputDevice != null) {
                                midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
                            }
                        }
                    }
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
                    if (midiDeviceAttachedListener != null) {
                        if (midiInputDevice != null) {
                            midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
                        }
                        if (midiOutputDevice != null) {
                            midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
                        }
                    }
                    break;
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            if (BleUuidUtils.matches(CHARACTERISTIC_BLE_MIDI, characteristic.getUuid())) {
                // send empty
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {});
            } else {
                switch (BleUuidUtils.toShortValue(characteristic.getUuid())) {
                    case 0x2A24:
                        // Model number
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, deviceName.getBytes());
                        break;
                    case 0x2A29:
                        // Manufacturer
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, manufacturer.getBytes());
                        break;
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            if (BleUuidUtils.matches(characteristic.getUuid(), CHARACTERISTIC_BLE_MIDI)) {
                if (midiInputDevice != null) {
                    midiInputDevice.incomingData(value);
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

    public MidiInputDevice getMidiInputDevice() {
        return midiInputDevice;
    }

    public MidiOutputDevice getMidiOutputDevice() {
        return midiOutputDevice;
    }

    public void setOnMidiDeviceAttachedListener(OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiDeviceAttachedListener = midiDeviceAttachedListener;
    }

    public void setOnMidiDeviceDetachedListener(OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiDeviceDetachedListener = midiDeviceDetachedListener;
    }

    public void setManufacturer(String manufacturer) {
        // TODO length check
        this.manufacturer = manufacturer;
    }

    public void setDeviceName(String deviceName) {
        // TODO length check
        this.deviceName = deviceName;
    }
}
