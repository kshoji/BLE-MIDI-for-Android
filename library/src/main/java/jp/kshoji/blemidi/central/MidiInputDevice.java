package jp.kshoji.blemidi.central;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jp.kshoji.blemidi.util.BleMidiDeviceUtils;
import jp.kshoji.blemidi.util.BleUuidUtils;

/**
 * MidiInputDevice for Central
 * @author K.Shoji
 */
public final class MidiInputDevice extends jp.kshoji.blemidi.device.MidiInputDevice {
    private final BluetoothGatt bluetoothGatt;

    /**
     * Constructor for Central
     *
     * @param context the context
     * @param bluetoothGatt the gatt of device
     * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
     */
    public MidiInputDevice(final Context context, final BluetoothGatt bluetoothGatt) throws IllegalArgumentException {
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

    @Override
    protected void incomingData(byte[] data) {
        super.incomingData(data);
    }
}
