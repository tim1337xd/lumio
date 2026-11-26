package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/** The cloud shadows start off in new configs only: a sky saved before without the setting keeps them on, a saved value always wins. */
class CloudShadowsMigrationTest {
	@Test
	void savedSkyWithoutTheSettingKeepsShadows() {
		JsonObject saved = new JsonObject();
		saved.addProperty("style", "CLOUDS");
		assertTrue(SkyLook.shadowsFromBefore(saved));
	}

	@Test
	void savedValueWins() {
		JsonObject saved = new JsonObject();
		saved.addProperty("style", "CLOUDS");
		saved.addProperty("cloud_shadows", false);
		assertFalse(SkyLook.shadowsFromBefore(saved));
	}

	@Test
	void newConfigStartsWithout() {
		assertFalse(SkyLook.shadowsFromBefore(new JsonObject()));
	}
}
