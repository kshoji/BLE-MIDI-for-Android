package jp.kshoji.blemidi.peripheral;

import android.bluetooth.BluetoothDevice;
import android.text.TextUtils;

/**
 * MidiInputDevice for Peripheral
 * @author K.Shoji
 */
public final class MidiInputDevice extends jp.kshoji.blemidi.device.MidiInputDevice {
    private final BluetoothDevice bluetoothDevice;

    /**
     * Constructor for Peripheral
     * @param bluetoothDevice the device
     *
     */
    public MidiInputDevice(BluetoothDevice bluetoothDevice) {
        super();
        this.bluetoothDevice = bluetoothDevice;
    }

    @Override
    public String getDeviceName() {
        if (TextUtils.isEmpty(bluetoothDevice.getName())) {
            return bluetoothDevice.getAddress();
        }
        return bluetoothDevice.getName();
    }

    @Override
    protected void incomingData(byte[] data) {
        super.incomingData(data);
    }
}
