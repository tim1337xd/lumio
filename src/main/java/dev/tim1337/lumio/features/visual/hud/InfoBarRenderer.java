package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.features.music.MusicFeature;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Draws the info bar in its style. Segments slide in and out with the box resizing smoothly, warning colors fade, and the server speed pulses
 * once when it drops. Everything moves on real elapsed time with a clamped step, and texts are measured only when they change; nothing is
 * allocated per frame.
 */
final class InfoBarRenderer {
	// Sizes in GUI pixels at size 1, from the mockups at about 0.63 of their pixels.
	private static final int HEIGHT = 24;
	private static final float PAD = 9f;
	/** Between two segments, with a thin divider in its middle. */
	private static final float GAP = 8f;
	private static final float DIVIDER_HEIGHT = 10f;
	private static final float BOX_RADIUS = 6f;
	private static final int ICON = 8;
	private static final float ICON_GAP = 4f;
	/** The value's top above the middle of the bar. */
	private static final float TEXT_RISE = 4f;
	private static final float UNIT_SCALE = 0.72f;
	private static final float UNIT_GAP = 3f;
	private static final int COVER = 13;
	private static final float COVER_GAP = 5f;
	private static final float SONG_MAX = 96f;
	/** With the lyrics in the bar, the song segment keeps its set width while music plays, lines or not; on a narrow screen no less than this. */
	private static final float SONG_LEAST = 60f;
	private static final float SONG_BAR = 1.5f;
	private static final float SONG_BAR_GAP = 1.2f;
	private static final float SONG_BAR_HEIGHT = 7.5f;
	private static final float CHIP_HEIGHT = 20f;
	private static final float CHIP_PAD = 7f;
	private static final float CHIP_GAP = 5f;
	private static final float MINIMAL_GAP = 12f;
	/** The haze behind the minimal bar: how dark at its middle, and how far past a segment it fades out. */
	private static final float HAZE = 0.55f;
	private static final float HAZE_SIDE = 28f;
	private static final float HAZE_HEIGHT = 6f;
	private static final float STACK_WIDTH = 132f;
	private static final float STACK_ROW = 16f;
	private static final float STACK_SONG_ROW = 18f;
	private static final float STACK_PAD_X = 8f;
	private static final float STACK_PAD_Y = 5f;
	private static final float STACK_ICON_COLUMN = 10f;
	private static final float STACK_LABEL_GAP = 6f;
	private static final float STACK_DIVIDER = 5f;
	private static final int STACK_COVER = 14;
	private static final float STACK_LABEL_SCALE = 0.9f;
	/** Margin the bar keeps from the screen edges. */
	private static final float SCREEN_MARGIN = 4f;
	private static final float PREVIEW_MARGIN = 10f;

	/** Warning colors fade over this time; the server speed pulse is one soft swell this long. */
	private static final float COLOR_SECONDS = 0.25f;
	private static final float PULSE_SECONDS = 1.2f;
	private static final float SONG_FADE_SECONDS = 0.25f;
	/** A changed number rolls in: its changed digits come up from below while the old ones leave upwards, over this time and distance. */
	private static final float ROLL_SECONDS = 0.25f;
	private static final float ROLL_DISTANCE = 4f;
	/** The top of the frame rate line follows the highest value slowly, so the line does not jump. */
	private static final float LINE_SCALE_RATE = 2f;
	private static final float DIVIDER_ALPHA = 0.14f;
	private static final float UNIT_ALPHA = 0.5f;
	private static final float ICON_ALPHA = 0.7f;
	private static final float LABEL_ALPHA = 0.62f;
	private static final int SHADOW = 0x8C000000;

	private static final int SONG = InfoBarSegment.SONG.ordinal();
	/** Single characters as strings, so numbers can be drawn digit by digit in fixed cells without building strings. */
	private static final String[] CHARS = new String[128];

	static {
		for (char c = 0; c < CHARS.length; c++) {
			CHARS[c] = String.valueOf(c);
		}
	}

	private final InfoBarFeature bar;
	private final InfoBarValues values;
	private final AnimClock clock = new AnimClock();
	private final Spring[] presence = new Spring[InfoBarSegment.MOST];
	private final Spring[] width = new Spring[InfoBarSegment.MOST];
	/** Shown warning state per segment, from 0 (fine) to 2 (bad), moving towards the value's state over {@link #COLOR_SECONDS}. */
	private final float[] shownLevel = new float[InfoBarSegment.MOST];
	/** Measured per segment: its content width, the value's width with digits in equal cells, and where each character of the value goes. */
	private final float[] contentWidth = new float[InfoBarSegment.MOST];
	private final float[] valueWidth = new float[InfoBarSegment.MOST];
	private final float[] unitWidth = new float[InfoBarSegment.MOST];
	private final float[][] charX = new float[InfoBarSegment.MOST][];
	/** Per number: the text it had before its last change, where its characters were, and how far the change has rolled in (1 when done). */
	private final String[] rolledFrom = new String[InfoBarSegment.MOST];
	private final float[][] rolledFromX = new float[InfoBarSegment.MOST][];
	private final float[] roll = new float[InfoBarSegment.MOST];
	private final String[] measuredText = new String[InfoBarSegment.MOST];
	/** Texts that are too long, shortened to fit, per segment (the area, the song). */
	private final String[] fitted = new String[InfoBarSegment.MOST];
	private final String[] lowerUnit = new String[InfoBarSegment.MOST];
	/** The segments in the order of the list, and whether each is switched on. */
	private final int[] order = new int[InfoBarSegment.MOST];
	private final boolean[] on = new boolean[InfoBarSegment.MOST];
	private int orderCount;
	private int orderVersion = -1;
	private int measuredVersion = -1;
	private float measuredZoom = -1f;
	private float measuredSongWidth = -1f;
	/** How much the song segment gives up so the bar fits on the screen; the other segments never shrink. */
	private float songCut;
	/** Where the song segment was drawn last, from its left to its right edge, in the bar's units; NaN when it was not. */
	private float songLeft = Float.NaN;
	private float songRight = Float.NaN;
	private int measuredOptions = -1;
	private InfoBarFeature.Style measuredStyle;
	private float digitWidth;
	private float lineTop = 60f;
	private String songPrevious = "";
	private String songShown = "";
	private float songFade = 1f;
	private int lastTpsLevel;
	private float pulse = 1f;
	private final int[] bounds = new int[4];
	private float radius = BOX_RADIUS;

