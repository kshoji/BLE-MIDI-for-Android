package jp.kshoji.blemidi.util;

import android.bluetooth.BluetoothStatusCodes;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Helpers for GATT writes that can fail under congestion.
 *
 * @author K.Shoji
 */
public final class BleMidiGattWriter {
    /**
     * Max retries when {@link BluetoothStatusCodes#ERROR_GATT_WRITE_REQUEST_BUSY} is returned.
     */
    public static final int MAX_CONGESTION_RETRIES = 100;

    /**
     * Sleep between congestion retries, in milliseconds.
     */
    public static final long CONGESTION_RETRY_INTERVAL_MS = 20L;

    private BleMidiGattWriter() {
    }

    /**
     * One GATT write or notify attempt.
     */
    public interface GattWrite {
        /**
         * @return a {@link BluetoothStatusCodes} result
         */
        int execute();
    }

    /**
     * Executes a GATT write and retries while the remote side is busy (status 201).
     *
     * @param writer performs one writeCharacteristic / notifyCharacteristicChanged
     * @return true if the last attempt returned {@link BluetoothStatusCodes#SUCCESS}
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean writeWithCongestionRetry(@NonNull GattWrite writer) {
        int result = writer.execute();
        int retry = 0;
        while (result == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY
                && retry < MAX_CONGESTION_RETRIES) {
            try {
                Thread.sleep(CONGESTION_RETRY_INTERVAL_MS);
            } catch (InterruptedException ignored) {
            }
            retry++;
            result = writer.execute();
        }
        if (result == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY) {
            Log.d(Constants.TAG, "GATT write still busy after " + MAX_CONGESTION_RETRIES + " retries");
        }
        return result == BluetoothStatusCodes.SUCCESS;
    }
}
