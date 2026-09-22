package jp.kshoji.blemidi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RpnNrpnValueWidthTrackerTest {

    @Test
    public void parameterSelectResetsWidth() {
        RpnNrpnValueWidthTracker tracker = new RpnNrpnValueWidthTracker();
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_MSB, 0);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_LSB, 1);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 10);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_LSB, 20);

        assertTrue(tracker.is14Bit(0));

        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_MSB, 0);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_LSB, 2);

        assertEquals(RpnNrpnValueWidthTracker.ValueWidth.UNKNOWN, tracker.getValueWidth(0));
        assertFalse(tracker.is7Bit(0));
        assertFalse(tracker.is14Bit(0));
    }

    @Test
    public void dataEntryMsbOnlyIs7Bit() {
        RpnNrpnValueWidthTracker tracker = new RpnNrpnValueWidthTracker();
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_MSB, 0);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_LSB, 0);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 64);

        assertTrue(tracker.is7Bit(0));
        assertFalse(tracker.is14Bit(0));
        assertEquals(RpnNrpnValueWidthTracker.ValueWidth.BITS_7, tracker.getValueWidth(0));
    }

    @Test
    public void dataEntryMsbThenLsbIs14Bit() {
        RpnNrpnValueWidthTracker tracker = new RpnNrpnValueWidthTracker();
        tracker.onControlChange(1, RpnNrpnValueWidthTracker.CC_NRPN_MSB, 1);
        tracker.onControlChange(1, RpnNrpnValueWidthTracker.CC_NRPN_LSB, 2);
        tracker.onControlChange(1, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 3);
        assertTrue(tracker.is7Bit(1));

        tracker.onControlChange(1, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_LSB, 4);

        assertTrue(tracker.is14Bit(1));
        assertFalse(tracker.is7Bit(1));
        assertEquals(RpnNrpnValueWidthTracker.ValueWidth.BITS_14, tracker.getValueWidth(1));
    }

    @Test
    public void dataEntryLsbAloneIs14Bit() {
        RpnNrpnValueWidthTracker tracker = new RpnNrpnValueWidthTracker();
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_MSB, 0);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_LSB, 5);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_LSB, 7);

        assertTrue(tracker.is14Bit(0));
    }

    @Test
    public void nullRpnResetsWidth() {
        RpnNrpnValueWidthTracker tracker = new RpnNrpnValueWidthTracker();
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_MSB, 0);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_LSB, 0);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 1);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_LSB, 2);
        assertTrue(tracker.is14Bit(0));

        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_MSB, 0x7f);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_RPN_LSB, 0x7f);

        assertEquals(RpnNrpnValueWidthTracker.ValueWidth.UNKNOWN, tracker.getValueWidth(0));
    }

    @Test
    public void channelsAreIndependent() {
        RpnNrpnValueWidthTracker tracker = new RpnNrpnValueWidthTracker();
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 1);
        tracker.onControlChange(2, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 2);
        tracker.onControlChange(2, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_LSB, 3);

        assertTrue(tracker.is7Bit(0));
        assertTrue(tracker.is14Bit(2));
        assertEquals(RpnNrpnValueWidthTracker.ValueWidth.UNKNOWN, tracker.getValueWidth(1));
    }

    @Test
    public void subsequentMsbAfter14BitBecomes7Bit() {
        RpnNrpnValueWidthTracker tracker = new RpnNrpnValueWidthTracker();
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 10);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_LSB, 20);
        tracker.onControlChange(0, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 30);

        assertTrue(tracker.is7Bit(0));
        assertFalse(tracker.is14Bit(0));
    }

    @Test
    public void resetClearsAllChannels() {
        RpnNrpnValueWidthTracker tracker = new RpnNrpnValueWidthTracker();
        tracker.onControlChange(3, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_MSB, 1);
        tracker.onControlChange(4, RpnNrpnValueWidthTracker.CC_DATA_ENTRY_LSB, 2);
        tracker.reset();

        assertEquals(RpnNrpnValueWidthTracker.ValueWidth.UNKNOWN, tracker.getValueWidth(3));
        assertEquals(RpnNrpnValueWidthTracker.ValueWidth.UNKNOWN, tracker.getValueWidth(4));
    }

    @Test
    public void staticHelpersIdentifyControllers() {
        assertTrue(RpnNrpnValueWidthTracker.isDataEntryMsb(6));
        assertTrue(RpnNrpnValueWidthTracker.isDataEntryLsb(38));
        assertTrue(RpnNrpnValueWidthTracker.isDataEntry(6));
        assertTrue(RpnNrpnValueWidthTracker.isParameterSelect(100));
        assertTrue(RpnNrpnValueWidthTracker.isParameterSelect(99));
        assertFalse(RpnNrpnValueWidthTracker.isDataEntry(7));
        assertFalse(RpnNrpnValueWidthTracker.isParameterSelect(1));
    }
}
