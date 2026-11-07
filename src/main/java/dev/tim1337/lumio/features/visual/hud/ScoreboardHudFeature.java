package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.util.ScoreLines;
import dev.tim1337.lumio.api.experimental.util.ShortNumber;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ScoreboardProvider;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.ActionRowSetting;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ChipsInfoSetting;
import dev.tim1337.lumio.config.setting.OrderedListSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.ui.Eased;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudParts;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The server's sidebar scoreboard as a panel like the others, moved and sized in the HUD editor; switched off, the game draws its own. Vanilla: the panel
 * it was before the HUD's redesign, a title over a line and the lines with their numbers, all the numbers the server sends. Custom: calm and airy, labels
 * on the left and values on the right with their digits in equal cells, small gaps instead of empty lines, in the crisp menu font at sizes it is built for,
 * read ten times a second so a server rewriting its lines does not make it flicker. Plain red score numbers say nothing and are left out there.
 */
public class ScoreboardHudFeature extends HudPanelFeature {
	/** The look of the scoreboard: the panel of before the HUD's redesign, or the calm one. */
	public enum Look {
		VANILLA("Game"),
		CUSTOM("Custom");

		private final Component displayName;

		Look(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** A line: its text, and the number the server formats for it (shown on the right), or none. */
	private record Row(Component line, HudText text, TabularText value, TabularText shortValue, String label, ScoreboardLines.Icon icon, HudText score, boolean blank, boolean separator) {
	}

	// the custom look in panel pixels: padding, line height, the title's row, the small gap instead of an empty line, the room before a value, corners
	private static final int PAD = 10;
	private static final int ROW = 13;
	private static final int TITLE_ROW = 18;
	private static final int GROUP_GAP = 6;
	private static final int SCORE_GAP = 14;
	/** A line's icon: one size, as high as the text, in a box of its own, and the gap after it. */
	private static final int ICON = 9;
	private static final int ICON_GAP = 4;
	/** What a value just gained: its size, its opacity, how far from the value, and how far it slides in. */
	private static final float GAIN_SIZE = TextScale.SMALL;
	private static final float GAIN_OPACITY = 0.65f;
	private static final float GAIN_GAP = 4f;
	private static final float GAIN_SLIDE = 4f;
	/** The widest the panel gets with an automatic width. */
	private static final int MOST_AUTO_WIDTH = 500;
	private static final float RADIUS = Radius.LG;
	/** The server address at the bottom: smaller and quiet. */
	private static final float ADDRESS_SIZE = TextScale.SMALL;
	private static final float ADDRESS_OPACITY = 0.55f;
	// the Vanilla look, as it was before the HUD's redesign
	private static final int OLD_PAD = 8;
	private static final int OLD_ROW = 12;
	private static final int OLD_TITLE = 20;
	private static final int OLD_GAP = 14;
	private static final int OLD_TITLE_RULE = 6;
	private static final float RULE_OPACITY = 0.16f;
	private static final float OLD_RADIUS = 12f;
	private static final int MIN_WIDTH = 60;
	/** The height of a line of text in panel pixels, to center it in its row. */
	private static final float TEXT_HEIGHT = 8f;
	/** The narrowest the content may be dragged to while it still fits, in panel pixels. */
	private static final int MIN_CONTENT_WIDTH = 50;
	/** Extra room so the text never touches the edge after rounding. */
	private static final int WIDTH_SLACK = 2;
	/** The lines are read from the scoreboard at most this often, and the panel shrinks only after a line stayed shorter this long. */
	private static final long REBUILD_NANOS = 100_000_000L;
	private static final long SHRINK_AFTER = 3000L;
	/** The server address at the bottom, which its chip can hide: "www.hypixel.net". */
	private static final Pattern ADDRESS = Pattern.compile("^(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)+$", Pattern.CASE_INSENSITIVE);

	private final EnumSetting<Look> look = add(new EnumSetting<>("style", Component.literal("Look"), Look.CUSTOM, Look::displayName,
		EnumSetting.Style.SEGMENTED), "Look");
	private final BooleanSetting showTitle = add(new BooleanSetting("show_title", Component.literal("Title"), true), "Shows");
	private final BooleanSetting showAddress = add(new BooleanSetting("show_address", Component.literal("Server address"), true), "Shows");
	private final BooleanSetting showScores = add(new BooleanSetting("show_scores", Component.literal("Server numbers"), true), "Shows");
	private final FloatSetting maxRows = add(new FloatSetting("max_rows", Component.literal("Max rows"), 15f, 3f, 15f, 0), "General");
	/** What the values just gained, by their label, kept until it has faded even when the server wrote it for a moment only. */
	private final ScoreGains gains = new ScoreGains();
	private final FloatSetting maxWidth = add(new FloatSetting("max_width", Component.literal("Max width"), 260f, 100f, 500f, 0), "General",
		"Longer lines end with …");

	private HudText title = new HudText(Component.literal("Scoreboard"));
	private Component titleSource;
	private List<Row> rows = List.of();
	/** The index of the server address among the rows, or -1. */
	private int addressRow = -1;
	private boolean live;
	/** Target and current panel size; the current size animates when lines or their text change. */
	private int targetWidth = MIN_WIDTH;
	private int targetHeight = 60;
	private final Eased widthEase = new Eased();
	private final Eased heightEase = new Eased();
	private int width = MIN_WIDTH;
	private int height = 60;
	private long preparedAt;
	private long narrowerSince;
	private long lowerSince;
	/** The widest content measured when drawn last, for the next target width. */
	private float measuredWidth;

	public ScoreboardHudFeature() {
		super("scoreboard_hud", "Scoreboard", "The scoreboard of a server, as a panel you can move and size.", false, Anchor.RIGHT, false, 3f, 0f);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("list");
		summary("The scoreboard, as a panel you can move and size");
		offerWidth(MIN_WIDTH + 60);
		// where the game puts its sidebar: at the right edge, centered top to bottom
		placeCenteredVertically();
	}

	// ----------------------------------------------------------------------------------------------------- settings card

	private ActionRowSetting linesRow;
	private ChipsInfoSetting linesChips;
	private SettingsView linesView;

	/** The lines an add-on picks and orders (SkyBlock's): one row with a button to a sheet, and what is on as chips under it. */
	private SettingsView linesView() {
		if (linesView == null) {
			List<Setting<?>> own = new ArrayList<>();
			ScoreboardProvider lines = Addons.scoreboard();
			if (lines != null) {
				for (AddonSetting<?> setting : lines.settings()) {
					if (setting instanceof Setting<?> known) {
						own.add(known);
					}
				}
			}
			linesView = new SettingsView("scoreboard_lines", lines == null ? "Lines" : lines.settingsHeading(), "Pick and drag to order", "list", SystemColors.GRAY_RGB,
				List.of(new SettingsView.Group("", own))).asSheet();
		}
		return linesView;
	}

	private List<String> linesOn() {
		List<String> on = new ArrayList<>();
		for (Setting<?> setting : linesView().settings()) {
			if (setting instanceof OrderedListSetting list) {
				for (String id : list.enabledInOrder()) {
					OrderedListSetting.Option option = list.option(id);
					if (option != null && !option.repeatable()) {
						on.add(option.title());
					}
				}
			}
		}
		return on;
	}

	@Override
	public Setting<?> settingOf(String key) {
		if (key.equals("lines_row")) {
			if (linesRow == null) {
				linesRow = new ActionRowSetting("lines_row", Component.literal("Lines"), new ActionRowSetting.Look(() -> null, () -> -1,
					() -> Addons.scoreboard() == null ? "Lines" : Addons.scoreboard().settingsHeading(), () -> "Pick and order lines in a sheet", () -> null, () -> null, () -> "Edit…"),
					() -> MacScreen.openSubPage(linesView()));
				linesRow.visibleWhen(() -> Addons.scoreboard() != null);
			}
			return linesRow;
		}
		if (key.equals("lines_chips")) {
			if (linesChips == null) {
				linesChips = new ChipsInfoSetting("lines_chips", this::linesOn);
				linesChips.visibleWhen(() -> Addons.scoreboard() != null);
			}
			return linesChips;
		}
		return super.settingOf(key);
	}

	/** @return whether the game's own scoreboard is hidden because this panel shows instead, in either look */
	public boolean hidesVanilla() {
		return isEnabled();
	}

	private boolean custom() {
		return look.get() == Look.CUSTOM;
	}

	public EnumSetting<Look> lookSetting() {
		return look;
	}

	/** @return the objective the game shows in the sidebar: the one for the player's team color, otherwise the regular one */
	private static Objective sidebarObjective(Minecraft client, Scoreboard scoreboard) {
		Objective chosen = null;
		PlayerTeam team = scoreboard.getPlayersTeam(client.player.getScoreboardName());
		if (team != null && team.getColor().isPresent()) {
			chosen = scoreboard.getDisplayObjective(team.getColor().get().displaySlot());
		}
		return chosen != null ? chosen : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	@Override
	protected void prepare(Minecraft client) {
		long now = System.nanoTime();
		if (now - preparedAt >= REBUILD_NANOS || preparedAt == 0) {
			preparedAt = now;
			build(client);
		}
		boolean animated = ModFeatures.MENU.animations();
		widthLimits(MIN_CONTENT_WIDTH, targetWidth);
		float eased = widthEase.follow(targetWidth);
		// a width the player set is exact, also while its edge is dragged; the automatic width eases to the content
		width = customWidth() ? widthOr(targetWidth) : Math.round(animated ? eased : targetWidth);
		height = Math.round(animated ? heightEase.follow(targetHeight) : targetHeight);
		if (!animated) {
			heightEase.follow(targetHeight);
		}
	}

	/** Reads the lines from the scoreboard (at most ten times a second) and keeps them as rows; unchanged lines keep their laid out text. */
	private void build(Minecraft client) {
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = sidebarObjective(client, scoreboard);
		live = objective != null;
		List<Component> lines = new ArrayList<>();
		List<Component> scores = new ArrayList<>();
		Component name;
		ScoreboardProvider custom = Addons.scoreboard();
		if (live) {
			List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
				.filter(entry -> !entry.isHidden())
				.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
				.toList();
			NumberFormat objectiveFormat = objective.numberFormat();
			if (custom != null && custom.active()) {
				// an add-on picks the lines (SkyBlock's custom scoreboard): its lines in its order
				name = custom.title() != null ? custom.title() : objective.getDisplayName();
				List<Component> sidebar = new ArrayList<>();
				for (PlayerScoreEntry entry : entries) {
					sidebar.add(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()));
				}
				for (Component line : custom.lines(sidebar)) {
					lines.add(line);
					scores.add(null);
				}
				// the server address stays the last line, whatever lines are chosen; its chip decides whether it shows
				Component last = sidebar.isEmpty() ? null : sidebar.getLast();
				if (last != null && ADDRESS.matcher(plain(last)).matches() && (lines.isEmpty() || !plain(lines.getLast()).equals(plain(last)))) {
					lines.add(last);
					scores.add(null);
				}
			} else {
				name = objective.getDisplayName();
				for (PlayerScoreEntry entry : entries.subList(0, Math.min(entries.size(), Math.round(maxRows.get())))) {
					lines.add(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()));
					// the plain red numbers of the default format say nothing and are left out; numbers the server formats itself stay
					boolean ownFormat = entry.numberFormatOverride() != null || objectiveFormat != null;
					NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
					scores.add(ownFormat || !custom() ? entry.formatValue(format) : null);
				}
			}
		} else {
			name = Component.literal("SCOREBOARD").withStyle(style -> style.withColor(0xFFD65A).withBold(true));
			lines.add(Component.literal("Late Summer 14th"));
			lines.add(Component.literal(" 7:20pm").withStyle(style -> style.withColor(0xAAAAAA)));
			lines.add(Component.literal(" ⏣ Dwarven Mines").withStyle(style -> style.withColor(0x55FFFF)));
			lines.add(Component.empty());
			lines.add(Component.literal("Purse: ").append(Component.literal(custom == null ? "1,204,553" : ShortNumber.of("1,204,553")).withStyle(style -> style.withColor(0xFFAA00))));
			lines.add(Component.literal("Bits: ").append(Component.literal(custom == null ? "12,400" : ShortNumber.of("12,400")).withStyle(style -> style.withColor(0x55FFFF))));
			lines.add(Component.empty());
			lines.add(Component.literal("www.example.net").withStyle(style -> style.withColor(0xFFFF55)));
			for (int i = 0; i < lines.size(); i++) {
				scores.add(null);
			}
		}
		if (!Objects.equals(name, titleSource)) {
			titleSource = name;
			title = new HudText(name, 1f, custom());
		}
		rows = toRows(lines, scores);
		fitSize();
	}

