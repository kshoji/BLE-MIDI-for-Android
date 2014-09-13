package jp.kshoji.blemidi.central;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;

import java.util.Set;

import jp.kshoji.blemidi.central.callback.BleMidiCallback;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;

/**
 * Client for BLE MIDI Peripheral device service
 *
 * @author K.Shoji
 */
public class BleMidiCentralProvider {
    final BluetoothAdapter bluetoothAdapter;
    final Context context;
    final Handler handler;
    final BleMidiCallback midiCallback;

    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
            if (bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_LE) {
                return;
            }

            bluetoothDevice.connectGatt(context, true, midiCallback);
        }
    };

    /**
     * Check if Bluetooth LE device supported on the running environment.
     *
     * @param context
     * @return true if supported
     */
    public static boolean isBleSupported(Context context) {
        try {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) == false) {
                return false;
            }

            BluetoothAdapter bluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothAdapter.disable();
                return true;
            }
        } catch (Throwable t) {
            // ignore exception
        }
        return false;
    }

    /**
     * Constructor
     *
     * @param context
     */
    public BleMidiCentralProvider(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) == false) {
            throw new UnsupportedOperationException("Bluetooth LE not supported on this device.");
        }
        bluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter == null) {
            throw new UnsupportedOperationException("Bluetooth is not available.");
        }

        this.context = context;
        this.midiCallback = new BleMidiCallback(context);
        this.handler = new Handler();
    }

    /**
     * Starts to scan devices
     *
     * @param timeoutInMilliSeconds 0 or negative value : no timeout
     */
    public void startScanDevice(int timeoutInMilliSeconds) {
        bluetoothAdapter.startLeScan(leScanCallback);

        if (timeoutInMilliSeconds > 0) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothAdapter.stopLeScan(leScanCallback);
                }
            }, timeoutInMilliSeconds);
        }
    }

    /**
     * Stops to scan devices
     */
    public void stopScanDevice() {
        bluetoothAdapter.stopLeScan(leScanCallback);
    }

    public Set<MidiInputDevice> getMidiInputDevices() {
        return midiCallback.getMidiInputDevices();
    }

    public Set<MidiOutputDevice> getMidiOutputDevices() {
        return midiCallback.getMidiOutputDevices();
    }

    public void setOnMidiDeviceAttachedListener(OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiCallback.setOnMidiDeviceAttachedListener(midiDeviceAttachedListener);
    }

    public void setOnMidiDeviceDetachedListener(OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiCallback.setOnMidiDeviceDetachedListener(midiDeviceDetachedListener);
    }
}
