package dev.tim1337.lumio.ui.title;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.features.visual.screen.MenusFeature.Season;
import dev.tim1337.lumio.features.visual.screen.MenusFeature;
import dev.tim1337.lumio.render.MenuWorld;
import dev.tim1337.lumio.render.RoundedImage;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.UiSounds;
import dev.tim1337.lumio.util.SeasonDates;
import dev.tim1337.lumio.util.TimeOfDay;

import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

/**
 * The main menu's season and time menu, opened by the season button. The time of day, shown large with a slider over the
 * sky's colors of a day and a round clock button to follow the computer's clock, filled while it does (hovering it explains it); moving the
 * slider stops following the clock. Below, the four seasons as pictures of the scene, the ring on the one on screen. Picking a season shows it
 * and keeps it; picking the real one follows the date again. Sizes are in the main menu's layout units, the pixels of its mockup.
 */
public final class SeasonPopover {
	private static final float WIDTH = 320f;
	private static final float PAD = 16f;
	private static final float RADIUS = Radius.WINDOW;
	private static final float HEADER = 22f;
	private static final float GAP = 16f;
	// the time card: the time with its switch, the slider and its labels
	private static final float CARD_PAD = 12f;
	private static final float TIME_ROW = 38f;
	private static final float TRACK_GAP = 10f;
	private static final float TRACK_HEIGHT = 16f;
	private static final float KNOB = 26f;
	private static final float LABELS_GAP = 10f;
	private static final float LABELS_ROW = 14f;
	private static final float CARD = CARD_PAD + TIME_ROW + TRACK_GAP + TRACK_HEIGHT + LABELS_GAP + LABELS_ROW + CARD_PAD;
	private static final float SEASON_ROW = 18f;
	/** The "Season" row sits a little closer to its pictures than the parts of the menu are to each other. */
	private static final float SEASON_ROW_GAP = 10f;
	private static final float TILE_GAP = 10f;
	// between the two rows of pictures
	private static final float TILE_ROW_GAP = 12f;
	private static final float PICTURE_HEIGHT = 78f;
	private static final float LABEL_ROW = 24f;
	private static final float TILES = 2 * (PICTURE_HEIGHT + LABEL_ROW) + TILE_ROW_GAP;
	private static final float HEIGHT = PAD + HEADER + GAP + CARD + GAP + SEASON_ROW + SEASON_ROW_GAP + TILES + PAD;
	private static final float PICTURE_RADIUS = 12f;
	/** The round button that follows the clock, and its clock icon. */
	private static final float FOLLOW = 30f;
	private static final int FOLLOW_ICON = 16;
	private static final float TIP_WIDTH = 220f;
	private static final float TIP_PAD_X = 10f;
	private static final float TIP_PAD_Y = 8f;
	private static final float TIP_LINE = 17.4f;
	private static final float TEXT = 1f / 9.6f;
	private static final int STEP_MINUTES = 5;
	private static final int FINE_STEP_MINUTES = 1;
	private static final String[] NAMES = {"Winter", "Spring", "Summer", "Autumn"};
	// the order of the tiles: spring, summer, autumn, winter
	private static final int[] ORDER = {SeasonDates.SPRING, SeasonDates.SUMMER, SeasonDates.AUTUMN, SeasonDates.WINTER};
	private static final String[] HOUR_LABELS = {"0:00", "6:00", "12:00", "18:00", "24:00"};
	// the sky's colors over the day along the slider: night blue, dawn orange, day light blue, dusk orange, night
	private static final float[] SKY_STOPS = {0f, 0.16f, 0.25f, 0.34f, 0.50f, 0.66f, 0.75f, 0.84f, 1f};
	private static final int[] SKY_COLORS = {0xFF0C1238, 0xFF2C2F6E, 0xFFF29A6B, 0xFF8EC8FF, 0xFFBFE3FF, 0xFF8EC8FF, 0xFFF08A5D, 0xFF43397A, 0xFF0C1238};
	private static final String TIME_TIP = "Follow the time: the time of day follows your computer's clock. Moving the slider turns this off.";

