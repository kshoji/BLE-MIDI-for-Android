package jp.kshoji.unity.midi;

import java.util.HashMap;
import java.util.Locale;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.unity3d.player.UnityPlayer;
import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;

/**
 * BLE MIDI Plugin for Unity
 * @author K.Shoji
 */
public class BleMidiUnityPlugin {
    private static final String GAME_OBJECT_NAME = "MidiManager";

    private final OnMidiInputEventListener midiInputEventListener = new OnMidiInputEventListener() {

        private String serializeMidiMessage(String deviceAddress, int[] data) {
            StringBuilder sb = new StringBuilder(deviceAddress);
            for (int i = 0; i < data.length; i++) {
                sb.append(",");
                sb.append(String.format(Locale.ROOT, "%d", data[i]));
                if (i == data.length - 1) {
                    return sb.toString();
                }
            }
            return sb.toString();
        }

        private String serializeMidiMessage(String deviceAddress, int cable, byte[] data) {
            StringBuilder sb = new StringBuilder(deviceAddress);
            sb.append(",");
            sb.append(String.format(Locale.ROOT, "%d", cable));
            for (int i = 0; i < data.length; i++) {
                sb.append(",");
                sb.append(String.format(Locale.ROOT, "%d", data[i] & 0xff));
                if (i == data.length - 1) {
                    return sb.toString();
                }
            }
            return sb.toString();
        }

        private String serializeMidiMessage(String deviceAddress, int[] data1, byte[] data2) {
            StringBuilder sb = new StringBuilder(deviceAddress);
            for (int i = 0; i < data1.length; i++) {
                sb.append(",");
                sb.append(String.format(Locale.ROOT, "%d", data1[i]));
            }
            for (int i = 0; i < data2.length; i++) {
                sb.append(",");
                sb.append(String.format(Locale.ROOT, "%d", data2[i] & 0xff));
            }
            return sb.toString();
        }

        private String serializeMidiMessage(String deviceAddress, int[] data1, long data2) {
            StringBuilder sb = new StringBuilder(deviceAddress);
            for (int i = 0; i < data1.length; i++) {
                sb.append(",");
                sb.append(String.format(Locale.ROOT, "%d", data1[i]));
            }
            sb.append(",");
            sb.append(String.format(Locale.ROOT, "%d", data2));
            return sb.toString();
        }

        private static final int cable = 0;

        @Override
        public void onMidiNoteOff(final MidiInputDevice sender, int channel, int note, int velocity) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiNoteOff", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, note, velocity}));
        }

