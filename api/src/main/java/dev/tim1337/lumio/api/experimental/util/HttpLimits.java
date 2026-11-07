package dev.tim1337.lumio.api.experimental.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Size limits for answers from web services: a broken or hostile service must not be able to fill the memory or the disk. */
public final class HttpLimits {
	/** Answers read whole: song data, lyrics and tokens are a few kilobytes. */
	public static final int TEXT_BYTES = 4 * 1024 * 1024;
	/** Data parsed while it arrives: the Hypixel item list and the price lists are several megabytes. */
	public static final long DATA_BYTES = 64L * 1024 * 1024;
	/** The item repository download. */
	public static final long REPO_BYTES = 256L * 1024 * 1024;

	private HttpLimits() {
	}

	/** @return the whole answer as text @throws IOException if it is larger than the limit */
	public static String readText(InputStream in) throws IOException {
		byte[] data = in.readNBytes(TEXT_BYTES + 1);
		if (data.length > TEXT_BYTES) {
			throw new IOException("The answer is larger than " + TEXT_BYTES + " bytes");
		}
		return new String(data, StandardCharsets.UTF_8);
	}

	/** @return the stream, failing once more than the limit was read from it; around a decompressing stream it limits the unpacked size */
	public static InputStream limited(InputStream in, long limit) {
		return new FilterInputStream(in) {
			private long read;

			@Override
			public int read() throws IOException {
				int value = super.read();
				if (value >= 0) {
					count(1);
				}
				return value;
			}

			@Override
			public int read(byte[] buffer, int offset, int length) throws IOException {
				int count = super.read(buffer, offset, length);
				if (count > 0) {
					count(count);
				}
				return count;
			}

			private void count(long bytes) throws IOException {
				read += bytes;
				if (read > limit) {
					throw new IOException("The answer is larger than " + limit + " bytes");
				}
			}
		};
	}
}
