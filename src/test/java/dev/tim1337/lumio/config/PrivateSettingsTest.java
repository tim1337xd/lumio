package dev.tim1337.lumio.config;

import dev.tim1337.lumio.config.setting.StringSetting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tokens, account ids and other private values never leave in a shared config, and a config from someone else can never set them. */
class PrivateSettingsTest {
	private static final String ID = "private_settings_test";

	private static StringSetting token;
	private static StringSetting clientId;
	private static StringSetting carried;
	private static StringSetting title;

	/** A feature with three private values and one ordinary setting, as the music and slayer features have them. */
	private static final class Sample extends Feature {
		Sample() {
			super(ID, "Sample", "", true);
			token = add(new StringSetting("cider_token", Component.literal("Token"), "", true));
			clientId = add(new StringSetting("spotify_client_id", Component.literal("Client ID"), "", false));
			carried = add(new StringSetting("carry_name", Component.literal("Player"), "", false));
			title = add(new StringSetting("title_text", Component.literal("Title"), "", false));
		}
	}

	@BeforeAll
	static void register() {
		FeatureManager.register(new Sample());
	}

	@Test
	void privateKeysAreKnown() {
		for (String key : new String[]{"cider_token", "spotify_client_id", "carry_name", "last_server_ip"}) {
			assertTrue(FeatureManager.isPrivate(key), key);
		}
		assertFalse(FeatureManager.isPrivate("title_text"));
	}

	@Test
	void exportLeavesPrivateValuesOut() {
		token.set("secret-token");
		clientId.set("my-client-id");
		carried.set("SomePlayer");
		title.set("Hello");
		String exported = FeatureManager.decode(FeatureManager.exportText());
		assertFalse(exported.contains("secret-token"));
		assertFalse(exported.contains("my-client-id"));
		assertFalse(exported.contains("SomePlayer"));
		JsonObject sample = JsonParser.parseString(exported).getAsJsonObject().getAsJsonObject(ID);
		assertEquals("Hello", sample.get("title_text").getAsString());
	}

	@Test
	void importNeverSetsPrivateValues() {
		token.set("mine");
		clientId.set("mine");
		carried.set("");
		title.set("before");
		String foreign = "{\"" + ID + "\":{\"cider_token\":\"theirs\",\"spotify_client_id\":\"theirs\",\"carry_name\":\"Them\",\"title_text\":\"after\"}}";
		assertTrue(FeatureManager.apply(foreign, Set.of(ConfigPart.FEATURES), feature -> true, false));
		assertEquals("mine", token.get());
		assertEquals("mine", clientId.get());
		assertEquals("", carried.get());
		assertEquals("after", title.get());
	}
}
