package dev.tim1337.lumio.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Installs an update the player downloaded, after the game has closed: the running jar cannot be replaced while the game uses it. Started by the
 * game as it closes, with the same Java, from a copy of this one class (only the JDK, no other class, so no jar stays in use); it waits for the
 * game to end, then carries out the plan. Nothing else: it downloads nothing and runs nothing.
 * <p>
 * At every moment the mods folder holds exactly one jar of each mod, whole:
 * <ol>
 * <li>prepare every line: the old jar is copied to the backup folder (it stays where it is), the new jar is copied next to it under a name the
 * game ignores and checked once more against its SHA-256; if any line cannot be prepared, nothing is changed and the plan waits;</li>
 * <li>replace: each staged file takes the old jar's place in one atomic rename; if one fails, those already replaced are put back the same way;</li>
 * <li>finish: the jar gets its release name in one more atomic rename, the backup takes the place of the previous one.</li>
 * </ol>
 */
public final class SwapHelper {
	/** The plan's lines: the old jar, the new jar, its name in the mods folder, the backup folder, what to tell the player, and its SHA-256. */
	static final String SEPARATOR = "\t";
	static final int FIELDS = 6;
	static final String PLAN = "install.plan";
	static final String DONE = "installed.txt";
	static final String LOG = "helper.log";
	/** The start of a problem that drops the line: there is nothing to install any more. */
	static final String GONE = "Nothing to install: ";
	/** A staged jar's name in the mods folder: no ".jar" at the end, so the game never loads a half file. */
	private static final String STAGED_PREFIX = ".lumio-update-";
	private static final String PART = ".part";
	private static final String JAR = ".jar";
	private static final long WAIT_SECONDS = 120;
	private static final long MOST_LOG_BYTES = 64 * 1024;
	private static final int BUFFER = 64 * 1024;
	private static final int OLD = 0;
	private static final int NEW = 1;
	private static final int NAME = 2;
	private static final int BACKUP = 3;
	private static final int LABEL = 4;
	private static final int HASH = 5;

	private SwapHelper() {
	}

	/** @param args the game's process id and the plan file */
	public static void main(String[] args) {
		if (args.length != 2) {
			return;
		}
		Path plan = Path.of(args[1]).toAbsolutePath().normalize();
		Path dir = plan.getParent();
		try {
			long pid = Long.parseLong(args[0]);
			var game = ProcessHandle.of(pid);
			if (game.isPresent()) {
				game.get().onExit().get(WAIT_SECONDS, TimeUnit.SECONDS);
			}
		} catch (Exception e) {
			log(dir, "The game did not close in time; the update waits for the next time");
			return;
		}
		run(plan);
	}

