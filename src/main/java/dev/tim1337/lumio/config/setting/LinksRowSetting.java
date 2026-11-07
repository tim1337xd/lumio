package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.LinksRowControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** The quiet links at the bottom of a page: one with an icon on the left, a smaller one on the right. It has no value. */
public class LinksRowSetting extends Setting<Void> {
	private final String icon;
	private final String left;
	private final Runnable leftAction;
	private final String right;
	private final Runnable rightAction;
	private boolean confirmRight;

	/** Makes the right link ask before an action that cannot be undone, such as a reset. */
	public LinksRowSetting confirmRight() {
		confirmRight = true;
		return this;
	}

	public LinksRowSetting(String key, String icon, String left, Runnable leftAction, String right, Runnable rightAction) {
		super(key, Component.literal(left), null);
		this.icon = icon;
		this.left = left;
		this.leftAction = leftAction;
		this.right = right;
		this.rightAction = rightAction;
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
		return new LinksRowControl(icon, left, leftAction, right, rightAction).confirmRight(confirmRight);
	}
}
