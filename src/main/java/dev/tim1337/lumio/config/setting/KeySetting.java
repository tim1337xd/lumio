package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.features.misc.keybinds.KeyCombo;
import dev.tim1337.lumio.features.misc.keybinds.KeyCombos;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.KeyControl;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A key of a feature, edited in the menu. It is a view of the game's key mapping, which stays where the game keeps it (options.txt, also shown
 * in the game's controls screen): nothing is stored in the mod's config, so shared configs and prebuilt looks never change anyone's keys.
 * <p>
 * Two keys only conflict where both act (see {@link KeyClash}): each has a context, in the world, in any menu, only in one menu, or while
 * typing. The game's own keys act in the world, and a few of them in menus too. A key of one menu is used instead of the others there: that is
 * no conflict, only a quiet note while its field has the focus.
 */
public class KeySetting extends Setting<Void> {
	/** Where a key acts. */
	public enum Where {
		WORLD, SCREENS, TYPING
	}

	/** How serious a conflict is: two of the mod's keys that would both act, or a key the game or another mod also uses. */
	public enum Conflict {
		NONE, OTHER, OWN
	}

	/** Every key setting, for the conflict check between them. */
	private static final List<KeySetting> ALL = new ArrayList<>();
	/** Whether a key changed since the game's options were last saved. */
	private static boolean changed;
	/** Counts the changes of keys and their conflicts, so a list of them sorts again only after one. */
	private static int version;

	private final KeyMapping mapping;
	private final Where where;
	private Conflict conflict = Conflict.NONE;
	private String conflictText;
	/** What this key is used instead of in its menu: a quiet note, shown only while its field has the focus. */
	private String overrideText;
	/** The menu the key only acts in, or null. */
	private String menu;
	/** The key whose field has the focus (it listens for a key), and the control that said so. */
	private static KeySetting focused;
	private static Object focusedBy;
	/** Where the game's own keys act: in the world, and a few (the inventory key, dropping, the off hand, the hotbar slots) in menus too. */
	private static final List<KeyClash.Context> GAME_KEY = List.of(KeyClash.Context.WORLD);
	private static final List<KeyClash.Context> GAME_KEY_IN_MENUS = List.of(KeyClash.Context.WORLD, KeyClash.Context.ANY_MENU);
	/** Name of the feature the key belongs to, for the conflict text of other keys. */
	private String owner = "";

	public KeySetting(KeyMapping mapping, Where where) {
		super(mapping.getName(), Component.translatable(mapping.getName()), null);
		this.mapping = mapping;
		this.where = where;
		ALL.add(this);
	}

	/**
	 * @return the key's name on its feature's page, without the feature's name in front of it ("Cinematic camera: next shot" is "Next shot"
	 *         there); {@link #fullName} keeps it
	 */
	@Override
	public String labelText() {
		return withoutOwner(super.labelText(), owner);
	}

	/** @return the key's whole name, as the game's controls list shows it */
	public String fullName() {
		return super.labelText();
	}

	/** @return a key's name without its feature's name in front of it ("Feature: name"), first letter capital; the name itself when it has none */
	public static String withoutOwner(String name, String owner) {
		String prefix = owner + ": ";
		if (!owner.isEmpty() && name.length() > prefix.length() && name.regionMatches(true, 0, prefix, 0, prefix.length())) {
			String rest = name.substring(prefix.length());
			return Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
		}
		return name;
	}

	/** Sets the feature name used when another key conflicts with this one. */
	public KeySetting ownedBy(String featureName) {
		this.owner = featureName;
		return this;
	}

	/** Says that the key acts only while one menu is open, with the menu's name as the player knows it ("Loadouts"). */
	public KeySetting inMenu(String menuName) {
		this.menu = menuName;
		return this;
	}

	/** @return where the key acts */
	public KeyClash.Context context() {
		return new KeyClash.Context(where, where == Where.SCREENS ? menu : null);
	}

	/** Under a key field while it waits for a key: what the field does then besides taking the key pressed. */
	public static final String LISTENING_HINT = "Esc clears, click cancels";

	/** A key field says whether it has the focus (it listens for a key): only then the quiet note of its key shows. */
	public static void focus(KeySetting key, Object by, boolean on) {
		if (on) {
			focused = key;
			focusedBy = by;
		} else if (focusedBy == by) {
			focused = null;
			focusedBy = null;
		}
	}

	/** @return whether what {@link #description} gives is the quiet note, not a conflict */
	public boolean quietNote() {
		return conflictText == null && description() != null;
	}

	/** @return the name of the feature the key belongs to */
	public String owner() {
		return owner;
	}

	/** @return a number that changes whenever a key or a conflict changes */
	public static int version() {
		return version;
	}

	/** @return every key of the mod and its add-ons, in the order they were added */
	public static List<KeySetting> all() {
		return Collections.unmodifiableList(ALL);
	}

	public KeyMapping mapping() {
		return mapping;
	}

	/** Binds the key, or removes it with {@link InputConstants#UNKNOWN}, and rechecks every key for conflicts. */
	public void bind(InputConstants.Key key) {
		bind(InputConstants.UNKNOWN.equals(key) ? KeyCombo.NONE : KeyCombo.of(key.getName()));
	}

	/** Binds a key combination (one key, or up to three: Tab + R), or none, and rechecks every key for conflicts. */
	public void bind(KeyCombo combo) {
		KeyCombos.bind(mapping, combo);
		changed = true;
		refreshConflicts();
	}

