package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.TextSelection;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.ConfigPart;
import dev.tim1337.lumio.config.ConfigStore;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.menu.ConfigsFeature;
import dev.tim1337.lumio.menu.Presets;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The configs page: the player's configs in a row of cards (with the parts each has, and automatic backups with a clock and "Restore"), the
 * prebuilt looks with a picture drawn from each look's colors, and the prebuilt feature sets with what applying one would change. A look is
 * tried with a click, with a bar at the bottom to keep it or go back; a feature set and a config open a small popover with their actions. Saving,
 * renaming and importing open a panel with a text field below the configs.
 */
public class ConfigListControl extends Control {
	private static final int LABEL_HEIGHT = 16;
	private static final int SECTION_GAP = 16;
	private static final int GAP = 8;
	private static final int OWN_HEIGHT = 50;
	private static final int OWN_MIN_WIDTH = 170;
	private static final int OWN_TILE = 20;
	private static final int CHIP_HEIGHT = 14;
	private static final int LOOK_MIN_WIDTH = 86;
	private static final int LOOK_PAD = 6;
	/** A look's picture: its height to its width, and the width of the mockup's picture its sizes are measured on. */
	private static final float LOOK_ASPECT = 0.92f;
	private static final float LOOK_MOCKUP_WIDTH = 156f;
	private static final int LOOK_TEXT = 26;
	/** How many preset cards would fill a row; the two there are take their share of it. */
	private static final int PRESET_COLUMNS = 4;
	private static final int PANEL_HEIGHT = 64;
	private static final int BAR_HEIGHT = 34;
	/** Room below the cards so the trial bar does not cover the last ones at the end of the page. */
	private static final int BAR_ROOM = BAR_HEIGHT + 20;
	private static final int POPOVER_WIDTH = 214;
	private static final int POPOVER_PAD = 12;
	private static final int BUTTON_HEIGHT = 22;
	private static final int CHECKBOX = 10;
	private static final int OPTION_HEIGHT = 16;
	/** The note under a config's share button, in two lines at most, wrapped once. */
	private static final String SHARE_NOTE = "Shared configs never contain your Cider token, Spotify sign-in or last server.";
	private static final int SHARE_NOTE_LINES = 2;
	private List<String> shareNote;
	/** The step between lines of small text. */
	private static final int LINE = 10;
	private static final int HEALTH_COLOR = SystemColors.RED;
	/** How often the configs are read again. */
	private static final long REFRESH_MILLIS = 500;
	/** How long a delete waits for its confirming second click. */
	private static final long CONFIRM_MILLIS = 4000;
	private static final int MAX_TYPED = 40;
	/** A pasted config is long; the import field takes this much. */
	private static final int MAX_PASTED = 200_000;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final String[] PART_NAMES = {"Features", "Colors", "Layout"};

	private enum Mode {
		NONE, SAVE, EDIT, IMPORT
	}

	private enum Popover {
		NONE, CONFIG, IMPORT
	}

	// what a click hits: actions, and cards and options by their index added to a base
	private static final int ACT_CONSUME = 0;
	private static final int ACT_SAVE_NEW = 1;
	private static final int ACT_LOAD = 2;
	private static final int ACT_OVERWRITE = 3;
	private static final int ACT_RENAME = 4;
	private static final int ACT_EXPORT = 5;
	private static final int ACT_DELETE = 6;
	private static final int ACT_PANEL_CONFIRM = 7;
	private static final int ACT_PANEL_CANCEL = 8;
	private static final int ACT_PANEL_RENAME = 9;
	private static final int ACT_BAR_BACK = 10;
	private static final int ACT_BAR_KEEP = 11;
	private static final int ACT_POPOVER_CLOSE = 13;
	private static final int ACT_PASTE_CLIPBOARD = 15;
	private static final int ACT_PASTE_TEXT = 16;
	private static final int ACT_RESTORE = 17;
	private static final int ACT_OWN = 1000;
	private static final int ACT_RESTORE_CARD = 2000;
	private static final int ACT_LOOK = 3000;
	private static final int ACT_OPTION = 5000;

	/** A clickable area from the last frame; kept in a pool and refilled every frame, so drawing creates nothing. */
	private static final class Hit {
		int x;
		int y;
		int w;
		int h;
		int action;

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** Clickable areas of one layer: the page, or the popover over it. */
	private static final class Hits {
		private Hit[] hits = new Hit[48];
		private int count;

		Hits() {
			for (int i = 0; i < hits.length; i++) {
				hits[i] = new Hit();
			}
		}

		void clear() {
			count = 0;
		}

		void add(int x, int y, int w, int h, int action) {
			if (count == hits.length) {
				Hit[] grown = new Hit[hits.length * 2];
				System.arraycopy(hits, 0, grown, 0, hits.length);
				for (int i = hits.length; i < grown.length; i++) {
					grown[i] = new Hit();
				}
				hits = grown;
			}
			Hit target = hits[count++];
			target.x = x;
			target.y = y;
			target.w = w;
			target.h = h;
			target.action = action;
		}

		/** @return the action at a point, the last drawn first, or -1 */
		int at(double mx, double my) {
			for (int i = count - 1; i >= 0; i--) {
				if (hits[i].contains(mx, my)) {
					return hits[i].action;
				}
			}
			return -1;
		}
	}

	private final Runnable onChanged;
	private final Hits pageHits = new Hits();
	private final Hits popoverHits = new Hits();
	private final float[] lookHover = new float[Presets.ALL.size()];
	private final Ui.EdgeColor[] lookGlows = new Ui.EdgeColor[Presets.ALL.size()];
	private float[] ownHover = new float[8];
	/** Hover of the buttons, by a slot of their own. */
	private final float[] buttonHover = new float[24];
	private final Spring barSpring = Spring.smooth(Spring.DEFAULT);
	private List<ConfigsFeature.Entry> entries = List.of();
	/** The line under each config's name: when it was saved or imported, built when the configs are read. */
	private List<String> infos = List.of();
	private long refreshedAt;
	private Mode mode = Mode.NONE;
	private String typed = "";
	private final TextSelection selection = new TextSelection();
	private Popover popover = Popover.NONE;
	private int popoverIndex = -1;
	private int popoverX;
	private int popoverY;
	private boolean confirmDelete;
	private long confirmAt;
	/** The config the name panel renames, or null. */
	private ConfigsFeature.Entry editing;
	/** The look the trial bar names, kept while it slides out. */
	private String barName = "";
	private int barX;
	private int barY;
	private int barWidth;

