package jp.kshoji.blemidi.service;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;

import jp.kshoji.blemidi.peripheral.BleMidi2PeripheralProvider;

/**
 * Service for BLE MIDI 2.0 Peripheral
 *
 * @author K.Shoji
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class BleMidi2PeripheralService extends AbstractBleMidiService {
    private BleMidi2PeripheralProvider midiProvider = null;

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
        public BleMidi2PeripheralService getService() {
            return BleMidi2PeripheralService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    protected void onStart() {
        midiProvider = new BleMidi2PeripheralProvider(this);
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
