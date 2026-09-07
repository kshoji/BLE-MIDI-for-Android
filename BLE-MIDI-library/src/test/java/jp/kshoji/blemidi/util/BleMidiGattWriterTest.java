package jp.kshoji.blemidi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothStatusCodes;

import org.junit.Test;

public class BleMidiGattWriterTest {

    @Test
    public void succeedsWithoutRetry() {
        final int[] attempts = {0};
        boolean ok = BleMidiGattWriter.writeWithCongestionRetry(new BleMidiGattWriter.GattWrite() {
            @Override
            public int execute() {
                attempts[0]++;
                return BluetoothStatusCodes.SUCCESS;
            }
        });
        assertTrue(ok);
        assertEquals(1, attempts[0]);
    }

    @Test
    public void retriesWhileBusyThenSucceeds() {
        final int[] attempts = {0};
        boolean ok = BleMidiGattWriter.writeWithCongestionRetry(new BleMidiGattWriter.GattWrite() {
            @Override
            public int execute() {
                attempts[0]++;
                if (attempts[0] < 3) {
                    return BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY;
                }
                return BluetoothStatusCodes.SUCCESS;
            }
        });
        assertTrue(ok);
        assertEquals(3, attempts[0]);
    }
}
