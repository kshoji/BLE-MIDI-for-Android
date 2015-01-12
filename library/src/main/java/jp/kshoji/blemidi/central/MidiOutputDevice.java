package jp.kshoji.blemidi.central;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jp.kshoji.blemidi.util.BleMidiDeviceUtils;

/**
 * MidiOutputDevice for Central
 * @author K.Shoji
 */
public class MidiOutputDevice extends jp.kshoji.blemidi.device.MidiOutputDevice {
    private final BluetoothGatt bluetoothGatt;

    /**
     * Constructor for Central
     *
     * @param context the context
     * @param bluetoothGatt the gatt of device
     * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
     */
    public MidiOutputDevice(final Context context, final BluetoothGatt bluetoothGatt) throws IllegalArgumentException {
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
    public void transferData(byte[] writeBuffer) {
        midiOutputCharacteristic.setValue(writeBuffer);

        try {
            bluetoothGatt.writeCharacteristic(midiOutputCharacteristic);
        } catch (Throwable ignored) {
            // android.os.DeadObjectException will be thrown
            // ignore it
        }
    }

    @Override
    public String getDeviceName() {
        return bluetoothGatt.getDevice().getName();
    }

    /**
     * Obtains device address
     * @return device address
     */
    public String getDeviceAddress() {
        return bluetoothGatt.getDevice().getAddress();
    }
}
