package jp.kshoji.unity.midi;

import android.content.Intent;

import com.unity3d.player.UnityPlayerActivity;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;

public class BleMidiUnityPlayerActivity extends UnityPlayerActivity {
    BleMidiPeripheralProvider bleMidiPeripheralProvider;
    BleMidiCentralProvider bleMidiCentralProvider;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        BleMidiCompanionDeviceResultHandler.handleActivityResult(
                this, bleMidiCentralProvider, requestCode, resultCode, data);
    }
}
