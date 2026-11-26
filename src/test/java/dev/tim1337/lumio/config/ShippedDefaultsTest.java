package dev.tim1337.lumio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.setting.Setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The shipped default settings ({@code assets/lumio/defaults.json}) hold only settings: every key is a setting of the main mod, never a private
 * value, a debugging tool's, a HUD position or a key bind, and every value reads back as written.
 */
class ShippedDefaultsTest {
	private static final String DEFAULTS = "/assets/lumio/defaults.json";
	private static final String FRESH = "/assets/lumio/defaults-fresh.json";

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void onlySettingsThatMayBeShipped() throws IOException {
		for (String name : List.of(DEFAULTS, FRESH)) {
			checkFile(name);
		}
	}

	private static JsonObject shipped(String name) throws IOException {
		try (InputStream in = ShippedDefaultsTest.class.getResourceAsStream(name)) {
			assertNotNull(in, name + " is in the jar");
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static void checkFile(String name) throws IOException {
		JsonObject file = shipped(name);
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, JsonElement> part : file.entrySet()) {
			Feature feature = FeatureManager.features().stream().filter(f -> f.id().equals(part.getKey())).findFirst().orElse(null);
			if (feature == null) {
				wrong.add(part.getKey() + " is no feature of the main mod");
				continue;
			}
			for (Map.Entry<String, JsonElement> entry : part.getValue().getAsJsonObject().entrySet()) {
				String id = part.getKey() + "." + entry.getKey();
				Setting<?> setting = feature.settings().stream().filter(s -> s.key().equals(entry.getKey())).findFirst().orElse(null);
				if (setting == null) {
					wrong.add(id + " is no setting");
				} else if (FeatureManager.isPrivate(feature.id(), setting.key())) {
					wrong.add(id + " is private");
				} else if (ConfigPart.of(feature, setting.key(), setting) == ConfigPart.LAYOUT) {
					wrong.add(id + " is a HUD position or size");
				} else if (setting.key().matches(".*(^|_)keys?($|_).*") || entry.getValue().toString().contains("key_name")) {
					wrong.add(id + " is a key bind");
				} else {
					setting.load(entry.getValue());
					if (!same(entry.getValue(), setting.save())) {
						wrong.add(id + " does not read back: " + setting.save());
					}
					setting.reset();
				}
			}
		}
		assertTrue(wrong.isEmpty(), name + ":\n" + String.join("\n", wrong));
		assertFalse(file.has("debug_log"), "no debugging tool");
	}

	/** @return whether a saved value says what the file says: numbers as floats, objects in the keys the file names */
	private static boolean same(JsonElement wanted, JsonElement saved) {
		if (saved == null) {
			return false;
		}
		if (wanted.isJsonPrimitive() && wanted.getAsJsonPrimitive().isNumber() && saved.isJsonPrimitive() && saved.getAsJsonPrimitive().isNumber()) {
			return Math.abs(wanted.getAsDouble() - saved.getAsDouble()) < 1e-4;
		}
		if (wanted.isJsonObject() && saved.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : wanted.getAsJsonObject().entrySet()) {
				if (!same(entry.getValue(), saved.getAsJsonObject().get(entry.getKey()))) {
					return false;
				}
			}
			return true;
		}
		if (wanted.isJsonArray() && saved.isJsonArray() && wanted.getAsJsonArray().size() == saved.getAsJsonArray().size()) {
			for (int i = 0; i < wanted.getAsJsonArray().size(); i++) {
				if (!same(wanted.getAsJsonArray().get(i), saved.getAsJsonArray().get(i))) {
					return false;
				}
			}
			return true;
		}
		return wanted.equals(saved);
	}

	@Test
	void aSavedValueWinsOverAShippedDefault() {
		Feature sky = FeatureManager.features().stream().filter(f -> f.id().equals("sky")).findFirst().orElseThrow();
		Setting<?> when = sky.settings().stream().filter(s -> s.key().equals("when")).findFirst().orElseThrow();
		JsonElement before = when.save();
		// the shipped default is taken first, then the player's saved value is read over it, as FeatureManager.load does
		when.load(JsonParser.parseString("\"ALWAYS\""));
		when.adoptAsDefault();
		when.load(JsonParser.parseString("\"NIGHT\""));
		assertEquals("NIGHT", when.save().getAsString(), "the player's own value");
		when.reset();
		assertEquals("ALWAYS", when.save().getAsString(), "reset goes back to the shipped default");
		when.load(before);
		when.adoptAsDefault();
	}

	/** A setting new since Beta 1 that the fresh-install file sets the other way than its code default. */
	private static Setting<?> visualFilter() {
		Feature filter = FeatureManager.features().stream().filter(f -> f.id().equals("visual_filter")).findFirst().orElseThrow();
		return filter.settings().stream().filter(s -> s.key().equals("enabled")).findFirst().orElseThrow();
	}

	@Test
	void aFreshInstallStartsWithTheFreshSettings() throws IOException {
		Setting<?> enabled = visualFilter();
		JsonElement codeDefault = enabled.save();
		JsonElement shipped = shipped(FRESH).getAsJsonObject("visual_filter").get("enabled");
		assertFalse(codeDefault.equals(shipped), "the test needs a value other than the code's default");
		// no settings file at all
		FeatureManager.applyFreshInstallSettings(FeatureManager.freshParts(false, addon -> false));
		assertEquals(shipped, enabled.save(), "a fresh install starts with the shipped value");
		enabled.reset();
		assertEquals(codeDefault, enabled.save(), "set as a value, not as the default");
		// the other tests see the code's defaults
		for (Map.Entry<String, JsonElement> part : shipped(FRESH).entrySet()) {
			Feature feature = FeatureManager.features().stream().filter(f -> f.id().equals(part.getKey())).findFirst().orElseThrow();
			feature.settings().stream().filter(s -> part.getValue().getAsJsonObject().has(s.key())).forEach(Setting::reset);
		}
	}

	@Test
	void anExistingSettingsFileSeesNothingChange() {
		Setting<?> enabled = visualFilter();
		JsonElement codeDefault = enabled.save();
		// a player of an earlier version: the settings file exists and has no line for the new setting
		FeatureManager.applyFreshInstallSettings(FeatureManager.freshParts(true, addon -> true));
		assertEquals(codeDefault, enabled.save(), "a setting new since keeps its default");
		assertFalse(FeatureManager.freshParts(true, addon -> false).test("lumio-skyblock"), "an add-on added later to an existing install is no fresh install");
		assertFalse(FeatureManager.freshParts(false, addon -> true).test("lumio-skyblock"), "an add-on with its own file keeps it");
		assertTrue(FeatureManager.freshParts(false, addon -> false).test("lumio-skyblock"));
	}

	@Test
	void theHeldItemKeepsTheGameDefaults() throws IOException {
		for (String name : List.of(DEFAULTS, FRESH)) {
			assertFalse(shipped(name).has("held_item_transform"), name + ": the held item's size, place, swing and equip keep their old defaults");
		}
	}
}
