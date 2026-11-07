package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.TickTilesControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Switches shown as picture tiles to tick, one or more, such as the places the lyrics show. Every tile is a switch setting of its own, saved where
 * it is; this setting saves nothing. A tile can be unavailable for now, greyed with a short note why.
 */
public class TickTilesSetting extends Setting<Void> {
	/**
	 * One tile: its switch, name, what it is, its picture, and when it can be used (null for always) with the note shown while it cannot.
	 */
	public record Tile(BooleanSetting setting, String name, String description, EnumSetting.OptionPicture picture, BooleanSupplier usable,
					   String unusableNote) {
		public boolean available() {
			return usable == null || usable.getAsBoolean();
		}
	}

	private final List<Tile> tiles;
	private final String hint;
	private boolean compact;
	private String note;

	/** @param hint a short note on the right of the group's title, such as "pick one or more", or null */
	public TickTilesSetting(String key, Component label, String hint, List<Tile> tiles) {
		super(key, label, null);
		this.tiles = tiles;
		this.hint = hint;
	}

	public List<Tile> tiles() {
		return tiles;
	}

	public String hint() {
		return hint;
	}

	/** Small tiles: a short picture with the name under it and no description; the note is a line under the tiles. @return this */
	public TickTilesSetting compact(String note) {
		this.compact = true;
		this.note = note;
		return this;
	}

	public boolean isCompact() {
		return compact;
	}

	/** @return the line under the tiles, or null */
	public String note() {
		return note;
	}

	@Override
	public String valueText() {
		StringBuilder text = new StringBuilder();
		for (Tile tile : tiles) {
			if (tile.setting().get()) {
				text.append(text.isEmpty() ? "" : ", ").append(tile.name());
			}
		}
		return text.isEmpty() ? "None" : text.toString();
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new TickTilesControl(this, onChanged);
	}
}
