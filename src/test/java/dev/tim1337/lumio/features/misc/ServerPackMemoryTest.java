package dev.tim1337.lumio.features.misc;

import net.minecraft.client.resources.server.PackReloadConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keep server resource packs' decisions: the same pack (the same hash, so the same file) leaves the reload out, another hash loads, and at the
 * start only cached files that are still there and unchanged are loaded again; a missing or changed file starts the game without them.
 */
class ServerPackMemoryTest {
	private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
	private static final String SERVER = "hypixel.net";

	@TempDir
	Path downloads;

	/** Writes a pack into the downloads as the game does: in a folder named by its id, the file named by its SHA-1. */
	private PackReloadConfig.IdAndPath cached(String contents) throws IOException {
		Path folder = Files.createDirectories(downloads.resolve(ID.toString()));
		Path temporary = folder.resolve("writing");
		Files.writeString(temporary, contents, StandardCharsets.UTF_8);
		Path file = folder.resolve(ServerPackMemory.sha1(temporary));
		Files.move(temporary, file);
		return new PackReloadConfig.IdAndPath(ID, file);
	}

	@Test
	void theSameHashLeavesTheReloadOut() throws IOException {
		var pack = cached("pack one");
		// the server sends the same pack again: the game asks for the same file, written another way
		var again = new PackReloadConfig.IdAndPath(ID, downloads.resolve(".").resolve(ID.toString()).resolve(pack.path().getFileName()));
		assertTrue(ServerPackMemory.skip(List.of(again), List.of(pack), false, false));
	}

	@Test
	void anotherHashLoads() throws IOException {
		var loaded = cached("pack one");
		var updated = cached("pack two, the server's newer version");
		assertFalse(ServerPackMemory.skip(List.of(updated), List.of(loaded), false, false));
		assertFalse(ServerPackMemory.skip(List.of(), List.of(loaded), false, false), "a server removing its pack while playing: reload");
		assertFalse(ServerPackMemory.skip(List.of(loaded), null, false, false), "after a failed load nothing is left out");
		assertFalse(ServerPackMemory.skip(List.of(loaded), List.of(loaded), false, true), "nor while a reload still runs");
	}

	@Test
	void afterLeavingTheRemovalIsLeftOut() throws IOException {
		var pack = cached("pack one");
		assertTrue(ServerPackMemory.skip(List.of(), List.of(pack), true, false));
	}

	@Test
	void aCachedPackLoadsAtTheStart() throws IOException {
		var pack = cached("pack one");
		String saved = ServerPackMemory.written(SERVER, List.of(pack), downloads);
		assertEquals(SERVER + "\n" + ID + " " + pack.path().getFileName(), saved);
		assertEquals(SERVER, ServerPackMemory.server(saved));
		List<PackReloadConfig.IdAndPath> restored = ServerPackMemory.restorable(saved, downloads);
		assertEquals(1, restored.size());
		// the restored pack is the one the server sends next time: that join leaves its reload out
		assertTrue(ServerPackMemory.skip(List.of(pack), restored, false, false));
	}

	@Test
	void aMissingFileStartsWithoutIt() throws IOException {
		var pack = cached("pack one");
		String saved = ServerPackMemory.written(SERVER, List.of(pack), downloads);
		Files.delete(pack.path());
		assertEquals(List.of(), ServerPackMemory.restorable(saved, downloads));
	}

	@Test
	void aChangedOrBrokenFileStartsWithoutIt() throws IOException {
		var pack = cached("pack one");
		String saved = ServerPackMemory.written(SERVER, List.of(pack), downloads);
		Files.writeString(pack.path(), "pack o", StandardCharsets.UTF_8);
		assertEquals(List.of(), ServerPackMemory.restorable(saved, downloads), "its hash no longer matches its name");
	}

	@Test
	void strangeLinesLoadNothing() {
		assertEquals(List.of(), ServerPackMemory.restorable(SERVER + "\nnot-an-id 0123456789abcdef0123456789abcdef01234567", downloads));
		assertEquals(List.of(), ServerPackMemory.restorable(SERVER + "\n" + ID + " ../../escape", downloads));
		assertEquals(List.of(), ServerPackMemory.restorable(SERVER, downloads));
		assertNull(ServerPackMemory.server(""));
	}

	@Test
	void onlyTheGamesDownloadsAreWrittenDown() throws IOException {
		var pack = cached("pack one");
		var elsewhere = new PackReloadConfig.IdAndPath(ID, downloads.getParent().resolve(pack.path().getFileName()));
		assertEquals("", ServerPackMemory.written(SERVER, List.of(elsewhere), downloads), "a world's own pack from elsewhere");
		assertEquals("", ServerPackMemory.written(null, List.of(pack), downloads), "no server");
		assertEquals("", ServerPackMemory.written(SERVER, List.of(), downloads));
	}
}
