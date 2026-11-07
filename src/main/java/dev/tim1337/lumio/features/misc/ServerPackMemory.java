package dev.tim1337.lumio.features.misc;

import net.minecraft.client.resources.server.PackReloadConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The decisions of Keep server resource packs, without the game: whether a reload can be left out, how the kept packs are written down for the
 * next start, and which of them can be loaded again then. Unit tested with files in a temporary folder.
 */
final class ServerPackMemory {
	/** The game names a downloaded pack by its SHA-1 (the hash the server sent), in a folder named by the pack's id. */
	private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
	private static final String HASH = "SHA-1";
	private static final int READ_BUFFER_BYTES = 64 * 1024;

	private ServerPackMemory() {
	}

	/**
	 * @param requested     the packs the game is about to load
	 * @param loaded        the packs the game's resources hold, or null when that is unknown (after a failed load)
	 * @param leaving       whether the player left the server and the game is removing its packs
	 * @param reloadRunning whether a reload of the game's is still running
	 * @return whether the reload can be left out: the resources already hold exactly these packs (the same files, so the same hashes), or the
	 *         player left and the packs are kept
	 */
	static boolean skip(List<PackReloadConfig.IdAndPath> requested, List<PackReloadConfig.IdAndPath> loaded, boolean leaving, boolean reloadRunning) {
		if (reloadRunning || loaded == null) {
			return false;
		}
		return samePacks(requested, loaded) || leaving && requested.isEmpty();
	}

	/** @return whether two lists name the same packs in the same order: the same ids and the same files, however the paths are written */
	static boolean samePacks(List<PackReloadConfig.IdAndPath> first, List<PackReloadConfig.IdAndPath> second) {
		if (first.size() != second.size()) {
			return false;
		}
		for (int i = 0; i < first.size(); i++) {
			if (!first.get(i).id().equals(second.get(i).id())
				|| !first.get(i).path().toAbsolutePath().normalize().equals(second.get(i).path().toAbsolutePath().normalize())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return the packs written down for the next start: the server's address, then a line per pack with its id and file name; empty when there
	 *         is no server or one of the packs is not one of the game's downloads (a world's own pack from elsewhere)
	 */
	static String written(String server, List<PackReloadConfig.IdAndPath> packs, Path downloads) {
		if (server == null || packs.isEmpty()) {
			return "";
		}
		StringBuilder text = new StringBuilder(server);
		for (PackReloadConfig.IdAndPath pack : packs) {
			String name = pack.path().getFileName().toString();
			if (!SHA1.matcher(name).matches() || !pack.path().toAbsolutePath().normalize().equals(downloads.resolve(pack.id().toString()).resolve(name))) {
				return "";
			}
			text.append('\n').append(pack.id()).append(' ').append(name);
		}
		return text.toString();
	}

	/**
	 * @return the packs written down last time that can be loaded at the start: each one still in the game's downloads, and its file's SHA-1
	 *         still its name (the hash the server sent), so a file cut short or changed is never loaded. Empty when one of them is missing or broken,
	 *         or a line is not a pack id and a SHA-1: then the game starts without them. Nothing is downloaded.
	 */
	static List<PackReloadConfig.IdAndPath> restorable(String text, Path downloads) {
		String[] lines = text.split("\n");
		List<PackReloadConfig.IdAndPath> packs = new ArrayList<>();
		for (int i = 1; i < lines.length; i++) {
			String[] parts = lines[i].split(" ");
			if (parts.length != 2 || !SHA1.matcher(parts[1]).matches()) {
				return List.of();
			}
			UUID id;
			try {
				id = UUID.fromString(parts[0]);
			} catch (IllegalArgumentException error) {
				return List.of();
			}
			Path file = downloads.resolve(id.toString()).resolve(parts[1]);
			if (!Files.isRegularFile(file) || !parts[1].equals(sha1(file))) {
				return List.of();
			}
			packs.add(new PackReloadConfig.IdAndPath(id, file));
		}
		return packs;
	}

	/** @return the server written down with the packs, or null when there is none */
	static String server(String text) {
		int end = text.indexOf('\n');
		return end > 0 ? text.substring(0, end) : null;
	}

	/** @return a file's SHA-1 in lowercase hex, or null when it cannot be read */
	static String sha1(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance(HASH);
			byte[] buffer = new byte[READ_BUFFER_BYTES];
			for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
				digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			return null;
		}
	}
}
