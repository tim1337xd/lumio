package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.PageLinkControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** A note at the end of a page that points to another page where related features are, with a link that opens it. It has no value. */
public class PageLinkSetting extends Setting<Void> {
	private final String text;
	private final String bold;
	private final Supplier<Feature> target;

	/**
	 * @param text   the note; the part equal to {@code bold} is drawn in bold
	 * @param bold   the target page's name as it appears in the text
	 * @param target the page the link opens, looked up when clicked since pages are made after the notes
	 */
	public PageLinkSetting(String key, String text, String bold, Supplier<Feature> target) {
		super(key, Component.literal(text), null);
		this.text = text;
		this.bold = bold;
		this.target = target;
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
		return new PageLinkControl(text, bold, "Open " + bold, target);
	}
}
