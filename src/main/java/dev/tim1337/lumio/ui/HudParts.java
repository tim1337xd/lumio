package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;

/**
 * The parts of a HUD element that can be switched on and off by clicking them in the HUD editor (the defense pill of the health bar, the
 * scoreboard's title), with where each was drawn this frame. An element fills it while it draws; a part that is off is drawn faded in the
 * editor, so it can be found and clicked. A part can also be a choice: a mode in a panel's header in the editor, which a click picks. Fixed
 * capacity, reused every frame: nothing is allocated while drawing.
 */
public final class HudParts {
	/** How visible a part that is off is drawn in the editor. */
	public static final float HIDDEN_ALPHA = HudEditor.HIDDEN_ALPHA;
	private static final int MOST = 48;

	private final String[] names = new String[MOST];
	private final BooleanSetting[] settings = new BooleanSetting[MOST];
	private final EnumSetting<?>[] choices = new EnumSetting<?>[MOST];
	private final int[] picks = new int[MOST];
	private final float[][] boxes = new float[MOST][4];
	private int count;

	public void clear() {
		count = 0;
	}

	/** Adds a part drawn at a box in GUI pixels; parts beyond the capacity are ignored. */
	public void add(String name, BooleanSetting setting, float x, float y, float width, float height) {
		if (count >= MOST || width <= 0f || height <= 0f) {
			return;
		}
		names[count] = name;
		settings[count] = setting;
		choices[count] = null;
		boxes[count][0] = x;
		boxes[count][1] = y;
		boxes[count][2] = width;
		boxes[count][3] = height;
		count++;
	}

	/** Adds a choice drawn at a box in GUI pixels: a click on it picks the choice of an enum setting at an index of its choices. */
	public void addChoice(String name, EnumSetting<?> setting, int index, float x, float y, float width, float height) {
		if (count >= MOST || width <= 0f || height <= 0f) {
			return;
		}
		names[count] = name;
		settings[count] = null;
		choices[count] = setting;
		picks[count] = index;
		boxes[count][0] = x;
		boxes[count][1] = y;
		boxes[count][2] = width;
		boxes[count][3] = height;
		count++;
	}

	/** @return the index of the part under the mouse, the smallest where several overlap (the later one on a tie), or -1 */
	public int at(double mouseX, double mouseY) {
		int found = -1;
		float smallest = Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			float[] box = boxes[i];
			if (mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3]) {
				float area = box[2] * box[3];
				if (area <= smallest) {
					smallest = area;
					found = i;
				}
			}
		}
		return found;
	}

	public String name(int index) {
		return names[index];
	}

	/** @return the part's switch, or null for a choice */
	public BooleanSetting setting(int index) {
		return settings[index];
	}

	/** Picks the choice of a choice part. */
	public void pick(int index) {
		pickAt(choices[index], picks[index]);
	}

	private static <E extends Enum<E>> void pickAt(EnumSetting<E> setting, int choice) {
		var all = setting.choices();
		if (choice >= 0 && choice < all.size()) {
			setting.set(all.get(choice));
		}
	}

	public float[] box(int index) {
		return boxes[index];
	}

	/** @return how visible a part is drawn: fully when on, faded in the HUD editor when off, not at all otherwise */
	public static float alpha(boolean on) {
		return HudEditor.partAlpha(on);
	}
}