	InfoBarRenderer(InfoBarFeature bar, InfoBarValues values) {
		this.bar = bar;
		this.values = values;
		for (int i = 0; i < InfoBarSegment.MOST; i++) {
			presence[i] = Spring.smooth(Spring.DEFAULT);
			width[i] = Spring.smooth(Spring.DEFAULT);
			fitted[i] = "";
			lowerUnit[i] = "";
			rolledFrom[i] = "";
			measuredText[i] = "";
			roll[i] = 1f;
		}
	}

	/** @return the x on screen of the song segment's right edge as last drawn in game, or NaN when it was not drawn */
	float songEdgeX() {
		return Float.isNaN(songRight) ? Float.NaN : bounds[0] + songRight * bar.scale();
	}

	/** @return the width the song segment would have with its right edge at an x on screen, in the bar's own GUI pixels */
	float songWidthTo(float x) {
		return Float.isNaN(songLeft) ? 0f : (x - bounds[0]) / Math.max(0.01f, bar.scale()) - songLeft;
	}

	int[] bounds() {
		return bounds;
	}

	float radius() {
		return radius;
	}

	// ----------------------------------------------------------------------------------------------------------- frame

	/** Draws the bar with its top centered at the point, at its size, kept on screen. @param hud whether this is the bar in game, which sets its bounds */
	void render(GuiGraphicsExtractor graphics, float centerX, float top, boolean hud) {
		float s = bar.scale();
		float zoom = Ui.zoom();
		Ui.setZoom(zoom * s);
		try {
			Ui ui = new Ui(graphics, Minecraft.getInstance().font, HudStyle.palette(), SystemColors.GREEN, 0f, false);
			float[] size = advance(ui);
			float w = size[0];
			float h = size[1];
			if (w <= 0.5f) {
				if (hud) {
					bounds[2] = 0;
					bounds[3] = 0;
				}
				return;
			}
			// kept on screen, also when its width changes in a corner
			float left = Mth.clamp(centerX - w * s / 2f, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, graphics.guiWidth() - SCREEN_MARGIN - w * s));
			if (hud) {
				bounds[0] = Math.round(left);
				bounds[1] = Math.round(top);
				bounds[2] = Math.round(w * s);
				bounds[3] = Math.round(h * s);
				radius = radiusOf(bar.style(), h) * s;
			}
			var pose = graphics.pose();
			pose.pushMatrix();
			try {
				pose.translate(left, top);
				pose.scale(s, s);
				draw(ui, w, h);
			} finally {
				pose.popMatrix();
			}
		} finally {
			Ui.setZoom(zoom);
		}
	}

	/** Draws the bar centered in a rectangle of the menu, at its size in game or smaller if it does not fit. */
	void renderFitted(GuiGraphicsExtractor graphics, int x, int y, int areaWidth, int areaHeight) {
		float zoom = Ui.zoom();
		Ui ui = new Ui(graphics, Minecraft.getInstance().font, HudStyle.palette(), SystemColors.GREEN, 0f, false);
		float[] size = advance(ui);
		float w = size[0];
		float h = size[1];
		if (w <= 0.5f) {
			return;
		}
		float s = Math.min(bar.scale(), Math.min((areaWidth - 2 * PREVIEW_MARGIN) / w, (areaHeight - 2 * PREVIEW_MARGIN) / h));
		var pose = graphics.pose();
		pose.pushMatrix();
		Ui.setZoom(zoom * s);
		try {
			pose.translate(x + (areaWidth - w * s) / 2f, y + (areaHeight - h * s) / 2f);
			pose.scale(s, s);
			draw(ui, w, h);
		} finally {
			Ui.setZoom(zoom);
			pose.popMatrix();
		}
	}

	private final float[] size = new float[2];

	/** Advances the animations by the time since the last frame and measures what changed. @return the bar's width and height */
	private float[] advance(Ui ui) {
		ModFeatures.MUSIC.tickHud();
		float dt = clock.tick();
		boolean animated = ModFeatures.MENU.animations();
		readOrder();
		measure(ui);
		InfoBarFeature.Style style = bar.style();
		songWidthNow(ui, style);
		songCut = songCut(style);
		for (int i = 0; i < InfoBarSegment.count(); i++) {
			float target = on[i] && values.available[i] ? 1f : 0f;
			if (animated) {
				presence[i].step(target, dt);
				width[i].step(shownWidth(i), dt);
				float wanted = values.level[i];
				float step = dt / COLOR_SECONDS;
				shownLevel[i] = shownLevel[i] < wanted ? Math.min(wanted, shownLevel[i] + step) : Math.max(wanted, shownLevel[i] - step);
			} else {
				presence[i].snap(target);
				width[i].snap(shownWidth(i));
				shownLevel[i] = values.level[i];
			}
		}
		// the server speed swells once when it drops below the orange mark
		int tpsLevel = values.level[InfoBarSegment.TPS.ordinal()];
		if (tpsLevel == InfoBarValues.BAD && lastTpsLevel != InfoBarValues.BAD && animated) {
			pulse = 0f;
		}
		lastTpsLevel = tpsLevel;
		pulse = animated ? Math.min(1f, pulse + dt / PULSE_SECONDS) : 1f;
		songFade = animated ? Math.min(1f, songFade + dt / SONG_FADE_SECONDS) : 1f;
		for (int i = 0; i < InfoBarSegment.count(); i++) {
			roll[i] = animated ? Math.min(1f, roll[i] + dt / ROLL_SECONDS) : 1f;
		}
		float maxFps = 0f;
		for (int k = 0; k < values.historyCount; k++) {
			maxFps = Math.max(maxFps, values.historyAt(k));
		}
		float wantedTop = Math.max(Math.max(maxFps, values.fpsNow()) * 1.2f, bar.fpsYellow() * 1.2f);
		lineTop += (wantedTop - lineTop) * (1f - (float) Math.exp(-dt * LINE_SCALE_RATE));

		if (style == InfoBarFeature.Style.STACKED) {
			float h = STACK_PAD_Y * 2;
			for (int k = 0; k < orderCount; k++) {
				int i = order[k];
				h += (i == SONG ? STACK_SONG_ROW + STACK_DIVIDER : STACK_ROW) * presence[i].value();
			}
			size[0] = anyShown() ? STACK_WIDTH : 0f;
			size[1] = h;
			return size;
		}
		float gap = style == InfoBarFeature.Style.CHIPS ? CHIP_GAP : style == InfoBarFeature.Style.MINIMAL ? MINIMAL_GAP : GAP;
		float pad = style == InfoBarFeature.Style.BOX || style == InfoBarFeature.Style.PILL ? PAD : 0f;
		float chipPad = style == InfoBarFeature.Style.CHIPS ? 2 * CHIP_PAD : 0f;
		float total = 0f;
		float shown = 0f;
		for (int k = 0; k < orderCount; k++) {
			int i = order[k];
			float p = presence[i].value();
			total += (width[i].value() + chipPad + gap) * p;
			shown += p;
		}
		size[0] = shown < 0.001f ? 0f : total - gap * Math.min(1f, shown) + 2 * pad;
		size[1] = style == InfoBarFeature.Style.CHIPS ? CHIP_HEIGHT : HEIGHT;
		return size;
	}

	/** @return the width a segment is drawn at: its content's, the song's less what it gives up on a narrow screen */
	private float shownWidth(int i) {
		return i == SONG ? Math.max(0f, contentWidth[i] - songCut) : contentWidth[i];
	}

	/**
	 * @return how much the song segment gives up so the bar fits on the screen: if the bar at its full width would be wider than the screen, the song
	 *         shrinks, never the other segments, and never below the least width
	 */
	private float songCut(InfoBarFeature.Style style) {
		if (style == InfoBarFeature.Style.STACKED || !on[SONG] || !values.available[SONG]) {
			return 0f;
		}
		float gap = style == InfoBarFeature.Style.CHIPS ? CHIP_GAP : style == InfoBarFeature.Style.MINIMAL ? MINIMAL_GAP : GAP;
		float pad = style == InfoBarFeature.Style.BOX || style == InfoBarFeature.Style.PILL ? PAD : 0f;
		float chipPad = style == InfoBarFeature.Style.CHIPS ? 2 * CHIP_PAD : 0f;
		float wanted = 2 * pad - gap;
		for (int k = 0; k < orderCount; k++) {
			int i = order[k];
			if (on[i] && values.available[i]) {
				wanted += contentWidth[i] + chipPad + gap;
			}
		}
		var window = Minecraft.getInstance().getWindow();
		float room = (window.getGuiScaledWidth() - 2 * SCREEN_MARGIN) / Math.max(0.01f, bar.scale());
		return Mth.clamp(wanted - room, 0f, Math.max(0f, contentWidth[SONG] - SONG_LEAST));
	}

	private boolean anyShown() {
		for (int k = 0; k < orderCount; k++) {
			if (presence[order[k]].value() > 0.001f) {
				return true;
			}
		}
		return false;
	}

	private static float radiusOf(InfoBarFeature.Style style, float height) {
		return switch (style) {
			case PILL, CHIPS -> height / 2f;
			case MINIMAL -> 4f;
			default -> BOX_RADIUS;
		};
	}

	/** Reads the order of the segments again after the list changed. */
	private void readOrder() {
		int version = bar.segmentsVersion();
		if (version == orderVersion) {
			return;
		}
		orderVersion = version;
		orderCount = 0;
		Arrays.fill(on, false);
		List<String> enabled = bar.enabledSegments();
		for (String id : enabled) {
			InfoBarSegment segment = InfoBarSegment.byId(id);
			if (segment != null) {
				on[segment.ordinal()] = true;
			}
		}
		// all segments in the list's order, so one switched off slides out where it was; the ones not listed (none) follow
		for (String id : bar.segmentRows()) {
			InfoBarSegment segment = InfoBarSegment.byId(id);
			if (segment != null && orderCount < InfoBarSegment.count()) {
				order[orderCount++] = segment.ordinal();
			}
		}
	}

	// ----------------------------------------------------------------------------------------------------------- measuring

	/** @return a number that changes with the options that change a segment's width */
	private int optionsKey() {
		return (bar.fpsLine() ? 1 : 0) | (bar.pingBars() ? 2 : 0) | (bar.pingNumber() ? 4 : 0) | (bar.facing() ? 8 : 0) | (bar.coordsWithY() ? 16 : 0)
			| (ModFeatures.MUSIC.barLyricsShown() ? 32 : 0);
	}

	private void measure(Ui ui) {
		InfoBarFeature.Style style = bar.style();
		int options = optionsKey();
		float zoom = Ui.zoom();
		if (values.textVersion == measuredVersion && zoom == measuredZoom && style == measuredStyle && options == measuredOptions
			&& bar.songWidth() == measuredSongWidth) {
			return;
		}
		measuredVersion = values.textVersion;
		measuredZoom = zoom;
		measuredStyle = style;
		measuredOptions = options;
		measuredSongWidth = bar.songWidth();
		digitWidth = 0f;
		for (char c = '0'; c <= '9'; c++) {
			digitWidth = Math.max(digitWidth, ui.boldWidthScaled(CHARS[c], 1f));
		}
		boolean minimal = style == InfoBarFeature.Style.MINIMAL;
		for (int i = 0; i < InfoBarSegment.count(); i++) {
			InfoBarSegment segment = InfoBarSegment.of(i);
			String text = values.text[i];
			String unit = values.unit[i];
			lowerUnit[i] = unit.toLowerCase(Locale.ROOT);
			unitWidth[i] = unit.isEmpty() ? 0f : ui.boldWidthScaled(minimal ? lowerUnit[i] : unit, UNIT_SCALE);
			if (tabular(segment)) {
				rememberRoll(i, text);
				charX[i] = layoutDigits(ui, text, i);
			} else if (segment == InfoBarSegment.SONG && ModFeatures.MUSIC.barLyricsShown()) {
				// the song and its lines share one fixed width, so the bar never changes size with them
				valueWidth[i] = style == InfoBarFeature.Style.STACKED ? stackedRoom(ui, segment) : barTextRoom(style);
			} else {
				float max = style == InfoBarFeature.Style.STACKED ? stackedRoom(ui, segment)
					: segment == InfoBarSegment.SONG ? SONG_MAX : segment.widest;
				String fit = fit(ui, text, max);
				if (segment == InfoBarSegment.SONG && !fit.equals(songShown)) {
					songPrevious = songShown;
					songShown = fit;
					songFade = songPrevious.isEmpty() ? 1f : 0f;
				}
				fitted[i] = fit;
				valueWidth[i] = ui.boldWidthScaled(fit, 1f);
			}
			contentWidth[i] = minimal ? minimalWidth(segment, i) : style == InfoBarFeature.Style.STACKED ? 0f : contentWidth(segment, i);
		}
	}

	/** @return the room of the song's text with the lyrics in the bar: the set width less the cover and the level bars beside it */
	private float barTextRoom(InfoBarFeature.Style style) {
		float fixed = bar.songWidth();
		return style == InfoBarFeature.Style.MINIMAL ? fixed : fixed - COVER - 2 * COVER_GAP - MusicFeature.levelBarsWidth(SONG_BAR, SONG_BAR_GAP);
	}

	/**
	 * With the lyrics in the bar, the song segment is as wide as the lyrics while a line is sung, and otherwise only as wide as the song: asked every
	 * frame, and the segment's width eases between the two like any other.
	 */
	private void songWidthNow(Ui ui, InfoBarFeature.Style style) {
		if (style == InfoBarFeature.Style.STACKED || !ModFeatures.MUSIC.barLyricsShown()) {
			return;
		}
		float full = barTextRoom(style);
		valueWidth[SONG] = ModFeatures.MUSIC.barLyrics().wantedRoom(ui, full);
		contentWidth[SONG] = style == InfoBarFeature.Style.MINIMAL ? minimalWidth(InfoBarSegment.SONG, SONG) : contentWidth(InfoBarSegment.SONG, SONG);
	}

	/** @return the room of the song's text as drawn: less what the segment gives up on a narrow screen, and while it grows or shrinks, what it has so far */
	private float songRoom() {
		float room = valueWidth[SONG] - songCut;
		if (bar.style() != InfoBarFeature.Style.STACKED && ModFeatures.MUSIC.barLyricsShown()) {
			room = Math.min(room, width[SONG].value() - (contentWidth[SONG] - valueWidth[SONG]));
		}
		return Math.max(0f, room);
	}

	/** @return the room for a text value in a row of the stacked style, beside its label (or beside the cover and bars for the song) */
	private static float stackedRoom(Ui ui, InfoBarSegment segment) {
		float inner = STACK_WIDTH - 2 * STACK_PAD_X;
		if (segment == InfoBarSegment.SONG) {
			return inner - STACK_COVER - STACK_LABEL_GAP - MusicFeature.levelBarsWidth(SONG_BAR, SONG_BAR_GAP) - STACK_LABEL_GAP;
		}
		float label = ui.widthScaled(segment.label, STACK_LABEL_SCALE);
		// the value is drawn smaller in the rows, so it gets the room at its own size
		return (inner - STACK_ICON_COLUMN - 2 * STACK_LABEL_GAP - label) / STACK_LABEL_SCALE;
	}

	/** @return whether the segment's value is a number drawn with its digits in equal cells, so it does not shift as digits change */
	private static boolean tabular(InfoBarSegment segment) {
		return switch (segment.kind) {
			case CLOCK, FPS, PING, TPS, COORDS, MEMORY, CPS -> true;
			default -> segment.digits;
		};
	}

	/**
	 * Before a number is laid out again: if its text changed, keeps the old text and where its characters were, so the change can roll in. The
	 * arrays are made once per segment and reused.
	 */
	private void rememberRoll(int i, String text) {
		String before = measuredText[i];
		measuredText[i] = text;
		if (before.isEmpty() || before.equals(text) || charX[i] == null || !ModFeatures.MENU.animations()) {
			return;
		}
		if (rolledFromX[i] == null || rolledFromX[i].length < charX[i].length) {
			rolledFromX[i] = new float[charX[i].length];
		}
		System.arraycopy(charX[i], 0, rolledFromX[i], 0, charX[i].length);
		rolledFrom[i] = before;
		roll[i] = 0f;
	}

	/** @return the x of every character of a number, digits in cells of the widest digit; sets its width */
	private float[] layoutDigits(Ui ui, String text, int i) {
		float[] at = charX[i] != null && charX[i].length >= text.length() ? charX[i] : new float[Math.max(8, text.length())];
		float x = 0f;
		for (int c = 0; c < text.length(); c++) {
			char ch = text.charAt(c);
			at[c] = x;
			x += Character.isDigit(ch) ? digitWidth : ch < CHARS.length ? ui.boldWidthScaled(CHARS[ch], 1f) : 0f;
		}
		valueWidth[i] = x;
		return at;
	}

	private static String fit(Ui ui, String text, float max) {
		if (ui.boldWidthScaled(text, 1f) <= max) {
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && ui.boldWidthScaled(cut + "...", 1f) > max) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.stripTrailing() + "...";
	}

	private float contentWidth(InfoBarSegment segment, int i) {
		float unit = unitWidth[i] > 0f ? UNIT_GAP + unitWidth[i] : 0f;
		return switch (segment.kind) {
			case FPS -> valueWidth[i] + unit + (bar.fpsLine() ? InfoBarSigns.LINE_GAP + InfoBarSigns.LINE_WIDTH : 0f);
			case PING -> (bar.pingBars() ? InfoBarSigns.SIGNALS_WIDTH : 0f) + (bar.pingBars() && bar.pingNumber() ? ICON_GAP : 0f)
				+ (bar.pingNumber() ? valueWidth[i] + unit : 0f);
			case TPS -> InfoBarSigns.DOT + ICON_GAP + valueWidth[i] + unit;
			case SONG -> COVER + COVER_GAP + valueWidth[i] + COVER_GAP + MusicFeature.levelBarsWidth(SONG_BAR, SONG_BAR_GAP);
			case MEMORY, CPS -> valueWidth[i] + unit;
			default -> ICON + ICON_GAP + valueWidth[i] + unit;
		};
	}

	private float minimalWidth(InfoBarSegment segment, int i) {
		float unit = unitWidth[i] > 0f && (segment != InfoBarSegment.PING || bar.pingNumber()) ? UNIT_GAP + unitWidth[i] : 0f;
		return valueWidth[i] + unit;
	}

	// ----------------------------------------------------------------------------------------------------------- drawing

	private void draw(Ui ui, float w, float h) {
		songLeft = Float.NaN;
		songRight = Float.NaN;
		switch (bar.style()) {
			case STACKED -> drawStacked(ui, w, h);
			case CHIPS -> drawChips(ui, h);
			case MINIMAL -> drawMinimal(ui, w, h);
			default -> drawBox(ui, w, h);
		}
	}

	private void drawBox(Ui ui, float w, float h) {
		float r = radiusOf(bar.style(), h);
		if (bar.background()) {
			HudStyle.panel(ui, 0, 0, Math.round(w), Math.round(h), r, HudStyle.opacity(), bar.glow());
		}
		InfoBarSigns.pulse(ui, 0f, 0f, w, h, r, pulse);
		float middle = h / 2f;
		float cursor = PAD;
		float before = 0f;
		for (int k = 0; k < orderCount; k++) {
			int i = order[k];
			float p = presence[i].value();
			if (p <= 0.001f) {
				continue;
			}
			float slot = width[i].value();
			if (before > 0f) {
				// a thin divider in the middle of the gap, as visible as the fainter of its two neighbors
				float divider = Math.min(before, p);
				ui.roundedSmooth(cursor - GAP / 2f - 0.5f, middle - DIVIDER_HEIGHT / 2f, 1f, DIVIDER_HEIGHT, 0.5f,
					HudStyle.line(DIVIDER_ALPHA * divider));
			}
			if (i == SONG) {
				songLeft = cursor;
				songRight = cursor + slot * p;
			}
			drawClipped(ui, InfoBarSegment.of(i), i, cursor, middle, slot * p, p, h);
			cursor += (slot + GAP) * p;
			before = p;
		}
	}

	private void drawChips(Ui ui, float h) {
		float cursor = 0f;
		float r = h / 2f;
		for (int k = 0; k < orderCount; k++) {
			int i = order[k];
			float p = presence[i].value();
			if (p <= 0.001f) {
				continue;
			}
			float chip = (width[i].value() + 2 * CHIP_PAD) * p;
			if (bar.background()) {
				HudStyle.panel(ui, Math.round(cursor), 0, Math.max(1, Math.round(chip)), Math.round(h), r, HudStyle.opacity() * p, bar.glow());
			}
			if (i == InfoBarSegment.TPS.ordinal()) {
				InfoBarSigns.pulse(ui, cursor, 0f, chip, h, r, pulse);
			}
			if (i == SONG) {
				songLeft = cursor + CHIP_PAD * p;
				songRight = songLeft + width[i].value() * p;
			}
			drawClipped(ui, InfoBarSegment.of(i), i, cursor + CHIP_PAD * p, h / 2f, width[i].value() * p, p, h);
			cursor += chip + CHIP_GAP * p;
		}
	}

	private void drawMinimal(Ui ui, float w, float h) {
		float middle = h / 2f;
		drawHaze(ui, h, middle);
		float cursor = 0f;
		float before = 0f;
		for (int k = 0; k < orderCount; k++) {
			int i = order[k];
			float p = presence[i].value();
			if (p <= 0.001f) {
				continue;
			}
			if (before > 0f) {
				float dot = Math.min(before, p);
				String separator = "·";
				float sx = cursor - MINIMAL_GAP / 2f - ui.boldWidthScaled(separator, UNIT_SCALE) / 2f;
				float sy = Ui.besideTop(middle - TEXT_RISE, 1f, UNIT_SCALE);
				ui.textAtScaledBold(separator, sx, sy, UNIT_SCALE, Ui.withOpacity(0xFFFFFFFF, 0.6f * dot));
			}
			float slot = width[i].value();
			ui.graphics.enableScissor(Math.round(cursor) - 1, 0, Math.round(cursor + slot * p) + 2, Math.round(h));
			if (i == SONG) {
				songLeft = cursor;
				songRight = cursor + slot * p;
			}
			InfoBarSegment segment = InfoBarSegment.of(i);
			float alpha = p * p;
			float x = cursor;
			float valueTop = middle - TEXT_RISE;
			int color = valueColor(i, 0xFFFFFFFF);
			if (segment == InfoBarSegment.SONG) {
				drawSongTitle(ui, x, valueTop, songRoom(), alpha, 0xFFFFFFFF, true);
			} else {
				drawValue(ui, i, x, valueTop, Ui.withOpacity(color, alpha), true);
				float unit = unitWidth[i];
				if (unit > 0f && (segment != InfoBarSegment.PING || bar.pingNumber())) {
					float ux = x + valueWidth[i] + UNIT_GAP;
					float uy = Ui.besideTop(valueTop, 1f, UNIT_SCALE);
					ui.textAtScaledBold(lowerUnit[i], ux + 0.5f, uy + 0.7f, UNIT_SCALE, Ui.withOpacity(SHADOW, alpha));
					ui.textAtScaledBold(lowerUnit[i], ux, uy, UNIT_SCALE, Ui.withOpacity(0xFFFFFFFF, 0.6f * alpha));
				}
			}
			ui.graphics.disableScissor();
			cursor += (slot + MINIMAL_GAP) * p;
			before = p;
		}
	}

	/**
	 * A soft dark haze behind each segment, as long as it, so the ends of a long bar are as readable as its middle. It is only as strong as the world
	 * needs by itself: over a dark world it disappears, over a bright one it darkens down to where white text reads.
	 */
	private void drawHaze(Ui ui, float h, float middle) {
		float cursor = 0f;
		for (int k = 0; k < orderCount; k++) {
			int i = order[k];
			float p = presence[i].value();
			if (p <= 0.001f) {
				continue;
			}
			float slot = width[i].value() * p;
			ui.wash(cursor + slot / 2f, middle, slot / 2f + HAZE_SIDE, h / 2f + HAZE_HEIGHT, Ui.withOpacity(0xFF000000, HAZE * p));
			cursor += (width[i].value() + MINIMAL_GAP) * p;
		}
	}

	private void drawStacked(Ui ui, float w, float h) {
		if (bar.background()) {
			HudStyle.panel(ui, 0, 0, Math.round(w), Math.round(h), BOX_RADIUS, HudStyle.opacity(), bar.glow());
		}
		InfoBarSigns.pulse(ui, 0f, 0f, w, h, BOX_RADIUS, pulse);
		float rowTop = STACK_PAD_Y;
		for (int k = 0; k < orderCount; k++) {
			int i = order[k];
			float p = presence[i].value();
			if (p <= 0.001f) {
				continue;
			}
			InfoBarSegment segment = InfoBarSegment.of(i);
			float rowHeight = (segment == InfoBarSegment.SONG ? STACK_SONG_ROW + STACK_DIVIDER : STACK_ROW) * p;
			ui.graphics.enableScissor(0, Math.round(rowTop), Math.round(w), Math.round(rowTop + rowHeight));
			float alpha = p * p;
			if (segment == InfoBarSegment.SONG) {
				ui.roundedSmooth(STACK_PAD_X, rowTop + STACK_DIVIDER / 2f - 0.5f, w - 2 * STACK_PAD_X, 1f, 0.5f,
					HudStyle.line(0.1f * alpha));
				float middle = rowTop + STACK_DIVIDER + STACK_SONG_ROW / 2f;
				MusicFeature music = ModFeatures.MUSIC;
				music.drawCoverArt(ui, Math.round(STACK_PAD_X), Math.round(middle - STACK_COVER / 2f), STACK_COVER, alpha);
				float barsWidth = MusicFeature.levelBarsWidth(SONG_BAR, SONG_BAR_GAP);
				music.drawLevelBars(ui, w - STACK_PAD_X - barsWidth, middle, SONG_BAR, SONG_BAR_GAP, SONG_BAR_HEIGHT, music.accentColor(), alpha);
				float textX = STACK_PAD_X + STACK_COVER + STACK_LABEL_GAP;
				drawSongTitle(ui, textX, middle - TEXT_RISE * STACK_LABEL_SCALE, valueWidth[i], alpha, HudStyle.text(), false);
			} else {
				float middle = rowTop + STACK_ROW / 2f;
				float iconCenter = STACK_PAD_X + STACK_ICON_COLUMN / 2f;
				drawIndicator(ui, segment, i, iconCenter, middle, alpha);
				float labelTop = middle - TEXT_RISE * STACK_LABEL_SCALE;
				ui.textAtScaled(segment.label, STACK_PAD_X + STACK_ICON_COLUMN + STACK_LABEL_GAP, labelTop, STACK_LABEL_SCALE,
					Ui.withOpacity(HudStyle.text(), LABEL_ALPHA * alpha));
				boolean ping = segment == InfoBarSegment.PING;
				String unit = ping ? lowerUnit[i] : segment == InfoBarSegment.COORDS ? values.unit[i] : "";
				float unitRoom = unit.isEmpty() ? 0f : UNIT_GAP + ui.boldWidthScaled(unit, STACK_LABEL_SCALE);
				float right = w - STACK_PAD_X;
				float valueX = right - unitRoom - valueWidth[i] * STACK_LABEL_SCALE;
				var pose = ui.graphics.pose();
				pose.pushMatrix();
				pose.translate(valueX, labelTop);
				pose.scale(STACK_LABEL_SCALE, STACK_LABEL_SCALE);
				drawValue(ui, i, 0f, 0f, Ui.withOpacity(valueColor(i, HudStyle.text()), alpha), false);
				pose.popMatrix();
				if (!unit.isEmpty()) {
					ui.textAtScaledBold(unit, right - unitRoom + UNIT_GAP, labelTop, STACK_LABEL_SCALE, Ui.withOpacity(HudStyle.text(), alpha));
				}
			}
			ui.graphics.disableScissor();
			rowTop += rowHeight;
		}
	}

	/** Draws a segment of a horizontal style, clipped to its current width while it slides in or out. */
	private void drawClipped(Ui ui, InfoBarSegment segment, int i, float x, float middle, float slot, float p, float h) {
		boolean clip = p < 0.999f;
		if (clip) {
			ui.graphics.enableScissor(Math.round(x) - 1, 0, Math.round(x + slot) + 1, Math.round(h));
		}
		drawSegment(ui, segment, i, x, middle, p * p);
		if (clip) {
			ui.graphics.disableScissor();
		}
	}

	/** Draws a segment's content in a row, from its left edge, centered on the middle line. */
	private void drawSegment(Ui ui, InfoBarSegment segment, int i, float x, float middle, float alpha) {
		float valueTop = middle - TEXT_RISE;
		int text = HudStyle.text();
		switch (segment.kind) {
			case FPS -> {
				drawValue(ui, i, x, valueTop, Ui.withOpacity(valueColor(i, text), alpha), false);
				float after = drawUnit(ui, i, x + valueWidth[i], valueTop, alpha);
				if (bar.fpsLine()) {
					InfoBarSigns.fpsLine(ui, values, after + InfoBarSigns.LINE_GAP, middle, lineTop, Ui.withOpacity(indicatorColor(i), alpha));
				}
			}
			case PING -> {
				float cursor = x;
				if (bar.pingBars()) {
					InfoBarSigns.signal(ui, cursor, middle, values.level[i], indicatorColor(i), alpha);
					cursor += InfoBarSigns.SIGNALS_WIDTH + (bar.pingNumber() ? ICON_GAP : 0f);
				}
				if (bar.pingNumber()) {
					drawValue(ui, i, cursor, valueTop, Ui.withOpacity(valueColor(i, text), alpha), false);
					drawUnit(ui, i, cursor + valueWidth[i], valueTop, alpha);
				}
			}
			case TPS -> {
				InfoBarSigns.dot(ui, x + InfoBarSigns.DOT / 2f, middle, indicatorColor(i), alpha);
				float cursor = x + InfoBarSigns.DOT + ICON_GAP;
				drawValue(ui, i, cursor, valueTop, Ui.withOpacity(valueColor(i, text), alpha), false);
				drawUnit(ui, i, cursor + valueWidth[i], valueTop, alpha);
			}
			case SONG -> {
				MusicFeature music = ModFeatures.MUSIC;
				music.drawCoverArt(ui, Math.round(x), Math.round(middle - COVER / 2f), COVER, alpha);
				float textX = x + COVER + COVER_GAP;
				drawSongTitle(ui, textX, valueTop, songRoom(), alpha, text, false);
				music.drawLevelBars(ui, textX + songRoom() + COVER_GAP, middle, SONG_BAR, SONG_BAR_GAP, SONG_BAR_HEIGHT, music.accentColor(), alpha);
			}
			case MEMORY, CPS -> {
				drawValue(ui, i, x, valueTop, Ui.withOpacity(text, alpha), false);
				drawUnit(ui, i, x + valueWidth[i], valueTop, alpha);
			}
			default -> {
				drawIndicator(ui, segment, i, x + ICON / 2f, middle, alpha);
				float cursor = x + ICON + ICON_GAP;
				drawValue(ui, i, cursor, valueTop, Ui.withOpacity(text, alpha), false);
				drawUnit(ui, i, cursor + valueWidth[i], valueTop, alpha);
			}
		}
	}

	/** Draws a segment's sign: its icon, or the signal bars, the dot or a short frame rate line, centered on the point. */
	private void drawIndicator(Ui ui, InfoBarSegment segment, int i, float centerX, float middle, float alpha) {
		switch (segment.kind) {
			case PING -> InfoBarSigns.signal(ui, centerX - InfoBarSigns.SIGNALS_WIDTH / 2f, middle, values.level[i], indicatorColor(i), alpha);
			case TPS -> InfoBarSigns.dot(ui, centerX, middle, indicatorColor(i), alpha);
			default -> ui.icon(segment.icon, Math.round(centerX - ICON / 2f), Math.round(middle - ICON / 2f), ICON,
				Ui.withOpacity(iconColor(segment), alpha));
		}
	}

	private static int iconColor(InfoBarSegment segment) {
		return switch (segment.kind) {
			case AREA -> SystemColors.TEAL;
			default -> segment.iconColor != 0 ? HudStyle.readable(segment.iconColor) : Ui.withOpacity(HudStyle.text(), ICON_ALPHA);
		};
	}

	/** Draws the value, a number with its digits in equal cells; with a shadow in the minimal style. */
	private void drawValue(Ui ui, int i, float x, float top, int color, boolean shadow) {
		InfoBarSegment segment = InfoBarSegment.of(i);
		if (!tabular(segment)) {
			if (shadow) {
				ui.textAtScaledBold(fitted[i], x + 0.5f, top + 0.7f, 1f, Ui.withOpacity(SHADOW, (color >>> 24) / 255f));
			}
			ui.textAtScaledBold(fitted[i], x, top, 1f, color);
			return;
		}
		String text = values.text[i];
		float[] at = charX[i];
		if (at == null) {
			return;
		}
		// while a change rolls in, the characters that changed come up from below and the old ones leave upwards; a number that got longer or
		// shorter moves as a whole, so all of it rolls
		float in = roll[i] >= 1f ? 1f : 1f - (1f - roll[i]) * (1f - roll[i]) * (1f - roll[i]);
		String before = rolledFrom[i];
		boolean sameLength = before.length() == text.length();
		if (in < 1f && rolledFromX[i] != null) {
			for (int c = 0; c < before.length() && c < rolledFromX[i].length; c++) {
				if (!sameLength || c >= text.length() || before.charAt(c) != text.charAt(c)) {
					drawChar(ui, before.charAt(c), x + rolledFromX[i][c], top - ROLL_DISTANCE * in, Ui.withOpacity(color, 1f - in), shadow);
				}
			}
		}
		for (int c = 0; c < text.length() && c < at.length; c++) {
			char ch = text.charAt(c);
			boolean changed = in < 1f && (!sameLength || c >= before.length() || before.charAt(c) != ch);
			drawChar(ui, ch, x + at[c], changed ? top + ROLL_DISTANCE * (1f - in) : top, changed ? Ui.withOpacity(color, in) : color, shadow);
		}
	}

	/** Draws one character of a number at its cell's left edge; a digit sits in the middle of its cell. */
	private void drawChar(Ui ui, char ch, float x, float top, int color, boolean shadow) {
		if (ch == ' ' || ch >= CHARS.length) {
			return;
		}
		String glyph = CHARS[ch];
		float cx = x + (Character.isDigit(ch) ? (digitWidth - ui.boldWidthScaled(glyph, 1f)) / 2f : 0f);
		if (shadow) {
			ui.textAtScaledBold(glyph, cx + 0.5f, top + 0.7f, 1f, Ui.withOpacity(SHADOW, (color >>> 24) / 255f));
		}
		ui.textAtScaledBold(glyph, cx, top, 1f, color);
	}

	/** Draws the small label after a value ("FPS"). @return the x after it */
	private float drawUnit(Ui ui, int i, float x, float valueTop, float alpha) {
		if (unitWidth[i] <= 0f) {
			return x;
		}
		float ux = x + UNIT_GAP;
		ui.textAtScaledBold(values.unit[i], ux, Ui.besideTop(valueTop, 1f, UNIT_SCALE), UNIT_SCALE, Ui.withOpacity(HudStyle.text(), UNIT_ALPHA * alpha));
		return ux + unitWidth[i];
	}

	/**
	 * Draws the song title, cross-fading from the previous song's after a song change; with the lyrics in the bar, the song and its lines in
	 * the segment's fixed room.
	 */
	private void drawSongTitle(Ui ui, float x, float top, float room, float alpha, int color, boolean shadow) {
		if (ModFeatures.MUSIC.barLyricsShown()) {
			ModFeatures.MUSIC.barLyrics().draw(ui, x, top, room, alpha, color, shadow);
			return;
		}
		float in = songFade * songFade * (3f - 2f * songFade);
		if (in < 1f && !songPrevious.isEmpty()) {
			if (shadow) {
				ui.textAtScaledBold(songPrevious, x + 0.5f, top + 0.7f, 1f, Ui.withOpacity(SHADOW, alpha * (1f - in)));
			}
			ui.textAtScaledBold(songPrevious, x, top, 1f, Ui.withOpacity(color, alpha * (1f - in)));
		}
		if (shadow) {
			ui.textAtScaledBold(songShown, x + 0.5f, top + 0.7f, 1f, Ui.withOpacity(SHADOW, alpha * in));
		}
		ui.textAtScaledBold(songShown, x, top, 1f, Ui.withOpacity(color, alpha * in));
	}

	// ----------------------------------------------------------------------------------------------------------- colors

	/** @return the value color: the normal one while fine, yellow on a warning, red (orange for the server speed) when bad, fading between them */
	private int valueColor(int i, int fine) {
		return levelColor(i, fine);
	}

	/** @return the color of the signs (line, bars, dot): green while fine, otherwise like the value */
	private int indicatorColor(int i) {
		return levelColor(i, SystemColors.GREEN);
	}

	private int levelColor(int i, int fine) {
		float level = shownLevel[i];
		int warn = HudStyle.readable(SystemColors.YELLOW);
		int bad = HudStyle.readable(i == InfoBarSegment.TPS.ordinal() ? SystemColors.ORANGE : SystemColors.RED);
		return level <= 1f ? Ui.mix(fine, warn, level) : Ui.mix(warn, bad, level - 1f);
	}
}