	private final Spring openSpring = Spring.smooth(Spring.SNAPPY);
	private final Spring followFill = Spring.smooth(Spring.SNAPPY);
	private final Spring followLight = Spring.smooth(Spring.SNAPPY);
	private final Spring[] tileLight = {Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY)};
	private boolean open;
	private float shown;
	// where the menu is, as last drawn
	private float x;
	private float y;
	// the slider: whether it is being dragged, and whether the arrow keys move it
	private boolean dragging;
	private boolean sliderFocused;
	// the time shown, made into text once a minute
	private int shownMinute = -1;
	private String clockText = "";
	private String partText = "";

	public boolean isOpen() {
		return open;
	}

	/** @return whether the menu is open or still closing, so the button stays highlighted meanwhile */
	public float amount() {
		return shown;
	}

	public void toggle() {
		open = !open;
		sliderFocused = false;
		dragging = false;
		UiSounds.click();
	}

	public void close() {
		open = false;
		sliderFocused = false;
		dragging = false;
	}

	// ------------------------------------------------------------------------------------------------------------ layout

	private float cardTop() {
		return y + PAD + HEADER + GAP;
	}

	private float trackY() {
		return cardTop() + CARD_PAD + TIME_ROW + TRACK_GAP;
	}

	private float trackLeft() {
		return x + PAD + CARD_PAD;
	}

	private float trackWidth() {
		return WIDTH - 2 * PAD - 2 * CARD_PAD;
	}

	private float seasonRowTop() {
		return cardTop() + CARD + GAP;
	}

	private float tilesTop() {
		return seasonRowTop() + SEASON_ROW + SEASON_ROW_GAP;
	}

	private float tileWidth() {
		return (WIDTH - 2 * PAD - TILE_GAP) / 2f;
	}

	/** The left edge of the round button that follows the clock, at the right of the time's row. */
	private float followLeft() {
		return x + WIDTH - PAD - CARD_PAD - FOLLOW;
	}

	private float followTop() {
		return cardTop() + CARD_PAD;
	}

	// ------------------------------------------------------------------------------------------------------------ drawing

	/** Draws the menu below a point at its top right corner, growing out of it. */
	public void draw(Ui ui, float right, float top, int mouseX, int mouseY) {
		shown = Mth.clamp(openSpring.follow(ui, open ? 1f : 0f), 0f, 1f);
		if (shown <= 0.01f) {
			return;
		}
		MenuWorld.prepareThumbnails();
		x = right - WIDTH;
		y = top;
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		// grows from the button's corner, like the menus' other popovers
		pose.translate(right, top);
		pose.scale(0.9f + 0.1f * shown, 0.8f + 0.2f * shown);
		pose.translate(-right, -top);
		float alpha = shown;
		ui.shadow(Math.round(x), Math.round(y), Math.round(WIDTH), Math.round(HEIGHT), RADIUS, 14, 0.45f * alpha);
		HudStyle.panel(ui, Math.round(x), Math.round(y), Math.round(WIDTH), Math.round(HEIGHT), RADIUS, Math.max(0.82f, HudStyle.opacity()) * alpha,
			UiGlow.Target.TITLE, alpha);
		MenusFeature menus = ModFeatures.MENUS;
		int text = Ui.withOpacity(HudStyle.text(), alpha);
		int secondary = Ui.withOpacity(HudStyle.secondary(), alpha);
		ui.textAtScaledBold("Season and time", x + PAD, y + PAD + 3f, 15f * TEXT, text);

		// the time: shown large with its part of the day, the button that follows the clock, and the slider
		float cardTop = cardTop();
		ui.roundedSmooth(x + PAD, cardTop, WIDTH - 2 * PAD, CARD, 14f, Ui.withOpacity(0xFFFFFFFF, 0.05f * alpha));
		float hour = menus.worldHour();
		updateClock(hour);
		float timeY = cardTop + CARD_PAD;
		// the time and its part of the day keep clear of the button: a long part of the day ("Afternoon") makes both a little smaller
		float room = followLeft() - 10f - (x + PAD + CARD_PAD);
		float clockScale = 28f * TEXT;
		float partScale = 13f * TEXT;
		float needed = ui.widthScaled(clockText, clockScale) * 1.04f + 8f + ui.widthScaled(partText, partScale);
		float fit = Math.min(1f, Math.max(0.75f, room / needed));
		clockScale *= fit;
		partScale *= fit;
		ui.textAtScaledBold(clockText, x + PAD + CARD_PAD, timeY + 2f + 14f * (1f - fit), clockScale, text);
		ui.textAtScaled(partText, x + PAD + CARD_PAD + ui.widthScaled(clockText, clockScale) * 1.04f + 8f * fit, timeY + 14f + 4f * (1f - fit), partScale, secondary);
		boolean timeTip = drawFollow(ui, menus.followsTime(), mouseX, mouseY, alpha);
		drawSlider(ui, hour, mouseX, mouseY, alpha, secondary);

		// the season: the four pictures
		ui.textAtScaled("Season", x + PAD, seasonRowTop() + 2f, 13.5f * TEXT, Ui.withOpacity(text, 0.8f));
		drawTiles(ui, menus.worldSeason().ordinal(), mouseX, mouseY, alpha, text, secondary);

		if (timeTip) {
			drawTip(ui, TIME_TIP, followTop() + FOLLOW + 6f, alpha);
		}
		pose.popMatrix();
	}

	private void updateClock(float hour) {
		int minute = TimeOfDay.minutes(hour);
		if (minute != shownMinute) {
			shownMinute = minute;
			clockText = TimeOfDay.clock(minute);
			partText = TimeOfDay.partOfDay(minute);
		}
	}

	/**
	 * Draws the round button that follows the clock: a clock, filled with the accent color while the time follows the computer's clock.
	 *
	 * @return whether it is hovered, so its tip is drawn on top
	 */
	private boolean drawFollow(Ui ui, boolean on, int mouseX, int mouseY, float alpha) {
		float left = followLeft();
		float top = followTop();
		boolean over = mouseX >= left && mouseX < left + FOLLOW && mouseY >= top && mouseY < top + FOLLOW;
		float fill = Mth.clamp(followFill.follow(ui, on ? 1f : 0f), 0f, 1f);
		float light = Mth.clamp(followLight.follow(ui, over ? 1f : 0f), 0f, 1f);
		int background = Ui.mix(Ui.withOpacity(0xFFFFFFFF, 0.12f + 0.08f * light), Ui.withOpacity(ui.accent, 1f), fill);
		ui.roundedSmooth(left, top, FOLLOW, FOLLOW, FOLLOW / 2f, Ui.withOpacity(background, alpha));
		int icon = Ui.withOpacity(0xFFFFFFFF, (0.7f + 0.3f * Math.max(fill, light)) * alpha);
		ui.icon("clock", Math.round(left + (FOLLOW - FOLLOW_ICON) / 2f), Math.round(top + (FOLLOW - FOLLOW_ICON) / 2f), FOLLOW_ICON, icon);
		return over;
	}

	private void drawSlider(Ui ui, float hour, int mouseX, int mouseY, float alpha, int secondary) {
		float left = trackLeft();
		float width = trackWidth();
		float top = trackY();
		int trackX = Math.round(left);
		int trackTop = Math.round(top);
		int trackW = Math.round(width);
		int trackH = Math.round(TRACK_HEIGHT);
		// the ends are rounded in the night color the gradient starts and ends with; the gradient runs between them
		float cap = TRACK_HEIGHT / 2f;
		ui.roundedSmooth(trackX, trackTop, trackW, trackH, cap, Ui.withOpacity(SKY_COLORS[0], alpha));
		for (int i = 0; i + 1 < SKY_STOPS.length; i++) {
			int from = Math.round(Math.max(cap, SKY_STOPS[i] * width));
			int to = Math.round(Math.min(width - cap, SKY_STOPS[i + 1] * width));
			if (to > from) {
				ui.horizontalGradient(trackX + from, trackTop, to - from, trackH, Ui.withOpacity(colorAt(from / width), alpha),
					Ui.withOpacity(colorAt(to / width), alpha));
			}
		}
		ui.ring(trackX, trackTop, trackW, trackH, cap, 1, Ui.withOpacity(0xFFFFFFFF, 0.14f * alpha));
		// the knob, with a soft ring that is stronger while the slider has the keys
		float knobX = left + Mth.clamp(hour / 24f, 0f, 1f) * width;
		float knobY = top + TRACK_HEIGHT / 2f;
		boolean over = Math.abs(mouseX - knobX) < KNOB && Math.abs(mouseY - knobY) < KNOB;
		ui.shadow(Math.round(knobX - KNOB / 2f), Math.round(knobY - KNOB / 2f), Math.round(KNOB), Math.round(KNOB), KNOB / 2f, 6, 0.45f * alpha);
		float ring = sliderFocused || dragging ? 0.45f : over ? 0.35f : 0.25f;
		ui.roundedSmooth(knobX - KNOB / 2f - 3f, knobY - KNOB / 2f - 3f, KNOB + 6f, KNOB + 6f, KNOB / 2f + 3f, Ui.withOpacity(0xFFFFFFFF, ring * alpha));
		ui.roundedSmooth(knobX - KNOB / 2f, knobY - KNOB / 2f, KNOB, KNOB, KNOB / 2f, Ui.withOpacity(0xFFFFFFFF, alpha));
		// the hour labels under it: the first and last at the ends, the others centered on their place
		float labelScale = 11.5f * TEXT;
		float labelY = top + TRACK_HEIGHT + LABELS_GAP;
		for (int i = 0; i < HOUR_LABELS.length; i++) {
			String label = HOUR_LABELS[i];
			float labelWidth = ui.widthScaled(label, labelScale);
			float at = left + width * i / (HOUR_LABELS.length - 1f);
			float labelX = i == 0 ? at : i == HOUR_LABELS.length - 1 ? at - labelWidth : at - labelWidth / 2f;
			ui.textAtScaled(label, labelX, labelY, labelScale, Ui.withOpacity(secondary, 0.75f));
		}
	}

	/** @return the sky's color at a place along the slider, from 0 to 1 */
	private static int colorAt(float t) {
		for (int i = 0; i + 1 < SKY_STOPS.length; i++) {
			if (t <= SKY_STOPS[i + 1]) {
				float local = (t - SKY_STOPS[i]) / (SKY_STOPS[i + 1] - SKY_STOPS[i]);
				return Ui.mix(SKY_COLORS[i], SKY_COLORS[i + 1], Mth.clamp(local, 0f, 1f));
			}
		}
		return SKY_COLORS[SKY_COLORS.length - 1];
	}

	private void drawTiles(Ui ui, int onScreen, int mouseX, int mouseY, float alpha, int text, int secondary) {
		float top = tilesTop();
		float tileWidth = tileWidth();
		for (int i = 0; i < ORDER.length; i++) {
			int season = ORDER[i];
			float tileX = x + PAD + (i % 2) * (tileWidth + TILE_GAP);
			float tileY = top + (i / 2) * (PICTURE_HEIGHT + LABEL_ROW + TILE_ROW_GAP);
			boolean over = mouseX >= tileX && mouseX < tileX + tileWidth && mouseY >= tileY && mouseY < tileY + PICTURE_HEIGHT + LABEL_ROW;
			float light = Mth.clamp(tileLight[i].follow(ui, over ? 1f : 0f), 0f, 1f);
			var picture = MenuWorld.thumbnail(season);
			int px = Math.round(tileX);
			int py = Math.round(tileY);
			int pw = Math.round(tileWidth);
			int ph = Math.round(PICTURE_HEIGHT);
			if (picture != null) {
				RoundedImage.draw(ui.graphics, picture, px, py, pw, ph, PICTURE_RADIUS, 0.1f, 1f, 0.9f, 0f, Ui.withOpacity(0xFFFFFFFF, alpha));
			} else {
				ui.roundedSmooth(px, py, pw, ph, PICTURE_RADIUS, Ui.withOpacity(0xFFFFFFFF, 0.08f * alpha));
			}
			if (light > 0.01f) {
				ui.roundedSmooth(px, py, pw, ph, PICTURE_RADIUS, Ui.withOpacity(0xFFFFFFFF, 0.10f * light * alpha));
			}
			boolean shownNow = season == onScreen;
			float nameScale = 13f * TEXT;
			float nameY = tileY + PICTURE_HEIGHT + 7f;
			if (shownNow) {
				ui.ring(px - 2, py - 2, pw + 4, ph + 4, PICTURE_RADIUS + 2f, 2, Ui.withOpacity(ui.accent, alpha));
				ui.textAtScaledBold(NAMES[season], tileX + 2f, nameY, nameScale, text);
			} else {
				ui.textAtScaled(NAMES[season], tileX + 2f, nameY, nameScale, secondary);
			}
		}
	}

	/** A tip under the button that follows the clock, in the menu's tooltip style: dark in every theme, with a thin light edge. */
	private void drawTip(Ui ui, String tip, float top, float alpha) {
		float scale = 12f * TEXT;
		List<String> lines = ui.wrap(tip, Math.round((TIP_WIDTH - 2 * TIP_PAD_X) / scale), 6);
		float height = 2 * TIP_PAD_Y + lines.size() * TIP_LINE - (TIP_LINE - 12f * 1.15f);
		float left = followLeft() + FOLLOW + 6f - TIP_WIDTH;
		int bx = Math.round(left);
		int by = Math.round(top);
		int bw = Math.round(TIP_WIDTH);
		int bh = Math.round(height);
		ui.shadow(bx, by, bw, bh, 10f, 10, 0.5f * alpha);
		ui.roundedSmooth(bx, by, bw, bh, 10f, Ui.withOpacity(0xF2161618, alpha));
		ui.ring(bx, by, bw, bh, 10f, 1, Ui.withOpacity(0x40FFFFFF, alpha));
		int color = Ui.withOpacity(MacTheme.DARK.palette().text(), 0.9f * alpha);
		for (int i = 0; i < lines.size(); i++) {
			ui.textAtScaled(lines.get(i), left + TIP_PAD_X, top + TIP_PAD_Y + i * TIP_LINE, scale, color);
		}
	}

	// ------------------------------------------------------------------------------------------------------------ input

	/** @return whether the menu took the click: inside it, or anywhere while it is open, which closes it */
	public boolean click(double mouseX, double mouseY, boolean fine) {
		if (!open) {
			return false;
		}
		if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + HEIGHT) {
			close();
			UiSounds.play(UiSounds.Cue.CLOSE);
			return true;
		}
		MenusFeature menus = ModFeatures.MENUS;
		sliderFocused = false;
		if (mouseX >= followLeft() && mouseX < followLeft() + FOLLOW && mouseY >= followTop() && mouseY < followTop() + FOLLOW) {
			menus.setFollowsTime(!menus.followsTime());
			UiSounds.toggle(menus.followsTime());
			return true;
		}
		float top = trackY();
		if (mouseX >= trackLeft() - KNOB / 2f && mouseX <= trackLeft() + trackWidth() + KNOB / 2f && mouseY >= top - KNOB / 2f
			&& mouseY <= top + TRACK_HEIGHT + KNOB / 2f) {
			dragging = true;
			sliderFocused = true;
			slideTo(mouseX, fine);
			return true;
		}
		float tilesTop = tilesTop();
		float tileWidth = tileWidth();
		for (int i = 0; i < ORDER.length; i++) {
			float tileX = x + PAD + (i % 2) * (tileWidth + TILE_GAP);
			float tileY = tilesTop + (i / 2) * (PICTURE_HEIGHT + LABEL_ROW + TILE_ROW_GAP);
			if (mouseX >= tileX && mouseX < tileX + tileWidth && mouseY >= tileY && mouseY < tileY + PICTURE_HEIGHT + LABEL_ROW) {
				menus.setWorldSeason(Season.values()[ORDER[i]]);
				UiSounds.click();
				return true;
			}
		}
		return true;
	}


	/** Moves the slider while it is dragged. @return whether it took the drag */
	public boolean drag(double mouseX, boolean fine) {
		if (!dragging) {
			return false;
		}
		slideTo(mouseX, fine);
		return true;
	}

	public boolean release() {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	private void slideTo(double mouseX, boolean fine) {
		float hour = (float) ((mouseX - trackLeft()) / trackWidth() * 24.0);
		ModFeatures.MENUS.setWorldHour(TimeOfDay.snap(hour, fine ? FINE_STEP_MINUTES : STEP_MINUTES));
	}

	/** The arrow keys move the slider once it was clicked: 5 minutes, or 1 with Shift. @return whether the key was taken */
	public boolean key(int key, boolean fine) {
		if (!open || !sliderFocused) {
			return false;
		}
		int direction = key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_UP ? 1 : key == InputConstants.KEY_LEFT || key == InputConstants.KEY_DOWN ? -1 : 0;
		if (direction == 0) {
			return false;
		}
		int step = fine ? FINE_STEP_MINUTES : STEP_MINUTES;
		float hour = ModFeatures.MENUS.worldHour();
		ModFeatures.MENUS.setWorldHour(TimeOfDay.snap(hour + direction * step / 60f, step));
		return true;
	}
}
