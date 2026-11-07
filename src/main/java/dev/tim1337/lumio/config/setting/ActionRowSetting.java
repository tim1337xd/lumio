package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ActionRowControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A row that opens something: its title, a line under it or a state beside it, and a button or a chevron on the right; for a summary of settings
 * kept in a window of their own, such as the source of the music or the rarely needed settings. It has no value.
 */
public class ActionRowSetting extends Setting<Void> {
	/**
	 * What the row shows, read when drawn.
	 *
	 * @param icon    the icon on a tile in front, or null for none
	 * @param subtext the line under the title, or null
	 * @param state   a colored state beside the button (an {@link EnumSetting.OptionState}, negative color for none), or null
	 * @param right   a short text before the chevron, such as how many settings it opens, or null
	 * @param button  the button's name, read when drawn, or null for a chevron and a row that is clicked as a whole
	 */
	public record Look(Supplier<String> icon, IntSupplier iconRgb, Supplier<String> title, Supplier<String> subtext,
					   Supplier<EnumSetting.OptionState> state, Supplier<String> right, Supplier<String> button) {
	}

	private final Look look;
	private final Runnable action;

	public ActionRowSetting(String key, Component label, Look look, Runnable action) {
		super(key, label, null);
		this.look = look;
		this.action = action;
	}

	public Look look() {
		return look;
	}

	public void run() {
		action.run();
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
		return new ActionRowControl(this);
	}
}
