package jp.kshoji.blemidi.device;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
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

    protected BluetoothGattCharacteristic midiInputCharacteristic;
    protected final BluetoothGatt bluetoothGatt;
    protected final BluetoothGattServer bluetoothGattServer;

    private final MidiParser midiParser = new MidiParser(this);

    /**
     * Obtains MidiInputDevice instance if available from specified BluetoothGatt
     * for Central
     *
     * @param context the context
     * @param bluetoothGatt the gatt of device
     * @return null if the device doesn't contain BLE MIDI service
     * TODO remove this method
     */
    public static MidiInputDevice getCentralInstance(Context context, final BluetoothGatt bluetoothGatt) {
        // create instance if available
        try {
            return new MidiInputDevice(context, bluetoothGatt);
        } catch (IllegalArgumentException e) {
            Log.i(Constants.TAG, e.getMessage());
            return null;
        }
    }

    /**
     * Constructor for Central
     *
     * @param context the context
     * @param bluetoothGatt the gatt of device
     * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
     */
    private MidiInputDevice(Context context, final BluetoothGatt bluetoothGatt) throws IllegalArgumentException {
        this.bluetoothGatt = bluetoothGatt;
        this.bluetoothGattServer = null;

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
     * Obtains MidiInputDevice instance if available from specified BluetoothGatt
     * for Peripheral
     *
     * @param bluetoothGattServer the gatt server
     * @return null if the device doesn't contain BLE MIDI service
     * TODO remove this method
     */
    public static MidiInputDevice getPeripheralInstance(final BluetoothGattServer bluetoothGattServer) {
        return new MidiInputDevice(bluetoothGattServer);
    }

    /**
     * Constructor for Peripheral
     *
     * @param bluetoothGattServer the gatt server
     */
    private MidiInputDevice(BluetoothGattServer bluetoothGattServer) {
        this.bluetoothGatt = null;
        this.bluetoothGattServer = bluetoothGattServer;
    }

    /**
     * Attaches {@link jp.kshoji.blemidi.listener.OnMidiInputEventListener}
     * @param midiInputEventListener the listener
     */
    public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
        midiParser.setMidiInputEventListener(midiInputEventListener);
    }

    /**
     * Close the device
     */
    public void close() {
        midiParser.setMidiInputEventListener(null);
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
        if (bluetoothGatt != null) {
            return bluetoothGatt.getDevice().getName();
        }
        if (bluetoothGattServer != null) {
            return "BLE MIDI";
        }

        return "(null)";
    }

    @Override
    public String toString() {
        return getDeviceName();
    }

    /**
     * Notifies MIDI data
     * @param data the midi data
     */
    public void incomingData(byte[] data) {
        midiParser.parse(data);
    }

    /**
     * Configure the device as BLE Central
     * TODO remove this method
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
}