	public ConfigListControl(Runnable onChanged) {
		this.onChanged = onChanged;
		for (int i = 0; i < lookGlows.length; i++) {
			int glow = Ui.withOpacity(0xFF000000 | Presets.ALL.get(i).primary(), 0.33f);
			lookGlows[i] = place -> glow;
		}
		configs().onHeaderButtons(this::importConfig, () -> open(Mode.SAVE, null));
	}

	private static ConfigsFeature configs() {
		return ModFeatures.CONFIGS;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	public boolean bare() {
		return true;
	}

	private void refresh(boolean now) {
		long time = System.currentTimeMillis();
		if (!now && time - refreshedAt < REFRESH_MILLIS) {
			return;
		}
		refreshedAt = time;
		entries = configs().entries();
		List<String> lines = new ArrayList<>(entries.size());
		for (ConfigsFeature.Entry entry : entries) {
			lines.add(info(entry.saved()));
		}
		infos = lines;
		if (ownHover.length < entries.size()) {
			ownHover = new float[entries.size() + 8];
		}
	}

	/** @return where a config came from and when: "Saved yesterday", "Imported · 3 days ago", "Saved automatically · today, 14:05" */
	private static String info(ConfigStore.Saved saved) {
		LocalDate today = LocalDate.now();
		var at = Instant.ofEpochMilli(saved.time()).atZone(ZoneId.systemDefault());
		long days = ChronoUnit.DAYS.between(at.toLocalDate(), today);
		String day = days <= 0 ? "today" : days == 1 ? "yesterday" : days < 7 ? days + " days ago" : at.format(DAY);
		return switch (saved.source()) {
			case SAVED -> "Saved " + day;
			case IMPORTED -> "Imported · " + day;
			// a backup made today is told apart by its time
			case AUTOMATIC -> "Saved automatically · " + (days <= 0 ? at.format(CLOCK) : days == 1 ? "yesterday " + at.format(CLOCK) : day);
		};
	}

	// ----------------------------------------------------------------------------------------------------------- layout

	private int columns(int min, int most) {
		return Math.max(1, Math.min(most, (width + GAP) / (min + GAP)));
	}

	private int ownColumns() {
		return columns(OWN_MIN_WIDTH, 3);
	}

	/** Presets take a quarter of the page each, or half of a narrow one, so their pictures stay the size of the old looks' cards. */
	private int lookColumns() {
		return Math.min(PRESET_COLUMNS, columns(LOOK_MIN_WIDTH, PRESET_COLUMNS));
	}

	private int cardWidth(int columns) {
		return (width - GAP * (columns - 1)) / columns;
	}

	private static int rows(int count, int columns) {
		return (count + columns - 1) / columns;
	}

	private int lookPictureHeight() {
		return Math.round((cardWidth(lookColumns()) - 2 * LOOK_PAD) * LOOK_ASPECT);
	}

	private int lookHeight() {
		return LOOK_PAD + lookPictureHeight() + LOOK_TEXT + LOOK_PAD;
	}

	private int ownHeight() {
		int cards = Math.max(1, entries.size());
		int rows = rows(cards, ownColumns());
		return rows * OWN_HEIGHT + (rows - 1) * GAP + (mode != Mode.NONE ? GAP + PANEL_HEIGHT : 0);
	}

	private int looksHeight() {
		int rows = rows(Presets.ALL.size(), lookColumns());
		return rows * lookHeight() + (rows - 1) * GAP;
	}

	@Override
	protected int measure(Ui ui, int width) {
		this.width = width;
		refresh(false);
		return 2 * LABEL_HEIGHT + ownHeight() + SECTION_GAP + looksHeight() + BAR_ROOM;
	}

	// ---------------------------------------------------------------------------------------------------------- drawing

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		pageHits.clear();
		refresh(false);
		if (confirmDelete && System.currentTimeMillis() - confirmAt > CONFIRM_MILLIS) {
			confirmDelete = false;
		}
		int top = y;
		label(ui, "YOUR CONFIGS", null, top);
		top += LABEL_HEIGHT;
		top = drawOwn(ui, mouseX, mouseY, top) + SECTION_GAP;
		label(ui, "PRESETS", "A whole setup to start from", top);
		top += LABEL_HEIGHT;
		drawLooks(ui, mouseX, mouseY, top);
	}

	private void label(Ui ui, String text, String note, int top) {
		Palette palette = ui.palette;
		ui.sectionLabel(text, x + 4, top + 4, palette.textSecondary());
		if (note != null) {
			float labelWidth = ui.boldWidthScaled(text, TextScale.SMALL) * 1.12f;
			ui.textAtScaled(note, x + 4 + labelWidth + 8, top + 3.5f, TextScale.SMALL, Ui.withOpacity(palette.textSecondary(), 0.7f));
		}
	}

	/** A card: quiet, lighter on hover, with an accent ring when it is in use or tried. */
	private static void card(Ui ui, int cx, int cy, int w, int h, float hover, boolean marked) {
		Palette palette = ui.palette;
		if (marked) {
			ui.shadow(cx, cy, w, h, Radius.LG, 8, 0.3f);
		}
		ui.rounded(cx, cy, w, h, Radius.LG, Ui.withOpacity(palette.text(), 0.04f + 0.03f * hover + (marked ? 0.04f : 0f)));
		ui.ring(cx, cy, w, h, Radius.LG, 1, Ui.withOpacity(palette.text(), 0.09f + 0.06f * hover));
		if (marked) {
			ui.ring(cx - 1, cy - 1, w + 2, h + 2, Radius.LG + 1, 2, ui.accent);
		}
	}

	private boolean over(int mouseX, int mouseY, int cx, int cy, int w, int h) {
		return mouseX >= cx && mouseX < cx + w && mouseY >= cy && mouseY < cy + h;
	}

