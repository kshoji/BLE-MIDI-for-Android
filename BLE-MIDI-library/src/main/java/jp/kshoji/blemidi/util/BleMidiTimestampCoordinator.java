package jp.kshoji.blemidi.util;

/**
 * Converts 13-bit BLE MIDI timestamps (0–8191) into monotonic fire times.
 * <p>
 * Wrap handling follows BlueZ {@code libmidi.c} {@code update_ev_timestamp}:
 * when the timestamp goes backwards, add {@link #MAX_TIMESTAMP}, unless wall-clock
 * elapsed is so small that the jump is treated as a stale / same-time event.
 * <p>
 * {@link SchedulingMode#SCHEDULED} delays callbacks to match BLE MIDI timestamps
 * without a latency ceiling (timing recovery; can accumulate backlog).
 * {@link SchedulingMode#LOW_LATENCY} is the same scheduling, but clamps how far
 * ahead of wall-clock events may fire and snaps the timeline when backlog grows.
 * {@link SchedulingMode#IMMEDIATE} fires as soon as the packet is received; timestamps
 * only advance {@link #getLastOrderKey()} for relative ordering within a batch.
 *
 * @author K.Shoji
 */
public final class BleMidiTimestampCoordinator {
    /**
     * BLE MIDI 13-bit timestamp range (0–8191).
     */
    public static final int MAX_TIMESTAMP = 8192;

    /**
     * Default ceiling for {@link SchedulingMode#LOW_LATENCY}: fire at most this many
     * milliseconds after receive time.
     */
    public static final int DEFAULT_MAX_SCHEDULE_AHEAD_MS = 40;

    /**
     * BlueZ uses this threshold to ignore implausibly large timestamp jumps
     * when wall-clock elapsed is under 1 second.
     */
    static final int IMPLAUSIBLE_JUMP_THRESHOLD = 7000;

    /**
     * How BLE MIDI timestamps affect callback delivery.
     */
    public enum SchedulingMode {
        /**
         * Delay callbacks according to BLE MIDI timestamps (jitter buffer / timing recovery).
         * No latency ceiling; backlog can grow under bursty delivery.
         */
        SCHEDULED,
        /**
         * Like {@link #SCHEDULED}, but clamp fire time to {@code now + maxScheduleAheadMs}
         * and realign the timeline when backlog exceeds that ceiling.
         */
        LOW_LATENCY,
        /**
         * Fire as soon as received; use timestamps only for relative ordering.
         */
        IMMEDIATE
    }

    private SchedulingMode schedulingMode = SchedulingMode.SCHEDULED;
    private int maxScheduleAheadMs = DEFAULT_MAX_SCHEDULE_AHEAD_MS;

    private int lastTimestamp;
    private long lastTimestampRecorded;
    private long lastFireTime;
    private long lastOrderKey;
    private int zeroTimestampCount;
    private Boolean isTimestampAlwaysZero;

    /**
     * Sets how timestamps affect fire times. Resets timeline state when the mode changes.
     *
     * @param schedulingMode the scheduling mode
     */
    public void setSchedulingMode(SchedulingMode schedulingMode) {
        if (schedulingMode == null) {
            schedulingMode = SchedulingMode.SCHEDULED;
        }
        if (this.schedulingMode != schedulingMode) {
            this.schedulingMode = schedulingMode;
            resetState();
        }
    }

    /**
     * @return the current scheduling mode
     */
    public SchedulingMode getSchedulingMode() {
        return schedulingMode;
    }

    /**
     * Sets the maximum milliseconds a {@link SchedulingMode#LOW_LATENCY} event may be
     * scheduled after receive time. Values below 0 are treated as 0.
     *
     * @param maxScheduleAheadMs ceiling in milliseconds (typical 20–50)
     */
    public void setMaxScheduleAheadMs(int maxScheduleAheadMs) {
        this.maxScheduleAheadMs = Math.max(0, maxScheduleAheadMs);
    }

    /**
     * @return the LOW_LATENCY schedule-ahead ceiling in milliseconds
     */
    public int getMaxScheduleAheadMs() {
        return maxScheduleAheadMs;
    }

    /**
     * Monotonic order key from the last {@link #calculateEventFireTime(int, long)} call.
     * When fire times are equal (IMMEDIATE, or LOW_LATENCY after clamping), use this to
     * sort same-arrival events.
     *
     * @return the order key
     */
    public long getLastOrderKey() {
        return lastOrderKey;
    }

