package dev.tim1337.lumio.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The swap at the game's close, on folders of the test's own: one jar of the mod in the mods folder before and after, never two and never a half
 * one; the new jar only when it is still the one downloaded; a plan of several lines all or nothing; only the last backup kept, and it restores.
 */
class SwapHelperTest {
	private static final String OLD_NAME = "lumio-1.0.0-beta.2-26.3.jar";
	private static final String NEW_NAME = "lumio-1.0.0-beta.3-26.3.jar";

	@TempDir
	Path dir;
	private Path mods;
	private Path work;
	private Path backup;
	private Path oldJar;
	private Path newJar;

	@BeforeEach
	void setUp() throws Exception {
		mods = Files.createDirectories(dir.resolve("mods"));
		work = Files.createDirectories(dir.resolve("lumio").resolve("updates"));
		backup = work.resolve("backup").resolve("lumio");
		oldJar = Files.writeString(mods.resolve(OLD_NAME), "old");
		newJar = Files.writeString(work.resolve(NEW_NAME), "new");
	}

	private static String sha(String text) throws Exception {
		return TestJars.sha256(text.getBytes(StandardCharsets.UTF_8));
	}

	private static String line(Path old, Path fresh, String name, Path backup, String label, String sha) {
		return String.join(SwapHelper.SEPARATOR, old.toString(), fresh.toString(), name, backup.toString(), label, sha);
	}

	private Path plan(String... lines) throws Exception {
		Path plan = work.resolve(SwapHelper.PLAN);
		Files.write(plan, List.of(lines));
		return plan;
	}