        @Override
        public void onMidiNoteOn(final MidiInputDevice sender, int channel, int note, int velocity) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiNoteOn", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, note, velocity}));
        }

        @Override
        public void onMidiPolyphonicAftertouch(final MidiInputDevice sender, int channel, int note, int pressure) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiPolyphonicAftertouch", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, note, pressure}));
        }

        @Override
        public void onMidiControlChange(final MidiInputDevice sender, int channel, int function, int value) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiControlChange", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, function, value}));
        }

        @Override
        public void onMidiProgramChange(final MidiInputDevice sender, int channel, int program) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiProgramChange", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, program}));
        }

        @Override
        public void onMidiChannelAftertouch(final MidiInputDevice sender, int channel, int pressure) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiChannelAftertouch", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, pressure}));
        }

        @Override
        public void onMidiPitchWheel(final MidiInputDevice sender, int channel, int amount) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiPitchWheel", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, amount}));
        }

        @Override
        public void onMidiSystemExclusive(final MidiInputDevice sender, @NonNull final byte[] systemExclusive) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiSystemExclusive", serializeMidiMessage(sender.getDeviceAddress(), cable, systemExclusive));
        }

        @Override
        public void onMidiTimeCodeQuarterFrame(MidiInputDevice sender, int timing) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiTimeCodeQuarterFrame", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, timing}));
        }

        @Override
        public void onMidiSongSelect(MidiInputDevice sender, int song) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiSongSelect", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, song}));
        }

        @Override
        public void onMidiSongPositionPointer(MidiInputDevice sender, int position) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiSongPositionPointer", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, position}));
        }

        @Override
        public void onMidiTuneRequest(MidiInputDevice sender) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiTuneRequest", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiTimingClock(MidiInputDevice sender) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiTimingClock", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiStart(MidiInputDevice sender) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiStart", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiContinue(MidiInputDevice sender) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiContinue", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiStop(MidiInputDevice sender) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiStop", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiActiveSensing(MidiInputDevice sender) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiActiveSensing", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiReset(MidiInputDevice sender) {
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiReset", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onRPNMessage(@NonNull MidiInputDevice sender, int channel, int function, int value) {

        }

        @Override
        public void onNRPNMessage(@NonNull MidiInputDevice sender, int channel, int function, int value) {

        }
    };

    public void sendMidiNoteOn(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 5) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiNoteOn(Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]));
        }
    }

    public void sendMidiNoteOff(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 5) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiNoteOff(Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]));
        }
    }

    public void sendMidiPolyphonicAftertouch(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 5) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiPolyphonicAftertouch(Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]));
        }
    }

    public void sendMidiControlChange(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 5) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiControlChange(Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]));
        }
    }

    public void sendMidiProgramChange(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 4) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiProgramChange(Integer.parseInt(split[2]), Integer.parseInt(split[3]));
        }
    }

    public void sendMidiChannelAftertouch(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 4) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiChannelAftertouch(Integer.parseInt(split[2]), Integer.parseInt(split[3]));
        }
    }

    public void sendMidiPitchWheel(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 4) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiPitchWheel(Integer.parseInt(split[2]), Integer.parseInt(split[3]));
        }
    }

    public void sendMidiSystemExclusive(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 1) {
            return;
        }
        byte[] sysEx = new byte[split.length - 1];
        for (int i = 0; i < split.length - 1; i++) {
            sysEx[i] = (byte) (Integer.parseInt(split[i + 1]) & 0xff);
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiSystemExclusive(sysEx);
        }
    }

    public void sendMidiTimeCodeQuarterFrame(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 3) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiTimeCodeQuarterFrame(Integer.parseInt(split[2]));
        }
    }

    public void sendMidiSongSelect(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 3) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiSongSelect(Integer.parseInt(split[2]));
        }
    }

    public void sendMidiSongPositionPointer(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 3) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiSongPositionPointer(Integer.parseInt(split[2]));
        }
    }

    public void sendMidiTuneRequest(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 1) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiTuneRequest();
        }
    }

    public void sendMidiTimingClock(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 1) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiTimingClock();
        }
    }

    public void sendMidiStart(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 1) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiStart();
        }
    }

    public void sendMidiContinue(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 1) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiContinue();
        }
    }

    public void sendMidiStop(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 2) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiStop();
        }
    }

    public void sendMidiActiveSensing(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 1) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiActiveSensing();
        }
    }

    public void sendMidiReset(String serializedMidiMessage) {
        String[] split = serializedMidiMessage.split(",");
        if (split.length < 1) {
            return;
        }
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(split[0]);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiReset();
        }
    }

    private BleMidiCentralProvider bleMidiCentralProvider;
    HashMap<String, MidiOutputDevice> midiOutputDeviceMap = new HashMap<>();

    public void initialize(Context context) {
        bleMidiCentralProvider = new BleMidiCentralProvider(context);
        bleMidiCentralProvider.setOnMidiDeviceAttachedListener(new OnMidiDeviceAttachedListener() {
            @Override
            public void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice) {
                midiInputDevice.setOnMidiInputEventListener(midiInputEventListener);
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiInputDeviceAttached", midiInputDevice.getDeviceAddress());
            }

            @Override
            public void onMidiOutputDeviceAttached(@NonNull MidiOutputDevice midiOutputDevice) {
                midiOutputDeviceMap.put(midiOutputDevice.getDeviceAddress(), midiOutputDevice);
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiOutputDeviceAttached", midiOutputDevice.getDeviceAddress());
            }
        });

        bleMidiCentralProvider.setOnMidiDeviceDetachedListener(new OnMidiDeviceDetachedListener() {
            @Override
            public void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice) {
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiInputDeviceDetached", midiInputDevice.getDeviceAddress());
            }

            @Override
            public void onMidiOutputDeviceDetached(@NonNull MidiOutputDevice midiOutputDevice) {
                midiOutputDeviceMap.remove(midiOutputDevice.getDeviceAddress());
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiOutputDeviceDetached", midiOutputDevice.getDeviceAddress());
            }
        });
    }

    /**
     * Starts scan device
     * @param timeoutInMilliSeconds timeout in msec, 0 : no timeout
     */
    public void startScanDevice(int timeoutInMilliSeconds) {
        bleMidiCentralProvider.startScanDevice(timeoutInMilliSeconds);
    }

    /**
     * Stops scan device
     */
    public void stopScanDevice() {
        bleMidiCentralProvider.stopScanDevice();
    }


    /**
     * Obtains device name for deviceId
     * @param deviceId the device id
     * @return device name, product name, or null
     */
    public String getDeviceName(String deviceId) {
        MidiOutputDevice device = midiOutputDeviceMap.get(deviceId);
        if (device != null) {
            if (!TextUtils.isEmpty(device.getDeviceName())) {
                return device.getDeviceName();
            }
        }
        return null;
    }

    public void terminate() {
        if (bleMidiCentralProvider != null) {
            bleMidiCentralProvider.stopScanDevice();
            bleMidiCentralProvider.terminate();
        }
    }
}
