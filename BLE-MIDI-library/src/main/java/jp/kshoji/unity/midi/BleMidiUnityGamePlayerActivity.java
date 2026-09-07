package jp.kshoji.unity.midi;

import com.unity3d.player.UnityPlayerGameActivity;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;

public class BleMidiUnityGamePlayerActivity extends UnityPlayerGameActivity {
    BleMidiPeripheralProvider bleMidiPeripheralProvider;
    BleMidiCentralProvider bleMidiCentralProvider;
}
