package dev.tim1337.lumio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

class ConfigFilesTest {
	private static final Gson GSON = new Gson();

	private static JsonObject json(String text) {
		return GSON.fromJson(text, JsonObject.class);
	}

	@Test
	void twoGamesKeepEachOthersChanges() {
		String base = "{\"_version\":14,\"menu\":{\"theme\":\"DARK\",\"accent\":\"BLUE\"},\"sky\":{\"style\":\"AUTO\"}}";
		// the other game changed the theme; this game changed the sky
		String theirs = "{\"_version\":14,\"menu\":{\"theme\":\"COLORED\",\"accent\":\"BLUE\"},\"sky\":{\"style\":\"AUTO\"}}";
		String ours = "{\"_version\":14,\"menu\":{\"theme\":\"DARK\",\"accent\":\"BLUE\"},\"sky\":{\"style\":\"STARS\"}}";
		JsonObject merged = json(ConfigFiles.merged(base, theirs, ours));
		assertEquals("COLORED", merged.getAsJsonObject("menu").get("theme").getAsString());
		assertEquals("STARS", merged.getAsJsonObject("sky").get("style").getAsString());
	}

	@Test
	void aChangeOfBothTakesThisGames() {
		String base = "{\"menu\":{\"theme\":\"DARK\"}}";
		JsonObject merged = json(ConfigFiles.merged(base, "{\"menu\":{\"theme\":\"GLASS\"}}", "{\"menu\":{\"theme\":\"LIGHT\"}}"));
		assertEquals("LIGHT", merged.getAsJsonObject("menu").get("theme").getAsString());
	}

	@Test
	void theLogNamesEachChangedValueAndHidesPrivateOnes() {
		String text = ConfigFiles.changes("{\"menu\":{\"theme\":\"DARK\"},\"music\":{\"cider_token\":\"abc\"}}",
			"{\"menu\":{\"theme\":\"COLORED\"},\"music\":{\"cider_token\":\"xyz\"}}");
		assertTrue(text.contains("menu.theme \"DARK\" → \"COLORED\""), text);
		assertTrue(text.contains("music.cider_token (private) → (private)"), text);
		assertTrue(!text.contains("xyz") && !text.contains("abc"), text);
		assertEquals("no values changed", ConfigFiles.changes("{\"a\":{\"b\":1}}", "{\"a\":{\"b\":1}}"));
	}
}
