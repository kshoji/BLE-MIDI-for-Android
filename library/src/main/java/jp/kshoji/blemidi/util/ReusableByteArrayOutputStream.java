package jp.kshoji.blemidi.util;

import java.io.ByteArrayOutputStream;

/**
 * {@link java.io.ByteArrayOutputStream} that can reset without memory leak.
 * 
 * @author K.Shoji
 */
public final class ReusableByteArrayOutputStream extends ByteArrayOutputStream {
	private static final int DEFAULT_BUFFER_LIMIT = 1024;
	private final byte[] fixedSizeBuffer;

	/**
	 * Construct instance
	 * 
	 * @param size the initial size of the stream
	 */
	public ReusableByteArrayOutputStream(int size) {
		super(size);
		fixedSizeBuffer = new byte[size];
		this.buf = fixedSizeBuffer;
	}

    /**
     * Replaces last written byte with the specified value
     *
     * @param oneByte the byte value
     * @return replaced value; -1 if {@link #size()} == 0
     */
    public synchronized int replaceLastByte(int oneByte) {
        if (count > 0) {
            byte replaced = buf[count - 1];
            buf[count - 1] = (byte) oneByte;
            return replaced & 0xff;
        } else {
            super.write(oneByte);
            return -1;
        }
    }

    /**
	 * Construct default instance, maximum buffer size is 1024 bytes.
	 */
	public ReusableByteArrayOutputStream() {
		this(DEFAULT_BUFFER_LIMIT);
	}

	@Override
	public synchronized void reset() {
		super.reset();
		
		// reset buffer size when the buffer has been extended
		if (this.buf.length > fixedSizeBuffer.length) {
			this.buf = fixedSizeBuffer;
		}
	}
}
