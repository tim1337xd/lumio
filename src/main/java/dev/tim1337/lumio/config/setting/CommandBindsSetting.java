package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.features.misc.keybinds.CommandBind;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.CommandBindsControl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The custom keybinds, in the order they were made: each a key that sends one command. Persisted as a list in the feature's config, so they are part of a
 * shared config as well.
 */
public class CommandBindsSetting extends Setting<Void> {
	/** The most keybinds kept, so an edited config cannot make the list endless. */
	public static final int MOST = 64;

	private final List<CommandBind> binds = new ArrayList<>();
	private final List<CommandBind> view = Collections.unmodifiableList(binds);
	/** Counts changes, so the page lays itself out again only after one. */
	private int version;

	public CommandBindsSetting(String key, Component label) {
		super(key, label, null);
	}

	/** @return the keybinds in order; changed only through this setting */
	public List<CommandBind> binds() {
		return view;
	}

	/** @return a number that changes whenever a keybind changes */
	public int version() {
		return version;
	}

	/** Adds a keybind at the end, or after another one (Duplicate). @return whether it was added */
	public boolean add(CommandBind bind, CommandBind after) {
		if (binds.size() >= MOST) {
			return false;
		}
		changing();
		int at = after == null ? -1 : binds.indexOf(after);
		binds.add(at < 0 ? binds.size() : at + 1, bind);
		return true;
	}

	public void remove(CommandBind bind) {
		changing();
		binds.remove(bind);
	}

	/** Puts a keybind back at a position, for Undo. @return whether it was added */
	public boolean insert(int index, CommandBind bind) {
		if (binds.size() >= MOST) {
			return false;
		}
		changing();
		binds.add(Math.max(0, Math.min(binds.size(), index)), bind);
		return true;
	}

	/** Called before a keybind is changed in place, so the change counts as one of the setting's. */
	public void changing() {
		version++;
		set(null);
	}

	@Override
	public void reset() {
		version++;
		binds.clear();
	}

	@Override
	public boolean isDefault() {
		return binds.isEmpty();
	}

	@Override
	public JsonElement save() {
		JsonArray list = new JsonArray();
		for (CommandBind bind : binds) {
			list.add(bind.save());
		}
		return list;
	}

	@Override
	public void load(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			return;
		}
		version++;
		binds.clear();
		for (JsonElement element : json.getAsJsonArray()) {
			CommandBind bind = CommandBind.load(element);
			if (bind != null && binds.size() < MOST) {
				binds.add(bind);
			}
		}
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new CommandBindsControl(this, onChanged);
	}
}
