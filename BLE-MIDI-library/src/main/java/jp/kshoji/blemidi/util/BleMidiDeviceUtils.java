package jp.kshoji.blemidi.util;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.kshoji.blemidi.R;

/**
 * Utility for finding BLE MIDI devices
 *
 * @author K.Shoji
 */
public final class BleMidiDeviceUtils {

    /**
     * Obtains BluetoothGattService for MIDI
     *
     * @param context the context
     * @param bluetoothGatt the gatt of device
     * @param midiVersion the MIDI major version: 1 or 2
     * @return null if no service found
     */
    @Nullable
    public static BluetoothGattService getMidiService(@NonNull final Context context, @NonNull final BluetoothGatt bluetoothGatt, final int midiVersion) {
        List<BluetoothGattService> services = bluetoothGatt.getServices();
        String[] uuidStringArray = context.getResources().getStringArray(midiVersion == 1 ? R.array.uuidListForMidiService : R.array.uuidListForMidi2Service);

        for (BluetoothGattService service : services) {
            for (String uuidString : uuidStringArray) {
                UUID uuid = BleUuidUtils.fromString(uuidString);
                if (BleUuidUtils.matches(service.getUuid(), uuid)) {
                    return service;
                }
            }
        }

        return null;
    }

    /**
     * Obtains BluetoothGattCharacteristic for MIDI Input
     *
     * @param context the context
     * @param bluetoothGattService the gatt service of device
     * @param midiVersion the MIDI major version: 1 or 2
     * @return null if no characteristic found
     */
    @Nullable
    public static BluetoothGattCharacteristic getMidiInputCharacteristic(@NonNull final Context context, @NonNull final BluetoothGattService bluetoothGattService, final int midiVersion) {
        List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
        String[] uuidStringArray = context.getResources().getStringArray(midiVersion == 1 ? R.array.uuidListForMidiInputCharacteristic : R.array.uuidListForMidi2InputCharacteristic);

        for (BluetoothGattCharacteristic characteristic : characteristics) {
            for (String uuidString : uuidStringArray) {
                UUID uuid = BleUuidUtils.fromString(uuidString);
                if (BleUuidUtils.matches(characteristic.getUuid(), uuid)) {
                    return characteristic;
                }
            }
        }

        return null;
    }

    /**
     * Obtains BluetoothGattCharacteristic for MIDI Output
     *
     * @param context the context
     * @param bluetoothGattService the gatt service of device
     * @param midiVersion the MIDI major version: 1 or 2
     * @return null if no characteristic found
     */
    @Nullable
    public static BluetoothGattCharacteristic getMidiOutputCharacteristic(@NonNull final Context context, @NonNull final BluetoothGattService bluetoothGattService, final int midiVersion) {
        List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
        String[] uuidStringArray = context.getResources().getStringArray(midiVersion == 1 ? R.array.uuidListForMidiOutputCharacteristic : R.array.uuidListForMidi2OutputCharacteristic);

        for (BluetoothGattCharacteristic characteristic : characteristics) {
            for (String uuidString : uuidStringArray) {
                UUID uuid = BleUuidUtils.fromString(uuidString);
                if (BleUuidUtils.matches(characteristic.getUuid(), uuid)) {
                    return characteristic;
                }
            }
        }

        return null;
    }

    /**
     * Obtains list of ScanFilter for BLE MIDI
     *
     * @param context the context
     * @param midiVersion the MIDI major version: 1 or 2
     * @return list of {@link android.bluetooth.le.ScanFilter} for BLE MIDI devices.
     */
    @NonNull
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static List<ScanFilter> getBleMidiScanFilters(@NonNull final Context context, final int midiVersion) {
        List<ScanFilter> scanFilters = new ArrayList<>();

        String[] uuidStringArray = context.getResources().getStringArray(midiVersion == 1 ? R.array.uuidListForMidiService : R.array.uuidListForMidi2Service);
        for (String uuidString : uuidStringArray) {
            scanFilters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuidString)).build());
        }

        return scanFilters;
    }
}