    /**
     * Calculates the absolute fire time for a BLE MIDI timestamp.
     *
     * @param timestamp 13-bit BLE MIDI timestamp
     * @param now       {@link System#currentTimeMillis()} at receive time
     * @return absolute fire time in milliseconds (comparable to {@code now})
     */
    public long calculateEventFireTime(int timestamp, long now) {
        if (schedulingMode == SchedulingMode.IMMEDIATE) {
            return calculateImmediateFireTime(timestamp, now);
        }
        if (schedulingMode == SchedulingMode.LOW_LATENCY) {
            return calculateLowLatencyFireTime(timestamp, now);
        }
        return calculateScheduledFireTime(timestamp, now);
    }

    private long calculateScheduledFireTime(int timestamp, long now) {
        long fireTime;
        Long zeroFireTime = handleAlwaysZeroTimestamp(timestamp, now);
        if (zeroFireTime != null) {
            fireTime = zeroFireTime;
        } else if (lastTimestampRecorded == 0 || now - lastTimestampRecorded >= MAX_TIMESTAMP) {
            fireTime = resetTimeline(timestamp, now);
        } else {
            int delta = computeTimestampDelta(timestamp, now);

            fireTime = lastFireTime + delta;
            if (fireTime < lastFireTime) {
                fireTime = lastFireTime;
            }

            lastTimestamp = timestamp;
            lastTimestampRecorded = now;
            lastFireTime = fireTime;
        }

        lastOrderKey = fireTime;
        return fireTime;
    }

    private long calculateLowLatencyFireTime(int timestamp, long now) {
        Long zeroFireTime = handleAlwaysZeroTimestamp(timestamp, now);
        if (zeroFireTime != null) {
            lastOrderKey = Math.max(lastOrderKey + 1, zeroFireTime);
            return zeroFireTime;
        }

        if (lastTimestampRecorded == 0 || now - lastTimestampRecorded >= MAX_TIMESTAMP) {
            long fireTime = resetTimeline(timestamp, now);
            lastOrderKey = fireTime;
            return fireTime;
        }

        // Backlog already past the ceiling: snap timeline to wall-clock before scheduling.
        if (lastFireTime > now + maxScheduleAheadMs) {
            lastFireTime = now;
        }

        int delta = computeTimestampDelta(timestamp, now);
        long uncapped = lastFireTime + delta;
        if (uncapped < lastFireTime) {
            uncapped = lastFireTime;
        }

        // orderKey follows timestamp deltas so clamped events keep relative order
        lastOrderKey = lastOrderKey + delta;
        if (lastOrderKey < 0) {
            lastOrderKey = Long.MAX_VALUE / 2;
        }

        long maxFire = now + maxScheduleAheadMs;
        long fireTime = uncapped;
        if (fireTime > maxFire) {
            fireTime = maxFire;
            // Realign so the next event does not inherit the uncapped backlog
            lastFireTime = fireTime;
        } else {
            lastFireTime = uncapped;
        }

        lastTimestamp = timestamp;
        lastTimestampRecorded = now;
        return fireTime;
    }

    private long calculateImmediateFireTime(int timestamp, long now) {
        Long zeroFireTime = handleAlwaysZeroTimestamp(timestamp, now);
        if (zeroFireTime != null) {
            lastOrderKey = Math.max(lastOrderKey + 1, zeroFireTime);
            return zeroFireTime;
        }

        if (lastTimestampRecorded == 0 || now - lastTimestampRecorded >= MAX_TIMESTAMP) {
            long fireTime = resetTimeline(timestamp, now);
            lastOrderKey = fireTime;
            return fireTime;
        }

        int delta = computeTimestampDelta(timestamp, now);
        lastOrderKey = lastOrderKey + delta;
        if (lastOrderKey < 0) {
            // overflow guard; keep ordering monotonic within a session
            lastOrderKey = Long.MAX_VALUE / 2;
        }

        lastTimestamp = timestamp;
        lastTimestampRecorded = now;
        lastFireTime = Math.max(now, lastFireTime);
        return lastFireTime;
    }

    private int computeTimestampDelta(int timestamp, long now) {
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
        return delta;
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

    private void resetState() {
        lastTimestamp = 0;
        lastTimestampRecorded = 0;
        lastFireTime = 0;
        lastOrderKey = 0;
        zeroTimestampCount = 0;
        isTimestampAlwaysZero = null;
    }
}
