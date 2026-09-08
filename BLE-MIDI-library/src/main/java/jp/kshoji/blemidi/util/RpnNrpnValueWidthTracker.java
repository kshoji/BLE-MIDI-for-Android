package jp.kshoji.blemidi.util;

/**
 * Distinguishes 7-bit vs 14-bit RPN/NRPN Data Entry from
 * {@link jp.kshoji.blemidi.listener.OnMidiInputEventListener#onMidiControlChange}.
 * <p>
 * {@code onRPNMessage} / {@code onNRPNMessage} expose a combined {@code value}
 * that may be 7 or 14 bits; this tracker uses the Control Change stream that
 * the parser already delivers for every CC (including 101/100/6/38 and 99/98/6/38).
 * <p>
 * Feed every {@code onMidiControlChange} into {@link #onControlChange}, then
 * read {@link #getValueWidth(int)} when the controller is Data Entry
 * ({@link #CC_DATA_ENTRY_MSB} or {@link #CC_DATA_ENTRY_LSB}).
 * Do not query from {@code onRPNMessage}: that callback is queued before the
 * matching Control Change, so the current CC has not been applied yet.
 *
 * @author K.Shoji
 */
public final class RpnNrpnValueWidthTracker {
    public static final int CC_DATA_ENTRY_MSB = 6;
    public static final int CC_DATA_ENTRY_LSB = 38;
    public static final int CC_NRPN_LSB = 98;
    public static final int CC_NRPN_MSB = 99;
    public static final int CC_RPN_LSB = 100;
    public static final int CC_RPN_MSB = 101;

    /**
     * Data Entry width for the current RPN/NRPN on a channel.
     */
    public enum ValueWidth {
        /**
         * Parameter selected (or idle), but no Data Entry CC yet.
         */
        UNKNOWN,
        /**
         * Only Data Entry MSB (CC 6) seen since the last parameter select.
         */
        BITS_7,
        /**
         * Data Entry LSB (CC 38) seen for the current parameter.
         */
        BITS_14
    }

    private final boolean[] sawDataEntryMsb = new boolean[16];
    private final boolean[] sawDataEntryLsb = new boolean[16];

    /**
     * @return {@code true} if {@code function} is Data Entry MSB (CC 6)
     */
    public static boolean isDataEntryMsb(int function) {
        return function == CC_DATA_ENTRY_MSB;
    }

    /**
     * @return {@code true} if {@code function} is Data Entry LSB (CC 38)
     */
    public static boolean isDataEntryLsb(int function) {
        return function == CC_DATA_ENTRY_LSB;
    }

    /**
     * @return {@code true} if {@code function} is Data Entry MSB or LSB
     */
    public static boolean isDataEntry(int function) {
        return isDataEntryMsb(function) || isDataEntryLsb(function);
    }

    /**
     * @return {@code true} if {@code function} selects an RPN or NRPN parameter
     */
    public static boolean isParameterSelect(int function) {
        return function == CC_RPN_MSB
                || function == CC_RPN_LSB
                || function == CC_NRPN_MSB
                || function == CC_NRPN_LSB;
    }

    /**
     * Updates the per-channel width from one Control Change.
     *
     * @param channel  0–15
     * @param function controller number
     * @param value    controller value (unused; kept for listener-shaped calls)
     */
    public void onControlChange(int channel, int function, int value) {
        int ch = channel & 0x0f;
        if (isParameterSelect(function)) {
            sawDataEntryMsb[ch] = false;
            sawDataEntryLsb[ch] = false;
            return;
        }
        if (isDataEntryMsb(function)) {
            sawDataEntryMsb[ch] = true;
            sawDataEntryLsb[ch] = false;
            return;
        }
        if (isDataEntryLsb(function)) {
            sawDataEntryLsb[ch] = true;
        }
    }

    /**
     * Width of the current Data Entry on {@code channel}.
     *
     * @param channel 0–15
     * @return {@link ValueWidth#BITS_14} if CC 38 has been seen,
     *         {@link ValueWidth#BITS_7} if only CC 6 has been seen,
     *         otherwise {@link ValueWidth#UNKNOWN}
     */
    public ValueWidth getValueWidth(int channel) {
        int ch = channel & 0x0f;
        if (sawDataEntryLsb[ch]) {
            return ValueWidth.BITS_14;
        }
        if (sawDataEntryMsb[ch]) {
            return ValueWidth.BITS_7;
        }
        return ValueWidth.UNKNOWN;
    }

    /**
     * @param channel 0–15
     * @return {@code true} if the current Data Entry includes CC 38
     */
    public boolean is14Bit(int channel) {
        return getValueWidth(channel) == ValueWidth.BITS_14;
    }

    /**
     * @param channel 0–15
     * @return {@code true} if only Data Entry MSB has been seen
     */
    public boolean is7Bit(int channel) {
        return getValueWidth(channel) == ValueWidth.BITS_7;
    }

    /**
     * Clears Data Entry state on every channel.
     */
    public void reset() {
        for (int i = 0; i < 16; i++) {
            sawDataEntryMsb[i] = false;
            sawDataEntryLsb[i] = false;
        }
    }
}
