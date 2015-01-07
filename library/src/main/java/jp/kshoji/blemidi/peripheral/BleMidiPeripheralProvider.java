package jp.kshoji.blemidi.peripheral;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
 * FIXME work in progress, this class is not tested.
 * TODO create MidiInputDevice, MidiOutputDevice from bluetooth* instances
 *
 * @author K.Shoji
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class BleMidiPeripheralProvider {
    final BluetoothManager bluetoothManager;
    final BluetoothLeAdvertiser bluetoothLeAdvertiser;
    final BluetoothGattServer gattServer;
    final Context context;
    private final BluetoothGattCharacteristic midiOutputCharacteristic;

    BluetoothGatt bluetoothGatt;
    MidiInputDevice midiInputDevice;
    MidiOutputDevice midiOutputDevice;
    OnMidiDeviceAttachedListener midiDeviceAttachedListener;
    OnMidiDeviceDetachedListener midiDeviceDetachedListener;

    /**
     * Constructor
     *
     * @param context
     */
    public BleMidiPeripheralProvider(Context context) {
        this.context = context;

        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        // register Gatt service to Gatt server
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        // TODO ? 16bit UUID
        BluetoothGattService midiGattService = new BluetoothGattService(BleUuidUtils.fromString("0001"), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        // UUID for receiving
        midiGattService.addCharacteristic(new BluetoothGattCharacteristic(BleUuidUtils.fromString("0002"), BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        // UUID for transmitting
        midiOutputCharacteristic = new BluetoothGattCharacteristic(BleUuidUtils.fromString("0003"), BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ);
        midiGattService.addCharacteristic(midiOutputCharacteristic);
        gattServer.addService(midiGattService);

        bluetoothLeAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

        startAdvertising();
    }

    private void startAdvertising() {
        // set up advertising setting
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build();

        // set up advertising data
        // TODO device name
        List<ParcelUuid> serviceUuids = new ArrayList<ParcelUuid>();
        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(BleUuidUtils.fromString("0001")))
                .build();

        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
    }

    /**
     * Callback for BLE connection
     */
    final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(Constants.TAG, "AdvertiseCallback.onStartSuccess settingsInEffect:" + settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.d(Constants.TAG, "AdvertiseCallback.onStartFailure with errorCode: " + errorCode);
        }
    };

    /**
     * Callback for BLE data transfer
     */
    final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);

                    bluetoothGatt = device.connectGatt(context, true, new BluetoothGattCallback() { });
                    Log.i(Constants.TAG, "BluetoothGattServerCallback.onConnectionStateChange connected. gatt:" + bluetoothGatt);

                    midiOutputDevice = MidiOutputDevice.getInstance(context, bluetoothGatt, midiOutputCharacteristic);
                    if (midiDeviceAttachedListener != null) {
                        // TODO invoke listener

                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    startAdvertising();

                    bluetoothGatt = null;
                    Log.i(Constants.TAG, "BluetoothGattServerCallback.onConnectionStateChange disconnected.");
                    if (midiDeviceDetachedListener != null) {
                        // TODO invoke listener
                    }
                    break;
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(Constants.TAG, "onCharacteristicReadRequest");

            // TODO ? process output data
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.i(Constants.TAG, "onCharacteristicWriteRequest");

            // process input data
            if (BleUuidUtils.matches(characteristic.getUuid(), UUID.fromString("0002"))) {
                // TODO replace with MidiInputDevice
                if (midiInputDevice == null) {
                    midiInputDevice = MidiInputDevice.getInstance(context, bluetoothGatt, characteristic);
                }
                midiInputDevice.incomingData(characteristic.getValue());
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
}