	/** The player's configs, or a card that saves the first one; the name panel below them. @return the y below */
	private int drawOwn(Ui ui, int mouseX, int mouseY, int top) {
		Palette palette = ui.palette;
		int columns = ownColumns();
		int cardWidth = cardWidth(columns);
		if (entries.isEmpty()) {
			boolean over = over(mouseX, mouseY, x, top, width, OWN_HEIGHT);
			if (over) {
				ui.rounded(x, top, width, OWN_HEIGHT, Radius.LG, Ui.withOpacity(ui.accent, 0.06f));
			}
			ui.dashedRounded(x, top, width, OWN_HEIGHT, Radius.LG, over ? ui.accent : Ui.withOpacity(palette.text(), 0.2f));
			ui.textCentered(ui.fit("Save the current settings as a config", width - 12), x + width / 2, top + OWN_HEIGHT / 2,
				over ? palette.text() : palette.textSecondary());
			pageHits.add(x, top, width, OWN_HEIGHT, ACT_SAVE_NEW);
		}
		for (int i = 0; i < entries.size(); i++) {
			int cx = x + (i % columns) * (cardWidth + GAP);
			int cy = top + (i / columns) * (OWN_HEIGHT + GAP);
			drawOwnCard(ui, entries.get(i), i, cx, cy, cardWidth, mouseX, mouseY);
		}
		int rows = rows(Math.max(1, entries.size()), columns);
		top += rows * OWN_HEIGHT + (rows - 1) * GAP;
		if (mode != Mode.NONE) {
			top += GAP;
			drawPanel(ui, mouseX, mouseY, top);
			top += PANEL_HEIGHT;
		}
		return top;
	}

	private void drawOwnCard(Ui ui, ConfigsFeature.Entry entry, int index, int cx, int cy, int w, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		ConfigStore.Saved saved = entry.saved();
		boolean active = configs().isActive(entry);
		float hover = ownHover[index] = ui.approach(ownHover[index], over(mouseX, mouseY, cx, cy, w, OWN_HEIGHT) ? 1f : 0f, Ui.HOVER_RATE);
		card(ui, cx, cy, w, OWN_HEIGHT, hover, active);
		pageHits.add(cx, cy, w, OWN_HEIGHT, ACT_OWN + index);
		int tileY = cy + (OWN_HEIGHT - OWN_TILE) / 2;
		if (saved.automatic()) {
			ui.roundedGradient(cx + 9, tileY, OWN_TILE, OWN_TILE, Radius.SM, 0xFF636366, 0xFF3A3A3C);
			ui.icon("clock", cx + 9 + 5, tileY + 5, OWN_TILE - 10, 0xFFFFFFFF);
		} else {
			ui.roundedGradient(cx + 9, tileY, OWN_TILE, OWN_TILE, Radius.SM, 0xFF000000 | entry.primary(), 0xFF000000 | entry.secondary());
		}
		int textX = cx + 9 + OWN_TILE + 8;
		// at the right: a check on the config in use, "Restore" on a backup
		int right = cx + w - 9;
		if (active) {
			ui.circle(right - 7, cy + OWN_HEIGHT / 2, 7, ui.accent);
			ui.icon("tick", right - 12, cy + OWN_HEIGHT / 2 - 5, 10, 0xFFFFFFFF);
			right -= 20;
		} else if (saved.automatic()) {
			String restore = "Restore";
			int restoreWidth = Math.round(ui.boldWidthScaled(restore, TextScale.SMALL)) + 10;
			int restoreX = right - restoreWidth;
			int restoreY = cy + OWN_HEIGHT / 2 - 7;
			ui.rounded(restoreX, restoreY, restoreWidth, 14, Radius.XS, Ui.withOpacity(palette.text(), 0.08f + 0.06f * hover));
			ui.textAtScaledBold(restore, restoreX + 5, restoreY + 3.5f, TextScale.SMALL, palette.text());
			pageHits.add(restoreX, restoreY, restoreWidth, 14, ACT_RESTORE_CARD + index);
			right = restoreX - 6;
		}
		int room = right - textX;
		ui.textAtScaledBold(ui.fit(entry.name(), room), textX, cy + 8, 1f, palette.text());
		ui.textAtScaled(ui.fitScaled(infos.get(index), room, TextScale.SMALL), textX, cy + 20, TextScale.SMALL, palette.textSecondary());
		int chipX = textX;
		for (ConfigPart part : ConfigPart.values()) {
			String name = PART_NAMES[part.ordinal()];
			int chipWidth = Math.round(ui.boldWidthScaled(name, TextScale.SMALL * 0.9f)) + 8;
			if (chipX + chipWidth > right) {
				break;
			}
			boolean has = saved.parts().contains(part);
			int chipY = cy + OWN_HEIGHT - 8 - CHIP_HEIGHT;
			if (has) {
				ui.rounded(chipX, chipY, chipWidth, CHIP_HEIGHT, Radius.XXS + 1, Ui.withOpacity(palette.text(), 0.12f));
			} else {
				ui.ring(chipX, chipY, chipWidth, CHIP_HEIGHT, Radius.XXS + 1, 1, Ui.withOpacity(palette.text(), 0.08f));
			}
			ui.textAtScaledBold(name, chipX + 4, chipY + 2.5f, TextScale.SMALL * 0.9f,
				has ? Ui.withOpacity(palette.text(), 0.85f) : Ui.withOpacity(palette.textSecondary(), 0.45f));
			chipX += chipWidth + 4;
		}
	}

	/** The looks, each with a picture of its colors; the one tried and the one in use have the accent ring. @return the y below */
	private int drawLooks(Ui ui, int mouseX, int mouseY, int top) {
		Palette palette = ui.palette;
		int columns = lookColumns();
		int cardWidth = cardWidth(columns);
		int cardHeight = lookHeight();
		int pictureHeight = lookPictureHeight();
		Presets.Preset trying = configs().trying();
		Presets.Preset before = configs().trialBefore();
		Presets.Preset current = trying == null ? null : null;
		for (int i = 0; i < Presets.ALL.size(); i++) {
			Presets.Preset look = Presets.ALL.get(i);
			int cx = x + (i % columns) * (cardWidth + GAP);
			int cy = top + (i / columns) * (cardHeight + GAP);
			float hover = lookHover[i] = ui.approach(lookHover[i], over(mouseX, mouseY, cx, cy, cardWidth, cardHeight) ? 1f : 0f, Ui.HOVER_RATE);
			card(ui, cx, cy, cardWidth, cardHeight, hover, look == trying || look == current);
			pageHits.add(cx, cy, cardWidth, cardHeight, ACT_LOOK + i);
			drawLookPicture(ui, look, i, cx + LOOK_PAD, cy + LOOK_PAD, cardWidth - 2 * LOOK_PAD, pictureHeight);
			int textY = cy + LOOK_PAD + pictureHeight + 6;
			String pill = look == trying ? "Trying" : look == before ? "Before" : null;
			int pillWidth = pill == null ? 0 : Math.round(ui.boldWidthScaled(pill, TextScale.SMALL)) + 10;
			int room = cardWidth - 2 * LOOK_PAD - 6 - (pill != null ? pillWidth + 4 : look == current ? 18 : 0);
			ui.textAtScaledBold(ui.fit(look.name(), room), cx + LOOK_PAD + 3, textY, 1f, palette.text());
			ui.textAtScaled(ui.fitScaled(look.note(), room, TextScale.SMALL), cx + LOOK_PAD + 3, textY + 11, TextScale.SMALL, palette.textSecondary());
			int pillX = cx + cardWidth - LOOK_PAD - 3 - pillWidth;
			if (pill != null) {
				boolean tried = look == trying;
				ui.rounded(pillX, textY + 2, pillWidth, 13, Radius.XS, tried ? ui.accent : Ui.withOpacity(palette.text(), 0.12f));
				ui.textAtScaledBold(pill, pillX + 5, textY + 5, TextScale.SMALL, tried ? 0xFFFFFFFF : palette.text());
			} else if (look == current) {
				ui.circle(cx + cardWidth - LOOK_PAD - 10, textY + 8, 7, ui.accent);
				ui.icon("tick", cx + cardWidth - LOOK_PAD - 15, textY + 3, 10, 0xFFFFFFFF);
			}
		}
		int rows = rows(Presets.ALL.size(), columns);
		return top + rows * cardHeight + (rows - 1) * GAP;
	}

