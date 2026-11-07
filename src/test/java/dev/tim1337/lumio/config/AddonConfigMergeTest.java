package dev.tim1337.lumio.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When reading, an add-on's own sections win over the main file's, and the main file's version counts. */
class AddonConfigMergeTest {
	private static final String MAIN = "{\"_version\":3,\"sky\":{\"enabled\":true},\"pet_hud\":{\"enabled\":false}}";

	@Test
	void theAddonsOwnSectionsWinWhenReading() {
		JsonObject main = json(MAIN);
		Map<String, JsonObject> addons = new LinkedHashMap<>();
		addons.put("lumio-skyblock", json("{\"_version\":4,\"pet_hud\":{\"enabled\":true}}"));
		JsonObject merged = AddonConfigMerge.merged(main, addons);
		assertEquals(json("{\"enabled\":true}"), merged.get("pet_hud"));
		assertEquals(json("{\"enabled\":true}"), merged.get("sky"));
		assertEquals(3, merged.get(ConfigUpgrade.VERSION_KEY).getAsInt(), "the main file's version is the one upgraded from");
		assertEquals(json(MAIN), main);
	}

	@Test
	void onlyAnAddonFile() {
		Map<String, JsonObject> addons = Map.of("lumio-skyblock", json("{\"_version\":4,\"pet_hud\":{\"enabled\":true}}"));
		JsonObject merged = AddonConfigMerge.merged(null, addons);
		assertEquals(4, merged.get(ConfigUpgrade.VERSION_KEY).getAsInt());
		assertTrue(merged.has("pet_hud"));
	}

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}
}
