package jp.kshoji.unity.midi;

import android.app.Activity;
import android.bluetooth.le.ScanResult;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.os.Build;
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    AssociationInfo associationInfo = data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo.class);
                    bleMidiCentralProvider.connectGatt(associationInfo.getAssociatedDevice().getBleDevice().getDevice());
                } catch (Throwable t) {
                    Log.d(Constants.TAG, t.getMessage(), t);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    ScanResult scanResult = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
                    bleMidiCentralProvider.connectGatt(scanResult.getDevice());
                } catch (Throwable t) {
                    Log.d(Constants.TAG, t.getMessage(), t);
                }
            }
        }
    }
}