	/**
	 * A look's picture, from its own colors: its sky, a small settings window in its theme with the accent on its sidebar, switch and slider and
	 * a glow around it, and two status bars. Sizes are the mockup's, scaled to the picture.
	 */
	private void drawLookPicture(Ui ui, Presets.Preset look, int index, int px, int py, int pw, int ph) {
		float s = pw / LOOK_MOCKUP_WIDTH;
		int accent = 0xFF000000 | look.primary();
		ui.roundedGradient(px, py, pw, ph, Radius.MD, 0xFF000000 | look.skyTop(), 0xFF000000 | look.skyBottom());
		int window;
		int side;
		int lines;
		int track;
		switch (look.theme()) {
			case LIGHT -> {
				window = 0xF0FAFAFC;
				side = 0x0F000000;
				lines = 0x38000000;
				track = 0x0F000000;
			}
			case GLASS -> {
				window = 0x29FFFFFF;
				side = 0x24FFFFFF;
				lines = 0x73FFFFFF;
				track = 0x24FFFFFF;
			}
			default -> {
				window = 0xEB18181C;
				side = 0x1AFFFFFF;
				lines = 0x47FFFFFF;
				track = 0x1AFFFFFF;
			}
		}
		int wx = px + Math.round(8 * s);
		int wy = py + Math.round(14 * s);
		int ww = pw - 2 * Math.round(8 * s);
		int wh = Math.min(Math.round(56 * s), ph - Math.round(32 * s));
		ui.halo(wx, wy, ww, wh, Radius.SM, Math.max(3, Math.round(9 * s)), lookGlows[index]);
		ui.rounded(wx, wy, ww, wh, Radius.SM, window);
		ui.ring(wx, wy, ww, wh, Radius.SM, 1, 0x2EFFFFFF);
		int sideWidth = Math.round(ww * 0.3f);
		ui.roundedLeft(wx, wy, sideWidth, wh, Radius.SM, side);
		int pad = Math.max(2, Math.round(4 * s));
		ui.rounded(wx + pad, wy + pad + 1, sideWidth - 2 * pad, Math.max(2, Math.round(5 * s)), Radius.XXS, accent);
		ui.rounded(wx + pad, wy + pad + Math.round(9 * s), Math.round((sideWidth - 2 * pad) * 0.75f), Math.max(2, Math.round(3 * s)), Radius.XXS, lines);
		ui.rounded(wx + pad, wy + pad + Math.round(15 * s), Math.round((sideWidth - 2 * pad) * 0.6f), Math.max(2, Math.round(3 * s)), Radius.XXS, lines);
		int mainX = wx + sideWidth + pad + 1;
		int mainWidth = ww - sideWidth - 2 * pad - 1;
		int line = Math.max(2, Math.round(4 * s));
		ui.rounded(mainX, wy + pad + 1, Math.round(mainWidth * 0.5f), line, Radius.XXS, lines);
		int rowY = wy + pad + Math.round(13 * s);
		ui.rounded(mainX, rowY, Math.round(mainWidth * 0.45f), Math.max(2, Math.round(3 * s)), Radius.XXS, lines);
		int switchWidth = Math.max(6, Math.round(13 * s));
		int switchHeight = Math.max(4, Math.round(7 * s));
		ui.rounded(mainX + mainWidth - switchWidth, rowY - switchHeight / 2 + 1, switchWidth, switchHeight, switchHeight / 2f, accent);
		int sliderY = rowY + Math.round(11 * s);
		int sliderHeight = Math.max(2, Math.round(3 * s));
		ui.rounded(mainX, sliderY, mainWidth, sliderHeight, Radius.XXS, track);
		ui.rounded(mainX, sliderY, Math.round(mainWidth * 0.6f), sliderHeight, Radius.XXS, accent);
		// the health bar and one in the accent at the bottom
		int barWidth = Math.round(32 * s);
		int barHeight = Math.max(2, Math.round(4 * s));
		int barY = py + ph - Math.round(9 * s) - barHeight;
		int middle = px + pw / 2;
		ui.rounded(middle - barWidth - 2, barY, barWidth, barHeight, Radius.XXS, HEALTH_COLOR);
		ui.rounded(middle + 2, barY, barWidth, barHeight, Radius.XXS, accent);
	}

	// ------------------------------------------------------------------------------------------------------- trial bar

