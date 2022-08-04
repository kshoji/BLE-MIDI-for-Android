package jp.kshoji.blemidi.service;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;

import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;

/**
 * Service for BLE MIDI Peripheral
 *
 * @author K.Shoji
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class BleMidiPeripheralService extends AbstractBleMidiService {
    private BleMidiPeripheralProvider midiProvider = null;

    /**
     * Binder for this Service
     */
    public class LocalBinder extends Binder {

        /**
         * Get the Service
         *
         * @return the Service
         */
        @NonNull
        public BleMidiPeripheralService getService() {
            return BleMidiPeripheralService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    protected void onStart() {
        midiProvider = new BleMidiPeripheralProvider(this);
        midiProvider.setOnMidiDeviceAttachedListener(serviceMidiDeviceAttachedListener);
        midiProvider.setOnMidiDeviceDetachedListener(serviceMidiDeviceDetachedListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopAdvertising();
    }

    /**
     * Starts advertising
     */
    public void startAdvertising() {
        if (midiProvider != null) {
            midiProvider.startAdvertising();
        }
    }

    /**
     * Stops advertising
     */
    public void stopAdvertising() {
        if (midiProvider != null) {
            midiProvider.stopAdvertising();
        }
    }
}
