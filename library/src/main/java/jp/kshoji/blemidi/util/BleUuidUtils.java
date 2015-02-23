package jp.kshoji.blemidi.util;

import android.os.ParcelUuid;
import android.support.annotation.NonNull;

import java.util.UUID;

/**
 * Utilities for BLE UUID
 *
 * @author K.Shoji
 */
public final class BleUuidUtils {

    /**
     * Parses a UUID string with the format defined by toString().
     *
     * @param uuidString the UUID string to parse.
     * @return an UUID instance.
     * @throws java.lang.NullPointerException if uuid is null.
     * @throws java.lang.IllegalArgumentException if uuid is not formatted correctly.
     */
    @NonNull
    public static UUID fromString(@NonNull final String uuidString) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            // may be a short style
            return UUID.fromString("0000" + uuidString + "-0000-1000-8000-00805F9B34FB");
        }
    }

    /**
     * Obtains a UUID from Short style value.
     *
     * @param uuidShortValue the Short style UUID value.
     * @return an UUID instance.
     */
    @NonNull
    public static UUID fromShortValue(final int uuidShortValue) {
        return UUID.fromString("0000" + String.format("%04X", uuidShortValue & 0xffff) + "-0000-1000-8000-00805F9B34FB");
    }

    /**
     * Obtains a ParcelUuid from Short style value.
     *
     * @param uuidShortValue the Short style UUID value.
     * @return an UUID instance.
     */
    @NonNull
    public static ParcelUuid parcelFromShortValue(final int uuidShortValue) {
        return ParcelUuid.fromString("0000" + String.format("%04X", uuidShortValue & 0xffff) + "-0000-1000-8000-00805F9B34FB");
    }

    /**
     * UUID to short style value
     *
     * @param uuid the UUID
     * @return short style value, -1 if the specified UUID is not short style
     */
    public static int toShortValue(@NonNull UUID uuid) {
        return (int)((uuid.getMostSignificantBits() >> 32) & 0xffff);
    }

    /**
     * check if full style or short (16bits) style UUID matches
     *
     * @param src the UUID to be compared
     * @param dst the UUID to be compared
     * @return true if the both of UUIDs matches
     */
    public static boolean matches(@NonNull final UUID src, @NonNull final UUID dst) {
        if (isShortUuid(src) || isShortUuid(dst)) {
            // at least one instance is short style: check only 16bits
            long srcShortUUID = src.getMostSignificantBits() & 0x0000ffff00000000L;
            long dstShortUUID = dst.getMostSignificantBits() & 0x0000ffff00000000L;

            return srcShortUUID == dstShortUUID;
        } else {
            return src.equals(dst);
        }
    }

    /**
     * Check if the specified UUID style is short style.
     *
     * @param src the UUID
     * @return true if the UUID is short style
     */
    private static boolean isShortUuid(@NonNull final UUID src) {
        return ((src.getMostSignificantBits() & 0xffff0000ffffffffL) == 0L) && (src.getLeastSignificantBits() == 0L);
    }
}
