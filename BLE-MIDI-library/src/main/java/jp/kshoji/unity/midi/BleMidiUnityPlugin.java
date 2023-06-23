package jp.kshoji.unity.midi;

import java.util.HashMap;
import java.util.Locale;
import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.unity3d.player.UnityPlayer;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;

/**
 * BLE MIDI Plugin for Unity
 * @author K.Shoji
 */
public class BleMidiUnityPlugin {
    private static final String GAME_OBJECT_NAME = "MidiManager";

    private final jp.kshoji.blemidi.listener.OnMidiInputEventListener midiInputEventListener = new jp.kshoji.blemidi.listener.OnMidiInputEventListener() {

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

        private static final int cable = 0;

        @Override
        public void onMidiNoteOff(@NonNull final MidiInputDevice sender, int channel, int note, int velocity) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiNoteOff(sender.getDeviceAddress(), channel, note, velocity);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiNoteOff", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, note, velocity}));
        }

        @Override
        public void onMidiNoteOn(@NonNull final MidiInputDevice sender, int channel, int note, int velocity) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiNoteOn(sender.getDeviceAddress(), channel, note, velocity);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiNoteOn", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, note, velocity}));
        }

        @Override
        public void onMidiPolyphonicAftertouch(@NonNull final MidiInputDevice sender, int channel, int note, int pressure) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiPolyphonicAftertouch(sender.getDeviceAddress(), channel, note, pressure);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiPolyphonicAftertouch", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, note, pressure}));
        }

        @Override
        public void onMidiControlChange(@NonNull final MidiInputDevice sender, int channel, int function, int value) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiControlChange(sender.getDeviceAddress(), channel, function, value);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiControlChange", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, function, value}));
        }

        @Override
        public void onMidiProgramChange(@NonNull final MidiInputDevice sender, int channel, int program) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiProgramChange(sender.getDeviceAddress(), channel, program);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiProgramChange", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, program}));
        }

        @Override
        public void onMidiChannelAftertouch(@NonNull final MidiInputDevice sender, int channel, int pressure) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiChannelAftertouch(sender.getDeviceAddress(), channel, pressure);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiChannelAftertouch", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, pressure}));
        }

        @Override
        public void onMidiPitchWheel(@NonNull final MidiInputDevice sender, int channel, int amount) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiPitchWheel(sender.getDeviceAddress(), channel, amount);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiPitchWheel", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, channel, amount}));
        }

        @Override
        public void onMidiSystemExclusive(@NonNull final MidiInputDevice sender, @NonNull final byte[] systemExclusive) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiSystemExclusive(sender.getDeviceAddress(), systemExclusive);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiSystemExclusive", serializeMidiMessage(sender.getDeviceAddress(), cable, systemExclusive));
        }

        @Override
        public void onMidiTimeCodeQuarterFrame(@NonNull MidiInputDevice sender, int timing) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiTimeCodeQuarterFrame(sender.getDeviceAddress(), timing);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiTimeCodeQuarterFrame", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, timing}));
        }

        @Override
        public void onMidiSongSelect(@NonNull MidiInputDevice sender, int song) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiTimeCodeQuarterFrame(sender.getDeviceAddress(), song);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiSongSelect", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, song}));
        }

        @Override
        public void onMidiSongPositionPointer(@NonNull MidiInputDevice sender, int position) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiSongPositionPointer(sender.getDeviceAddress(), position);
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiSongPositionPointer", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable, position}));
        }

        @Override
        public void onMidiTuneRequest(@NonNull MidiInputDevice sender) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiTuneRequest(sender.getDeviceAddress());
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiTuneRequest", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiTimingClock(@NonNull MidiInputDevice sender) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiTimingClock(sender.getDeviceAddress());
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiTimingClock", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiStart(@NonNull MidiInputDevice sender) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiStart(sender.getDeviceAddress());
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiStart", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiContinue(@NonNull MidiInputDevice sender) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiContinue(sender.getDeviceAddress());
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiContinue", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiStop(@NonNull MidiInputDevice sender) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiStop(sender.getDeviceAddress());
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiStop", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiActiveSensing(@NonNull MidiInputDevice sender) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiActiveSensing(sender.getDeviceAddress());
                return;
            }
            UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiActiveSensing", serializeMidiMessage(sender.getDeviceAddress(), new int[] {cable}));
        }

        @Override
        public void onMidiReset(@NonNull MidiInputDevice sender) {
            if (onMidiInputEventListener != null) {
                onMidiInputEventListener.onMidiReset(sender.getDeviceAddress());
                return;
            }
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
    public void sendMidiNoteOn(String deviceId, int channel, int note, int velocity) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiNoteOn(channel, note, velocity);
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
    public void sendMidiNoteOff(String deviceId, int channel, int note, int velocity) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiNoteOff(channel, note, velocity);
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
    public void sendMidiPolyphonicAftertouch(String deviceId, int channel, int note, int pressure) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiPolyphonicAftertouch(channel, note, pressure);
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
    public void sendMidiControlChange(String deviceId, int channel, int function, int value) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiControlChange(channel, function, value);
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
    public void sendMidiProgramChange(String deviceId, int channel, int program) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiProgramChange(channel, program);
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
    public void sendMidiChannelAftertouch(String deviceId, int channel, int pressure) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiChannelAftertouch(channel, pressure);
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
    public void sendMidiPitchWheel(String deviceId, int channel, int amount) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiPitchWheel(channel, amount);
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
    public void sendMidiSystemExclusive(String deviceId, byte[] systemExclusive) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiSystemExclusive(systemExclusive);
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
    public void sendMidiTimeCodeQuarterFrame(String deviceId, int timing) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiTimeCodeQuarterFrame(timing);
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
    public void sendMidiSongSelect(String deviceId, int song) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiSongSelect(song);
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
    public void sendMidiSongPositionPointer(String deviceId, int position) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiSongPositionPointer(position);
        }
    }

    public void sendMidiTuneRequest(String deviceId) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiTuneRequest();
        }
    }

    public void sendMidiTimingClock(String deviceId) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiTimingClock();
        }
    }

    public void sendMidiStart(String deviceId) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiStart();
        }
    }

    public void sendMidiContinue(String deviceId) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiContinue();
        }
    }

    public void sendMidiStop(String deviceId) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiStop();
        }
    }

    public void sendMidiActiveSensing(String deviceId) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiActiveSensing();
        }
    }

    public void sendMidiReset(String deviceId) {
        MidiOutputDevice midiOutputDevice = midiOutputDeviceMap.get(deviceId);
        if (midiOutputDevice != null) {
            midiOutputDevice.sendMidiReset();
        }
    }

    private BleMidiPeripheralProvider bleMidiPeripheralProvider;
    private BleMidiCentralProvider bleMidiCentralProvider;
    HashMap<String, MidiOutputDevice> midiOutputDeviceMap = new HashMap<>();
    HashMap<String, MidiInputDevice> midiInputDeviceMap = new HashMap<>();

    OnBleMidiDeviceConnectionListener onMidiDeviceConnectionListener;
    OnBleMidiInputEventListener onMidiInputEventListener;

    public void initialize(Context context, OnBleMidiDeviceConnectionListener onMidiDeviceConnectionListener, OnBleMidiInputEventListener onMidiInputEventListener)
    {
        this.onMidiDeviceConnectionListener = onMidiDeviceConnectionListener;
        this.onMidiInputEventListener = onMidiInputEventListener;
        initialize(context);
    }

    public void initialize(Context context) {
        bleMidiCentralProvider = new BleMidiCentralProvider(context);
        if (UnityPlayer.currentActivity instanceof  BleMidiUnityPlayerActivity) {
            ((BleMidiUnityPlayerActivity)UnityPlayer.currentActivity).bleMidiCentralProvider = bleMidiCentralProvider;
        }
        bleMidiCentralProvider.setOnMidiDeviceAttachedListener(new jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener() {
            @Override
            public void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice) {
                midiInputDeviceMap.put(midiInputDevice.getDeviceAddress(), midiInputDevice);
                midiInputDevice.setOnMidiInputEventListener(midiInputEventListener);
                if (onMidiDeviceConnectionListener != null) {
                    onMidiDeviceConnectionListener.onMidiInputDeviceAttached(midiInputDevice.getDeviceAddress());
                    return;
                }
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiInputDeviceAttached", midiInputDevice.getDeviceAddress());
            }

            @Override
            public void onMidiOutputDeviceAttached(@NonNull MidiOutputDevice midiOutputDevice) {
                midiOutputDeviceMap.put(midiOutputDevice.getDeviceAddress(), midiOutputDevice);
                if (onMidiDeviceConnectionListener != null) {
                    onMidiDeviceConnectionListener.onMidiOutputDeviceAttached(midiOutputDevice.getDeviceAddress());
                    return;
                }
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiOutputDeviceAttached", midiOutputDevice.getDeviceAddress());
            }
        });

        bleMidiCentralProvider.setOnMidiDeviceDetachedListener(new jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener() {
            @Override
            public void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice) {
                midiInputDeviceMap.remove(midiInputDevice.getDeviceAddress());
                if (onMidiDeviceConnectionListener != null) {
                    onMidiDeviceConnectionListener.onMidiInputDeviceDetached(midiInputDevice.getDeviceAddress());
                    return;
                }
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiInputDeviceDetached", midiInputDevice.getDeviceAddress());
            }

            @Override
            public void onMidiOutputDeviceDetached(@NonNull MidiOutputDevice midiOutputDevice) {
                midiOutputDeviceMap.remove(midiOutputDevice.getDeviceAddress());
                if (onMidiDeviceConnectionListener != null) {
                    onMidiDeviceConnectionListener.onMidiOutputDeviceDetached(midiOutputDevice.getDeviceAddress());
                    return;
                }
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiOutputDeviceDetached", midiOutputDevice.getDeviceAddress());
            }
        });

        bleMidiPeripheralProvider = new BleMidiPeripheralProvider(context);
        if (UnityPlayer.currentActivity instanceof BleMidiUnityPlayerActivity) {
            ((BleMidiUnityPlayerActivity)UnityPlayer.currentActivity).bleMidiPeripheralProvider = bleMidiPeripheralProvider;
        }
        bleMidiPeripheralProvider.setOnMidiDeviceAttachedListener(new jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener() {
            @Override
            public void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice) {
                midiInputDeviceMap.put(midiInputDevice.getDeviceAddress(), midiInputDevice);
                midiInputDevice.setOnMidiInputEventListener(midiInputEventListener);
                if (onMidiDeviceConnectionListener != null) {
                    onMidiDeviceConnectionListener.onMidiInputDeviceAttached(midiInputDevice.getDeviceAddress());
                    return;
                }
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiInputDeviceAttached", midiInputDevice.getDeviceAddress());
            }

            @Override
            public void onMidiOutputDeviceAttached(@NonNull MidiOutputDevice midiOutputDevice) {
                midiOutputDeviceMap.put(midiOutputDevice.getDeviceAddress(), midiOutputDevice);
                if (onMidiDeviceConnectionListener != null) {
                    onMidiDeviceConnectionListener.onMidiOutputDeviceAttached(midiOutputDevice.getDeviceAddress());
                    return;
                }
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiOutputDeviceAttached", midiOutputDevice.getDeviceAddress());
            }
        });
        bleMidiPeripheralProvider.setOnMidiDeviceDetachedListener(new jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener() {
            @Override
            public void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice) {
                midiInputDeviceMap.remove(midiInputDevice.getDeviceAddress());
                if (onMidiDeviceConnectionListener != null) {
                    onMidiDeviceConnectionListener.onMidiInputDeviceDetached(midiInputDevice.getDeviceAddress());
                    return;
                }
                UnityPlayer.UnitySendMessage(GAME_OBJECT_NAME, "OnMidiInputDeviceDetached", midiInputDevice.getDeviceAddress());
            }

            @Override
            public void onMidiOutputDeviceDetached(@NonNull MidiOutputDevice midiOutputDevice) {
                midiOutputDeviceMap.remove(midiOutputDevice.getDeviceAddress());
                if (onMidiDeviceConnectionListener != null) {
                    onMidiDeviceConnectionListener.onMidiOutputDeviceDetached(midiOutputDevice.getDeviceAddress());
                    return;
                }
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
     * Starts advertising
     */
    public void startAdvertising()
    {
        bleMidiPeripheralProvider.startAdvertising();
    }

    /**
     * Stops advertising
     */
    public void stopAdvertising()
    {
        bleMidiPeripheralProvider.stopAdvertising();
    }

    /**
     * Obtains device name for deviceId
     * @param deviceId the device id
     * @return device name, product name, or null
     */
    public String getDeviceName(String deviceId) {
        MidiOutputDevice outputDevice = midiOutputDeviceMap.get(deviceId);
        if (outputDevice != null) {
            if (!TextUtils.isEmpty(outputDevice.getDeviceName())) {
                return outputDevice.getDeviceName();
            }
        }

        MidiInputDevice inputDevice = midiInputDeviceMap.get(deviceId);
        if (inputDevice != null) {
            if (!TextUtils.isEmpty(inputDevice.getDeviceName())) {
                return inputDevice.getDeviceName();
            }
        }

        return null;
    }

    public void terminate() {
        if (bleMidiCentralProvider != null) {
            bleMidiCentralProvider.stopScanDevice();
            bleMidiCentralProvider.terminate();
        }
        if (bleMidiPeripheralProvider != null) {
            bleMidiPeripheralProvider.stopAdvertising();
            bleMidiPeripheralProvider.terminate();
        }
    }
}