	/** The bar of a look being tried, kept at the bottom of the visible page; it slides up and down on the menu's springs. */
	@Override
	public void renderSticky(Ui ui, int mouseX, int mouseY) {
		Presets.Preset trying = configs().trying();
		float shown = barSpring.follow(ui, trying != null ? 1f : 0f);
		if (trying != null) {
			barName = trying.name();
		}
		if (shown <= 0.01f) {
			barWidth = 0;
			return;
		}
		Palette palette = ui.palette;
		String title = "Trying " + barName;
		String text = "Only colors change. Leaving this page brings your old look back.";
		String keep = "Keep " + barName;
		int backWidth = ui.width("Go back") + 20;
		int keepWidth = ui.width(keep) + 22;
		int textWidth = Math.round(Math.max(ui.boldWidthScaled(title, 1f), ui.widthScaled(text, TextScale.SMALL)));
		barWidth = Math.min(width, 12 + 14 + 8 + textWidth + 6 + 12 + backWidth + 6 + keepWidth + 8);
		barX = x + (width - barWidth) / 2;
		barY = Math.round(Math.min(viewBottom, y + height) - 10 - BAR_HEIGHT + (1f - shown) * (BAR_HEIGHT + 16));
		float before = Ui.alpha();
		Ui.setAlpha(before * Math.min(1f, shown * 1.5f));
		ui.shadow(barX, barY, barWidth, BAR_HEIGHT, Radius.XL, 14, 0.5f);
		HudStyle.panel(ui, barX, barY, barWidth, BAR_HEIGHT, Radius.XL, 0.97f);
		ui.icon("sparkle", barX + 12, barY + (BAR_HEIGHT - 14) / 2, 14, palette.text());
		int textX = barX + 12 + 14 + 8;
		int textRoom = barWidth - (textX - barX) - 12 - backWidth - 6 - keepWidth - 8;
		ui.textAtScaledBold(ui.fit(title, textRoom), textX, barY + 7, 1f, palette.text());
		ui.textAtScaled(ui.fitScaled(text, textRoom, TextScale.SMALL), textX, barY + 19, TextScale.SMALL, palette.textSecondary());
		int buttonY = barY + (BAR_HEIGHT - BUTTON_HEIGHT - 2) / 2;
		int keepX = barX + barWidth - 8 - keepWidth;
		button(ui, mouseX, mouseY, keepX - 6 - backWidth, buttonY, backWidth, BUTTON_HEIGHT + 2, "Go back", false, false, true, 0, ACT_BAR_BACK, pageHits);
		button(ui, mouseX, mouseY, keepX, buttonY, keepWidth, BUTTON_HEIGHT + 2, keep, true, false, true, 1, ACT_BAR_KEEP, pageHits);
		Ui.setAlpha(before);
	}

	@Override
	public boolean contains(double mouseX, double mouseY) {
		return super.contains(mouseX, mouseY)
			|| barWidth > 0 && mouseX >= barX && mouseX < barX + barWidth && mouseY >= barY && mouseY < barY + BAR_HEIGHT;
	}

	// --------------------------------------------------------------------------------------------------------- popovers

	@Override
	public boolean isOverlayOpen() {
		return popover != Popover.NONE;
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		popoverHits.clear();
		switch (popover) {
			case CONFIG -> {
				if (popoverIndex < entries.size()) {
					drawConfigPopover(ui, mouseX, mouseY, entries.get(popoverIndex));
				} else {
					closePopover();
				}
			}
			case IMPORT -> drawImportPopover(ui, mouseX, mouseY);
			default -> {
			}
		}
	}

	/** Opens a popover under a card, or above it where there is no room below. */
	private void openPopover(Popover kind, int index, int anchorX, int anchorTop, int anchorBottom, int height) {
		popover = kind;
		popoverIndex = index;
		confirmDelete = false;
		popoverX = Math.clamp(anchorX, x, x + width - POPOVER_WIDTH);
		popoverY = anchorBottom + 6 + height <= viewBottom ? anchorBottom + 6 : Math.max(viewTop + 4, anchorTop - 6 - height);
		UiSounds.play(UiSounds.Cue.OPEN);
	}

	private void closePopover() {
		popover = Popover.NONE;
		popoverIndex = -1;
		confirmDelete = false;
	}

	private void popoverFrame(Ui ui, int height) {
		ui.shadow(popoverX, popoverY, POPOVER_WIDTH, height, Radius.XL, 14, 0.5f);
		HudStyle.panel(ui, popoverX, popoverY, POPOVER_WIDTH, height, Radius.XL, 0.97f);
		popoverHits.add(popoverX, popoverY, POPOVER_WIDTH, height, ACT_CONSUME);
	}

	private int configPopoverHeight(ConfigsFeature.Entry entry) {
		return entry.saved().automatic() ? POPOVER_PAD + 26 + 22 + BUTTON_HEIGHT + 6 + BUTTON_HEIGHT + POPOVER_PAD
			: POPOVER_PAD + 26 + LABEL_HEIGHT + configs().loadOptions().size() * OPTION_HEIGHT + 6 + 3 * (BUTTON_HEIGHT + 6) - 6 + 6 + SHARE_NOTE_LINES * LINE + POPOVER_PAD;
	}