	/** Keeps the lines as rows, reusing the laid out text of a line that did not change, so a server rewriting its lines does not flicker. */
	private List<Row> toRows(List<Component> lines, List<Component> scores) {
		List<Row> built = new ArrayList<>(lines.size());
		addressRow = -1;
		int last = lines.size() - 1;
		while (last >= 0 && plain(lines.get(last)).isEmpty()) {
			last--;
		}
		boolean custom = custom();
		boolean address = last >= 0 && scores.get(last) == null && ADDRESS.matcher(plain(lines.get(last))).matches();
		boolean sameLook = rowsCustom == custom;
		rowsCustom = custom;
		for (int i = 0; i <= last; i++) {
			Component line = lines.get(i);
			Component score = scores.get(i);
			Row before = sameLook && i < rows.size() ? rows.get(i) : null;
			boolean same = before != null && before.line().equals(line);
			HudText scoreText = score == null ? null : before != null && before.score() != null && before.score().source().equals(score) ? before.score()
				: new HudText(score, 1f, false);
			boolean blank = custom && plain(line).isEmpty() && score == null;
			boolean separator = line == ScoreLines.LINE;
			if (blank && !built.isEmpty() && built.getLast().blank()) {
				// several empty lines in a row are one gap, with its line if one of them has it
				if (!separator || built.getLast().separator()) {
					continue;
				}
				built.removeLast();
			}
			if (same) {
				built.add(new Row(line, before.text(), before.value(), before.shortValue(), before.label(), before.icon(), scoreText, blank, separator));
				if (address && i == last) {
					addressRow = built.size() - 1;
				}
				continue;
			}
			boolean isAddress = address && i == last;
			// the place's name stands as sent, also with a colon in it: it is never a label with a value
			ScoreboardLines.Icon location = custom && !isAddress ? ScoreboardLines.locationIcon(line) : null;
			ScoreboardLines.Split split = custom && score == null && location == null ? ScoreboardLines.split(line) : null;
			ScoreboardLines.Icon icon = location != null ? location : custom && !isAddress && split == null ? ScoreboardLines.icon(line) : null;
			Component shownLine = split != null ? split.label() : icon != null ? icon.rest() : line;
			HudText text = new HudText(shownLine, isAddress && custom ? ADDRESS_SIZE : 1f, false);
			TabularText value = split == null ? null : new TabularText(split.value());
			String shorter = split == null ? "" : ScoreboardLines.shortened(split.value().getString());
			TabularText shortValue = split == null || shorter.equals(split.value().getString()) ? null : new TabularText(Component.literal(shorter).withStyle(valueStyle(split.value())));
			String label = split == null ? "" : plain(split.label());
			if (split != null) {
				gains.see(label, split.gain(), split.value().getString(), System.currentTimeMillis());
			}
			built.add(new Row(line, text, value, shortValue, label, icon, scoreText, blank, separator));
			if (isAddress) {
				addressRow = built.size() - 1;
			}
		}
		return built;
	}

