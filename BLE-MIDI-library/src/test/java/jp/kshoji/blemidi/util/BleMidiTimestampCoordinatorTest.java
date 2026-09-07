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
}
