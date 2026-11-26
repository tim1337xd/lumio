package dev.tim1337.lumio.config;

import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.StringSetting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tokens, other players' names and other private values never leave in a shared config, and a config from someone else can never set them; a
 * debugging tool's settings are never shared at all. Private settings are named with their feature, so the same name elsewhere is shared.
 */
class PrivateSettingsTest {
	/** The id of the SkyBlock add-on's party commands, which the main mod's tests never make. */
	private static final String ID = "party_commands";
	private static final String DEBUG_ID = "debug_sample";

	private static StringSetting whitelist;
	private static StringSetting blocked;
	private static StringSetting title;
	private static BooleanSetting tools;

	/** A feature with two private values and one ordinary setting, as the party commands have them. */
	private static final class Sample extends Feature {
		Sample() {
			super(ID, "Sample", "", true);
			whitelist = add(new StringSetting("whitelist", Component.literal("Whitelist"), "", false));
			blocked = add(new StringSetting("blocked", Component.literal("Blocked players"), "", false));
			title = add(new StringSetting("title_text", Component.literal("Title"), "", false));
		}
	}

	/** A debugging tool's feature: none of it is shared. */
	private static final class DebugSample extends Feature {
		DebugSample() {
			super(DEBUG_ID, "Debug sample", "", false);
			tools = add(new BooleanSetting("tools", Component.literal("Debug tools"), false));
		}
	}

	@BeforeAll
	static void register() {
		FeatureManager.register(new Sample());
		FeatureManager.register(new DebugSample());
	}

	@Test
	void privateSettingsAreKnownWithTheirFeature() {
		assertTrue(FeatureManager.isPrivate("music", "cider_token"));
		assertTrue(FeatureManager.isPrivate("slayer_phase", "carry_name"));
		assertTrue(FeatureManager.isPrivate("party_commands", "whitelist"));
		assertTrue(FeatureManager.isPrivate("menus", "last_server_ip"));
		assertTrue(FeatureManager.isPrivate("debug_general", "tools"), "every setting of a debugging tool");
		assertTrue(FeatureManager.isPrivate("debug_log", "enabled"));
		// the same names in another feature are ordinary settings
		assertFalse(FeatureManager.isPrivate("item_list", "items"));
		assertFalse(FeatureManager.isPrivate("party_commands", "title_text"));
		assertFalse(FeatureManager.isPrivate(null, "cider_token"));
	}

	@Test
	void exportLeavesPrivateValuesAndDebugToolsOut() {
		whitelist.set("FriendOne");
		blocked.set("SomePlayer");
		title.set("Hello");
		tools.set(true);
		String exported = FeatureManager.decode(FeatureManager.exportText());
		assertFalse(exported.contains("FriendOne"));
		assertFalse(exported.contains("SomePlayer"));
		JsonObject root = JsonParser.parseString(exported).getAsJsonObject();
		assertEquals("Hello", root.getAsJsonObject(ID).get("title_text").getAsString());
		assertNull(root.get(DEBUG_ID), "a debugging tool is never in a shared config");
	}

	@Test
	void importNeverSetsPrivateValuesNorDebugTools() {
		whitelist.set("mine");
		blocked.set("");
		title.set("before");
		tools.set(false);
		String foreign = "{\"" + ID + "\":{\"whitelist\":\"theirs\",\"blocked\":\"Them\",\"title_text\":\"after\"},\"" + DEBUG_ID + "\":{\"tools\":true}}";
		assertTrue(FeatureManager.apply(foreign, Set.of(ConfigPart.FEATURES), feature -> true, false));
		assertEquals("mine", whitelist.get());
		assertEquals("", blocked.get());
		assertEquals("after", title.get());
		assertFalse(tools.get(), "a config cannot turn a debugging tool on");
	}
}