	/** A config's popover: what loading it applies, and load, overwrite, rename, copy and delete; a backup's: restore, rename and delete. */
	private void drawConfigPopover(Ui ui, int mouseX, int mouseY, ConfigsFeature.Entry entry) {
		Palette palette = ui.palette;
		int height = configPopoverHeight(entry);
		popoverFrame(ui, height);
		int left = popoverX + POPOVER_PAD;
		int full = POPOVER_WIDTH - 2 * POPOVER_PAD;
		int half = (full - 6) / 2;
		int top = popoverY + POPOVER_PAD;
		ui.textAtScaledBold(ui.fit(entry.name(), full), left, top, 1f, palette.text());
		ui.textAtScaled(ui.fitScaled(infos.get(popoverIndex), full, TextScale.SMALL), left, top + 12, TextScale.SMALL, palette.textSecondary());
		top += 26;
		if (entry.saved().automatic()) {
			ui.textAtScaled(ui.fitScaled("Restoring brings back only what it has: " + partsText(entry.saved()) + ".", full, TextScale.SMALL), left, top,
				TextScale.SMALL, palette.textSecondary());
			top += 22;
			button(ui, mouseX, mouseY, left, top, full, BUTTON_HEIGHT, "Restore", true, false, true, 2, ACT_RESTORE, popoverHits);
			top += BUTTON_HEIGHT + 6;
			button(ui, mouseX, mouseY, left, top, half, BUTTON_HEIGHT, "Rename", false, false, true, 3, ACT_RENAME, popoverHits);
			button(ui, mouseX, mouseY, left + half + 6, top, full - half - 6, BUTTON_HEIGHT, confirmDelete ? "Sure?" : "Delete", false, true, true, 4,
				ACT_DELETE, popoverHits);
			return;
		}
		ui.sectionLabel("WHAT IS LOADED", left, top + 2, palette.textSecondary());
		top += LABEL_HEIGHT;
		List<BooleanSetting> options = configs().loadOptions();
		for (int i = 0; i < options.size(); i++) {
			BooleanSetting option = options.get(i);
			int boxY = top + (OPTION_HEIGHT - CHECKBOX) / 2;
			if (option.get()) {
				ui.rounded(left, boxY, CHECKBOX, CHECKBOX, Radius.XS, ui.accent);
				ui.icon("tick", left + 1, boxY + 1, CHECKBOX - 2, 0xFFFFFFFF);
			} else {
				ui.ring(left, boxY, CHECKBOX, CHECKBOX, Radius.XS, 1, Ui.withOpacity(palette.textSecondary(), 0.7f));
			}
			ui.textMiddle(ui.fit(option.labelText(), full - CHECKBOX - 6), left + CHECKBOX + 6, top + OPTION_HEIGHT / 2, palette.text());
			popoverHits.add(left, top, full, OPTION_HEIGHT, ACT_OPTION + i);
			top += OPTION_HEIGHT;
		}
		top += 6;
		button(ui, mouseX, mouseY, left, top, full, BUTTON_HEIGHT, "Load", true, false, true, 2, ACT_LOAD, popoverHits);
		top += BUTTON_HEIGHT + 6;
		button(ui, mouseX, mouseY, left, top, half, BUTTON_HEIGHT, "Overwrite", false, false, true, 5, ACT_OVERWRITE, popoverHits);
		button(ui, mouseX, mouseY, left + half + 6, top, full - half - 6, BUTTON_HEIGHT, "Rename", false, false, true, 3, ACT_RENAME, popoverHits);
		top += BUTTON_HEIGHT + 6;
		button(ui, mouseX, mouseY, left, top, half, BUTTON_HEIGHT, "Copy as text", false, false, true, 6, ACT_EXPORT, popoverHits);
		button(ui, mouseX, mouseY, left + half + 6, top, full - half - 6, BUTTON_HEIGHT, confirmDelete ? "Sure?" : "Delete", false, true, true, 4,
			ACT_DELETE, popoverHits);
		top += BUTTON_HEIGHT + 6;
		if (shareNote == null) {
			shareNote = ui.wrapScaled(SHARE_NOTE, full, TextScale.SMALL, SHARE_NOTE_LINES);
		}
		for (int line = 0; line < shareNote.size(); line++) {
			ui.textAtScaled(shareNote.get(line), left, top + line * LINE, TextScale.SMALL, palette.textSecondary());
		}
	}

	private static String partsText(ConfigStore.Saved saved) {
		List<String> names = new ArrayList<>();
		for (ConfigPart part : saved.parts()) {
			names.add(PART_NAMES[part.ordinal()].toLowerCase(Locale.ROOT));
		}
		return names.isEmpty() ? "nothing" : String.join(" and ", names);
	}

	private static final int IMPORT_POPOVER_HEIGHT = POPOVER_PAD + 26 + BUTTON_HEIGHT + 6 + BUTTON_HEIGHT + POPOVER_PAD;

	/** Import with a config in the clipboard: pasting it is the first choice, typing or pasting the text yourself the second. */
	private void drawImportPopover(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		popoverFrame(ui, IMPORT_POPOVER_HEIGHT);
		int left = popoverX + POPOVER_PAD;
		int full = POPOVER_WIDTH - 2 * POPOVER_PAD;
		int top = popoverY + POPOVER_PAD;
		ui.textAtScaledBold("Import a config", left, top, 1f, palette.text());
		ui.textAtScaled(ui.fitScaled("Your clipboard has a config in it.", full, TextScale.SMALL), left, top + 12, TextScale.SMALL, palette.textSecondary());
		top += 26;
		button(ui, mouseX, mouseY, left, top, full, BUTTON_HEIGHT, "Paste config from clipboard", true, false, true, 9, ACT_PASTE_CLIPBOARD, popoverHits);
		top += BUTTON_HEIGHT + 6;
		button(ui, mouseX, mouseY, left, top, full, BUTTON_HEIGHT, "Paste the text yourself", false, false, true, 10, ACT_PASTE_TEXT, popoverHits);
	}

	// ------------------------------------------------------------------------------------------------------- name panel

	/** The panel below the configs: a field for a name (saving, renaming) or a pasted config (importing), and its actions. */
	private void drawPanel(Ui ui, int mouseX, int mouseY, int top) {
		Palette palette = ui.palette;
		ui.rounded(x, top, width, PANEL_HEIGHT, Radius.LG, palette.control());
		ui.ring(x, top, width, PANEL_HEIGHT, Radius.LG, 1, palette.controlBorder());
		// Consume clicks into the field (or anywhere on the panel) here: the window gives keyboard focus to the control that consumed the click,
		// and an unconsumed click took the focus away from the field, so typed text went nowhere.
		pageHits.add(x, top, width, PANEL_HEIGHT, ACT_CONSUME);
		String title = switch (mode) {
			case SAVE -> "Save the settings of now as a new config";
			case IMPORT -> "Paste a config someone shared with you (Ctrl+V)";
			default -> "Rename or overwrite \"" + (editing == null ? "" : editing.name()) + "\"";
		};
		ui.textAtScaled(ui.fit(title, width - 20), x + 10, top + 8, TextScale.SMALL, palette.textSecondary());
		int fieldY = top + 22;
		boolean renaming = mode == Mode.EDIT && editing != null && !editing.saved().automatic();
		int buttonsWidth = renaming ? 3 * 70 + 12 : 2 * 64 + 6;
		int fieldWidth = width - 20 - buttonsWidth - 8;
		ui.rounded(x + 10, fieldY, fieldWidth, 24, Radius.SM, Ui.withOpacity(0xFF000000, 0.25f));
		ui.ring(x + 10, fieldY, fieldWidth, 24, Radius.SM, 1, ui.accent);
		String shown = typed.isEmpty() ? (mode == Mode.IMPORT ? "Config text" : "Name") : mode == Mode.IMPORT ? tail(ui, typed, fieldWidth - 16) : typed;
		String fitted = ui.fit(shown, fieldWidth - 16);
		if (!typed.isEmpty()) {
			selection.draw(ui, x + 18, fieldY + 12, ui.width(fitted));
		}
		ui.textMiddle(fitted, x + 18, fieldY + 12, typed.isEmpty() ? palette.textSecondary() : palette.text());
		if (!selection.all() && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caret = typed.isEmpty() ? 0 : ui.width(fitted) + 1;
			ui.fill(x + 18 + caret, fieldY + 6, 1, 12, palette.text());
		}
		int bx = x + width - 10 - buttonsWidth;
		boolean named = !typed.isBlank();
		if (renaming) {
			button(ui, mouseX, mouseY, bx, fieldY, 70, 24, "Rename", false, false, named, 11, ACT_PANEL_RENAME, pageHits);
			button(ui, mouseX, mouseY, bx + 76, fieldY, 70, 24, "Overwrite", true, false, named, 12, ACT_PANEL_CONFIRM, pageHits);
			button(ui, mouseX, mouseY, bx + 152, fieldY, 70, 24, "Cancel", false, false, true, 13, ACT_PANEL_CANCEL, pageHits);
		} else {
			String confirm = switch (mode) {
				case SAVE -> "Save";
				case IMPORT -> "Import";
				default -> "Rename";
			};
			button(ui, mouseX, mouseY, bx, fieldY, 64, 24, confirm, true, false, named, 11, mode == Mode.EDIT ? ACT_PANEL_RENAME : ACT_PANEL_CONFIRM, pageHits);
			button(ui, mouseX, mouseY, bx + 70, fieldY, 64, 24, "Cancel", false, false, true, 13, ACT_PANEL_CANCEL, pageHits);
		}
	}

