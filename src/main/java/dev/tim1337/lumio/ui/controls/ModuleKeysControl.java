package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The list of every key of the mod and its add-ons: a row per key with its feature and name on the left and its key on the right, which is set
 * again in the row ({@link KeyControl}: click, then press a key; Escape removes it). A conflict is written under the name. The keys without a key
 * come last, folded under one row until it is opened.
 */
public class ModuleKeysControl extends Control {
	private static final int ROW = 26;
	private static final int NOTE = 11;
	private static final int KEY_WIDTH = 120;
	private static final int CHEVRON = 10;
	private static final String SEPARATOR = " › ";

	private final Runnable onChanged;
	/** One key button per key, made once and kept, so a key that is being set keeps listening while the list is laid out again. */
	private final Map<KeySetting, KeyControl> buttons = new IdentityHashMap<>();
	private final List<KeySetting> set = new ArrayList<>();
	private final List<KeySetting> unset = new ArrayList<>();
	private boolean unfolded;
	/** The keys' version and count the list was sorted for: it sorts again only after a key or a conflict changed. */
	private int sortedFor = -1;
	private int sortedCount = -1;
	private int foldTop = -1;

	public ModuleKeysControl(Runnable onChanged) {
		this.onChanged = onChanged;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	/** The page's heading names the list already. */
	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	protected int measure(Ui ui, int width) {
		sort();
		int top = y;
		for (KeySetting key : set) {
			top += placeRow(ui, key, top, width);
		}
		foldTop = -1;
		if (!unset.isEmpty()) {
			foldTop = top;
			top += ROW;
			if (unfolded) {
				for (KeySetting key : unset) {
					top += placeRow(ui, key, top, width);
				}
			}
		}
		return top - y;
	}

	/**
	 * Splits the keys into those with a key and those without, each by feature and then name; only what is shown on the page counts. A key that
	 * acts in the world and in menus has a setting for each: it is one row, showing the more serious conflict.
	 */
	private void sort() {
		if (sortedFor == KeySetting.version() && sortedCount == KeySetting.all().size() && (!set.isEmpty() || !unset.isEmpty())) {
			return;
		}
		sortedFor = KeySetting.version();
		sortedCount = KeySetting.all().size();
		set.clear();
		unset.clear();
		Map<KeyMapping, KeySetting> byMapping = new IdentityHashMap<>();
		for (KeySetting key : KeySetting.all()) {
			if (!key.isVisible()) {
				continue;
			}
			KeySetting before = byMapping.get(key.mapping());
			if (before == null || key.conflict().ordinal() > before.conflict().ordinal()) {
				byMapping.put(key.mapping(), key);
			}
		}
		for (KeySetting key : KeySetting.all()) {
			if (byMapping.get(key.mapping()) == key) {
				(key.mapping().isUnbound() ? unset : set).add(key);
			}
		}
		Comparator<KeySetting> order = Comparator.comparing(KeySetting::owner, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(KeySetting::labelText, String.CASE_INSENSITIVE_ORDER);
		set.sort(order);
		unset.sort(order);
	}

	private int placeRow(Ui ui, KeySetting key, int top, int width) {
		KeyControl button = buttons.computeIfAbsent(key, k -> new KeyControl(k, this::changed));
		int keyWidth = Math.min(KEY_WIDTH, button.inlineWidth(ui, KEY_WIDTH));
		button.place(ui, x + width - keyWidth, top + 2, keyWidth);
		return key.description() == null ? ROW : ROW + NOTE;
	}

	private void changed() {
		onChanged.run();
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		for (KeySetting key : set) {
			renderRow(ui, key, mouseX, mouseY, palette);
		}
		if (foldTop < 0) {
			return;
		}
		boolean over = mouseX >= x && mouseX < x + width && mouseY >= foldTop && mouseY < foldTop + ROW;
		float hover = hover(ui, 0, over);
		if (hover > 0f) {
			ui.rounded(x - 4, foldTop + 1, width + 8, ROW - 2, 6, Ui.withOpacity(palette.hover(), hover));
		}
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(x + CHEVRON / 2f, foldTop + ROW / 2f);
		pose.rotate(unfolded ? 0f : (float) (-Math.PI / 2.0));
		ui.icon("chevron", -CHEVRON / 2, -CHEVRON / 2, CHEVRON, Ui.withOpacity(palette.textSecondary(), 0.8f));
		pose.popMatrix();
		ui.textMiddle("Not set (" + unset.size() + ")", x + CHEVRON + 8, foldTop + ROW / 2, palette.textSecondary());
		if (unfolded) {
			for (KeySetting key : unset) {
				renderRow(ui, key, mouseX, mouseY, palette);
			}
		}
	}

	private void renderRow(Ui ui, KeySetting key, int mouseX, int mouseY, Palette palette) {
		KeyControl button = buttons.get(key);
		if (button == null) {
			return;
		}
		int top = button.top() - 2;
		int textRoom = button.left() - x - 8;
		String owner = key.owner().isEmpty() ? "" : key.owner() + SEPARATOR;
		String name = withoutOwner(key.labelText(), key.owner());
		int ownerWidth = ui.width(owner);
		if (ownerWidth < textRoom / 2) {
			ui.textMiddle(owner, x, top + ROW / 2, palette.textSecondary());
			ui.textMiddle(ui.fit(name, textRoom - ownerWidth), x + ownerWidth, top + ROW / 2, palette.text());
		} else {
			ui.textMiddle(ui.fit(name, textRoom), x, top + ROW / 2, palette.text());
		}
		String note = key.description();
		if (note != null) {
			int color = key.conflict() == KeySetting.Conflict.OWN ? 0xFF000000 | SystemColors.RED_TEXT_RGB : SystemColors.ORANGE;
			ui.textAtScaled(ui.fit(note, textRoom), x, top + ROW - 3, TextScale.SMALL, color);
		}
		button.render(ui, mouseX, mouseY);
	}

	/** @return a key's name without its feature's name in front ("Cinematic camera: next shot" under Cinematic camera is "Next shot") */
	static String withoutOwner(String name, String owner) {
		String prefix = owner + ": ";
		if (!owner.isEmpty() && name.length() > prefix.length() && name.regionMatches(true, 0, prefix, 0, prefix.length())) {
			String rest = name.substring(prefix.length());
			return Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
		}
		return name;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (KeyControl key : shown()) {
			if (key.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
		}
		if (foldTop >= 0 && button == ScreenInput.LEFT && mouseX >= x && mouseX < x + width && mouseY >= foldTop && mouseY < foldTop + ROW) {
			unfolded = !unfolded;
			UiSounds.click();
			onChanged.run();
			return true;
		}
		return false;
	}

	/** @return the key buttons on show: those with a key, and the others while unfolded */
	private List<KeyControl> shown() {
		List<KeyControl> shown = new ArrayList<>(set.size() + unset.size());
		for (KeySetting key : set) {
			KeyControl button = buttons.get(key);
			if (button != null) {
				shown.add(button);
			}
		}
		if (unfolded) {
			for (KeySetting key : unset) {
				KeyControl button = buttons.get(key);
				if (button != null) {
					shown.add(button);
				}
			}
		}
		return shown;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		for (KeyControl button : buttons.values()) {
			if (button.listening()) {
				return button.keyPressed(key, modifiers);
			}
		}
		return false;
	}

	@Override
	public boolean wantsKeyboard() {
		for (KeyControl button : buttons.values()) {
			if (button.listening()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void loseFocus() {
		for (KeyControl button : buttons.values()) {
			button.loseFocus();
		}
	}
}
