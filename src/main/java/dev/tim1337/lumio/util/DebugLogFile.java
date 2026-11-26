package dev.tim1337.lumio.util;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.debug.DebugLevel;
import dev.tim1337.lumio.api.experimental.debug.DebugSink;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Debug log switch's file: while it is on (or a debug tool asks for it), every probe's log line goes to {@code logs/lumio-debug.log} next to
 * the game's own log, so a player can send it with a bug report. The file starts empty with each game start and stops growing at
 * {@value #MOST_BYTES} bytes. Lines are written by a thread of their own, never the game's; when it falls far behind, lines are left out and
 * counted. The home folder in a line or an exception is written as "~", as in the game's log.
 */
public final class DebugLogFile implements DebugSink {
	/** The owner of the file's wish in {@link DebugHooks#wantLogFile}: the switch in About. */
	public static final String SWITCH = "switch";
	private static final String FILE = "lumio-debug.log";
	private static final long MOST_BYTES = 32L * 1024 * 1024;
	private static final int MOST_WAITING = 8192;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static final String HOME = System.getProperty("user.home", "");

	private static DebugLogFile instance;

	private final BlockingQueue<String> waiting = new ArrayBlockingQueue<>(MOST_WAITING);
	private final AtomicInteger dropped = new AtomicInteger();
	private Thread writer;

	private DebugLogFile() {
	}

	/** Makes the file's sink known to the hooks; it listens once the switch is on. */
	public static void install() {
		if (instance == null) {
			instance = new DebugLogFile();
			DebugHooks.installLogFile(instance);
		}
	}

	@Override
	public void log(String category, DebugLevel level, String text, Throwable error) {
		StringBuilder line = new StringBuilder(text.length() + 48);
		line.append(LocalTime.now().format(TIME)).append(" [").append(level).append("] [").append(category).append("] ").append(text);
		if (error != null) {
			StringWriter trace = new StringWriter();
			error.printStackTrace(new PrintWriter(trace));
			line.append(System.lineSeparator()).append(trace.toString().stripTrailing());
		}
		String written = HOME.length() > 1 ? line.toString().replace(HOME, "~") : line.toString();
		if (!waiting.offer(written)) {
			dropped.incrementAndGet();
		}
		startWriter();
	}

	private synchronized void startWriter() {
		if (writer == null) {
			writer = new Thread(this::write, "Lumio debug log");
			writer.setDaemon(true);
			writer.start();
		}
	}

	/** The writer's loop: the file is made new once, then lines are added until it is full. */
	private void write() {
		Path file = FabricLoader.getInstance().getGameDir().resolve("logs").resolve(FILE);
		long written = 0L;
		try {
			Files.createDirectories(file.getParent());
			try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE)) {
				while (true) {
					String line = waiting.take();
					int lost = dropped.getAndSet(0);
					if (lost > 0) {
						line = lost + " lines left out, the log was too fast" + System.lineSeparator() + line;
					}
					if (written + line.length() > MOST_BYTES) {
						out.write("The debug log is full; nothing more is written until the game starts again." + System.lineSeparator());
						out.flush();
						return;
					}
					out.write(line);
					out.write(System.lineSeparator());
					written += line.length() + 1;
					if (waiting.isEmpty()) {
						out.flush();
					}
				}
			}
		} catch (IOException e) {
			Lumio.LOGGER.warn("The debug log could not be written to logs/{}", FILE, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
