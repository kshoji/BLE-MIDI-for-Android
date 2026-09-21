package jp.kshoji.blemidi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BleMidiTimestampCoordinatorTest {

    @Test
    public void noWrapKeepsDelta() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        long t0 = 1_000_000L;

        long first = coordinator.calculateEventFireTime(100, t0);
        long second = coordinator.calculateEventFireTime(250, t0 + 150);

        assertEquals(t0, first);
        assertEquals(first + 150, second);
        assertTrue(second > first);
    }

    @Test
    public void wrapFrom8000To5000IsMonotonic() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        long t0 = 1_000_000L;

        long first = coordinator.calculateEventFireTime(8000, t0);
        // Issue #19: 8192 - 8000 + 5000 = 5192 ms after the previous event
        long second = coordinator.calculateEventFireTime(5000, t0 + 5192);

        assertEquals(t0, first);
        assertEquals(first + 5192, second);
        assertTrue(second > first);
    }

    @Test
    public void wrapWithBunchedArrivalStillSchedulesForward() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        long t0 = 1_000_000L;

        long first = coordinator.calculateEventFireTime(8000, t0);
        long second = coordinator.calculateEventFireTime(5000, t0 + 100);

        assertEquals(first + 5192, second);
        assertTrue(second > first);
    }

    @Test
    public void longPauseResetsAndStaysMonotonic() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        long t0 = 1_000_000L;

        long first = coordinator.calculateEventFireTime(100, t0);
        long later = t0 + BleMidiTimestampCoordinator.MAX_TIMESTAMP;
        long second = coordinator.calculateEventFireTime(200, later);

        assertEquals(later, second);
        assertTrue(second >= first);
    }

    @Test
    public void alwaysZeroTimestampsFireImmediatelyAndStayMonotonic() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        long t0 = 1_000_000L;
        long previous = Long.MIN_VALUE;

        for (int i = 0; i < 6; i++) {
            long fireTime = coordinator.calculateEventFireTime(0, t0 + i);
            assertTrue(fireTime >= t0 + i);
            assertTrue(fireTime >= previous);
            previous = fireTime;
        }
    }

    @Test
    public void nonZeroAfterAlwaysZeroResetsTimeline() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        long t0 = 1_000_000L;

        for (int i = 0; i < 4; i++) {
            coordinator.calculateEventFireTime(0, t0 + i);
        }

        long firstNonZero = coordinator.calculateEventFireTime(100, t0 + 10);
        long secondNonZero = coordinator.calculateEventFireTime(150, t0 + 60);

        assertTrue(firstNonZero >= t0 + 10);
        assertEquals(firstNonZero + 50, secondNonZero);
        assertTrue(secondNonZero > firstNonZero);
    }

    @Test
    public void immediateModeNeverSchedulesIntoTheFuture() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        coordinator.setSchedulingMode(BleMidiTimestampCoordinator.SchedulingMode.IMMEDIATE);
        long t0 = 1_000_000L;

        long first = coordinator.calculateEventFireTime(100, t0);
        long second = coordinator.calculateEventFireTime(250, t0); // same arrival, +150 ms timestamp
        long third = coordinator.calculateEventFireTime(5000, t0 + 50); // large jump / wrap-ish

        assertEquals(t0, first);
        assertEquals(t0, second);
        assertEquals(t0 + 50, third);
        assertTrue(second <= t0);
        assertTrue(third <= t0 + 50);
    }

    @Test
    public void immediateModeOrderKeyPreservesWrapOrder() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        coordinator.setSchedulingMode(BleMidiTimestampCoordinator.SchedulingMode.IMMEDIATE);
        long t0 = 1_000_000L;

        long firstFire = coordinator.calculateEventFireTime(8000, t0);
        long firstOrder = coordinator.getLastOrderKey();
        long secondFire = coordinator.calculateEventFireTime(5000, t0 + 100);
        long secondOrder = coordinator.getLastOrderKey();

        assertEquals(t0, firstFire);
        assertEquals(t0 + 100, secondFire);
        assertEquals(firstOrder + 5192, secondOrder);
        assertTrue(secondOrder > firstOrder);
    }

    @Test
    public void immediateModeBatchKeepsRelativeOrderKeys() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        coordinator.setSchedulingMode(BleMidiTimestampCoordinator.SchedulingMode.IMMEDIATE);
        long t0 = 1_000_000L;

        coordinator.calculateEventFireTime(100, t0);
        long o1 = coordinator.getLastOrderKey();
        coordinator.calculateEventFireTime(110, t0);
        long o2 = coordinator.getLastOrderKey();
        coordinator.calculateEventFireTime(130, t0);
        long o3 = coordinator.getLastOrderKey();

        assertEquals(o1 + 10, o2);
        assertEquals(o2 + 20, o3);
    }

    @Test
    public void lowLatencyClampsLargeTimestampJump() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        coordinator.setSchedulingMode(BleMidiTimestampCoordinator.SchedulingMode.LOW_LATENCY);
        coordinator.setMaxScheduleAheadMs(40);
        long t0 = 1_000_000L;

        long first = coordinator.calculateEventFireTime(100, t0);
        // +500 ms on timestamp in the same arrival would be ~1s lag under SCHEDULED wrap cases;
        // LOW_LATENCY must stay within now + 40.
        long second = coordinator.calculateEventFireTime(600, t0);

        assertEquals(t0, first);
        assertEquals(t0 + 40, second);
        assertTrue(second - t0 <= 40);
    }

    @Test
    public void lowLatencyClampsWrapBunchedArrival() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        coordinator.setSchedulingMode(BleMidiTimestampCoordinator.SchedulingMode.LOW_LATENCY);
        coordinator.setMaxScheduleAheadMs(40);
        long t0 = 1_000_000L;

        long first = coordinator.calculateEventFireTime(8000, t0);
        long second = coordinator.calculateEventFireTime(5000, t0 + 100);

        assertEquals(t0, first);
        // SCHEDULED would schedule +5192 ms; LOW_LATENCY clamps to now + 40
        assertEquals(t0 + 100 + 40, second);
        assertTrue(second - (t0 + 100) <= 40);
    }

    @Test
    public void lowLatencySnapsBacklogThenContinues() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        coordinator.setSchedulingMode(BleMidiTimestampCoordinator.SchedulingMode.LOW_LATENCY);
        coordinator.setMaxScheduleAheadMs(40);
        long t0 = 1_000_000L;

        coordinator.calculateEventFireTime(100, t0);
        // Build uncapped pressure then clamp
        coordinator.calculateEventFireTime(300, t0); // would be +200, clamps to t0+40, timeline = t0+40
        // Next packet arrives later; lastFireTime was snapped so delay stays bounded
        long third = coordinator.calculateEventFireTime(320, t0 + 50);

        assertTrue(third <= t0 + 50 + 40);
        assertTrue(third >= t0 + 50);
    }

    @Test
    public void lowLatencyPreservesOrderKeysWhenClamped() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        coordinator.setSchedulingMode(BleMidiTimestampCoordinator.SchedulingMode.LOW_LATENCY);
        coordinator.setMaxScheduleAheadMs(40);
        long t0 = 1_000_000L;

        coordinator.calculateEventFireTime(100, t0);
        long o1 = coordinator.getLastOrderKey();
        long fire2 = coordinator.calculateEventFireTime(200, t0);
        long o2 = coordinator.getLastOrderKey();
        long fire3 = coordinator.calculateEventFireTime(300, t0);
        long o3 = coordinator.getLastOrderKey();

        assertEquals(t0 + 40, fire2);
        assertEquals(t0 + 40, fire3);
        assertEquals(o1 + 100, o2);
        assertEquals(o2 + 100, o3);
        assertTrue(o3 > o2);
    }

    @Test
    public void lowLatencySmallDeltasMatchScheduled() {
        BleMidiTimestampCoordinator coordinator = new BleMidiTimestampCoordinator();
        coordinator.setSchedulingMode(BleMidiTimestampCoordinator.SchedulingMode.LOW_LATENCY);
        coordinator.setMaxScheduleAheadMs(40);
        long t0 = 1_000_000L;

        long first = coordinator.calculateEventFireTime(100, t0);
        long second = coordinator.calculateEventFireTime(120, t0 + 20);

        assertEquals(t0, first);
        assertEquals(first + 20, second);
    }
}
