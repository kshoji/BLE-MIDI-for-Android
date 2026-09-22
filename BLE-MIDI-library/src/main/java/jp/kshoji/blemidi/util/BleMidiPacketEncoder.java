package jp.kshoji.blemidi.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Encodes queued MIDI messages into BLE MIDI packets.
 * <p>
 * Limits the number of messages per write and never mixes SysEx with
 * channel / system-common messages, so congested queues do not produce
 * oversized GATT writes.
 *
 * @author K.Shoji
 */
public final class BleMidiPacketEncoder {
    /**
     * Default maximum MIDI messages packed into one BLE packet.
     * Matches the working limit from
     * <a href="https://github.com/kshoji/BLE-MIDI-for-Android/issues/40">issue #40</a>.
     */
    public static final int DEFAULT_MAX_MESSAGES_PER_PACKET = 6;

    private BleMidiPacketEncoder() {
    }

    /**
     * Queued MIDI message waiting to be packed.
     */
    public static final class PendingMidiMessage {
        @NonNull
        public final byte[] data;
        public final long timestamp;

        public PendingMidiMessage(@NonNull byte[] data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        public boolean isSystemExclusive() {
            return BleMidiPacketEncoder.isSystemExclusive(data);
        }
    }

    /**
     * @param midiMessage raw MIDI bytes
     * @return true if the message starts with F0
     */
    public static boolean isSystemExclusive(@Nullable byte[] midiMessage) {
        return midiMessage != null && midiMessage.length > 0 && (midiMessage[0] & 0xff) == 0xf0;
    }

    /**
     * Encoded size of one MIDI message in a BLE packet.
     *
     * @param firstInPacket true if this is the first message (includes timestamp high)
     * @param dataLength    MIDI payload length
     * @return number of bytes written for this message
     */
    public static int encodedSize(boolean firstInPacket, int dataLength) {
        return (firstInPacket ? 1 : 0) + 1 + dataLength;
    }

    /**
     * Packs channel / system-common messages from the front of {@code queue}
     * into one BLE MIDI packet.
     * <p>
     * Stops before SysEx, when {@code maxMessagesPerPacket} is reached, or when
     * the next message would exceed {@code bufferSize}. Packed messages are
     * removed from {@code queue}. SysEx at the head is left untouched.
     *
     * @param queue                 pending messages (consumed from the front)
     * @param bufferSize            max BLE write size (typically MTU - 3)
     * @param maxMessagesPerPacket  max MIDI messages in this packet
     * @return packed packet, or an empty array if nothing could be packed
     */
    @NonNull
    public static byte[] packChannelMessages(@NonNull List<PendingMidiMessage> queue,
                                             int bufferSize,
                                             int maxMessagesPerPacket) {
        if (queue.isEmpty() || bufferSize <= 0 || maxMessagesPerPacket <= 0) {
            return new byte[0];
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream(Math.min(bufferSize, 256));
        int messageCount = 0;

        while (!queue.isEmpty() && messageCount < maxMessagesPerPacket) {
            PendingMidiMessage next = queue.get(0);
            if (next.isSystemExclusive()) {
                break;
            }

            int additional = encodedSize(messageCount == 0, next.data.length);
            if (stream.size() + additional > bufferSize) {
                if (messageCount == 0) {
                    // Single message larger than the buffer: send it alone to avoid stalling.
                    queue.remove(0);
                    writeMessage(stream, next, true);
                }
                break;
            }

            queue.remove(0);
            writeMessage(stream, next, messageCount == 0);
            messageCount++;
        }

        return stream.toByteArray();
    }

    private static void writeMessage(@NonNull ByteArrayOutputStream stream,
                                     @NonNull PendingMidiMessage message,
                                     boolean firstInPacket) {
        long timestamp = message.timestamp;
        if (firstInPacket) {
            stream.write((byte) (0x80 | ((timestamp >> 7) & 0x3f)));
        }
        stream.write((byte) (0x80 | (timestamp & 0x7f)));
        try {
            stream.write(message.data);
        } catch (IOException ignored) {
        }
    }
}
