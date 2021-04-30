package jp.kshoji.blemidi.service;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;

import jp.kshoji.blemidi.central.BleMidi2CentralProvider;

/**
 * Service for BLE MIDI 2.0 Central
 *
 * @author K.Shoji
 */
public final class BleMidi2CentralService extends AbstractBleMidiService {
    private BleMidi2CentralProvider midiProvider = null;

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
        public BleMidi2CentralService getService() {
            return BleMidi2CentralService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    protected void onStart() {
        midiProvider = new BleMidi2CentralProvider(this);
        midiProvider.setOnMidiDeviceAttachedListener(serviceMidiDeviceAttachedListener);
        midiProvider.setOnMidiDeviceDetachedListener(serviceMidiDeviceDetachedListener);
        midiProvider.startScanDevice(0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopScanDevice();
    }

    /**
     * Starts scanning devices
     */
    public void startScanDevice() {
        if (midiProvider != null) {
            midiProvider.startScanDevice(0);
        }
    }

    /**
     * Stops scanning devices
     */
    public void stopScanDevice() {
        if (midiProvider != null) {
            midiProvider.stopScanDevice();
        }
    }
}
