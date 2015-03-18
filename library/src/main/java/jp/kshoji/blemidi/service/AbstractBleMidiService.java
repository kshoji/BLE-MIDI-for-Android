package jp.kshoji.blemidi.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.util.Constants;

/**
 * Abstract Service for BLE MIDI
 *
 * @author K.Shoji
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
abstract class AbstractBleMidiService extends Service {

    private final Set<MidiInputDevice> midiInputDevices = new HashSet<>();
    private final Set<MidiOutputDevice> midiOutputDevices = new HashSet<>();

    private OnMidiDeviceAttachedListener midiDeviceAttachedListener = null;
    private OnMidiDeviceDetachedListener midiDeviceDetachedListener = null;

    private boolean isRunning = false;

    protected abstract void onStart();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            Log.d(Constants.TAG, "MIDI service starting.");

            onStart();

            isRunning = true;
        }

        return START_REDELIVER_INTENT; // must be restarted if stopped by the system, We must respond to midi events(!)
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        midiInputDevices.clear();

        midiOutputDevices.clear();

        Log.d(Constants.TAG, "MIDI service stopped.");
    }

    /**
     * Set {@link jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener} to listen MIDI devices have been connected
     *
     * @param midiDeviceAttachedListener the event listener
     */
    public void setOnMidiDeviceAttachedListener(@Nullable OnMidiDeviceAttachedListener midiDeviceAttachedListener) {
        this.midiDeviceAttachedListener = midiDeviceAttachedListener;
    }

    /**
     * Set {@link jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener} to listen MIDI devices have been disconnected
     *
     * @param midiDeviceDetachedListener the event listener
     */
    public void setOnMidiDeviceDetachedListener(@Nullable OnMidiDeviceDetachedListener midiDeviceDetachedListener) {
        this.midiDeviceDetachedListener = midiDeviceDetachedListener;
    }

    /**
     * Get {@link java.util.Set} of{@link jp.kshoji.blemidi.device.MidiInputDevice} to send MIDI events.
     *
     * @return the Set of MidiInputDevice
     */
    @NonNull
    public Set<MidiInputDevice> getMidiInputDevices() {
        return Collections.unmodifiableSet(midiInputDevices);
    }

    /**
     * Get {@link java.util.Set} of{@link jp.kshoji.blemidi.device.MidiOutputDevice} to send MIDI events.
     *
     * @return the Set of MidiOutputDevice
     */
    @NonNull
    public Set<MidiOutputDevice> getMidiOutputDevices() {
        return Collections.unmodifiableSet(midiOutputDevices);
    }

    protected OnMidiDeviceAttachedListener serviceMidiDeviceAttachedListener = new OnMidiDeviceAttachedListener() {

        @Override
        public void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice) {
            midiInputDevices.add(midiInputDevice);

            if (midiDeviceAttachedListener != null) {
                midiDeviceAttachedListener.onMidiInputDeviceAttached(midiInputDevice);
            }
        }

        @Override
        public void onMidiOutputDeviceAttached(@NonNull MidiOutputDevice midiOutputDevice) {
            midiOutputDevices.add(midiOutputDevice);

            if (midiDeviceAttachedListener != null) {
                midiDeviceAttachedListener.onMidiOutputDeviceAttached(midiOutputDevice);
            }
        }
    };

    protected OnMidiDeviceDetachedListener serviceMidiDeviceDetachedListener = new OnMidiDeviceDetachedListener() {

        @Override
        public void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice) {
            midiInputDevice.setOnMidiInputEventListener(null);
            midiInputDevices.remove(midiInputDevice);

            if (midiDeviceDetachedListener != null) {
                midiDeviceDetachedListener.onMidiInputDeviceDetached(midiInputDevice);
            }
        }

        @Override
        public void onMidiOutputDeviceDetached(@NonNull MidiOutputDevice midiOutputDevice) {
            midiOutputDevices.remove(midiOutputDevice);

            if (midiDeviceDetachedListener != null) {
                midiDeviceDetachedListener.onMidiOutputDeviceDetached(midiOutputDevice);
            }
        }
    };
}