	/** @return the jars the game would load from the mods folder */
	private List<String> jars() throws Exception {
		List<String> names = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(mods, "*.jar")) {
			for (Path file : files) {
				names.add(file.getFileName().toString());
			}
		}
		return names;
	}

	@Test
	void theOldJarGoesToTheBackupAndTheNewOneIn() throws Exception {
		Files.createDirectories(backup);
		Files.writeString(backup.resolve("lumio-1.0.0-beta.1-26.3.jar"), "older");
		assertNull(SwapHelper.swap(oldJar, newJar, NEW_NAME, backup, work, sha("new")));
		assertEquals(List.of(NEW_NAME), jars(), "one jar of the mod, under its release name");
		assertEquals("new", Files.readString(mods.resolve(NEW_NAME)));
		assertEquals("old", Files.readString(backup.resolve(OLD_NAME)));
		assertFalse(Files.exists(backup.resolve("lumio-1.0.0-beta.1-26.3.jar")), "only the last backup is kept");
		assertFalse(Files.exists(newJar), "the download is used up");
		assertEquals(1, countOf(mods), "no staged file left behind");
	}

	private static int countOf(Path folder) throws Exception {
		try (var files = Files.list(folder)) {
			return (int) files.count();
		}
	}

	@Test
	void aChangedDownloadIsNeverInstalled() throws Exception {
		Files.writeString(newJar, "changed after the check");
		Path plan = plan(line(oldJar, newJar, NEW_NAME, backup, "Lumio 1.0.0 Beta 3", sha("new")));
		SwapHelper.run(plan);
		assertEquals(List.of(OLD_NAME), jars());
		assertEquals("old", Files.readString(oldJar));
		assertEquals(1, countOf(mods), "no staged file left behind");
		assertFalse(Files.exists(plan), "dropped: there is nothing to install");
	}

	@Test
	void aPlanRunsAndIsToldOnce() throws Exception {
		Path plan = plan(line(oldJar, newJar, NEW_NAME, backup, "Lumio 1.0.0 Beta 3", sha("new")));
		SwapHelper.run(plan);
		assertFalse(Files.exists(plan), "done: the plan is gone");
		assertEquals(List.of("Lumio 1.0.0 Beta 3"), Files.readAllLines(work.resolve(SwapHelper.DONE)));
	}

	@Test
	void aPlanOfTwoIsAllOrNothing() throws Exception {
		Path addonOld = Files.writeString(mods.resolve("lumio-skyblock-1.0.0-beta.2-26.3.jar"), "old add-on");
		Path addonNew = Files.writeString(work.resolve("lumio-skyblock-1.0.0-beta.3-26.3.jar"), "new add-on");
		// a file in the way of the add-on: Lumio is not installed alone either
		Files.writeString(mods.resolve("lumio-skyblock-1.0.0-beta.3-26.3.jar"), "another file");
		Path plan = plan(line(oldJar, newJar, NEW_NAME, backup, "Lumio", sha("new")),
			line(addonOld, addonNew, "lumio-skyblock-1.0.0-beta.3-26.3.jar", work.resolve("backup").resolve("lumio-skyblock"), "add-on", sha("new add-on")));
		SwapHelper.run(plan);
		assertEquals("old", Files.readString(oldJar), "Lumio waits for the add-on");
		assertEquals("new", Files.readString(newJar));
		assertEquals(2, Files.readAllLines(plan).size(), "both tried again next time");
		Files.delete(mods.resolve("lumio-skyblock-1.0.0-beta.3-26.3.jar"));
		SwapHelper.run(plan);
		assertEquals(List.of(NEW_NAME, "lumio-skyblock-1.0.0-beta.3-26.3.jar"), jars().stream().sorted().toList());
		assertFalse(Files.exists(plan));
	}

	@Test
	void aFileInTheWayLeavesEverythingAndStaysPlanned() throws Exception {
		Files.writeString(mods.resolve(NEW_NAME), "another file");
		Path plan = plan(line(oldJar, newJar, NEW_NAME, backup, "Lumio 1.0.0 Beta 3", sha("new")));
		SwapHelper.run(plan);
		assertEquals("old", Files.readString(oldJar));
		assertEquals("new", Files.readString(newJar));
		assertTrue(Files.exists(plan), "tried again next time");
	}

	@Test
	void aLockedJarIsLeftAsItWas() throws Exception {
		// a jar still held by the game: on Windows it cannot be replaced, and nothing changes; elsewhere the swap may work
		String problem;
		try (FileChannel held = FileChannel.open(oldJar, StandardOpenOption.READ, StandardOpenOption.WRITE); FileLock ignored = held.lock()) {
			problem = SwapHelper.swap(oldJar, newJar, NEW_NAME, backup, work, sha("new"));
		}
		if (problem != null) {
			assertEquals(List.of(OLD_NAME), jars());
			assertEquals("old", Files.readString(oldJar));
			assertTrue(Files.exists(newJar), "the download stays for the next try");
			assertEquals(1, countOf(mods), "no staged file left behind");
		} else {
			assertEquals(List.of(NEW_NAME), jars());
		}
	}

	@Test
	void onlyJarsFromTheUpdatersFolderAndNoPathTricks() throws Exception {
		Path elsewhere = Files.writeString(dir.resolve("other.jar"), "other");
		assertTrue(SwapHelper.swap(oldJar, elsewhere, "other.jar", backup, work, sha("other")).startsWith(SwapHelper.GONE));
		assertTrue(SwapHelper.swap(oldJar, newJar, "../evil.jar", backup, work, sha("new")).startsWith(SwapHelper.GONE));
		assertTrue(SwapHelper.swap(oldJar, newJar, "..\\evil.jar", backup, work, sha("new")).startsWith(SwapHelper.GONE));
		assertTrue(SwapHelper.swap(oldJar, newJar, "evil.txt", backup, work, sha("new")).startsWith(SwapHelper.GONE));
		assertTrue(SwapHelper.swap(oldJar, work.resolve("../../outside.jar"), NEW_NAME, backup, work, sha("new")).startsWith(SwapHelper.GONE));
		assertEquals("old", Files.readString(oldJar));
		assertEquals(List.of(OLD_NAME), jars());
	}

	@Test
	void theBackupComesBack() throws Exception {
		assertNull(SwapHelper.swap(oldJar, newJar, NEW_NAME, backup, work, sha("new")));
		Path current = mods.resolve(NEW_NAME);
		Path kept = backup.resolve(OLD_NAME);
		assertNull(SwapHelper.swap(current, kept, OLD_NAME, backup, work, sha("old")));
		assertEquals(List.of(OLD_NAME), jars());
		assertEquals("old", Files.readString(mods.resolve(OLD_NAME)));
		assertEquals("new", Files.readString(backup.resolve(NEW_NAME)), "the newer one is the backup now");
	}
}
