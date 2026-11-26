package dev.tim1337.lumio.features.misc.keybinds;

import dev.tim1337.lumio.config.setting.KeySetting;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.Place;
import dev.tim1337.lumio.api.ScreenHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ModuleKeysSetting;
import dev.tim1337.lumio.config.setting.CommandBindsSetting;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.ui.Toasts;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;


import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom keybinds: a key that sends one command, as if the player typed it. One press sends one command; holding the key does not repeat it, and a
 * short cooldown stops a double press from sending twice. There are no sequences, delays or macros, and only commands starting with "/". A keybind
 * works everywhere, only in SkyBlock or only on chosen islands (outside SkyBlock only those for everywhere work), and by default only while no
 * screen is open; otherwise also in inventories and containers, never while typing. While it works it takes its key from the game.
 */
public class CommandKeybindsFeature extends Feature {
	/** How long the notice of a sent command stays. */
	private static final float NOTICE_SECONDS = 1.5f;
	/** The key caps of each key with modifiers, as {@link #keyParts} made them. */
	private static final Map<Integer, List<String>> KEY_PARTS = new HashMap<>();

	private final CommandBindsSetting binds = add(new CommandBindsSetting("binds", Component.literal("Keybinds")));
	private final BooleanSetting notice = add(new BooleanSetting("notice", Component.literal("Show the command when it is sent"), true));
	/** Every key of the mod and its add-ons, set again in its row; only a view of the game's keys, nothing is kept in the config. */
	private final ModuleKeysSetting moduleKeys = add(new ModuleKeysSetting("module_keys", Component.literal("Module keys")));
	/** The player's own islands, one name per line; edited on the page, never shown as a row of its own. */
	private final StringSetting customIslandNames = add(new StringSetting("custom_islands", Component.literal("Own islands"), "", false));
	private final CustomIslands customIslands = new CustomIslands();
	private String parsedIslands;
	private final CommandBinds trigger = new CommandBinds();
	/** Names of keys with modifiers, built when a keybind is shown or sent, not per frame. */
	private int namedKey = CommandBind.NO_KEY;
	private int namedModifiers;
	private String keyName = "";

	public CommandKeybindsFeature() {
		super("command_keybinds", "Keybinds", "Put a command on a key.", false);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("keyboard");
		summary("Commands on keys of your choice");
		customIslandNames.visibleWhen(() -> false);
	}

	@Override
	public boolean ownPage() {
		return true;
	}

	public CommandBindsSetting binds() {
		return binds;
	}

	/** Adds keybinds from a shared config after the player's own, as copies. @return how many were added (the list has a limit) */
	public int addKeybinds(List<CommandBind> added) {
		int count = 0;
		for (CommandBind bind : added) {
			if (binds.add(bind.copy(), null)) {
				count++;
			}
		}
		return count;
	}

	/** @return the player's own islands, read again only after the setting changed (a loaded or imported config) */
	public CustomIslands customIslands() {
		String saved = customIslandNames.get();
		if (!saved.equals(parsedIslands)) {
			parsedIslands = saved;
			customIslands.load(saved);
		}
		return customIslands;
	}

	/** Saves the player's own islands after a change to {@link #customIslands()}. */
	public void saveCustomIslands() {
		parsedIslands = customIslands.save();
		customIslandNames.set(parsedIslands);
	}

	/** @return the place the player is at, as the server names it, or null outside the add-on's world or before it is known */
	public static String currentIsland() {
		String area = CustomIslands.clean(KeybindPlaces.placeName());
		return area.isEmpty() ? null : area;
	}

	/**
	 * A key event from the game's keyboard handler, before the game or a screen gets it.
	 *
	 * @return whether a keybind takes it: then nobody else gets it
	 */
	public boolean onKey(int action, KeyEvent event) {
		if (action == InputConstants.RELEASE) {
			// releases are always seen, so a key held while the feature was switched off is not stuck
			trigger.key(List.of(), event.key(), ScreenInput.modifiers(event.modifiers()), action, 0L, null);
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || binds.binds().isEmpty() || client.player == null || client.getConnection() == null) {
			return false;
		}
		Screen screen = client.gui.screen();
		if (screen != null && !keysFree(screen, InputConstants.getKey(event))) {
			return false;
		}
		boolean inWorld = KeybindPlaces.inWorld();
		String area = inWorld ? KeybindPlaces.placeName() : null;
		Place known = area == null ? null : CustomIslands.builtIn(area);
		CommandBinds.Spot place = new CommandBinds.Spot(inWorld, known == null ? null : known.key(), area == null ? "" : CustomIslands.key(area), screen != null);
		// a more specific bind of the game's or Lumio's on this key (Tab + R while Tab is held) wins over a keybind with fewer keys
		int giveWayTo = action == InputConstants.PRESS ? KeyCombos.mostSpecific(InputConstants.Type.KEYSYM.getOrCreate(event.key())) : 0;
		CommandBinds.Outcome outcome = trigger.key(binds.binds(), event.key(), ScreenInput.modifiers(event.modifiers()), action, System.currentTimeMillis(), place,
			KeyCombos::down, giveWayTo);
		if (outcome.send() != null) {
			send(client, outcome.send());
		}
		return outcome.taken();
	}

