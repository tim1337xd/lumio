package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.ColoredPalette;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ColorSlotsControl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The colors of the Colored theme: one to three, in the order they flow. Each can be the menu's own color. In a config it is a list of colors like "#FF2E63",
 * with "menu" for one that follows the menu's color.
 */
public class ColoredColorsSetting extends Setting<Void> implements ColorSlots {
	private static final String MENU = "menu";
	/** The color a new one starts with: one of the defaults that is not in use, where there is one. */
	private static final int[] PRESETS = {0xFF2E63, 0xFF8A00, 0xFFD60A, 0x30D158, 0x00C7BE, 0x32ADE6, 0x2F6BFF, 0xA23BFF, 0xFF5CCB, 0xFFFFFF};

	private final ColorSetting[] slots = new ColorSetting[ColoredPalette.MOST_COLORS];
	/** Filled anew when asked: one array per count, so no new one is made for every frame. */
	private final int[][] current = new int[ColoredPalette.MOST_COLORS][];
	private int count = ColoredPalette.MOST_COLORS;

	public ColoredColorsSetting(String key, Component label) {
		super(key, label, null);
		for (int i = 0; i < slots.length; i++) {
			slots[i] = new ColorSetting(key + "_" + (i + 1), Component.literal("Color " + (i + 1)), ColoredPalette.DEFAULT_COLORS[i]).followsBaseMenu().withPresets(PRESETS);
			current[i] = new int[i + 1];
		}
	}

	/** @return the colors now, as 0xRRGGBB; the array is reused, for reading only */
	public int[] colors() {
		int[] out = current[count - 1];
		for (int i = 0; i < count; i++) {
			out[i] = slots[i].get();
		}
		return out;
	}

	/** Sets the colors, one to three; each is then the color itself, not the menu's. */
	public void setColors(int[] colors) {
		count = Math.clamp(colors.length, 1, slots.length);
		for (int i = 0; i < count; i++) {
			slots[i].setUsesMenu(false);
			slots[i].set(colors[i]);
		}
	}

	/** @return how many colors there are */
	@Override
	public int count() {
		return count;
	}

	/** @return the setting of a color, for its picker */
	@Override
	public ColorSetting slot(int index) {
		return slots[index];
	}

	@Override
	public boolean canAdd() {
		return count < slots.length;
	}

	@Override
	public boolean canRemove() {
		return count > 1;
	}

	/** Adds a color at the end: the default of its place. */
	@Override
	public void add() {
		if (canAdd()) {
			slots[count].setUsesMenu(false);
			slots[count].set(ColoredPalette.DEFAULT_COLORS[count]);
			count++;
		}
	}

	/** Removes a color; the ones after it move up. The last one stays. */
	@Override
	public void remove(int index) {
		if (!canRemove() || index < 0 || index >= count) {
			return;
		}
		for (int i = index; i < count - 1; i++) {
			slots[i].setUsesMenu(slots[i + 1].usesMenu());
			slots[i].set(rawColor(i + 1));
		}
		count--;
	}

	/** @return the picked color of a slot, also while it follows the menu's */
	private int rawColor(int index) {
		boolean menu = slots[index].usesMenu();
		slots[index].setUsesMenu(false);
		int color = slots[index].get();
		slots[index].setUsesMenu(menu);
		return color;
	}

	@Override
	public void reset() {
		count = slots.length;
		for (ColorSetting slot : slots) {
			slot.reset();
		}
	}

	@Override
	public boolean isDefault() {
		if (count != slots.length) {
			return false;
		}
		for (ColorSetting slot : slots) {
			if (!slot.isDefault()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String valueText() {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < count; i++) {
			text.append(i == 0 ? "" : ", ").append(slots[i].valueText());
		}
		return text.toString();
	}

	@Override
	public int valueSwatch() {
		return slots[0].get();
	}

	@Override
	public JsonElement save() {
		JsonArray out = new JsonArray();
		for (int i = 0; i < count; i++) {
			out.add(slots[i].usesMenu() ? MENU : String.format(Locale.ROOT, "#%06X", rawColor(i)));
		}
		return out;
	}

	@Override
	public void load(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			return;
		}
		JsonArray array = json.getAsJsonArray();
		int[] read = new int[slots.length];
		boolean[] menu = new boolean[slots.length];
		int found = 0;
		for (int i = 0; i < array.size() && found < slots.length; i++) {
			JsonElement entry = array.get(i);
			if (!(entry instanceof JsonPrimitive primitive) || !primitive.isString()) {
				continue;
			}
			String text = primitive.getAsString();
			if (MENU.equals(text)) {
				menu[found++] = true;
				continue;
			}
			int color = parse(text);
			if (color >= 0) {
				read[found++] = color;
			}
		}
		if (found == 0) {
			return;
		}
		count = found;
		for (int i = 0; i < found; i++) {
			slots[i].setUsesMenu(menu[i]);
			if (!menu[i]) {
				slots[i].set(read[i]);
			}
		}
	}

	/** @return a color in the form "#RRGGBB", or -1 when it is not one */
	private static int parse(String text) {
		if (text.length() != 7 || text.charAt(0) != '#') {
			return -1;
		}
		try {
			return Integer.parseInt(text.substring(1), 16);
		} catch (NumberFormatException notAColor) {
			return -1;
		}
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new ColorSlotsControl(this, onChanged);
	}
}
