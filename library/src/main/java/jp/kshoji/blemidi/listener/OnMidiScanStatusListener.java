package jp.kshoji.blemidi.listener;

/**
 * Listener for Bluetooth LE Scanning status change events.
 */
public interface OnMidiScanStatusListener {
    void onMidiScanStatusChanged(boolean isScanning);
}
