package dev.tim1337.lumio.api.experimental.debug;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The debug hooks cost nothing while no debug tool and no Debug log listens: {@link DebugHooks#ACTIVE} stays false, guarded probes build no text,
 * make no lambda and allocate nothing; and they reach every sink while one listens.
 */
class DebugHooksTest {
	private final List<DebugSink> added = new ArrayList<>();

	/** A sink that keeps what it got. */
	private static final class Kept implements DebugSink {
		final List<String> lines = new ArrayList<>();
		Throwable error;
		int passes;

		@Override
		public void log(String category, DebugLevel level, String text, Throwable thrown) {
			lines.add(category + " " + level + " " + text);
			error = thrown;
		}

		@Override
		public void passBegin(int pass) {
			passes++;
		}

		@Override
		public boolean wants(String category) {
			return DebugCategory.SWING.equals(category);
		}
	}

	private <S extends DebugSink> S add(S sink) {
		added.add(sink);
		DebugHooks.addSink(sink);
		return sink;
	}

	@AfterEach
	void removeSinks() {
		added.forEach(DebugHooks::removeSink);
		DebugHooks.wantLogFile("test", false);
		DebugHooks.installLogFile(null);
	}

	@Test
	void nothingListensAtFirst() {
		assertFalse(DebugHooks.ACTIVE);
		assertFalse(DebugHooks.wants(DebugCategory.SWING));
	}

	@Test
	void guardedProbesBuildNothingWhileInactive() {
		int[] built = {0};
		for (int i = 0; i < 1000; i++) {
			if (DebugHooks.ACTIVE) {
				built[0]++;
				DebugHooks.log(DebugCategory.GENERAL, "built {}", i);
			}
		}
		assertEquals(0, built[0]);
	}

	@Test
	void probesAllocateNothingWhileInactive() {
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long thread = Thread.currentThread().threadId();
		// warmed up first, so the measurement holds only the probes
		for (int round = 0; round < 3; round++) {
			probes(200_000);
		}
		long before = threads.getThreadAllocatedBytes(thread);
		probes(1_000_000);
		long allocated = threads.getThreadAllocatedBytes(thread) - before;
		// a few hundred bytes of the measurement itself at most; a probe that built text would allocate megabytes here
		assertTrue(allocated < 4096, "the inactive probes allocated " + allocated + " bytes");
	}

	private static void probes(int count) {
		for (int i = 0; i < count; i++) {
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(DebugCategory.HUD, "line {} of {}", i, count);
			}
			DebugHooks.passBegin(DebugPass.SKY);
			DebugHooks.passEnd(DebugPass.SKY);
			DebugHooks.frameBegin();
			if (DebugHooks.wants(DebugCategory.SWING)) {
				DebugHooks.event(DebugCategory.SWING, "swing", "frame " + i);
			}
		}
	}

	@Test
	void aSinkMakesItActiveAndGetsTheProbes() {
		Kept sink = add(new Kept());
		assertTrue(DebugHooks.ACTIVE);
		RuntimeException failure = new RuntimeException("x");
		DebugHooks.log(DebugCategory.CHAT, "read {} from {}", 3, "a line", failure);
		assertEquals(List.of("chat DEBUG read 3 from a line"), sink.lines);
		assertSame(failure, sink.error);
		DebugHooks.passBegin(DebugPass.GLASS);
		assertEquals(1, sink.passes);
		assertTrue(DebugHooks.wants(DebugCategory.SWING));
		assertFalse(DebugHooks.wants(DebugCategory.COLORED));
		DebugHooks.removeSink(sink);
		added.clear();
		assertFalse(DebugHooks.ACTIVE);
	}

	@Test
	void aSinkThatFailsIsDropped() {
		add(new DebugSink() {
			@Override
			public void log(String category, DebugLevel level, String text, Throwable error) {
				throw new IllegalStateException("broken");
			}
		});
		DebugHooks.log(DebugCategory.GENERAL, "once");
		assertFalse(DebugHooks.ACTIVE);
	}

	@Test
	void theLogFileListensOnlyWhileSomeoneWantsIt() {
		Kept file = new Kept();
		DebugHooks.installLogFile(file);
		assertFalse(DebugHooks.ACTIVE);
		DebugHooks.wantLogFile("test", true);
		assertTrue(DebugHooks.ACTIVE);
		assertTrue(DebugHooks.logFileWritten());
		DebugHooks.log(DebugCategory.GENERAL, "to the file");
		assertEquals(1, file.lines.size());
		DebugHooks.wantLogFile("test", false);
		assertFalse(DebugHooks.ACTIVE);
	}

	@Test
	void theUpdateTestServerIsSetAndCleared() {
		DebugHooks.setUpdateTestServer("http://127.0.0.1:8123");
		assertEquals("http://127.0.0.1:8123", DebugHooks.updateTestServer());
		DebugHooks.setUpdateTestServer(null);
		assertNull(DebugHooks.updateTestServer());
	}
}
