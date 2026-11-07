package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ColorSlotsControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/**
 * The glow's own colors as one row of swatches: as many as the glow's color count, of its fixed slots. It saves nothing; the count and the colors
 * are settings of their own, so saved configs stay as they are.
 */
public class GlowColorsSetting extends Setting<Void> implements ColorSlots {
	private final FloatSetting count;
	private final ColorSetting[] colors;

	public GlowColorsSetting(String key, Component label, FloatSetting count, ColorSetting[] colors) {
		super(key, label, null);
		this.count = count;
		this.colors = colors;
	}

	@Override
	public int count() {
		return Math.clamp(Math.round(count.get()), 1, colors.length);
	}

	@Override
	public ColorSetting slot(int index) {
		return colors[index];
	}

	@Override
	public boolean canAdd() {
		return count() < colors.length;
	}

	@Override
	public boolean canRemove() {
		return count() > 1;
	}

	@Override
	public void add() {
		if (canAdd()) {
			count.set((float) count() + 1);
		}
	}

	@Override
	public void remove(int index) {
		int shown = count();
		if (!canRemove() || index < 0 || index >= shown) {
			return;
		}
		for (int i = index; i < shown - 1; i++) {
			ColorSetting next = colors[i + 1];
			boolean nextFollows = next.usesMenu();
			next.setUsesMenu(false);
			int picked = next.get();
			next.setUsesMenu(nextFollows);
			// only the first color can follow the accent: any other that moves up takes the color it shows
			colors[i].setUsesMenu(colors[i].canUseMenu() && nextFollows);
			colors[i].set(picked);
		}
		count.set((float) shown - 1);
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}

	@Override
	public void reset() {
		// the count and the colors are reset themselves
	}

	@Override
	public boolean isDefault() {
		return true;
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new ColorSlotsControl(this, onChanged);
	}
}
