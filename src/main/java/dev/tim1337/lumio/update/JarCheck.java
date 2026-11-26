package dev.tim1337.lumio.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks a downloaded jar before it may be installed: its size and SHA-256 as GitHub lists them, and its fabric.mod.json: the same mod and version, made for the
 * Minecraft version in use, a loader and Java it can run on, and for an add-on the Lumio API it needs. Any mismatch is a reason, and the file must
 * not be used.
 */
final class JarCheck {
	/**
	 * What the jar must fit: the mod and the version offered (null: not asked), the Minecraft version, Java, the loader, and the Lumio API that will
	 * be there (null: not asked).
	 */
	record Expected(String modId, LumioVersion version, String minecraft, int java, String loader, String lumioApi) {
	}

	private static final String MOD_FILE = "fabric.mod.json";
	private static final int MOST_MOD_FILE_BYTES = 64 * 1024;
	private static final int BUFFER = 64 * 1024;

	private JarCheck() {
	}

	/** @return the SHA-256 of a file in lower case hex */
	static String sha256(Path file) throws IOException {
		return SwapHelper.sha256(file);
	}

	/** @return why the file must not be used, or null when it fits */
	static String problem(Path file, GitHubRelease.Asset asset, Expected expected) {
		try {
			if (Files.size(file) != asset.size()) {
				return "the file's size is not the release's";
			}
			String listed = asset.sha256();
			if (listed.isEmpty()) {
				return "the release lists no checksum";
			}
			if (!listed.equals(sha256(file))) {
				return "the checksum does not match";
			}
			return modProblem(readModFile(file), expected);
		} catch (IOException | RuntimeException e) {
			return "the file could not be read";
		}
	}

	private static JsonObject readModFile(Path file) throws IOException {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			ZipEntry entry = zip.getEntry(MOD_FILE);
			if (entry == null) {
				throw new IOException("no " + MOD_FILE);
			}
			try (InputStream in = zip.getInputStream(entry)) {
				byte[] data = in.readNBytes(MOST_MOD_FILE_BYTES + 1);
				if (data.length > MOST_MOD_FILE_BYTES) {
					throw new IOException(MOD_FILE + " too large");
				}
				JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
				if (!(root instanceof JsonObject object)) {
					throw new IOException(MOD_FILE + " is no object");
				}
				return object;
			}
		}
	}

	/** @return why a mod file does not fit, or null when it does */
	static String modProblem(JsonObject mod, Expected expected) {
		JsonElement id = mod.get("id");
		if (id == null || !id.isJsonPrimitive() || !expected.modId().equals(id.getAsString())) {
			return "it is another mod";
		}
		if (expected.version() != null && !expected.version().equals(LumioVersion.parse(text(mod, "version")))) {
			// a signed jar of another version under this release's name: never installed, so no older jar comes in this way
			return "it is another version than the release";
		}
		JsonObject depends = mod.get("depends") instanceof JsonObject object ? object : new JsonObject();
		if (!depends.has("minecraft") || !fits(depends.get("minecraft"), expected.minecraft())) {
			return "it is not made for Minecraft " + expected.minecraft();
		}
		if (depends.has("java") && !fits(depends.get("java"), Integer.toString(expected.java()))) {
			return "it needs a newer Java";
		}
		if (depends.has("fabricloader") && !fits(depends.get("fabricloader"), expected.loader())) {
			return "it needs a newer Fabric Loader";
		}
		if (expected.lumioApi() != null && depends.has("lumio-api") && !fits(depends.get("lumio-api"), expected.lumioApi())) {
			return "it needs another version of Lumio";
		}
		return null;
	}

	/** @return a text field of the mod file, or null */
	private static String text(JsonObject mod, String key) {
		JsonElement value = mod.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	/** @return the lowest Lumio API version a mod file asks for (">=1.17.0" gives "1.17.0"), or null */
	static String lowestApi(JsonObject mod) {
		JsonObject depends = mod.get("depends") instanceof JsonObject object ? object : null;
		JsonElement api = depends == null ? null : depends.get("lumio-api");
		if (api == null || !api.isJsonPrimitive()) {
			return null;
		}
		String text = api.getAsString().strip();
		return text.startsWith(">=") ? text.substring(2).strip().split("\\s+")[0] : null;
	}

	/** @return the mod file of a jar, or null when it cannot be read */
	static JsonObject modFile(Path file) {
		try {
			return readModFile(file);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** @return whether a version fits a dependency: one predicate, or a list of which any one fits */
	static boolean fits(JsonElement dependency, String version) {
		try {
			Version parsed = Version.parse(version);
			if (dependency instanceof JsonArray list) {
				for (JsonElement each : list) {
					if (each.isJsonPrimitive() && VersionPredicate.parse(each.getAsString()).test(parsed)) {
						return true;
					}
				}
				return false;
			}
			return dependency.isJsonPrimitive() && VersionPredicate.parse(dependency.getAsString()).test(parsed);
		} catch (VersionParsingException | RuntimeException e) {
			return false;
		}
	}
}