	/** @return the style of a value's first colored part, for it written shorter */
	private static net.minecraft.network.chat.Style valueStyle(Component value) {
		net.minecraft.network.chat.Style[] found = {net.minecraft.network.chat.Style.EMPTY};
		value.visit((style, part) -> {
			found[0] = style;
			return part.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(Boolean.TRUE);
		}, net.minecraft.network.chat.Style.EMPTY);
		return found[0];
	}

	/** Which look the rows were laid out for. */
	private boolean rowsCustom = true;

	/** @return a line as plain text, without color codes (servers also write them into the text itself) */
	private static String plain(Component line) {
		String text = ChatFormatting.stripFormatting(line.getString());
		return text == null ? "" : text.strip();
	}

	/** Sets the size the panel grows to at once and shrinks to only after a while, so a briefly shorter line does not make it pulse. */
	private void fitSize() {
		int wantedHeight = contentHeight(HudEditScreen.isOpen());
		int wantedWidth = Mth.clamp((int) Math.ceil(measuredWidth) + PAD * 2 + WIDTH_SLACK, MIN_WIDTH, custom() ? MOST_AUTO_WIDTH : Math.max(MIN_WIDTH, Math.round(maxWidth.get())));
		long now = System.currentTimeMillis();
		if (wantedWidth >= targetWidth) {
			targetWidth = wantedWidth;
			narrowerSince = 0;
		} else if (narrowerSince == 0) {
			narrowerSince = now;
		} else if (now - narrowerSince > SHRINK_AFTER) {
			targetWidth = wantedWidth;
			narrowerSince = 0;
		}
		if (wantedHeight >= targetHeight) {
			targetHeight = wantedHeight;
			lowerSince = 0;
		} else if (lowerSince == 0) {
			lowerSince = now;
		} else if (now - lowerSince > SHRINK_AFTER) {
			targetHeight = wantedHeight;
			lowerSince = 0;
		}
	}

