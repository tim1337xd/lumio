package dev.tim1337.lumio.util;

import dev.tim1337.lumio.api.experimental.util.HttpLimits;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Answers of web services are read only up to their limit; a larger one fails instead of filling the memory or the disk. */
class HttpLimitsTest {
	@Test
	void textWithinTheLimitIsRead() throws IOException {
		assertEquals("{\"ok\":true}", HttpLimits.readText(new ByteArrayInputStream("{\"ok\":true}".getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void textOverTheLimitFails() {
		InputStream endless = new InputStream() {
			@Override
			public int read() {
				return 'a';
			}
		};
		assertThrows(IOException.class, () -> HttpLimits.readText(endless));
	}

	@Test
	void limitedStreamFailsPastTheLimit() throws IOException {
		try (InputStream in = HttpLimits.limited(new ByteArrayInputStream(new byte[100]), 64)) {
			assertThrows(IOException.class, in::readAllBytes);
		}
		try (InputStream in = HttpLimits.limited(new ByteArrayInputStream(new byte[64]), 64)) {
			assertEquals(64, in.readAllBytes().length);
		}
	}

	@Test
	void limitAroundUnpackingStopsAZipBomb() throws IOException {
		// a megabyte of zeros packs into about a kilobyte; limited after unpacking, it fails at the limit
		ByteArrayOutputStream packed = new ByteArrayOutputStream();
		try (GZIPOutputStream zip = new GZIPOutputStream(packed)) {
			zip.write(new byte[1024 * 1024]);
		}
		try (InputStream in = HttpLimits.limited(new GZIPInputStream(new ByteArrayInputStream(packed.toByteArray())), 4096)) {
			assertThrows(IOException.class, in::readAllBytes);
		}
	}
}
