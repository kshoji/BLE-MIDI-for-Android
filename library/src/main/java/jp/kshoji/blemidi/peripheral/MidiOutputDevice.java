package jp.kshoji.blemidi.peripheral;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.text.TextUtils;

/**
 * MidiOutputDevice for Peripheral
 * @author K.Shoji
 */
public class MidiOutputDevice extends jp.kshoji.blemidi.device.MidiOutputDevice {
    private final BluetoothGattServer bluetoothGattServer;
    private final BluetoothDevice bluetoothDevice;

    /**
     * Constructor for Peripheral
     * @param bluetoothDevice the device
     * @param bluetoothGattServer the gatt server
     * @param midiCharacteristic the characteristic of device
     */
    public MidiOutputDevice(final BluetoothDevice bluetoothDevice, final BluetoothGattServer bluetoothGattServer, final BluetoothGattCharacteristic midiCharacteristic) {
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
        midiOutputCharacteristic.setValue(writeBuffer);

        try {
            bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, midiOutputCharacteristic, false);
        } catch (Throwable ignored) {
            // ignore it
        }
    }
}
