package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ActionBarParts;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.ui.Eased;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The action bar text (on SkyBlock: defense, mana spent on an ability, ...) as a movable panel of its own. It animates in when there is text and out
 * when there is none, and its width follows the text smoothly. The vanilla action bar is not drawn while this is enabled.
 */
public class ActionBarHudFeature extends HudPanelFeature {
	private static final int PAD = 7;
	private static final int HEIGHT = 14;
	private static final float RADIUS = 6f;
	/** The pill is lighter than other panels, as in the mockup. */
	private static final float PILL_OPACITY = 0.62f;
	/** How near its place in the stack the panel has to be dropped to dock there again, in GUI pixels. */
	private static final float DOCK_REACH = 24f;
	private static final int MAX_TEXT = 360;

	private final FloatSetting seconds = add(new FloatSetting("seconds", Component.literal("Display time"), 3f, 1f, 8f, 1), "General", "In seconds, after the last text");
	private final EnumSetting<BarFont> font = add(new EnumSetting<>("font", Component.literal("Font"), BarFont.MENU,
		BarFont::displayName, EnumSetting.Style.SEGMENTED), "General");

	/** Whether the text sits in the stack above the hotbar, above the item name (otherwise where it was placed in the editor). */
	private final BooleanSetting docked = add(new BooleanSetting("docked", Component.literal("Above the hotbar"), true), "General");

