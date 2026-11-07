package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.util.Mth;

/** An on/off switch whose knob springs to the other end, overshoots slightly and settles, and stretches while pressed. */
public class SwitchControl extends Control {
	public static final int TRACK_WIDTH = 34;
	public static final int TRACK_HEIGHT = 20;
	private static final int KNOB = 16;
	/** Maximum knob stretch while pressed, in menu pixels. */
	private static final float STRETCH = 4f;

	private final BooleanSetting setting;
	private final Runnable onChanged;
	/** Knob position: 0 off, 1 on, in between while moving, slightly beyond either end while settling. */
	private final Spring position = Spring.bouncy(Spring.DEFAULT);
	/** Press progress, from 0 to 1. */
	private final Spring press = Spring.smooth(Spring.SNAPPY);
	private boolean pressed;

	public SwitchControl(BooleanSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return TRACK_HEIGHT;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return TRACK_WIDTH;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		float p = position.follow(ui, setting.get() ? 1f : 0f);
		float grip = press.follow(ui, pressed ? 1f : 0f);

		float h = hover(ui, 0, contains(mouseX, mouseY));
		draw(ui, x, y, p, h, grip);
	}

	/**
	 * Draws a switch with its top-left corner at the given position; also used for switches that are not standalone controls, such as the one at
	 * the end of a search result.
	 *
	 * @param position knob position, 0 off and 1 on, slightly beyond either while settling
	 * @param hover    hover amount, from 0 to 1
	 * @param grip     press amount, from 0 to 1, which stretches the knob
	 */
	public static void draw(Ui ui, int x, int y, float position, float hover, float grip) {
		float lit = Mth.clamp(position, 0f, 1f);
		int track = Ui.mix(Ui.mix(ui.palette.trackOff(), ui.accent, lit), 0xFFFFFFFF, 0.10f * hover);
		ui.rounded(x, y, TRACK_WIDTH, TRACK_HEIGHT, Radius.pill(TRACK_HEIGHT), track);
		if (ui.palette.glass()) {
			ui.ring(x, y, TRACK_WIDTH, TRACK_HEIGHT, Radius.pill(TRACK_HEIGHT), 1, Ui.GLASS_RIM);
		}

		// while pressed, the knob stretches towards the middle of the track while the end it rests at stays in place
		float width = KNOB + STRETCH * grip;
		float travel = TRACK_WIDTH - 4 - width;
		float left = x + 2 + Mth.clamp(position, -0.12f, 1.12f) * travel;
		ui.roundedSmooth(left - 4, y - 3, width + 8, TRACK_HEIGHT + 6, Radius.pill(TRACK_HEIGHT + 6), Ui.withOpacity(ui.accent, 0.22f * hover)); // hover glow
		ui.roundedSmooth(left, y + 3, width, KNOB, Radius.pill(KNOB), Ui.KNOB_SHADOW); // drop shadow, slightly lower
		ui.roundedSmooth(left, y + 2, width, KNOB, Radius.pill(KNOB), ui.palette.knob());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && contains(mouseX, mouseY)) {
			setting.set(!setting.get());
			pressed = true;
			UiSounds.toggle(setting.get());
			onChanged.run();
			return true;
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		pressed = false;
	}
}
