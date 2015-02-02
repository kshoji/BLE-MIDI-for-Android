package jp.kshoji.blemidi.central;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.util.List;
import java.util.Set;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.listener.OnMidiScanStatusListener;
import jp.kshoji.blemidi.util.BleMidiDeviceUtils;
import jp.kshoji.blemidi.util.Constants;

/**
 * Client for BLE MIDI Peripheral device service
 *
 * @author K.Shoji
 */
public final class BleMidiCentralProvider {
    private final BluetoothAdapter bluetoothAdapter;
    private final Context context;
    private final Handler handler;
    private final BleMidiCallback midiCallback;

    /**
     * Callback for BLE device scanning
     */
    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
            if (bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_LE && bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_DUAL) {
                return;
            }

            bluetoothDevice.connectGatt(context, true, midiCallback);
        }
    };

    /**
     * Callback for BLE device scanning (for Lollipop or later)
     */
    private final ScanCallback scanCallback;

    /**
     * Constructor<br />
     * Before constructing the instance, check the Bluetooth availability.
     *
     * @param context the context
     */
    @SuppressLint("NewApi")
    public BleMidiCentralProvider(final Context context) throws UnsupportedOperationException {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) == false) {
            throw new UnsupportedOperationException("Bluetooth LE not supported on this device.");
        }
        bluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (bluetoothAdapter == null) {
            throw new UnsupportedOperationException("Bluetooth is not available.");
        }

        if (bluetoothAdapter.isEnabled() == false) {
            throw new UnsupportedOperationException("Bluetooth is disabled.");
        }

        this.context = context;
        this.midiCallback = new BleMidiCallback(context);
        this.handler = new Handler(context.getMainLooper());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scanCallback = new ScanCallback() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);

                    if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                        final BluetoothDevice bluetoothDevice = result.getDevice();

                        if (bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_LE && bluetoothDevice.getType() != BluetoothDevice.DEVICE_TYPE_DUAL) {
                            return;
                        }

                        if (!midiCallback.isConnected(bluetoothDevice)) {
                            bluetoothDevice.connectGatt(BleMidiCentralProvider.this.context, true, midiCallback);
                        }
                    }
                }
            };
        } else {
            scanCallback = null;
        }
    }

    private volatile boolean isScanning = false;

    /**
     * Set if the Bluetooth LE device need `Pairing` <br />
     * Pairing feature can be used on Android KitKat (API Level 19) or later.
     *
     * @param needsPairing if true, request paring with the connecting device
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void setRequestPairing(boolean needsPairing) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Log.d(Constants.TAG, "Pairing feature is not supported on API Level " + Build.VERSION.SDK_INT);
            return;
        }
        midiCallback.setNeedsBonding(needsPairing);
    }

    private Runnable stopScanRunnable = null;

    /**
     * Starts to scan devices
     *
     * @param timeoutInMilliSeconds 0 or negative value : no timeout
     */
    @SuppressLint({ "Deprecation", "NewApi" })
    public void startScanDevice(int timeoutInMilliSeconds) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            List<ScanFilter> scanFilters = BleMidiDeviceUtils.getBleMidiScanFilters(context);
            ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback);
        } else {
            bluetoothAdapter.startLeScan(leScanCallback);
        }

        isScanning = true;
        if (onMidiScanStatusListener != null) {
            onMidiScanStatusListener.onMidiScanStatusChanged(isScanning);
        }

        if (stopScanRunnable != null) {
            handler.removeCallbacks(stopScanRunnable);
        }

        if (timeoutInMilliSeconds > 0) {
            stopScanRunnable = new Runnable() {
                @Override
                public void run() {
                    stopScanDevice();

                    isScanning = false;
                    if (onMidiScanStatusListener != null) {
                        onMidiScanStatusListener.onMidiScanStatusChanged(isScanning);
                    }
                }
            };
            handler.postDelayed(stopScanRunnable, timeoutInMilliSeconds);
        }
    }

    /**
     * Stops to scan devices
     */
    @SuppressLint({ "Deprecation", "NewApi" })
    public void stopScanDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            } else {
                bluetoothAdapter.stopLeScan(leScanCallback);
            }
        } catch (Throwable ignored) {
            // NullPointerException on Bluetooth is OFF
        }

        if (stopScanRunnable != null) {
            handler.removeCallbacks(stopScanRunnable);
            stopScanRunnable = null;
        }

        isScanning = false;
        if (onMidiScanStatusListener != null) {
            onMidiScanStatusListener.onMidiScanStatusChanged(isScanning);
        }
    }

    /**
     * Disconnects the specified device
     *
     * @param midiInputDevice the device
     */
    public void disconnectDevice(MidiInputDevice midiInputDevice) {
        midiCallback.disconnectDevice(midiInputDevice);
    }

    /**
     * Disconnects the specified device
     *
     * @param midiOutputDevice the device
     */
    public void disconnectDevice(MidiOutputDevice midiOutputDevice) {
        midiCallback.disconnectDevice(midiOutputDevice);
    }

    /**
     * Obtains the set of {@link jp.kshoji.blemidi.device.MidiInputDevice} that is currently connected
     *
     * @return unmodifiable set
     */
    public Set<MidiInputDevice> getMidiInputDevices() {
        return midiCallback.getMidiInputDevices();
    }

    /**
     * Obtains the set of {@link jp.kshoji.blemidi.device.MidiOutputDevice} that is currently connected
     *
     * @return unmodifiable set
     */
    public Set<MidiOutputDevice> getMidiOutputDevices() {
        return midiCallback.getMidiOutputDevices();
    }

    private OnMidiScanStatusListener onMidiScanStatusListener;

    /**
     * Set the listener of device scanning status
     *
     * @param onMidiScanStatusListener the listener
     */
    public void setOnMidiScanStatusListener(OnMidiScanStatusListener onMidiScanStatusListener) {
        this.onMidiScanStatusListener = onMidiScanStatusListener;
    }

    /**
     * Set the listener for attaching devices
     *
     * @param midiDeviceAttachedListener the listener
     */
    public void setOnMidiDeviceAttachedListener(OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiCallback.setOnMidiDeviceAttachedListener(midiDeviceAttachedListener);
    }

    /**
     * Set the listener for detaching devices
     *
     * @param midiDeviceDetachedListener the listener
     */
    public void setOnMidiDeviceDetachedListener(OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiCallback.setOnMidiDeviceDetachedListener(midiDeviceDetachedListener);
    }

    /**
     * Terminates provider
     */
    public void terminate() {
        stopScanDevice();
        midiCallback.terminate();
    }
}