	/**
	 * A press of a mouse button or a turn of the wheel (by name, such as "key.mouse.4" or the wheel's), for keybinds on it.
	 *
	 * @return whether a keybind takes it: then the game does not get it
	 */
	public boolean onOther(String triggerName) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || binds.binds().isEmpty() || client.player == null || client.getConnection() == null) {
			return false;
		}
		Screen screen = client.gui.screen();
		if (screen == null ? ownedInWorld(keyNamed(triggerName)) : !keysFree(screen, keyNamed(triggerName))) {
			return false;
		}
		boolean inWorld = KeybindPlaces.inWorld();
		String area = inWorld ? KeybindPlaces.placeName() : null;
		Place known = area == null ? null : CustomIslands.builtIn(area);
		CommandBinds.Spot place = new CommandBinds.Spot(inWorld, known == null ? null : known.key(), area == null ? "" : CustomIslands.key(area), screen != null);
		CommandBinds.Outcome outcome = trigger.other(binds.binds(), triggerName, heldModifiers(), System.currentTimeMillis(), place, KeyCombos::down);
		if (outcome.send() != null) {
			send(client, outcome.send());
		}
		return outcome.taken();
	}

	/** @return the modifiers held now, as a key event gives them */
	private static int heldModifiers() {
		return (KeyCombos.down(KeyCombo.CONTROL) ? ScreenInput.CONTROL : 0) | (KeyCombos.down(KeyCombo.SHIFT) ? ScreenInput.SHIFT : 0)
			| (KeyCombos.down(KeyCombo.ALT) ? ScreenInput.ALT : 0);
	}

	/**
	 * @return whether keybinds may act on this key while this screen is open: only inventories and containers, not while one of their text fields
	 *         has the keyboard, and not on a key an add-on uses itself in this menu. Chat, signs, books, menus and the settings (where keybinds are
	 *         recorded) never lose a key to them.
	 */
	private static boolean keysFree(Screen screen, InputConstants.Key key) {
		// a text field of the screen itself, such as the creative search or the anvil name, has the keyboard as well
		if (!(screen instanceof AbstractContainerScreen<?>) || screen.getFocused() instanceof EditBox) {
			return false;
		}
		// and not while a search field of an add-on has it (the storage overlay, the item list)
		for (ScreenHooks hooks : Addons.screenHooks()) {
			if (hooks.typing(screen) || key != null && hooks.ownsKey(screen, key)) {
				return false;
			}
		}
		return true;
	}

	/** @return whether an add-on uses this key itself in the world now (such as a farming key while farming): the keybind on it waits */
	private static boolean ownedInWorld(InputConstants.Key key) {
		if (key == null) {
			return false;
		}
		for (ScreenHooks hooks : Addons.screenHooks()) {
			if (hooks.ownsWorldKey(key)) {
				return true;
			}
		}
		return false;
	}

	/** @return the key or mouse button of a trigger's name ("key.mouse.4"), or null for one that is neither, such as the wheel */
	private static InputConstants.Key keyNamed(String triggerName) {
		try {
			return InputConstants.getKey(triggerName);
		} catch (IllegalArgumentException notAKey) {
			return null;
		}
	}

	/** Sends the keybind's command once, exactly as typed, and shows it briefly if wanted. */
	private void send(Minecraft client, CommandBind bind) {
		client.getConnection().sendCommand(bind.command.substring(1));
		if (notice.get()) {
			Toasts.brief("keyboard", bind.command, "Sent with " + keyName(bind.key, bind.modifiers), NOTICE_SECONDS);
		}
	}

	/** @return a key with its modifiers as the player reads it: "G", "Ctrl + 1", or "None" */
	public String keyName(int key, int modifiers) {
		if (key != namedKey || modifiers != namedModifiers) {
			namedKey = key;
			namedModifiers = modifiers;
			keyName = String.join(" + ", keyParts(key, modifiers));
		}
		return keyName;
	}

	/** @return the parts of a key, one per key cap: the modifiers in the usual order, then the key */
	/** @return a keybind's keys as shown on key caps: "Tab", "R"; its combination, modifiers first */
	public static List<String> keyParts(CommandBind bind) {
		if (bind.holders.isEmpty() && bind.trigger.isEmpty()) {
			return keyParts(bind.key, bind.modifiers);
		}
		KeyCombo combo = bind.combo();
		List<String> parts = new ArrayList<>(combo.size());
		for (String holder : combo.holders()) {
			parts.add(KeyCombos.keyName(holder));
		}
		parts.add(KeyCombos.keyName(combo.trigger()));
		return parts;
	}

	public static List<String> keyParts(int key, int modifiers) {
		if (key == CommandBind.NO_KEY) {
			return List.of("None");
		}
		// asked for every keybind on every frame of the page: made once per key and modifiers, at most a few thousand of them
		int id = key * (CommandBind.MODIFIERS + 1) + (modifiers & CommandBind.MODIFIERS);
		List<String> known = KEY_PARTS.get(id);
		if (known != null) {
			return known;
		}
		ArrayList<String> parts = new ArrayList<>(4);
		if ((modifiers & ScreenInput.CONTROL) != 0) {
			parts.add("Ctrl");
		}
		if ((modifiers & ScreenInput.SHIFT) != 0) {
			parts.add("Shift");
		}
		if ((modifiers & ScreenInput.ALT) != 0) {
			parts.add("Alt");
		}
		parts.add(InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString());
		List<String> made = List.copyOf(parts);
		KEY_PARTS.put(id, made);
		return made;
	}

	/**
	 * @return what else uses a key, in plain words, or null: a key of the game, of another mod or of this mod (all of them are in the game's key list).
	 *         The keybind takes the key while it works, so this is a note, not an error.
	 */
	public static String otherUseOf(int key) {
		if (key == CommandBind.NO_KEY) {
			return null;
		}
		Options options = Minecraft.getInstance().options;
		for (KeyMapping mapping : options.keyMappings) {
			InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(mapping);
			// a key that acts only with others held (F3 + H, Tab + R) is no other use of the plain key
			if (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == key && KeySetting.comboOf(options, mapping).size() <= 1) {
				return Component.translatable(mapping.getName()).getString();
			}
		}
		return null;
	}
}
