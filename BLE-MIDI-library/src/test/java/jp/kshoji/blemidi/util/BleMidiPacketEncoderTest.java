package jp.kshoji.blemidi.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class BleMidiPacketEncoderTest {

    private static BleMidiPacketEncoder.PendingMidiMessage noteOn(int note, long timestamp) {
        return new BleMidiPacketEncoder.PendingMidiMessage(
                new byte[] {(byte) 0x90, (byte) note, 0x7f}, timestamp);
    }

    private static BleMidiPacketEncoder.PendingMidiMessage sysEx(long timestamp) {
        return new BleMidiPacketEncoder.PendingMidiMessage(
                new byte[] {(byte) 0xf0, 0x01, 0x02, (byte) 0xf7}, timestamp);
    }

    @Test
    public void packSingleNoteOnIncludesHeaderAndTimestamp() {
        List<BleMidiPacketEncoder.PendingMidiMessage> queue = new ArrayList<>();
        queue.add(noteOn(60, 0));

        byte[] packet = BleMidiPacketEncoder.packChannelMessages(queue, 20, 6);

        assertArrayEquals(new byte[] {
                (byte) 0x80, (byte) 0x80, (byte) 0x90, 60, 0x7f
        }, packet);
        assertEquals(0, queue.size());
    }

    @Test
    public void packStopsAtMaxMessagesPerPacket() {
        List<BleMidiPacketEncoder.PendingMidiMessage> queue = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            queue.add(noteOn(60 + i, 0));
        }

        byte[] packet = BleMidiPacketEncoder.packChannelMessages(queue, 100, 6);

        // header + 6 * (timestamp-low + 3-byte note)
        assertEquals(1 + 6 * 4, packet.length);
        assertEquals(1, queue.size());
        assertEquals(66, queue.get(0).data[1] & 0xff);
    }

    @Test
    public void packStopsBeforeExceedingBufferSize() {
        List<BleMidiPacketEncoder.PendingMidiMessage> queue = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            queue.add(noteOn(60 + i, 0));
        }

        // 4 notes = 1 + 16 = 17 bytes; 5th note would be 21 > 20
        byte[] packet = BleMidiPacketEncoder.packChannelMessages(queue, 20, 6);

        assertEquals(17, packet.length);
        assertEquals(2, queue.size());
    }

    @Test
    public void packDoesNotConsumeSysExAtHead() {
        List<BleMidiPacketEncoder.PendingMidiMessage> queue = new ArrayList<>();
        queue.add(sysEx(0));
        queue.add(noteOn(60, 0));

        byte[] packet = BleMidiPacketEncoder.packChannelMessages(queue, 20, 6);

        assertEquals(0, packet.length);
        assertEquals(2, queue.size());
        assertTrue(queue.get(0).isSystemExclusive());
    }

    @Test
    public void packStopsBeforeFollowingSysEx() {
        List<BleMidiPacketEncoder.PendingMidiMessage> queue = new ArrayList<>();
        queue.add(noteOn(60, 0));
        queue.add(noteOn(64, 0));
        queue.add(sysEx(0));
        queue.add(noteOn(67, 0));

        byte[] packet = BleMidiPacketEncoder.packChannelMessages(queue, 100, 6);

        assertEquals(1 + 4 + 4, packet.length);
        assertEquals(2, queue.size());
        assertTrue(queue.get(0).isSystemExclusive());
        assertFalse(queue.get(1).isSystemExclusive());
    }

    @Test
    public void emptyQueueReturnsEmptyPacket() {
        List<BleMidiPacketEncoder.PendingMidiMessage> queue = new ArrayList<>();
        assertEquals(0, BleMidiPacketEncoder.packChannelMessages(queue, 20, 6).length);
    }

    @Test
    public void subsequentMessageUsesTimestampLowOnly() {
        List<BleMidiPacketEncoder.PendingMidiMessage> queue = new ArrayList<>();
        queue.add(noteOn(60, 0x0100));
        queue.add(noteOn(64, 0x0003));

        byte[] packet = BleMidiPacketEncoder.packChannelMessages(queue, 20, 6);

        assertArrayEquals(new byte[] {
                (byte) (0x80 | 0x02), (byte) 0x80, (byte) 0x90, 60, 0x7f,
                (byte) (0x80 | 0x03), (byte) 0x90, 64, 0x7f
        }, packet);
    }
}
