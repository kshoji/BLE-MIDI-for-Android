package jp.kshoji.unity.midi;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.util.BleUtils;
import jp.kshoji.blemidi.util.Constants;

/**
 * Shared CompanionDevice activity-result handling for Unity player activities.
 */
final class BleMidiCompanionDeviceResultHandler {
    private BleMidiCompanionDeviceResultHandler() {
    }

    static void handleActivityResult(@NonNull final Context context,
                                     @Nullable final BleMidiCentralProvider bleMidiCentralProvider,
                                     final int requestCode,
                                     final int resultCode,
                                     @Nullable final Intent data) {
        if (requestCode != BleUtils.SELECT_DEVICE_REQUEST_CODE || resultCode != Activity.RESULT_OK) {
            return;
        }
        if (bleMidiCentralProvider == null) {
            Log.e(Constants.TAG, "CompanionDevice result ignored: bleMidiCentralProvider is null");
            return;
        }
        if (data == null) {
            Log.e(Constants.TAG, "CompanionDevice result ignored: Intent data is null");
            return;
        }

        final BluetoothDevice device =
                BleUtils.getBluetoothDeviceFromCompanionDeviceResult(context, data);
        if (device == null) {
            Log.e(Constants.TAG, "CompanionDevice result: failed to resolve BluetoothDevice");
            return;
        }
        bleMidiCentralProvider.connectGatt(device);
    }
}
