package jp.kshoji.blemidi.device;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.BleMidiDeviceUtils;
import jp.kshoji.blemidi.util.BleUuidUtils;
import jp.kshoji.blemidi.util.Constants;
import jp.kshoji.blemidi.util.MidiParser;

/**
 * Represents BLE MIDI Input Device
 *
 * @author K.Shoji
 */
public final class MidiInputDevice {

    private BluetoothGattCharacteristic midiInputCharacteristic;
    private final BluetoothGatt bluetoothGatt;

    private final MidiParser midiParser = new MidiParser(this);

    /**
     * Obtains MidiInputDevice instance if available from specified BluetoothGatt
     * for Central
     *
     * @param context
     * @param bluetoothGatt
     * @return null if the device doesn't contain BLE MIDI service
     */
    public static MidiInputDevice getInstance(Context context, final BluetoothGatt bluetoothGatt) {
        // create instance if available
        try {
            return new MidiInputDevice(context, bluetoothGatt);
        } catch (IllegalArgumentException e) {
            Log.i(Constants.TAG, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Constructor for Central
     *
     * @param context
     * @param bluetoothGatt
     * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
     */
    private MidiInputDevice(Context context, final BluetoothGatt bluetoothGatt) throws IllegalArgumentException {
        this.bluetoothGatt = bluetoothGatt;

        BluetoothGattService midiService = BleMidiDeviceUtils.getMidiService(context, bluetoothGatt);
        if (midiService == null) {
            List<UUID> uuidList = new ArrayList<UUID>();
            for (BluetoothGattService service : bluetoothGatt.getServices()) {
                uuidList.add(service.getUuid());
                Log.i(Constants.TAG, "service UUID:" + service.getUuid());
                for (BluetoothGattCharacteristic characteristic :service.getCharacteristics()) {
                    Log.i(Constants.TAG, "characteristic UUID:" + characteristic.getUuid() + "\tproperties:" + characteristic.getProperties());
                }
            }
            throw new IllegalArgumentException("MIDI GattService not found. Service UUIDs:" + Arrays.toString(uuidList.toArray()));
        }

        midiInputCharacteristic = BleMidiDeviceUtils.getMidiInputCharacteristic(context, midiService);
        if (midiInputCharacteristic == null) {
            throw new IllegalArgumentException("MIDI GattCharacteristic not found. Service UUID:" + midiService.getUuid());
        }

        bluetoothGatt.setCharacteristicNotification(midiInputCharacteristic, true);

        List<BluetoothGattDescriptor> descriptors = midiInputCharacteristic.getDescriptors();
        for (BluetoothGattDescriptor descriptor : descriptors) {
            if (BleUuidUtils.matches(BleUuidUtils.fromShortValue(0x2902), descriptor.getUuid())) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean writeDescriptor = bluetoothGatt.writeDescriptor(descriptor);
                Log.i(Constants.TAG, "wrote descriptor: " + writeDescriptor + " data: " + Arrays.toString(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE));
            }
        }
    }

    /**
     * Obtains MidiInputDevice instance if available from specified BluetoothGatt
     * for Peripheral
     *
     * @param context
     * @param bluetoothGatt
     * @param characteristic
     * @return null if the device doesn't contain BLE MIDI service
     */
    public static MidiInputDevice getInstance(Context context, final BluetoothGatt bluetoothGatt, final BluetoothGattCharacteristic characteristic) {
        // create instance if available
        return new MidiInputDevice(context, bluetoothGatt, characteristic);
    }

    /**
     * Constructor for Peripheral
     *
     * @param context
     * @param bluetoothGatt
     * @param characteristic
     */
    private MidiInputDevice(Context context, BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
        this.bluetoothGatt = bluetoothGatt;
        midiInputCharacteristic = characteristic;

//        List<BluetoothGattDescriptor> descriptors = midiInputCharacteristic.getDescriptors();
//
//        for (BluetoothGattDescriptor descriptor : descriptors) {
//            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            bluetoothGatt.writeDescriptor(descriptor);
//        }
        bluetoothGatt.setCharacteristicNotification(midiInputCharacteristic, true);
    }

    /**
     * Attaches {@link jp.kshoji.blemidi.listener.OnMidiInputEventListener}
     * @param midiInputEventListener
     */
    public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
        midiParser.setMidiInputEventListener(midiInputEventListener);
    }

    /**
     * Close the device
     */
    public void close() {
        midiParser.setMidiInputEventListener(null);

        bluetoothGatt.disconnect();
        bluetoothGatt.close();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MidiInputDevice that = (MidiInputDevice) o;

        if (midiInputCharacteristic == null && that.midiInputCharacteristic == null) {
            return true;
        }

        if (midiInputCharacteristic == null || that.midiInputCharacteristic == null) {
            return false;
        }

        return midiInputCharacteristic.getUuid().equals(that.midiInputCharacteristic.getUuid());
    }

    @Override
    public int hashCode() {
        return midiInputCharacteristic.getUuid().hashCode();
    }

    /**
     * Obtains the device name
     *
     * @return device name
     */
    public String getDeviceName() {
        return bluetoothGatt.getDevice().getName();
    }

    @Override
    public String toString() {
        return getDeviceName();
    }

    /**
     * Notifies MIDI data
     * @param data
     */
    public void incomingData(byte[] data) {
        Log.d(Constants.TAG, "inputDevice: " + this.hashCode() + ", data: " + Arrays.toString(data));

        midiParser.parse(data);
    }

    /**
     * Obtain the {@link android.bluetooth.BluetoothGattCharacteristic} of this device
     * @return
     */
    public BluetoothGattCharacteristic getCharacteristic() {
        return midiInputCharacteristic;
    }
}
