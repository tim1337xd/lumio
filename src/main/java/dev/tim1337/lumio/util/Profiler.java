package dev.tim1337.lumio.util;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.ui.Toasts;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Java Flight Recorder profiling: a recording starts while the profiling switch (next to the debug log) is on and is written to a file in the mod
 * folder when the switch is turned off or the game closes. The overhead is around 1%, so the game stays playable while recording. Open the file
 * with JDK Mission Control or read it with {@code jfr print}.
 */
public final class Profiler {
	/** The running recording, typed as Object so this class still loads on JVMs without JFR. */
	private static Object recording;
	private static Path file;
	/** Set once JFR turned out to be unavailable, so it is not retried. */
	private static boolean unavailable;

	private Profiler() {
	}

	/** Called every tick: starts or stops the recording to match the switch. */
	public static void tick(boolean wanted) {
		if (unavailable) {
			return;
		}
		if (wanted && recording == null) {
			start();
		} else if (!wanted && recording != null) {
			stop();
		}
	}

	private static void start() {
		try {
			Path folder = FabricLoader.getInstance().getGameDir().resolve("lumio").resolve("profiles");
			Files.createDirectories(folder);
			file = folder.resolve("profile-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".jfr");
			// JFR's built-in "profile" settings: method sampling every few milliseconds plus allocation events
			Recording made = new Recording(Configuration.getConfiguration("profile"));
			made.setName("Lumio");
			made.setToDisk(true);
			made.setDestination(file);
			// also written when the game exits while recording
			made.setDumpOnExit(true);
			made.start();
			recording = made;
			Logs.info("Performance profile: recording to lumio/profiles/{}", file.getFileName());
			Toasts.info("Performance profile", "Recording. Turn it off to save it.");
		} catch (Throwable e) {
			// a JVM without JFR (or one that forbids it): the switch does nothing
			unavailable = true;
			recording = null;
			Logs.warn("Performance profile: the Flight Recorder of Java can not be used", e);
			Toasts.info("Performance profile", "Not possible with this Java");
		}
	}

	private static void stop() {
		try {
			// with a destination set, stopping writes the file
			((Recording) recording).stop();
			((Recording) recording).close();
			Logs.info("Performance profile: saved to lumio/profiles/{}", file.getFileName());
			Toasts.info("Performance profile", "Saved: " + file.getFileName());
		} catch (Throwable e) {
			Logs.warn("Performance profile: could not be saved", e);
		} finally {
			recording = null;
		}
	}
}