	private final BooleanSetting hideText = add(new BooleanSetting("hide_parts", Component.literal("Hide duplicate stats"), true), "Content",
		"Leaves out what the status bars show");
	private final BooleanSetting removeHealth = new BooleanSetting("remove_health", Component.literal("Health"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting removeMana = new BooleanSetting("remove_mana", Component.literal("Mana"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting removeDefense = new BooleanSetting("remove_defense", Component.literal("Defense"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting removeOverflow = new BooleanSetting("remove_overflow", Component.literal("Overflow mana"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting removeVitality = new BooleanSetting("remove_vitality", Component.literal("Vitality"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting removeFuel = new BooleanSetting("remove_fuel", Component.literal("Drill fuel"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting removeLocation =new BooleanSetting("remove_location", Component.literal("The name of the place"), false, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting removeOther = new BooleanSetting("remove_misc", Component.literal("Every other number and sign"), false, BooleanSetting.Style.CHECKBOX);
	private final MultiSelectSetting removeWhat = add(new MultiSelectSetting("remove_what", Component.literal("Hidden parts"),
		List.of(removeHealth, removeMana, removeDefense, removeOverflow, removeVitality, removeFuel, removeLocation, removeOther)), "Content");

	{
		// the parts it can hide are the ones an add-on reads from the action bar (SkyBlock's): offered only while one does; the list of parts
		// is under this switch, so it is hidden with it
		hideText.visibleWhen(() -> Addons.status() != null);
	}

	/** @return whether parts of the text are removed (when the status bars display them) */
	public boolean takesOut() {
		return hideText.get();
	}

	/** @return what is removed, as {@link ActionBarParts} bits */
	public int removeMask() {
		return (removeHealth.get() ? ActionBarParts.HEALTH : 0) | (removeMana.get() ? ActionBarParts.MANA : 0)
			| (removeDefense.get() ? ActionBarParts.DEFENSE : 0) | (removeOverflow.get() ? ActionBarParts.OVERFLOW : 0)
			| (removeVitality.get() ? ActionBarParts.VITALITY : 0) | (removeLocation.get() ? ActionBarParts.LOCATION : 0)
			| (removeOther.get() ? ActionBarParts.OTHER : 0)
			// fuel is only removed while it has a bar, so it is not lost when the bar is disabled
			| (removeFuel.get() && ModFeatures.STATUS != null && ModFeatures.STATUS.wantsFuel() ? ActionBarParts.FUEL : 0);
	}

	private static final String PIN_ICON = "pin";
	/** The pin as tall as the text's capital letters, which the icon's picture is at this size, and a space before the name. */
	private static final int PIN = 9;
	private static final int PIN_GAP = 3;
	private int pinRoom;
	private int pinRgb = -1;
	private FormattedCharSequence sequence = FormattedCharSequence.EMPTY;
	/** The text as received, which is re-laid out in the menu font. */
	private FormattedText source = FormattedText.EMPTY;
	private int textWidth;

	/** The text in the menu font, rebuilt only when the text, font or scale changes, since the HUD is drawn every frame. */
	private List<Ui.Piece> menuText;
	private float menuSymbolScale = 1f;
	private FormattedText menuSource;
	private float menuUnit;
	private boolean menuSemibold;

	/**
	 * @return the text in the menu font, in pieces: the symbols the menu font lacks (the server's ❤ ❈ ✎) are drawn in the game's font at the
	 *         text's size; null when it is drawn in the game font
	 */
	private List<Ui.Piece> menuText() {
		Boolean game = font.get().override();
		if (game == null ? ModFeatures.MENU.gameFont() : game) {
			return null;
		}
		Ui.setFontOverride(Boolean.FALSE);
		try {
			// without the background a heavier face, which reads better on the world
			boolean semibold = !hasBackground();
			float unit = Ui.menuFontUnit(1f, semibold);
			if (menuSource != source || unit != menuUnit || semibold != menuSemibold || menuText == null) {
				menuText = Ui.inMenuFontPieces(source, 1f, semibold);
				menuSymbolScale = Ui.symbolScale(1f, semibold);
				menuSource = source;
				menuUnit = unit;
				menuSemibold = semibold;
			}
		} finally {
			Ui.setFontOverride(null);
		}
		return menuText;
	}
	private long seen;
	private boolean has;

	private final Eased widthEase = new Eased();
	private final Eased shownEase = new Eased();
	private int width = 80;
	private float visible;

	public ActionBarHudFeature() {
		super("action_bar_hud", "Text above the hotbar", "What the server writes above the hotbar, as a panel that moves and is animated.", false, Anchor.CENTER, true, 0f, 94f);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("text");
		summary("What the server writes above the hotbar, as a panel");
		removeWhat.under(hideText);
		docked.visibleWhen(() -> false);
	}

	@Override
	protected float dockedBottom(int guiWidth, int guiHeight) {
		return docked.get() ? HudStack.actionBarBottom(guiWidth, guiHeight) : Float.NaN;
	}

	@Override
	protected float dockedCenterX(int guiWidth, int guiHeight) {
		return HudStack.centerX(guiWidth, guiHeight);
	}

	/** Dragging takes the text out of the stack; it is placed from where it was. */
	@Override
	public void hudDragStart() {
		if (docked.get()) {
			docked.set(false);
			super.hudAnchorNearest();
		}
	}

	/** Dropped near its place in the stack, the text docks there again. */
	@Override
	public void hudDragEnd() {
		Minecraft client = Minecraft.getInstance();
		int guiWidth = client.getWindow().getGuiScaledWidth();
		int guiHeight = client.getWindow().getGuiScaledHeight();
		int[] box = hudBounds();
		float centerOff = Math.abs(box[0] + box[2] / 2f - HudStack.centerX(guiWidth, guiHeight));
		float bottomOff = Math.abs(box[1] + box[3] - HudStack.actionBarBottom(guiWidth, guiHeight));
		if (centerOff <= DOCK_REACH && bottomOff <= DOCK_REACH) {
			float size = scale.get();
			hudReset();
			scale.set(size);
			docked.set(true);
		}
	}

	@Override
	public void hudAnchorNearest() {
		if (!docked.get()) {
			super.hudAnchorNearest();
		}
	}

	@Override
	public void hudReset() {
		super.hudReset();
		docked.reset();
	}

	/** Draws the pin before the text, if the text has one, in the symbol's color. */
	private void drawPin(Ui ui, float left, int alpha, int plain) {
		if (pinRoom > 0) {
			int rgb = pinRgb < 0 ? 0xFF000000 | plain : HudStyle.readable(0xFF000000 | pinRgb);
			ui.icon(PIN_ICON, Math.round(left), (HEIGHT - PIN) / 2, PIN, Ui.withOpacity(rgb, alpha / 255f));
		}
	}

	/** @return whether the vanilla action bar is hidden because this panel draws it */
	public boolean hidesVanilla() {
		return isEnabled();
	}

	/** Anything readable: not whitespace and not a server font glyph. Compiled once, since the text arrives several times per second. */
	private static final Pattern READABLE = Pattern.compile("[^\\s\\uE000-\\uF8FF]");

	/** Called with every action bar text, after health and mana were removed from it. */
	public void accept(Component message) {
		// nothing readable left (only spaces, color codes and resource pack glyphs): no text, and no empty panel
		if (message == null || !READABLE.matcher(TextColors.noColors(message.getString())).find()) {
			// keep the last text while the panel animates out, so it fades and sinks the way it came in
			has = false;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		// the place's symbol at the start of the text is the mod's pin: the fonts have no such glyph, and draw a tiny one in its place; the name stays as sent
		ScoreboardLines.Icon location = ScoreboardLines.locationIcon(message);
		pinRgb = location == null ? -1 : location.rgb();
		pinRoom = location == null ? 0 : PIN + PIN_GAP;
		Component text = location == null ? message : location.rest();
		FormattedText shown = text;
		int measured = client.font.width(text);
		if (measured > MAX_TEXT) {
			shown = FormattedText.composite(client.font.substrByWidth(text, MAX_TEXT - client.font.width("...")), FormattedText.of("..."));
		}
		shown = Ui.withSymbols(shown);
		sequence = Language.getInstance().getVisualOrder(shown);
		source = shown;
		textWidth = Math.min(client.font.width(shown), MAX_TEXT);
		seen = System.currentTimeMillis();
		has = true;
	}

	@Override
	protected void prepare(Minecraft client) {
		boolean editing = HudEditScreen.isOpen() || simulated();
		if (editing && !has) {
			pinRoom = 0;
			String sample = "Defense 142    -12 Mana (Ability)";
			sequence = Component.literal(sample).getVisualOrderText();
			source = Component.literal(sample);
			textWidth = client.font.width(sample);
		}
		// in the menu font the text has its own width, which the panel adopts
		List<Ui.Piece> inMenuFont = menuText();
		if (inMenuFont != null) {
			textWidth = Math.round(Ui.piecesWidth(client.font, inMenuFont, menuSymbolScale) * menuUnit);
		}
		boolean on = editing || (has && System.currentTimeMillis() - seen < seconds.get() * 1000f);
		visible = shownEase.follow(on ? 1f : 0f);
		width = Math.max(60, Math.round(widthEase.follow(textWidth + pinRoom + PAD * 2)));
	}

	@Override
	protected boolean shown(Minecraft client) {
		return visible > 0.01f && textWidth > 0;
	}

	@Override
	protected int panelWidth() {
		return width;
	}

	@Override
	protected int panelHeight() {
		return HEIGHT;
	}

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		var pose = graphics.pose();
		pose.pushMatrix();
		// rises slightly when appearing and sinks when disappearing
		pose.translate(0f, (1f - visible) * 5f);
		if (hasBackground()) {
			// glow as its switch says: without passing it, the panel glowed regardless of the switch
			HudStyle.panel(ui, 0, 0, width, HEIGHT, RADIUS, HudStyle.opacity() * (HudStyle.colored() ? 1f : PILL_OPACITY) * visible, glow.get());
		}
		int alpha = Math.round(Mth.clamp(visible, 0f, 1f) * 255f);
		graphics.enableScissor(PAD / 2, 0, width - PAD / 2, HEIGHT);
		// on a light panel server colors are darkened, and uncolored text uses the theme's dark text color
		int plain = HudStyle.light() ? HudStyle.text() & 0xFFFFFF : 0xFFFFFF;
		List<Ui.Piece> inMenuFont = menuText();
		if (inMenuFont != null) {
			// in the menu font like the other panels: its glyphs are rasterized in screen pixels at the panel's drawn size
			float drawnWidth = Ui.piecesWidth(client.font, inMenuFont, menuSymbolScale) * menuUnit;
			drawPin(ui, (width - drawnWidth - pinRoom) / 2f, alpha, plain);
			pose.pushMatrix();
			pose.translate((width - drawnWidth + pinRoom) / 2f, (HEIGHT - 8) / 2f);
			pose.scale(menuUnit, menuUnit);
			if (!hasBackground()) {
				// a dark outline of one GUI pixel around the text, which is this many pixels of its font
				outline(graphics, client, inMenuFont, Math.max(1, Math.round(1f / menuUnit)), alpha);
			}
			Ui.drawPieces(graphics, client.font, inMenuFont, menuSymbolScale, (alpha << 24) | plain, false);
			pose.popMatrix();
		} else {
			int left = (width - textWidth - pinRoom) / 2;
			drawPin(ui, left, alpha, plain);
			if (!hasBackground()) {
				outline(graphics, client, sequence, left + pinRoom, (HEIGHT - 8) / 2, 1, alpha);
			}
			graphics.text(client.font, HudStyle.readable(sequence), left + pinRoom, (HEIGHT - 8) / 2, (alpha << 24) | plain, false);
		}
		graphics.disableScissor();
		pose.popMatrix();
	}
	/** The outline's color and how strong it is against the text. */
	private static final TextColor OUTLINE_COLOR = TextColor.fromRgb(0x0A0A0A);
	private static final float OUTLINE_ALPHA = 0.85f;
	/** The text with every part in the outline's color, made again only when the text changes. */
	private FormattedCharSequence darkFor;
	private FormattedCharSequence dark;
	private List<Ui.Piece> darkPiecesFor;
	private List<Ui.Piece> darkPieces;

	/** Draws a dark copy of the text around it, a step away in all eight directions: only the look, the text and its colors stay as the server sent them. */
	private void outline(GuiGraphicsExtractor graphics, Minecraft client, FormattedCharSequence text, int x, int y, int step, int alpha) {
		if (darkFor != text) {
			darkFor = text;
			dark = darkened(text);
		}
		int color = Math.round(alpha * OUTLINE_ALPHA) << 24 | 0x0A0A0A;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					graphics.text(client.font, dark, x + dx * step, y + dy * step, color, false);
				}
			}
		}
	}

	/** The same outline around text in the menu font's pieces, drawn in the pose of the menu font. */
	private void outline(GuiGraphicsExtractor graphics, Minecraft client, List<Ui.Piece> pieces, int step, int alpha) {
		if (darkPiecesFor != pieces) {
			darkPiecesFor = pieces;
			List<Ui.Piece> made = new ArrayList<>(pieces.size());
			for (Ui.Piece piece : pieces) {
				made.add(new Ui.Piece(darkened(piece.text()), piece.symbols()));
			}
			darkPieces = made;
		}
		int color = Math.round(alpha * OUTLINE_ALPHA) << 24 | 0x0A0A0A;
		var pose = graphics.pose();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					pose.pushMatrix();
					pose.translate(dx * step, dy * step);
					Ui.drawPieces(graphics, client.font, darkPieces, menuSymbolScale, color, false);
					pose.popMatrix();
				}
			}
		}
	}

	private static FormattedCharSequence darkened(FormattedCharSequence text) {
		return sink -> text.accept((index, style, codepoint) -> sink.accept(index, style.withColor(OUTLINE_COLOR), codepoint));
	}
}
