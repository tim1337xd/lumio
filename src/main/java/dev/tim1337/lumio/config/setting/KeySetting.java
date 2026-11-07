package dev.tim1337.lumio.config.setting;

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
import java.util.List;

/**
 * A key of a feature, edited in the menu. It is a view of the game's key mapping, which stays where the game keeps it (options.txt, also shown
 * in the game's controls screen): nothing is stored in the mod's config, so shared configs and prebuilt looks never change anyone's keys.
 * <p>
 * Two keys only conflict where both act: keys used in the world are compared with every other key, keys used in inventory screens only with
 * the other screen keys and with the game's keys that act in inventories.
 */
public class KeySetting extends Setting<Void> {
	/** Where a key acts. */
	public enum Where {
		WORLD, SCREENS
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
	/** Name of the feature the key belongs to, for the conflict text of other keys. */
	private String owner = "";

	public KeySetting(KeyMapping mapping, Where where) {
		super(mapping.getName(), Component.translatable(mapping.getName()), null);
		this.mapping = mapping;
		this.where = where;
		ALL.add(this);
	}

	/** Sets the feature name used when another key conflicts with this one. */
	public KeySetting ownedBy(String featureName) {
		this.owner = featureName;
		return this;
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

	/** @return the bound key, or {@link InputConstants#UNKNOWN} */
	public InputConstants.Key boundKey() {
		return KeyMappingHelper.getBoundKeyOf(mapping);
	}

	/** Binds the key, or removes it with {@link InputConstants#UNKNOWN}, and rechecks every key for conflicts. */
	public void bind(InputConstants.Key key) {
		mapping.setKey(key);
		KeyMapping.resetMapping();
		changed = true;
		refreshConflicts();
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

	/** The conflict, explained under the key's name; computed when keys change, since the menu asks for it every frame. */
	@Override
	public String description() {
		return conflictText;
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
		if (mapping.isUnbound()) {
			return;
		}
		for (KeySetting other : ALL) {
			if (other != this && other.where == where && other.mapping.same(mapping)) {
				conflict = Conflict.OWN;
				conflictText = "Also used by " + other.owner + " › " + other.labelText();
				return;
			}
		}
		for (KeyMapping other : options.keyMappings) {
			if (other == mapping || own(other) || !other.same(mapping) || where == Where.SCREENS && !actsInScreens(options, other)) {
				continue;
			}
			conflict = Conflict.OTHER;
			conflictText = "Also: " + Component.translatable(other.getName()).getString() + " (" + other.getCategory().label().getString() + ")";
			return;
		}
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