	/** @return the end of a long pasted text that fits the width, so the field shows what was pasted last */
	private static String tail(Ui ui, String text, int room) {
		int start = Math.max(0, text.length() - 80);
		String end = text.substring(start);
		while (end.length() > 1 && ui.width(end) > room) {
			end = end.substring(1);
		}
		return end;
	}

	private void button(Ui ui, int mouseX, int mouseY, int bx, int by, int w, int h, String label, boolean primary, boolean danger, boolean enabled, int slot,
						int action, Hits target) {
		Palette palette = ui.palette;
		boolean over = enabled && mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
		buttonHover[slot] = ui.approach(buttonHover[slot], over ? 1f : 0f, Ui.HOVER_RATE);
		float hover = buttonHover[slot];
		if (!enabled) {
			// disabled: only an outline and dimmed text, never the primary or destructive color, so it clearly does nothing
			ui.ring(bx, by, w, h, h / 2f, 1, Ui.withOpacity(palette.controlBorder(), 0.6f));
			ui.textCentered(ui.fit(label, w - 8), bx + w / 2, by + h / 2, Ui.withOpacity(palette.textSecondary(), 0.55f));
			return;
		}
		boolean red = danger && confirmDelete;
		if (primary || red) {
			int base = red ? SystemColors.RED : ui.accent;
			ui.rounded(bx, by, w, h, h / 2f, Ui.mix(base, 0xFFFFFFFF, 0.14f * hover));
		} else {
			ui.rounded(bx, by, w, h, h / 2f, palette.control());
			ui.ring(bx, by, w, h, h / 2f, 1, danger ? Ui.withOpacity(SystemColors.RED, 0.45f) : palette.controlBorder());
			if (hover > 0f) {
				ui.rounded(bx, by, w, h, h / 2f, Ui.withOpacity(palette.hover(), hover));
			}
		}
		int color = primary || red ? 0xFFFFFFFF : danger ? SystemColors.RED_TEXT : palette.text();
		ui.textCentered(ui.fit(label, w - 8), bx + w / 2, by + h / 2, color);
		target.add(bx, by, w, h, action);
	}

	// ------------------------------------------------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		int action;
		if (popover != Popover.NONE) {
			action = popoverHits.at(mouseX, mouseY);
			if (action < 0) {
				// a click beside the popover closes it
				closePopover();
				onChanged.run();
				return true;
			}
		} else {
			action = pageHits.at(mouseX, mouseY);
			if (action < 0) {
				return false;
			}
		}
		run(action, mouseX, mouseY);
		refresh(true);
		onChanged.run();
		return true;
	}

	private void run(int action, double mouseX, double mouseY) {
		if (action >= ACT_OPTION) {
			BooleanSetting option = configs().loadOptions().get(action - ACT_OPTION);
			option.set(!option.get());
			UiSounds.toggle(option.get());
			return;
		}
		if (action >= ACT_LOOK) {
			configs().tryLook(Presets.ALL.get(action - ACT_LOOK));
			return;
		}
		if (action >= ACT_RESTORE_CARD) {
			configs().restore(entries.get(action - ACT_RESTORE_CARD));
			return;
		}
		if (action >= ACT_OWN) {
			openConfigPopover(action - ACT_OWN);
			return;
		}
		ConfigsFeature.Entry chosen = popover == Popover.CONFIG && popoverIndex < entries.size() ? entries.get(popoverIndex) : null;
		switch (action) {
			case ACT_SAVE_NEW -> open(Mode.SAVE, null);
			case ACT_LOAD -> {
				if (chosen != null) {
					closePopover();
					configs().load(chosen);
				}
			}
			case ACT_RESTORE -> {
				if (chosen != null) {
					closePopover();
					configs().restore(chosen);
				}
			}
			case ACT_OVERWRITE -> {
				if (chosen != null) {
					closePopover();
					overwrite(chosen);
				}
			}
			case ACT_RENAME -> {
				closePopover();
				open(Mode.EDIT, chosen);
			}
			case ACT_EXPORT -> {
				closePopover();
				export(chosen);
			}
			case ACT_DELETE -> delete(chosen);
			case ACT_POPOVER_CLOSE -> closePopover();
			case ACT_PASTE_CLIPBOARD -> {
				closePopover();
				add(Minecraft.getInstance().keyboardHandler.getClipboard());
			}
			case ACT_PASTE_TEXT -> {
				closePopover();
				open(Mode.IMPORT, null);
			}
			case ACT_BAR_BACK -> configs().goBack();
			case ACT_BAR_KEEP -> configs().keep();
			case ACT_PANEL_CONFIRM -> confirm();
			case ACT_PANEL_CANCEL -> close();
			case ACT_PANEL_RENAME -> rename();
			default -> {
				// a click into the panel or a popover only keeps the keyboard and the popover there
			}
		}
	}

	private void openConfigPopover(int index) {
		if (popover == Popover.CONFIG && popoverIndex == index) {
			closePopover();
			return;
		}
		int columns = ownColumns();
		int cardWidth = cardWidth(columns);
		int cx = x + (index % columns) * (cardWidth + GAP);
		int cy = y + LABEL_HEIGHT + (index / columns) * (OWN_HEIGHT + GAP);
		openPopover(Popover.CONFIG, index, cx, cy, cy + OWN_HEIGHT, configPopoverHeight(entries.get(index)));
	}

