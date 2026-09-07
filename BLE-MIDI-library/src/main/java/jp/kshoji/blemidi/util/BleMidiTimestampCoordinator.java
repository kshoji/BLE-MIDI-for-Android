package jp.kshoji.blemidi.util;

/**
 * Converts 13-bit BLE MIDI timestamps (0–8191) into monotonic fire times.
 * <p>
 * Wrap handling follows BlueZ {@code libmidi.c} {@code update_ev_timestamp}:
 * when the timestamp goes backwards, add {@link #MAX_TIMESTAMP}, unless wall-clock
 * elapsed is so small that the jump is treated as a stale / same-time event.
 *
 * @author K.Shoji
 */
public final class BleMidiTimestampCoordinator {
    /**
     * BLE MIDI 13-bit timestamp range (0–8191).
     */
    public static final int MAX_TIMESTAMP = 8192;

    /**
     * BlueZ uses this threshold to ignore implausibly large timestamp jumps
     * when wall-clock elapsed is under 1 second.
     */
    static final int IMPLAUSIBLE_JUMP_THRESHOLD = 7000;

    private int lastTimestamp;
    private long lastTimestampRecorded;
    private long lastFireTime;
    private int zeroTimestampCount;
    private Boolean isTimestampAlwaysZero;

    /**
     * Calculates the absolute fire time for a BLE MIDI timestamp.
     *
     * @param timestamp 13-bit BLE MIDI timestamp
     * @param now       {@link System#currentTimeMillis()} at receive time
     * @return absolute fire time in milliseconds (comparable to {@code now})
     */
    public long calculateEventFireTime(int timestamp, long now) {
        long fireTime;
        Long zeroFireTime = handleAlwaysZeroTimestamp(timestamp, now);
        if (zeroFireTime != null) {
            fireTime = zeroFireTime;
        } else if (lastTimestampRecorded == 0 || now - lastTimestampRecorded >= MAX_TIMESTAMP) {
            fireTime = resetTimeline(timestamp, now);
        } else {
            int delta = timestamp - lastTimestamp;
            long elapsedRealtime = now - lastTimestampRecorded;

            if (delta > IMPLAUSIBLE_JUMP_THRESHOLD && elapsedRealtime < 1000) {
                delta = 0;
            } else if (delta < 0) {
                int wrapped = delta + MAX_TIMESTAMP;
                if (wrapped > IMPLAUSIBLE_JUMP_THRESHOLD && elapsedRealtime < 1000) {
                    delta = 0;
                } else {
                    delta = wrapped;
                }
            }

            fireTime = lastFireTime + delta;
            if (fireTime < lastFireTime) {
                fireTime = lastFireTime;
            }

            lastTimestamp = timestamp;
            lastTimestampRecorded = now;
            lastFireTime = fireTime;
        }

        return fireTime;
    }

    /**
     * @return fire time if this event is handled as timestamp-always-zero; otherwise null
     */
    private Long handleAlwaysZeroTimestamp(int timestamp, long now) {
        if (isTimestampAlwaysZero != null) {
            if (isTimestampAlwaysZero) {
                if (timestamp != 0) {
                    isTimestampAlwaysZero = false;
                    zeroTimestampCount = 0;
                    lastTimestampRecorded = 0;
                    return null;
                }
                return recordImmediate(now);
            }
            if (timestamp == 0) {
                isTimestampAlwaysZero = null;
                zeroTimestampCount = 0;
                return recordImmediate(now);
            }
            return null;
        }

        if (timestamp == 0) {
            if (zeroTimestampCount >= 3) {
                isTimestampAlwaysZero = true;
            } else {
                zeroTimestampCount++;
            }
            return recordImmediate(now);
        }

        isTimestampAlwaysZero = false;
        zeroTimestampCount = 0;
        lastTimestampRecorded = 0;
        return null;
    }

    private long resetTimeline(int timestamp, long now) {
        lastTimestamp = timestamp;
        lastTimestampRecorded = now;
        lastFireTime = Math.max(now, lastFireTime);
        return lastFireTime;
    }

    private long recordImmediate(long now) {
        lastFireTime = Math.max(now, lastFireTime);
        return lastFireTime;
    }
}
