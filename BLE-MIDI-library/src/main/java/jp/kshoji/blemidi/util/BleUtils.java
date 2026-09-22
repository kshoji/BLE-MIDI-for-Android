package jp.kshoji.blemidi.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.companion.AssociatedDevice;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utilities for Bluetooth LE
 *
 * @author K.Shoji
 */
public class BleUtils {

    /**
     * Check if Bluetooth LE device supported on the running environment.
     *
     * @param context the context
     * @return true if supported
     */
    public static boolean isBleSupported(@NonNull final Context context) {
        try {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) == false) {
                return false;
            }

            final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

            final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                return true;
            }
        } catch (final Throwable ignored) {
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
    public static boolean isBlePeripheralSupported(@NonNull final Context context) {
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
     *
     * @param context the context
     * @return true if bluetooth enabled
     */
    public static boolean isBluetoothEnabled(@NonNull final Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        if (bluetoothManager == null) {
            return false;
        }

        final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            return false;
        }

        return bluetoothAdapter.isEnabled();
    }

    /**
     * Request code for bluetooth enabling
     */
    public static final int REQUEST_CODE_BLUETOOTH_ENABLE = 0xb1e;

    /**
     * Request code for BLE MIDI device selection
     */
    public static final int SELECT_DEVICE_REQUEST_CODE = 0x5e1e;

    /**
     * Enables bluetooth function.<br />
     * the Activity may implement the `onActivityResult` method with the request code `REQUEST_CODE_BLUETOOTH_ENABLE`.
     *
     * @param activity the activity
     */
    @SuppressLint("MissingPermission")
    public static void enableBluetooth(@NonNull final Activity activity) {
        activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_BLUETOOTH_ENABLE);
    }

    /**
     * Resolves a {@link BluetoothDevice} from Companion Device {@link AssociationInfo}.
     * Tries AssociatedDevice (API 34+), then falls back to MAC address.
     *
     * @param context the context
     * @param associationInfo the association info
     * @return the BluetoothDevice, or null if unresolved
     */
    @Nullable
    @SuppressLint("NewApi")
    public static BluetoothDevice getBluetoothDeviceFromAssociationInfo(@NonNull final Context context,
                                                                        @NonNull final AssociationInfo associationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                final AssociatedDevice associatedDevice = associationInfo.getAssociatedDevice();
                if (associatedDevice != null) {
                    final ScanResult bleScanResult = associatedDevice.getBleDevice();
                    if (bleScanResult != null) {
                        final BluetoothDevice device = bleScanResult.getDevice();
                        if (device != null) {
                            return device;
                        }
                    }
                    final BluetoothDevice bluetoothDevice = associatedDevice.getBluetoothDevice();
                    if (bluetoothDevice != null) {
                        return bluetoothDevice;
                    }
                }
            } catch (final Throwable t) {
                Log.e(Constants.TAG, "Failed to resolve BluetoothDevice from AssociatedDevice", t);
            }
        }

        try {
            final MacAddress macAddress = associationInfo.getDeviceMacAddress();
            if (macAddress != null) {
                return getRemoteBluetoothDevice(context, macAddress.toString());
            }
            Log.e(Constants.TAG, "AssociationInfo has no device MAC address");
        } catch (final Throwable t) {
            Log.e(Constants.TAG, "Failed to resolve BluetoothDevice from AssociationInfo MAC address", t);
        }
        return null;
    }

    /**
     * Resolves a {@link BluetoothDevice} from Companion Device Manager activity result Intent.
     * Tries {@link CompanionDeviceManager#EXTRA_ASSOCIATION}, then deprecated {@link CompanionDeviceManager#EXTRA_DEVICE}.
     *
     * @param context the context
     * @param data the activity result Intent
     * @return the BluetoothDevice, or null if unresolved
     */
    @Nullable
    @SuppressLint({"NewApi", "Deprecation"})
    public static BluetoothDevice getBluetoothDeviceFromCompanionDeviceResult(@NonNull final Context context,
                                                                              @Nullable final Intent data) {
        if (data == null) {
            Log.e(Constants.TAG, "CompanionDevice result Intent is null");
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                final AssociationInfo associationInfo;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    associationInfo = data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo.class);
                } else {
                    associationInfo = data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION);
                }
                if (associationInfo != null) {
                    final BluetoothDevice device = getBluetoothDeviceFromAssociationInfo(context, associationInfo);
                    if (device != null) {
                        return device;
                    }
                }
            } catch (final Throwable t) {
                Log.e(Constants.TAG, "Failed to read CompanionDeviceManager.EXTRA_ASSOCIATION", t);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                final ScanResult scanResult;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    scanResult = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult.class);
                } else {
                    scanResult = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
                }
                if (scanResult != null) {
                    final BluetoothDevice device = scanResult.getDevice();
                    if (device != null) {
                        return device;
                    }
                    Log.e(Constants.TAG, "CompanionDeviceManager.EXTRA_DEVICE ScanResult has no BluetoothDevice");
                } else {
                    Log.e(Constants.TAG, "CompanionDevice result has neither EXTRA_ASSOCIATION nor EXTRA_DEVICE");
                }
            } catch (final Throwable t) {
                Log.e(Constants.TAG, "Failed to read CompanionDeviceManager.EXTRA_DEVICE", t);
            }
        } else {
            Log.e(Constants.TAG, "CompanionDevice result is not supported on API " + Build.VERSION.SDK_INT);
        }
        return null;
    }

    @Nullable
    private static BluetoothDevice getRemoteBluetoothDevice(@NonNull final Context context,
                                                            @NonNull final String address) {
        try {
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e(Constants.TAG, "BluetoothManager is unavailable");
                return null;
            }
            final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter == null) {
                Log.e(Constants.TAG, "BluetoothAdapter is unavailable");
                return null;
            }
            return bluetoothAdapter.getRemoteDevice(address);
        } catch (final Throwable t) {
            Log.e(Constants.TAG, "Failed to get remote BluetoothDevice for address: " + address, t);
            return null;
        }
    }
}