	@Override
	public boolean wantsKeyboard() {
		return mode != Mode.NONE;
	}

	@Override
	public void loseFocus() {
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (mode == Mode.NONE || codepoint < 32) {
			return false;
		}
		String next = selection.typed(typed, new String(Character.toChars(codepoint)));
		if (next.length() <= maxTyped()) {
			typed = next;
		}
		return true;
	}

	private int maxTyped() {
		return mode == Mode.IMPORT ? MAX_PASTED : MAX_TYPED;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (popover != Popover.NONE && key == InputConstants.KEY_ESCAPE) {
			closePopover();
			onChanged.run();
			return true;
		}
		if (mode == Mode.NONE) {
			return false;
		}
		if (selection.key(key, modifiers, () -> typed, value -> typed = value, maxTyped())) {
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			if (!typed.isEmpty()) {
				typed = typed.substring(0, typed.offsetByCodePoints(typed.length(), -1));
			}
		} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (mode == Mode.EDIT && editing != null && editing.saved().automatic()) {
				rename();
			} else {
				confirm();
			}
			refresh(true);
			onChanged.run();
		} else if (key == InputConstants.KEY_ESCAPE) {
			close();
			onChanged.run();
		}
		return true;
	}

	// ---------------------------------------------------------------------------------------------------------- actions

	private void open(Mode wanted, ConfigsFeature.Entry entry) {
		if (mode == wanted && editing == entry) {
			close();
			return;
		}
		if (wanted == Mode.EDIT && entry == null) {
			return;
		}
		closePopover();
		mode = wanted;
		editing = entry;
		typed = wanted == Mode.EDIT ? entry.name() : "";
		selection.clear();
		UiSounds.click();
		onChanged.run();
	}

	private void close() {
		mode = Mode.NONE;
		editing = null;
		typed = "";
		selection.clear();
	}

	/** Import: with a config in the clipboard a popover offers to paste it; otherwise the panel to paste one into opens. */
	private void importConfig() {
		if (FeatureManager.isSettings(Minecraft.getInstance().keyboardHandler.getClipboard())) {
			int anchorX = x + width - POPOVER_WIDTH;
			popover = Popover.NONE;
			openPopover(Popover.IMPORT, 0, anchorX, y, y, IMPORT_POPOVER_HEIGHT);
			popoverY = viewTop + 2;
			onChanged.run();
		} else {
			open(Mode.IMPORT, null);
		}
	}

	/** Adds a config from text, as "Imported" (or the next free name). */
	private void add(String text) {
		String name = ConfigStore.freeName("Imported");
		if (ConfigStore.add(name, text)) {
			UiSounds.play(UiSounds.Cue.DONE);
			Toasts.info("Config imported", "\"" + name + "\" is in your configs. Click it to load it or give it a name.");
			close();
		} else {
			UiSounds.play(UiSounds.Cue.FAIL);
			Toasts.error("config.add", "Could not import the config", "This is not a config that was copied from Lumio.");
		}
	}

	/** Overwrites a config of the player's with the current settings, under its name. */
	private void overwrite(ConfigsFeature.Entry chosen) {
		if (ConfigStore.save(chosen.name())) {
			UiSounds.play(UiSounds.Cue.DONE);
			Toasts.info("Overwrote \"" + chosen.name() + "\"", "It has your current settings now.");
			configs().setActive(chosen.id());
		} else {
			Toasts.error("config.save", "Could not overwrite the config", "\"" + chosen.name() + "\" could not be written.");
		}
	}

	/** Save: creates a new config from the current settings. Edit: saves the current settings into the config under the entered name. Import: adds the pasted text. */
	private void confirm() {
		if (mode == Mode.IMPORT) {
			add(typed);
			return;
		}
		String name = typed.strip();
		if (name.isEmpty()) {
			return;
		}
		if (mode == Mode.SAVE) {
			String free = ConfigStore.freeName(name);
			if (ConfigStore.save(free)) {
				configs().rememberColors(free);
				UiSounds.play(UiSounds.Cue.DONE);
				Toasts.info("Config saved", "\"" + free + "\" has the settings of now.");
				configs().setActive("own:" + free);
				close();
			} else {
				Toasts.error("config.save", "Could not save the config", "The name can not be used as the name of a file.");
			}
		} else if (mode == Mode.EDIT && editing != null) {
			if (!name.equals(editing.name())) {
				ConfigStore.delete(editing.saved());
				configs().renameColors(editing.name(), name);
			}
			if (ConfigStore.save(name)) {
				UiSounds.play(UiSounds.Cue.DONE);
				Toasts.info("Config saved", "\"" + name + "\" has the settings of now.");
				configs().setActive("own:" + name);
				close();
			} else {
				Toasts.error("config.save", "Could not save the config", "The name can not be used as the name of a file.");
			}
		}
	}

	/** Renames the config to the entered name, keeping its settings; a renamed backup becomes a config of the player's. */
	private void rename() {
		String name = typed.strip();
		if (editing == null || name.isEmpty() || name.equals(editing.name())) {
			close();
			return;
		}
		boolean wasActive = configs().isActive(editing);
		String renamed = ConfigStore.rename(editing.saved(), name);
		if (renamed != null) {
			configs().renameColors(editing.name(), renamed);
			UiSounds.play(UiSounds.Cue.DONE);
			Toasts.info("Config renamed", "\"" + editing.name() + "\" is \"" + renamed + "\" now.");
			if (wasActive) {
				configs().setActive("own:" + renamed);
			}
			close();
		} else {
			Toasts.error("config.rename", "Could not rename the config", "The name can not be used as the name of a file.");
		}
	}

	private void delete(ConfigsFeature.Entry chosen) {
		if (chosen == null) {
			return;
		}
		if (confirmDelete) {
			configs().delete(chosen);
			closePopover();
			close();
			UiSounds.play(UiSounds.Cue.CLOSE);
		} else {
			confirmDelete = true;
			confirmAt = System.currentTimeMillis();
			UiSounds.click();
		}
	}

	private void export(ConfigsFeature.Entry chosen) {
		if (chosen == null) {
			return;
		}
		String text = ConfigStore.read(chosen.saved());
		if (text != null) {
			Minecraft.getInstance().keyboardHandler.setClipboard(text);
			UiSounds.click();
			Toasts.info("Config copied", "\"" + chosen.name() + "\" is in the clipboard, to give it to someone.");
		}
	}
}