	/** Carries out a plan: all its lines or none; the lines that are done are told next time, the others stay for the next try. */
	static void run(Path plan) {
		Path dir = plan.getParent();
		List<String> lines;
		try {
			lines = Files.readAllLines(plan, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return;
		}
		List<String[]> entries = new ArrayList<>();
		List<String> waiting = new ArrayList<>();
		String problem = null;
		for (String line : lines) {
			if (line.isBlank()) {
				continue;
			}
			String[] parts = line.split(SEPARATOR, -1);
			String reason = parts.length != FIELDS ? GONE + "a line of the plan is broken" : prepare(parts, dir);
			if (reason == null) {
				entries.add(parts);
				waiting.add(line);
			} else if (reason.startsWith(GONE)) {
				log(dir, reason);
			} else {
				problem = reason;
				waiting.add(line);
			}
		}
		if (problem == null) {
			problem = replaceAll(entries);
		}
		List<String> told = new ArrayList<>();
		if (problem != null) {
			for (String[] entry : entries) {
				undo(entry);
			}
			log(dir, "Not installed yet, tried again next time: " + problem);
		} else {
			for (String[] entry : entries) {
				finish(entry, dir);
				told.add(entry[LABEL]);
			}
			waiting.clear();
		}
		try {
			if (!told.isEmpty()) {
				Files.write(dir.resolve(DONE), told, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			if (waiting.isEmpty()) {
				Files.deleteIfExists(plan);
			} else {
				Path temp = plan.resolveSibling(plan.getFileName() + PART);
				Files.write(temp, waiting, StandardCharsets.UTF_8);
				Files.move(temp, plan, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
		} catch (IOException e) {
			log(dir, "The plan could not be updated");
		}
	}

	/**
	 * Swaps one mod's jar, as {@link #run} does for a plan of one line.
	 *
	 * @param work the updater's own folder, where the new jar is downloaded and the backup folder is
	 * @return null when it worked, else why not
	 */
	static String swap(Path oldJar, Path newJar, String targetName, Path backup, Path work, String sha256) {
		String[] entry = {oldJar.toString(), newJar.toString(), targetName, backup.toString(), "", sha256};
		String problem = prepare(entry, work);
		if (problem == null) {
			problem = replaceAll(List.<String[]>of(entry));
		}
		if (problem != null) {
			undo(entry);
			return problem;
		}
		finish(entry, work);
		return null;
	}

	/** Step 1: checks a line and stages its files; changes nothing in the mods folder but a staged file the game ignores. */
	private static String prepare(String[] entry, Path work) {
		Path oldJar = Path.of(entry[OLD]).toAbsolutePath().normalize();
		Path newJar = Path.of(entry[NEW]).toAbsolutePath().normalize();
		Path backup = Path.of(entry[BACKUP]).toAbsolutePath().normalize();
		work = work.toAbsolutePath().normalize();
		String targetName = entry[NAME];
		if (!targetName.endsWith(JAR) || targetName.contains("/") || targetName.contains("\\") || targetName.contains("..")
			|| !oldJar.getFileName().toString().endsWith(JAR) || !newJar.getFileName().toString().endsWith(JAR)) {
			return GONE + "the plan names other files than jars";
		}
		// the helper only ever takes a jar from its own folder (a download, or the backup to restore)
		if (!newJar.startsWith(work) || !backup.startsWith(work)) {
			return GONE + "the new jar is not in the updater's folder";
		}
		if (!Files.isRegularFile(newJar)) {
			return GONE + "the new jar is gone";
		}
		Path target = oldJar.resolveSibling(targetName);
		if (Files.exists(target) && !target.equals(oldJar)) {
			return "a file " + targetName + " is in the way";
		}
		try {
			if (Files.isRegularFile(oldJar)) {
				Files.createDirectories(backup);
				Files.copy(oldJar, backupPart(entry), StandardCopyOption.REPLACE_EXISTING);
			}
			Files.copy(newJar, staged(entry), StandardCopyOption.REPLACE_EXISTING);
			if (!sha256(staged(entry)).equalsIgnoreCase(entry[HASH])) {
				undo(entry);
				return GONE + "the new jar is not the one downloaded";
			}
			return null;
		} catch (IOException e) {
			undo(entry);
			return "the files could not be prepared (" + e.getClass().getSimpleName() + ")";
		}
	}

	/** Step 2: every staged jar takes its old jar's place in one atomic rename; on a failure the ones done are put back. */
	private static String replaceAll(List<String[]> entries) {
		for (int i = 0; i < entries.size(); i++) {
			String[] entry = entries.get(i);
			try {
				Files.move(staged(entry), installedAt(entry), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				for (int done = i - 1; done >= 0; done--) {
					putBack(entries.get(done));
				}
				return "a file is still in use (" + e.getClass().getSimpleName() + ")";
			}
		}
		return null;
	}

	/** Undoes a replaced line: the backup's copy takes the place back the same way, or the new jar goes when there was no old one. */
	private static void putBack(String[] entry) {
		try {
			Path oldJar = Path.of(entry[OLD]);
			if (Files.isRegularFile(backupPart(entry))) {
				Files.copy(backupPart(entry), staged(entry), StandardCopyOption.REPLACE_EXISTING);
				Files.move(staged(entry), oldJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} else {
				Files.deleteIfExists(installedAt(entry));
			}
		} catch (IOException ignored) {
			// the next step logs the failed install; this jar keeps the new version under the old name
		}
	}

	/** Step 3: the release name, the backup in place of the previous one, and the download gone. */
	private static void finish(String[] entry, Path dir) {
		Path oldJar = Path.of(entry[OLD]);
		Path target = oldJar.resolveSibling(entry[NAME]);
		Path backup = Path.of(entry[BACKUP]);
		try {
			if (!target.equals(installedAt(entry))) {
				Files.move(installedAt(entry), target, StandardCopyOption.ATOMIC_MOVE);
			}
		} catch (IOException e) {
			log(dir, "Installed, but still under its old name: " + oldJar.getFileName());
		}
		try {
			if (Files.isRegularFile(backupPart(entry))) {
				clear(backup);
				Files.move(backupPart(entry), backup.resolve(oldJar.getFileName()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			Files.deleteIfExists(Path.of(entry[NEW]));
		} catch (IOException e) {
			log(dir, "The backup could not be kept");
		}
	}

	/** Takes back what step 1 made: the staged jar and the backup's copy. */
	private static void undo(String[] entry) {
		try {
			Files.deleteIfExists(staged(entry));
			Files.deleteIfExists(backupPart(entry));
		} catch (IOException ignored) {
			// neither is a jar the game loads
		}
	}

	/** @return where the mod's jar is while it is replaced: the old jar's own name, or the release name when there was none */
	private static Path installedAt(String[] entry) {
		Path oldJar = Path.of(entry[OLD]);
		return Files.isRegularFile(oldJar) || Files.isRegularFile(backupPart(entry)) ? oldJar : oldJar.resolveSibling(entry[NAME]);
	}

	private static Path staged(String[] entry) {
		return Path.of(entry[OLD]).resolveSibling(STAGED_PREFIX + entry[NAME] + PART);
	}

	private static Path backupPart(String[] entry) {
		return Path.of(entry[BACKUP]).resolve(Path.of(entry[OLD]).getFileName() + PART);
	}

	/** Empties the backup folder of its jars: only the last version is kept. */
	private static void clear(Path backup) throws IOException {
		try (DirectoryStream<Path> files = Files.newDirectoryStream(backup, "*" + JAR)) {
			for (Path file : files) {
				Files.deleteIfExists(file);
			}
		}
	}

	static String sha256(Path file) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[BUFFER];
			int read;
			while ((read = in.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void log(Path dir, String message) {
		try {
			Path log = dir.resolve(LOG);
			if (Files.exists(log) && Files.size(log) > MOST_LOG_BYTES) {
				Files.delete(log);
			}
			Files.writeString(log, Instant.now() + " " + message + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// nothing more can be done from here
		}
	}
}
