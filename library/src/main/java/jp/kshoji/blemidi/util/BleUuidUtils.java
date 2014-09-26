package jp.kshoji.blemidi.util;

import java.util.UUID;

/**
 * Utilities for BLE UUID
 *
 * @author K.Shoji
 */
public class BleUuidUtils {

    /**
     * Parses a UUID string with the format defined by toString().
     *
     * @param uuidString the UUID string to parse.
     * @return an UUID instance.
     * @throws java.lang.NullPointerException if uuid is null.
     * @throws java.lang.IllegalArgumentException if uuid is not formatted correctly.
     */
    public static UUID fromString(final String uuidString) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            // may be a short style
            return UUID.fromString("0000" + uuidString + "-0000-0000-0000-000000000000");
        }
    }

    /**
     * check if full style or short (16bits) style UUID matches
     *
     * @param src
     * @param dst
     *
     * @return true if UUID matches
     */
    public static boolean matches(final UUID src, final UUID dst) {
        if (src == null && dst == null) {
            return true;
        }
        if (src == null || dst == null) {
            return false;
        }

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
     * Check if the specified UUID's style is short style.
     *
     * @param src
     * @return true if the UUID is short style
     */
    private static boolean isShortUuid(final UUID src) {
        return ((src.getMostSignificantBits() & 0xffff0000ffffffffL) == 0L) && (src.getLeastSignificantBits() == 0L);
    }
}
