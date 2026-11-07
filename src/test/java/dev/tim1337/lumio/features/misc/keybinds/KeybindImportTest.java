package dev.tim1337.lumio.features.misc.keybinds;

import dev.tim1337.lumio.config.ConfigPart;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.FeatureManagerAccess;
import dev.tim1337.lumio.config.setting.CommandBindsSetting;
import dev.tim1337.lumio.config.setting.StringSetting;

import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The keybinds of a config someone else shared: a normal import leaves them out, and with "Also import keybinds" exactly the listed ones are
 * added to the player's own, never replacing them.
 */
class KeybindImportTest {
	private static final String OTHER_ID = "keybind_import_test_other";

	private static CommandBindsSetting own;
	private static StringSetting title;

	/** The keybinds feature as a config stores it: its id and its list. */
	private static final class Keybinds extends Feature {
		Keybinds() {
			super(KeybindImport.FEATURE_ID, "Keybinds", "", true);
			own = add(new CommandBindsSetting(KeybindImport.LIST_KEY, Component.literal("Keybinds")));
		}
	}

	/** Any other feature of a config. */
	private static final class Other extends Feature {
		Other() {
			super(OTHER_ID, "Other", "", true);
			title = add(new StringSetting("title_text", Component.literal("Title"), "", false));
		}
	}

	@BeforeAll
	static void register() {
		FeatureManager.register(new Keybinds());
		FeatureManager.register(new Other());
	}

	private static CommandBind bind(int key, int modifiers, String command) {
		CommandBind bind = new CommandBind();
		bind.key = key;
		bind.modifiers = modifiers;
		bind.command = command;
		return bind;
	}

	private static String config(String binds) {
		return "{\"" + KeybindImport.FEATURE_ID + "\":{\"" + KeybindImport.LIST_KEY + "\":" + binds + "},\"" + OTHER_ID + "\":{\"title_text\":\"theirs\"}}";
	}

	@Test
	void aNormalImportLeavesKeybindsOut() {
		while (!own.binds().isEmpty()) {
			own.remove(own.binds().get(0));
		}
		own.add(bind(InputConstants.KEY_G, 0, "/warp hub"), null);
		title.set("mine");
		String foreign = config("[{\"key\":" + InputConstants.KEY_H + ",\"command\":\"/coopadd someone\"}]");
		assertTrue(FeatureManagerAccess.apply(foreign, Set.of(ConfigPart.FEATURES), KeybindImport.withoutKeybinds()));
		assertEquals("theirs", title.get());
		assertEquals(1, own.binds().size());
		assertEquals("/warp hub", own.binds().get(0).command);
	}

	@Test
	void readsEveryKeybindOfAConfig() {
		// 72 and 74 are H and J in the numbers a config without key names has, on every version
		String text = config("[{\"key\":72,\"modifiers\":" + ScreenInput.CONTROL + ",\"command\":\"/pv\"},"
			+ "{\"key\":74,\"command\":\"/pets\"}]");
		List<CommandBind> found = KeybindImport.inConfig(JsonParser.parseString(text).getAsJsonObject());
		assertEquals(2, found.size());
		assertEquals(InputConstants.KEY_H, found.get(0).key);
		assertEquals(ScreenInput.CONTROL, found.get(0).modifiers);
		assertEquals("/pets", found.get(1).command);
	}

	@Test
	void configsWithoutKeybindsOrBrokenOnesBringNone() {
		assertTrue(KeybindImport.inConfig(null).isEmpty());
		assertTrue(KeybindImport.inConfig(JsonParser.parseString("{}").getAsJsonObject()).isEmpty());
		assertTrue(KeybindImport.inConfig(JsonParser.parseString(config("\"not a list\"")).getAsJsonObject()).isEmpty());
	}

	@Test
	void onlySendableNewKeybindsAreAdded() {
		List<CommandBind> current = List.of(bind(InputConstants.KEY_G, 0, "/warp hub"));
		List<CommandBind> incoming = List.of(
			bind(InputConstants.KEY_G, 0, "/warp hub"),          // already the player's
			bind(InputConstants.KEY_H, 0, "/pv"),                // new
			bind(InputConstants.KEY_H, 0, "/pv"),                // twice in the config
			bind(InputConstants.KEY_J, 0, "pets"),               // not a command
			bind(InputConstants.KEY_K, 0, "/warp\n/coopadd x"),  // two lines
			bind(CommandBind.NO_KEY, 0, "/hub"),            // no key
			bind(InputConstants.KEY_H, ScreenInput.SHIFT, "/pv"));
		List<CommandBind> added = KeybindImport.toAdd(current, incoming, CommandBindsSetting.MOST);
		assertEquals(2, added.size());
		assertEquals(0, added.get(0).modifiers);
		assertEquals(ScreenInput.SHIFT, added.get(1).modifiers);
	}

	@Test
	void neverMoreThanTheListHolds() {
		List<CommandBind> current = new ArrayList<>();
		for (int i = 0; i < CommandBindsSetting.MOST - 1; i++) {
			current.add(bind(InputConstants.KEY_F1, 0, "/c" + i));
		}
		List<CommandBind> incoming = List.of(bind(InputConstants.KEY_H, 0, "/pv"), bind(InputConstants.KEY_J, 0, "/pets"));
		assertEquals(1, KeybindImport.toAdd(current, incoming, CommandBindsSetting.MOST).size());
	}
}