	/** The search finds a key by its combination: "tab r" finds "Tab + R". */
	@Override
	public String searchText() {
		return KeyCombos.shown(mapping);
	}

	/** @return its key combination: its key alone without one */
	public KeyCombo combo() {
		return KeyCombos.of(mapping);
	}

	/**
	 * Keys are not reset together with settings: loading a config or resetting all settings leaves them as they are, as before keys were in the
	 * menu. Only the reset of the feature's own settings window resets them, with {@link #resetKey}.
	 */
	@Override
	public void reset() {
	}

	/** Binds the key the feature comes with. */
	public void resetKey() {
		bind(mapping.getDefaultKey());
	}

	@Override
	public boolean isDefault() {
		return mapping.isDefault();
	}

	public Conflict conflict() {
		return conflict;
	}

	/**
	 * The conflict, explained under the key's name; computed when keys change, since the menu asks for it every frame. Without one, and only while
	 * the key's field has the focus: what the key is used instead of in its menu.
	 */
	@Override
	public String description() {
		if (conflictText != null) {
			return conflictText;
		}
		if (focused != this) {
			return null;
		}
		// while the field waits for a key: what the key is used instead of, or else what ends the waiting
		return overrideText != null ? overrideText : LISTENING_HINT;
	}

	/** Rechecks all keys of the mod against each other and against the game's keys. Called when a key changes and when the menu opens. */
	public static void refreshConflicts() {
		version++;
		Options options = Minecraft.getInstance().options;
		for (KeySetting setting : ALL) {
			setting.check(options);
		}
	}

	private void check(Options options) {
		conflict = Conflict.NONE;
		conflictText = null;
		overrideText = null;
		if (mapping.isUnbound()) {
			return;
		}
		// the same keys conflict; a plain key and a combination with it do not (the combination wins while its keys are held)
		KeyCombo own = combo();
		KeyClash.Context context = context();
		// each name once, in the order found: the mod's keys first, then the game's
		Set<String> clashes = new LinkedHashSet<>();
		Set<String> overridden = new LinkedHashSet<>();
		for (KeySetting other : ALL) {
			// the same key listed on two pages (an add-on lists Lumio's place key too) is no conflict with itself
			if (other == this || other.mapping == mapping || !own.sameAs(other.combo())) {
				continue;
			}
			KeyClash.Kind kind = KeyClash.between(context, other.context());
			String name = other.owner.isEmpty() ? other.labelText() : other.owner + " › " + other.labelText();
			if (kind == KeyClash.Kind.BOTH) {
				conflict = Conflict.OWN;
				clashes.add(name);
			} else if (kind == KeyClash.Kind.OVERRIDES) {
				overridden.add(name);
			}
		}
		for (KeyMapping other : options.keyMappings) {
			if (other == mapping || own(other) || !own.sameAs(comboOf(options, other))) {
				continue;
			}
			KeyClash.Kind kind = KeyClash.between(context, actsInScreens(options, other) ? GAME_KEY_IN_MENUS : GAME_KEY);
			String name = Component.translatable(other.getName()).getString();
			if (kind == KeyClash.Kind.BOTH) {
				if (conflict == Conflict.NONE) {
					conflict = Conflict.OTHER;
				}
				clashes.add(name);
			} else if (kind == KeyClash.Kind.OVERRIDES) {
				overridden.add(name);
			}
		}
		conflictText = KeyClash.conflictHint(clashes);
		overrideText = KeyClash.overrideNote(overridden, context.menu());
	}

	/**
	 * @return the combination a key of the game acts on: a debug key (F3 + H, Show Advanced Tooltips, and the others) acts only with the debug modifier
	 *         held, so a plain H is no conflict with it, while F3 + H is
	 */
	public static KeyCombo comboOf(Options options, KeyMapping other) {
		KeyCombo combo = KeyCombos.of(other);
		if (other.getCategory() != KeyMapping.Category.DEBUG || other == options.keyDebugOverlay || other == options.keyDebugModifier || !combo.bound()
			|| options.keyDebugModifier.isUnbound()) {
			return combo;
		}
		return debugCombo(KeyMappingHelper.getBoundKeyOf(options.keyDebugModifier).getName(), combo.trigger());
	}

	/** @return a debug key's combination: the debug modifier held, then its own key */
	public static KeyCombo debugCombo(String modifier, String trigger) {
		return KeyCombo.pressed(List.of(modifier, trigger));
	}

	private static boolean own(KeyMapping mapping) {
		for (KeySetting setting : ALL) {
			if (setting.mapping == mapping) {
				return true;
			}
		}
		return false;
	}

	/** @return whether a key of the game also acts while an inventory is open: opening it, dropping, the off hand and the hotbar slots */
	private static boolean actsInScreens(Options options, KeyMapping other) {
		if (other == options.keyInventory || other == options.keyDrop || other == options.keySwapOffhand) {
			return true;
		}
		for (KeyMapping slot : options.keyHotbarSlots) {
			if (other == slot) {
				return true;
			}
		}
		return false;
	}

	/** Saves the game's options if a key changed, once, when the menu closes, as the game's own controls screen does. */
	public static void saveIfChanged() {
		if (changed) {
			changed = false;
			Minecraft.getInstance().options.save();
		}
	}

	/** The key lives in the game's options, not in the mod's config. */
	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new KeyControl(this, onChanged);
	}
}
