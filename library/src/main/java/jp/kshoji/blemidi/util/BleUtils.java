package jp.kshoji.blemidi.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * Utilities for Bluetooth LE
 * @author K.Shoji
 */
public class BleUtils {

    /**
     * Check if Bluetooth LE device supported on the running environment.
     *
     * @param context the context
     * @return true if supported
     */
    public static boolean isBleSupported(Context context) {
        try {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) == false) {
                return false;
            }

            BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

            final BluetoothAdapter bluetoothAdapter;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                bluetoothAdapter = bluetoothManager.getAdapter();
            } else {
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }

            if (bluetoothAdapter != null) {
                return true;
            }
        } catch (Throwable t) {
            // ignore exception
        }
        return false;
    }

    /**
     * Check if Bluetooth LE Peripheral mode supported on the running environment.
     *
     * @param context the context
     * @return true if supported
     */
    @SuppressLint("NewApi")
    public static boolean isBlePeripheralSupported(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }

        final BluetoothAdapter bluetoothAdapter =  ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        if (bluetoothAdapter == null) {
            return false;
        }

        return bluetoothAdapter.isMultipleAdvertisementSupported();
    }

    /**
     * Check if bluetooth function enabled
     * @param context the context
     * @return true if bluetooth enabled
     */
    public static boolean isBluetoothEnabled(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        if (bluetoothManager == null) {
            Log.i(Constants.TAG, "bluetoothManager == null");
            return false;
        }

        final BluetoothAdapter bluetoothAdapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        } else {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (bluetoothAdapter == null) {
            Log.i(Constants.TAG, "bluetoothAdapter == null");
            return false;
        }

        return bluetoothAdapter.isEnabled();
    }

    /**
     * Request code for bluetooth enabling
     */
    public static final int REQUEST_CODE_BLUETOOTH_ENABLE = 0xb1e;

    /**
     * Enables bluetooth function.<br />
     * the Activity may implement the `onActivityResult` method with the request code `REQUEST_CODE_BLUETOOTH_ENABLE`.
     * @param activity the activity
     */
    public static void enableBluetooth(Activity activity) {
        Log.i(Constants.TAG, "enabling bluetooth..");
        activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_BLUETOOTH_ENABLE);
    }
}