	private int contentHeight(boolean editing) {
		if (!custom()) {
			return OLD_PAD * 2 + (showTitle.get() || editing ? OLD_TITLE : 0) + rows.size() * OLD_ROW - 2;
		}
		int h = PAD * 2;
		if (showTitle.get() || editing) {
			h += TITLE_ROW;
		}
		for (int i = 0; i < rows.size(); i++) {
			if (i != addressRow || showAddress.get() || editing) {
				h += rows.get(i).blank() ? GROUP_GAP : ROW;
			}
		}
		return h;
	}

	@Override
	protected boolean shown(Minecraft client) {
		return live && !rows.isEmpty();
	}

	@Override
	protected boolean inEditorWhenHidden() {
		return true;
	}

	@Override
	protected int panelWidth() {
		return width;
	}

	@Override
	protected int panelHeight() {
		return height;
	}

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		if (custom()) {
			drawCustom(ui, graphics, client);
		} else {
			drawOld(ui, graphics, client);
		}
	}

	/** The calm look: a title, labels on the left and values on the right, small gaps between the groups. */
	private void drawCustom(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, width, height, RADIUS);
		var font = client.font;
		float room = width - 2f * PAD;
		float widest = 0f;
		float y = PAD;
		graphics.enableScissor(0, 0, width, height);
		int text = HudStyle.text();
		float titleAlpha = HudParts.alpha(showTitle.get());
		if (titleAlpha > 0f) {
			title.layout(font, room);
			widest = Math.max(widest, title.naturalWidth(font));
			title.draw(graphics, font, (width - title.width()) / 2f, y, Ui.withOpacity(text, titleAlpha), false);
			part("Title", showTitle, PAD, y, width - 2 * PAD, TITLE_ROW - GROUP_GAP);
			y += TITLE_ROW;
		}
		boolean editing = HudEditScreen.isOpen();
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			boolean isAddress = i == addressRow;
			if (isAddress && !showAddress.get() && !editing) {
				continue;
			}
			if (row.blank()) {
				if (row.separator()) {
					ui.fill(PAD, Math.round(y + (GROUP_GAP - 1) / 2f), width - 2 * PAD, 1, HudStyle.line(RULE_OPACITY));
				}
				y += GROUP_GAP;
				continue;
			}
			float shown = Mth.clamp((height - PAD - y) / ROW, 0f, 1f);
			if (shown <= 0f) {
				break;
			}
			float alpha = shown * (isAddress ? HudParts.alpha(showAddress.get()) : 1f);
			float rowY = y + (ROW - TEXT_HEIGHT) / 2f;
			if (isAddress) {
				row.text().layout(font, room);
				widest = Math.max(widest, row.text().naturalWidth(font));
				row.text().draw(graphics, font, (width - row.text().width()) / 2f, rowY, Ui.withOpacity(text, alpha * ADDRESS_OPACITY), false);
				part("Server address", showAddress, PAD, y, width - 2 * PAD, ROW);
				y += ROW;
				continue;
			}
			float valueWidth = 0f;
			float textX = PAD;
			float textRoom = room;
			if (row.icon() != null) {
				drawIcon(ui, row.icon(), PAD, Math.round(y + (ROW - ICON) / 2f), Ui.withOpacity(row.icon().rgb() < 0 ? text : HudStyle.readable(0xFF000000 | row.icon().rgb()), alpha));
				textX += ICON + ICON_GAP;
				textRoom -= ICON + ICON_GAP;
			}
			if (row.value() != null) {
				float whole = row.value().width(font);
				float labelWidth = row.text().naturalWidth(font);
				// the value is never cut: where the width is set and it does not fit, it is written shorter first
				TabularText written = customWidth() && row.shortValue() != null && ScoreGains.rowWidth(labelWidth, whole, SCORE_GAP) > room ? row.shortValue() : row.value();
				float shownWidth = written.width(font);
				valueWidth = shownWidth + SCORE_GAP;
				// at the row's right edge, always: what it gained takes no room in the row, and no other line moves for it
				written.draw(graphics, font, width - PAD, rowY, Ui.withOpacity(text, alpha));
				widest = Math.max(widest, ScoreGains.rowWidth(labelWidth, whole, SCORE_GAP));
				drawGain(ui, row.label(), textX + labelWidth, ScoreGains.valueLeft(width - PAD, shownWidth), width - PAD, rowY, alpha);
			}
			boolean score = row.score() != null && (showScores.get() || editing);
			if (score) {
				row.score().layout(font, room * 0.5f);
				valueWidth = row.score().width() + SCORE_GAP;
				float scoreAlpha = alpha * HudParts.alpha(showScores.get());
				row.score().draw(graphics, font, width - PAD - row.score().width(), rowY, Ui.withOpacity(text, scoreAlpha), false);
				part("Server numbers", showScores, width - PAD - row.score().width(), y, row.score().width(), ROW);
			}
			row.text().layout(font, textRoom - valueWidth);
			// the panel grows to the whole texts, measured uncut, so a line is only cut where the width set does not allow more
			widest = Math.max(widest, row.text().naturalWidth(font) + valueWidth + textX - PAD);
			row.text().draw(graphics, font, textX, rowY, Ui.withOpacity(text, alpha), false);
			y += ROW;
		}
		graphics.disableScissor();
		measuredWidth = widest;
		if (editing) {
			fitSize();
		}
	}

	/** Draws a line's icon, centered in its box: the mod's icon, or the symbol from the server. */
	private static void drawIcon(Ui ui, ScoreboardLines.Icon icon, int x, int y, int color) {
		if (icon.name() != null) {
			ui.icon(icon.name(), x, y, ICON, color);
		} else {
			ui.textAtScaled(icon.symbol(), x + (ICON - ui.widthScaled(icon.symbol(), 1f)) / 2f, y + (ICON - TEXT_HEIGHT) / 2f, 1f, color);
		}
	}

	/** Draws what a value just gained as a small overlay, over the row and taking no room in it: in the gap left of the value, or above the value's right edge where the gap is too small; it slides in and fades. */
	private void drawGain(Ui ui, String label, float labelEnd, float valueLeft, float right, float y, float alpha) {
		long now = System.currentTimeMillis();
		String gain = gains.text(label, now);
		if (gain == null) {
			return;
		}
		float gainWidth = ui.widthScaled(gain, GAIN_SIZE);
		boolean beside = ScoreGains.place(labelEnd, valueLeft, gainWidth, GAIN_GAP) == ScoreGains.Place.BESIDE;
		float x = beside ? valueLeft - GAIN_GAP - gainWidth : right - gainWidth;
		float gainY = beside ? y + (TEXT_HEIGHT - TEXT_HEIGHT * GAIN_SIZE) / 2f : y - TEXT_HEIGHT * GAIN_SIZE;
		ui.textAtScaled(gain, x, gainY + GAIN_SLIDE * gains.slide(label, now), GAIN_SIZE, Ui.withOpacity(HudStyle.text(), GAIN_OPACITY * gains.opacity(label, now) * alpha));
	}

	private void drawOld(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, width, height, OLD_RADIUS);
		var font = client.font;
		float room = width - 2f * OLD_PAD;
		float widest = 0f;
		int y = OLD_PAD;
		graphics.enableScissor(0, 0, width, height);
		int text = HudStyle.text();
		boolean editing = HudEditScreen.isOpen();
		float titleAlpha = HudParts.alpha(showTitle.get());
		if (titleAlpha > 0f) {
			title.layout(font, room);
			widest = Math.max(widest, title.naturalWidth(font));
			title.draw(graphics, font, (width - title.width()) / 2f, y + 1, Ui.withOpacity(text, titleAlpha), false);
			ui.fill(OLD_PAD, y + OLD_TITLE - OLD_TITLE_RULE, width - 2 * OLD_PAD, 1, HudStyle.line(RULE_OPACITY * titleAlpha));
			part("Title", showTitle, OLD_PAD, y, width - 2 * OLD_PAD, OLD_TITLE - OLD_TITLE_RULE);
			y += OLD_TITLE;
		}
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			float shown = Mth.clamp((height - OLD_PAD - y) / (float) OLD_ROW, 0f, 1f);
			if (shown <= 0f) {
				break;
			}
			float scoreWidth = 0f;
			boolean score = row.score() != null && (showScores.get() || editing);
			if (score) {
				row.score().layout(font, room * 0.5f);
				scoreWidth = row.score().width() + OLD_GAP;
				float scoreAlpha = shown * HudParts.alpha(showScores.get());
				row.score().draw(graphics, font, width - OLD_PAD - row.score().width(), y, Ui.withOpacity(accentArgb(), scoreAlpha), false);
				part("Server numbers", showScores, width - OLD_PAD - row.score().width(), y, row.score().width(), OLD_ROW);
			}
			if (row.separator()) {
				ui.fill(OLD_PAD, y + (OLD_ROW - 1) / 2, width - 2 * OLD_PAD, 1, HudStyle.line(RULE_OPACITY));
			}
			row.text().layout(font, room - scoreWidth);
			widest = Math.max(widest, row.text().naturalWidth(font) + scoreWidth);
			row.text().draw(graphics, font, OLD_PAD, y, Ui.withOpacity(text, shown), false);
			y += OLD_ROW;
		}
		graphics.disableScissor();
		measuredWidth = widest;
		if (editing) {
			fitSize();
		}
	}
}
