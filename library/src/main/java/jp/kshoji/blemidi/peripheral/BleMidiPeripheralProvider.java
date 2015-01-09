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
import android.content.IntentFilter;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jp.kshoji.blemidi.central.callback.BleMidiCallback;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.util.BleUuidUtils;
import jp.kshoji.blemidi.util.Constants;

/**
 * Represents BLE MIDI Peripheral functions<br />
 * Supported with Android L or newer. (Currently, Nexus 5 only)
 *
 * FIXME work in progress, this class is not well tested.
 * TODO create MidiInputDevice, MidiOutputDevice from bluetooth* instances
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

    final BluetoothManager bluetoothManager;
    final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    BluetoothGattServer gattServer;
    final Context context;

    BluetoothGattService informationGattService;
    BluetoothGattService midiGattService;

    final BleMidiCallback midiCallback;
    MidiInputDevice midiInputDevice;
    MidiOutputDevice midiOutputDevice;
    OnMidiDeviceAttachedListener midiDeviceAttachedListener;
    OnMidiDeviceDetachedListener midiDeviceDetachedListener;

    String manufacturer = "kshoji.jp";
    String deviceName = "BLE MIDI";

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

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        Log.i(Constants.TAG, "bluetoothLeAdvertiser: " + bluetoothLeAdvertiser);
        Log.i(Constants.TAG, "isMultipleAdvertisementSupported:" + bluetoothAdapter.isMultipleAdvertisementSupported());

        // Device information service
        informationGattService = new BluetoothGattService(SERVICE_DEVICE_INFORMATION, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        informationGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_MANUFACTURER_NAME, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        informationGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_MODEL_NUMBER, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));

        // MIDI service
        midiGattService = new BluetoothGattService(SERVICE_BLE_MIDI, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        midiGattService.addCharacteristic(new BluetoothGattCharacteristic(CHARACTERISTIC_BLE_MIDI, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE));

        midiCallback = new BleMidiCallback(context);
    }

    /**
     * Starts advertising
     */
    public void startAdvertising() {
        // FIXME frequently stating causes NullPointerException
//        if (gattServer != null) {
//            gattServer.clearServices();
//            gattServer.close();
//        }

        // register Gatt service to Gatt server
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Log.i(Constants.TAG, "gattServer is null, check Bluetooth is ON.");
        }

        Log.i(Constants.TAG, "gattServer: " + gattServer);

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

        Log.i(Constants.TAG, "advertiseSettings:" + advertiseSettings);
        Log.i(Constants.TAG, "advertiseData:" + advertiseData);

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
     * Callback for BLE connection
     */
    final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.d(Constants.TAG, "AdvertiseCallback.onStartFailure with errorCode: " + errorCode);
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(Constants.TAG, "AdvertiseCallback.onStartSuccess settingsInEffect:" + settingsInEffect);
        }
    };

    /**
     * BroadcastReceiver for BLE Bonding
     *
     * @author K.Shoji
     */
    public static class BondingBroadcastReceiver extends BroadcastReceiver {
        final BluetoothGattServer gattServer;
        final BluetoothDevice device;

        BondingBroadcastReceiver(BluetoothGattServer gattServer, BluetoothDevice device) {
            this.gattServer = gattServer;
            this.device = device;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                Log.i(Constants.TAG, "BondingBroadcastReceiver.onReceive state(12:bonded):" + state);

                if (state == BluetoothDevice.BOND_BONDED) {
                    Log.i(Constants.TAG, "BOND_BONDED");
                    // successfully bonded
                    context.unregisterReceiver(this);

                    gattServer.connect(device, true);
                }
            }
        }
    }

    /**
     * Callback for BLE data transfer
     */
    final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.i(Constants.TAG, "BluetoothGattServerCallback.onNotificationSent status: " + status + ", device:" + device.toString());
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i(Constants.TAG, "BluetoothGattServerCallback.onConnectionStateChange connected. status: " + status + ", device:" + device.getName());
                    // check bond status
                    if (device.getBondState() == BluetoothDevice.BOND_NONE && false) {
                        // FIXME can not create bonding
                        Log.i(Constants.TAG, "creating Bond with: " + device.getName());
                        device.createBond();
                        device.setPairingConfirmation(true);

                        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                        context.registerReceiver(new BondingBroadcastReceiver(gattServer, device), filter);
                    } else {

                        Log.i(Constants.TAG, "device have bond, now connecting..");
                        List<BluetoothGattService> services = gattServer.getServices();
                        for (BluetoothGattService service : services) {
                            Log.i(Constants.TAG, "gattServer.service:" + service.getUuid());
                        }
                    }
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.i(Constants.TAG, "BluetoothGattServerCallback.onConnectionStateChange disconnected.");
                    break;
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            // this method will be called
            Log.i(Constants.TAG, "BluetoothGattServerCallback.onServiceAdded service.uuid:" + service.getUuid());
            if (BleUuidUtils.matches(service.getUuid(), UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700"))) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    Log.i(Constants.TAG, "BluetoothGattServerCallback.onServiceAdded characteristic:" + characteristic.getUuid());
                    if (BleUuidUtils.matches(characteristic.getUuid(), UUID.fromString("7772e5db-3868-4112-a1a9-f2669d106bf3"))) {
                        // do something??
//                        gattServer.notifyCharacteristicChanged(device, characteristic, false);
                    }
                }
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            Log.i(Constants.TAG, "BluetoothGattServerCallback.onExecuteWrite");
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.i(Constants.TAG, "BluetoothGattServerCallback.onDescriptorReadRequest");
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.i(Constants.TAG, "BluetoothGattServerCallback.onDescriptorWriteRequest");
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(Constants.TAG, "BluetoothGattServerCallback.onCharacteristicReadRequest uuid: " + Integer.toHexString(BleUuidUtils.toShortValue(characteristic.getUuid())));

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
            Log.i(Constants.TAG, "BluetoothGattServerCallback.onCharacteristicWriteRequest, offset: " + offset + ", value: " + Arrays.toString(value));

            if (BleUuidUtils.matches(characteristic.getUuid(), CHARACTERISTIC_BLE_MIDI)) {
                if (midiInputDevice != null) {
                    // TODO treat offset
                    midiInputDevice.incomingData(characteristic.getValue());
                }

                if (responseNeeded) {
                    // return empty data
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[] {});
                }
            }
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
