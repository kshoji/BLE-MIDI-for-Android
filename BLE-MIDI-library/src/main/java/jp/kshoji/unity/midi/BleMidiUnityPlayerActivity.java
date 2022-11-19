package jp.kshoji.unity.midi;

import android.app.Activity;
import android.bluetooth.le.ScanResult;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.util.Log;

import com.unity3d.player.UnityPlayerActivity;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;
import jp.kshoji.blemidi.util.BleUtils;
import jp.kshoji.blemidi.util.Constants;

public class BleMidiUnityPlayerActivity extends UnityPlayerActivity {
    BleMidiPeripheralProvider bleMidiPeripheralProvider;
    BleMidiCentralProvider bleMidiCentralProvider;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == BleUtils.SELECT_DEVICE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Log.i(Constants.TAG, "onActivityResult SELECT_DEVICE_REQUEST_CODE && Activity.RESULT_OK");
                try {
                    ScanResult scanResult = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
                    Log.i(Constants.TAG, "get BluetoothDevice from CompanionDeviceManager, device: " + scanResult.getDevice());
                    bleMidiCentralProvider.connectGatt(scanResult.getDevice());
                    Log.i(Constants.TAG, "scanResult.device connected.");
                } catch (Throwable t) {
                    Log.d(Constants.TAG, t.getMessage(), t);
                }
            }
        }
    }
}
