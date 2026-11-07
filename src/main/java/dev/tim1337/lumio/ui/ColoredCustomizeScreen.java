package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.render.ColoredFlow;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Customize Colored: a sheet at the right of the screen with the theme's controls, and the real HUD over the real world beside it, so everything shows as it is while it changes;
 * a sample of the menu can stand there too. Everything is a setting of the menu, so it is saved and shared with configs.
 */
public class ColoredCustomizeScreen extends Screen {
	private enum Shown {
		GAME("In the game"),
		MENU("Menu"),
		BOTH("Both");

		private final Component displayName;

		Shown(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		Component displayName() {
			return displayName;
		}
	}

	private static final int SHEET_WIDTH = 348;
	private static final int MARGIN = 12;
	private static final int PAD = 14;
	private static final int TITLE_HEIGHT = 30;
	private static final int LABEL_HEIGHT = 15;
	private static final int ROW_GAP = 8;
	private static final int ROW = 22;
	private static final int FOOTER = 40;
	private static final int PRESET_GAP = 5;
	private static final int PRESET_HEIGHT = 58;
	private static final int PRESET_PICTURE = 28;
	private static final int PAD_SIDE = 112;
	private static final int PAD_GAP = 12;
	private static final float PAD_ARROW_STEP = 0.02f;
	private static final int DOT = 6;
	/** How far the dot's middle stays from the pad's edge, so all of the dot and its ring are on the pad. */
	private static final int PAD_INSET = DOT + 3;
	private static final int LABEL_PAD = 5;
	private static final float MINI_DRIFT = 0.18f;
	private static final float MINI_SECONDS = 9f;
	private static final int SAMPLE_WIDTH = 300;
	private static final int SAMPLE_HEIGHT = 190;

	private final Screen parent;
	private final MenuFeature menu = ModFeatures.MENU;
	private Shown shown = Shown.GAME;
	/** The two switches about where the colors go, at the top under the preview's choice. */
	private final List<Row> top = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private final EnumSetting<Shown> shownSetting = new DerivedEnumSetting<>("shown", Component.literal("Shown"), Shown.GAME, Shown::displayName, EnumSetting.Style.SEGMENTED,
		() -> shown, value -> shown = value);
	private final Control shownControl = shownSetting.createControl(this::changed);
	private final Control useColors = new ButtonSetting("use_colors", Component.literal(""), Component.literal("Use its colors"), false, this::useSuggested).createControl(this::changed);
	private final Control reset = new ButtonSetting("reset_flow", Component.literal(""), Component.literal("Reset to Flow"), false, this::resetToFlow).createControl(this::changed);
	private final Control done = new ButtonSetting("done", Component.literal(""), Component.literal("Done"), false, this::onClose).createControl(this::changed);
	private final float[] presetHover = new float[ColoredMood.Preset.values().length];
	private final int[][] presetBoxes = new int[ColoredMood.Preset.values().length][];
	private int padX;
	private int padY;
	private boolean draggingPad;
	private boolean padFocused;
	private Control pressed;
	private float time;
	private long last = System.nanoTime();
	private float zoom = 1f;
	private int sheetX;
	private int sheetY;

	/** A setting's row: its name above or beside its control. */
	private record Row(Setting<?> setting, Control control, boolean below) {
	}

	public ColoredCustomizeScreen(Screen parent) {
		super(Component.literal("Customize Colored"));
		this.parent = parent;
		top.add(row(menu.coloredInGameSetting(), false));
		top.add(row(menu.coloredWholeScreenSetting(), false));
		rows.add(row(menu.coloredColorsSetting(), false));
		rows.add(row(menu.coloredFollowSongSetting(), false));
	}

	private Row row(Setting<?> setting, boolean below) {
		return new Row(setting, setting.createControl(this::changed), below);
	}

	private final List<Row> besidePad = new ArrayList<>();
	private final List<Row> switches = new ArrayList<>();

	@Override
	protected void init() {
		besidePad.clear();
		besidePad.add(row(menu.coloredMovementSetting(), true));
		besidePad.add(row(menu.coloredFieldSizeSetting(), true));
		besidePad.add(row(menu.coloredIntensitySetting(), true));
		switches.clear();
		switches.add(row(menu.coloredGrainSetting(), false));
		switches.add(row(menu.coloredBreatheSetting(), false));
	}

	private void changed() {
		FeatureManager.saveSoon();
	}

	private void useSuggested() {
		ColoredMood.Preset preset = menu.coloredPreset();
		if (preset != null && preset.suggested() != null) {
			menu.coloredColorsSetting().setColors(preset.suggested());
			changed();
		}
	}

	private void resetToFlow() {
		menu.applyColoredPreset(ColoredMood.Preset.FLOW);
		changed();
	}

	/** @return the sheet's height without any zoom */
	private int sheetHeight() {
		return TITLE_HEIGHT + 2 * (ROW + ROW_GAP / 2) + LABEL_HEIGHT + PRESET_HEIGHT + ROW_GAP + 2 * ROW + 2 * ROW_GAP + LABEL_HEIGHT + PAD_SIDE + ROW_GAP + 2 * (ROW + ROW_GAP) + FOOTER + 2 * PAD;
	}

	private int mouseInSheetX(double mouseX) {
		return (int) Math.floor(mouseX / zoom);
	}

	private int mouseInSheetY(double mouseY) {
		return (int) Math.floor(mouseY / zoom);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// nothing: the world and the HUD beside the sheet are the preview
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		time += dt;
		zoom = Math.min(1f, Math.min((height - 2f * MARGIN) / sheetHeight(), (width * 0.75f) / SHEET_WIDTH));
		Ui.setZoom(zoom);
		Ui ui = new Ui(graphics, font, HudStyle.palette(), menu.accentArgb(), dt, true);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(zoom, zoom);
		int shownWidth = Math.round(width / zoom);
		int shownHeight = Math.round(height / zoom);
		int mx = mouseInSheetX(mouseX);
		int my = mouseInSheetY(mouseY);
		if (shown != Shown.GAME) {
			drawMenuSample(ui, shownWidth, shownHeight);
		}
		sheetX = shownWidth - SHEET_WIDTH - MARGIN;
		sheetY = Math.max(MARGIN, (shownHeight - sheetHeight()) / 2);
		HudStyle.panel(ui, sheetX, sheetY, SHEET_WIDTH, sheetHeight(), Radius.XXL, HudStyle.opacity(), UiGlow.Target.MENU, 1f);
		drawContent(ui, mx, my);
		drawOverlays(ui, mx, my);
		pose.popMatrix();
		Ui.setZoom(1f);
	}

	/** A sample of the menu beside the sheet: its window with a card on the left of it, in the theme's colors. */
	private void drawMenuSample(Ui ui, int shownWidth, int shownHeight) {
		int x = Math.max(MARGIN, (shownWidth - SHEET_WIDTH - MARGIN - SAMPLE_WIDTH) / 2);
		int y = (shownHeight - SAMPLE_HEIGHT) / 2;
		HudStyle.panel(ui, x, y, SAMPLE_WIDTH, SAMPLE_HEIGHT, Radius.XXL, HudStyle.opacity(), UiGlow.Target.MENU, 1f);
		Palette palette = ui.palette;
		ui.textAtScaledTitle("Menu settings", x + PAD, y + PAD, TextScale.BODY, palette.text());
		ui.textAtScaled("How your colors look in a menu", x + PAD, y + PAD + LABEL_HEIGHT, TextScale.SMALL, palette.textSecondary());
		int cardY = y + PAD + 2 * LABEL_HEIGHT + 4;
		ui.rounded(x + PAD, cardY, SAMPLE_WIDTH - 2 * PAD, SAMPLE_HEIGHT - (cardY - y) - PAD, Radius.LG, palette.card());
		ui.ring(x + PAD, cardY, SAMPLE_WIDTH - 2 * PAD, SAMPLE_HEIGHT - (cardY - y) - PAD, Radius.LG, 1, palette.cardBorder());
		ui.textMiddle("A setting", x + 2 * PAD, cardY + 20, palette.text());
		ui.rounded(x + SAMPLE_WIDTH - 2 * PAD - 80, cardY + 16, 80, 8, Radius.pill(8), ui.accent);
		ui.textMiddle("Another setting", x + 2 * PAD, cardY + 48, palette.textSecondary());
		ui.rounded(x + SAMPLE_WIDTH - 2 * PAD - 80, cardY + 44, 50, 8, Radius.pill(8), palette.trackOff());
	}

	private void drawContent(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int left = sheetX + PAD;
		int right = sheetX + SHEET_WIDTH - PAD;
		int inner = right - left;
		int y = sheetY + PAD;
		ui.textAtScaledTitle("Customize Colored", left, y, TextScale.BODY, palette.text());
		int shownSegment = shownControl.inlineWidth(ui, inner / 2);
		shownControl.place(ui, right - shownSegment, y - 4, shownSegment);
		shownControl.renderSafely(ui, mouseX, mouseY);
		y += TITLE_HEIGHT;
		for (Row row : top) {
			y = drawBesideRow(ui, row, left, right, y, mouseX, mouseY);
		}
		ColoredMood.Preset current = menu.coloredPreset();
		ui.sectionLabel("PRESETS", left, y, palette.textSecondary());
		if (current == null) {
			ui.textRight("Custom", right, y + 4, palette.textSecondary());
		}
		y += LABEL_HEIGHT;
		drawPresets(ui, left, y, inner, current, mouseX, mouseY);
		y += PRESET_HEIGHT + ROW_GAP;
		if (current != null && current.suggested() != null) {
			int buttonWidth = useColors.inlineWidth(ui, inner / 2);
			useColors.place(ui, left + ui.width(rows.getFirst().setting().labelText()) + ROW_GAP, y, buttonWidth);
		}
		for (Row row : rows) {
			y = drawBesideRow(ui, row, left, right, y, mouseX, mouseY);
		}
		y += ROW_GAP / 2;
		ui.sectionLabel("MOOD", left, y, palette.textSecondary());
		y += LABEL_HEIGHT;
		padX = left;
		padY = y;
		drawPad(ui, mouseX, mouseY);
		int columnLeft = left + PAD_SIDE + PAD_GAP;
		int rowY = y;
		for (Row row : besidePad) {
			ui.textAtScaled(row.setting().labelText(), columnLeft, rowY, TextScale.SMALL, palette.textSecondary());
			int width = right - columnLeft;
			row.control().place(ui, columnLeft, rowY + 10, width);
			row.control().renderSafely(ui, mouseX, mouseY);
			rowY += (PAD_SIDE - 2) / besidePad.size();
		}
		y += PAD_SIDE + ROW_GAP;
		for (Row row : switches) {
			y = drawBesideRow(ui, row, left, right, y, mouseX, mouseY);
		}
		int footerY = sheetY + sheetHeight() - PAD - ROW;
		int resetWidth = reset.inlineWidth(ui, inner);
		reset.place(ui, left, footerY, resetWidth);
		reset.renderSafely(ui, mouseX, mouseY);
		int doneWidth = done.inlineWidth(ui, inner);
		done.place(ui, right - doneWidth, footerY, doneWidth);
		done.renderSafely(ui, mouseX, mouseY);
		if (current != null && current.suggested() != null) {
			useColors.renderSafely(ui, mouseX, mouseY);
		}
	}

	private int drawBesideRow(Ui ui, Row row, int left, int right, int y, int mouseX, int mouseY) {
		if (row.setting().isDisabled()) {
			// what has no effect now says why, in place of its switch
			ui.textMiddle(ui.fit(row.setting().labelText(), (right - left) / 2), left, y + ROW / 2, ui.palette.textSecondary());
			ui.textRight(row.setting().disabledNote(), right, y + ROW / 2, ui.palette.textSecondary());
			return y + ROW + ROW_GAP / 2;
		}
		ui.textMiddle(ui.fit(row.setting().labelText(), (right - left) / 2), left, y + ROW / 2, ui.palette.text());
		int width = row.control().inlineWidth(ui, (right - left) / 2);
		row.control().place(ui, right - width, y + (ROW - Math.max(row.control().height(), 0)) / 2, width);
		row.control().place(ui, right - width, y + (ROW - row.control().height()) / 2, width);
		row.control().renderSafely(ui, mouseX, mouseY);
		return y + ROW + ROW_GAP / 2;
	}

	private void drawPresets(Ui ui, int left, int y, int inner, ColoredMood.Preset current, int mouseX, int mouseY) {
		ColoredMood.Preset[] presets = ColoredMood.Preset.values();
		int each = (inner - (presets.length - 1) * PRESET_GAP) / presets.length;
		int[] colors = ColoredPalette.fields(menu.coloredColors());
		for (int i = 0; i < presets.length; i++) {
			int x = left + i * (each + PRESET_GAP);
			presetBoxes[i] = new int[]{x, y, each, PRESET_HEIGHT};
			boolean over = mouseX >= x && mouseX < x + each && mouseY >= y && mouseY < y + PRESET_HEIGHT;
			presetHover[i] = ui.approach(presetHover[i], over ? 1f : 0f, Ui.HOVER_RATE);
			boolean chosen = presets[i] == current;
			ui.rounded(x, y, each, PRESET_HEIGHT, Radius.MD, Ui.mix(ui.palette.control(), ui.palette.hover(), presetHover[i]));
			ui.ring(x, y, each, PRESET_HEIGHT, Radius.MD, 1, chosen ? ui.accent : ui.palette.controlBorder());
			drawMini(ui, x + 3, y + 3, each - 6, PRESET_PICTURE, presets[i], colors);
			String title = ui.fitScaled(presets[i].title(), each - 4, TextScale.SMALL);
			float titleWidth = chosen ? ui.boldWidthScaled(title, TextScale.SMALL) : ui.widthScaled(title, TextScale.SMALL);
			float titleX = x + (each - titleWidth) / 2f;
			if (chosen) {
				ui.textAtScaledBold(title, titleX, y + PRESET_PICTURE + 8, TextScale.SMALL, ui.palette.text());
			} else {
				ui.textAtScaled(title, titleX, y + PRESET_PICTURE + 8, TextScale.SMALL, ui.palette.text());
			}
			String line = ui.fitScaled(presets[i].line(), each - 2, TextScale.SMALL * 0.9f);
			ui.textAtScaled(line, x + (each - ui.widthScaled(line, TextScale.SMALL * 0.9f)) / 2f, y + PRESET_PICTURE + 19, TextScale.SMALL * 0.9f, ui.palette.textSecondary());
		}
	}

	/** A small moving picture of a preset, with the player's colors: its fields at its speed; the clip stays inside the picture's rounded corners. */
	private void drawMini(Ui ui, int x, int y, int width, int height, ColoredMood.Preset preset, int[] fields) {
		ui.rounded(x, y, width, height, Radius.SM, 0xFF000000 | ColoredPalette.base(menu.coloredColors()));
		int inset = 2;
		ui.graphics.enableScissor(x + inset, y + inset, x + width - inset, y + height - inset);
		float moved = preset.movement() == ColoredMood.Movement.STILL ? 0f : time * ColoredMood.rate(preset.speed()) / MINI_SECONDS;
		float reach = ColoredMood.radiusFactor(preset.fieldSize());
		float stretch = preset.movement() == ColoredMood.Movement.WAVES ? 2f : 1f;
		for (int i = 0; i < 3; i++) {
			float angle = (moved + i / 3f) * (float) (Math.PI * 2.0);
			float cx = x + width * (0.5f + MINI_DRIFT * 1.6f * (float) Math.cos(angle));
			float cy = y + height * (0.5f + MINI_DRIFT * 1.6f * (float) Math.sin(angle));
			int color = ColoredMood.intense(fields[i], preset.intensity());
			ui.wash(cx, cy, width * 0.5f * reach * stretch, height * 0.8f * reach, 0xFF000000 | color);
		}
		ui.graphics.disableScissor();
	}

	private void drawPad(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		ui.rounded(padX, padY, PAD_SIDE, PAD_SIDE, Radius.MD, palette.control());
		ui.ring(padX, padY, PAD_SIDE, PAD_SIDE, Radius.MD, 1, padFocused ? ui.accent : palette.controlBorder());
		ui.fill(padX + PAD_SIDE / 2, padY + 4, 1, PAD_SIDE - 8, Ui.withOpacity(palette.text(), 0.08f));
		ui.fill(padX + 4, padY + PAD_SIDE / 2, PAD_SIDE - 8, 1, Ui.withOpacity(palette.text(), 0.08f));
		float small = TextScale.SMALL * 0.85f;
		ui.textAtScaled("DEFINED", padX + (PAD_SIDE - ui.widthScaled("DEFINED", small)) / 2f, padY + 4, small, palette.textSecondary());
		ui.textAtScaled("SOFT", padX + (PAD_SIDE - ui.widthScaled("SOFT", small)) / 2f, padY + PAD_SIDE - 10, small, palette.textSecondary());
		ui.textAtScaled("CALM", padX + 4, padY + PAD_SIDE / 2f + 3, small, palette.textSecondary());
		ui.textAtScaled("LIVELY", padX + PAD_SIDE - 4 - ui.widthScaled("LIVELY", small), padY + PAD_SIDE / 2f + 3, small, palette.textSecondary());
		float speed = menu.coloredSpeed();
		float edges = menu.coloredEdges();
		int dotX = padX + PAD_INSET + Math.round(speed * (PAD_SIDE - 2 * PAD_INSET));
		int dotY = padY + PAD_SIDE - PAD_INSET - Math.round(edges * (PAD_SIDE - 2 * PAD_INSET));
		ui.circle(dotX, dotY, DOT + 2, Ui.withOpacity(ui.accent, 0.35f));
		ui.circle(dotX, dotY, DOT, 0xFFFFFFFF);
		ui.circle(dotX, dotY, DOT - 3, ui.accent);
		if (draggingPad || padFocused) {
			String label = "Speed " + Math.round(speed * 100f) + " % · Edges " + Math.round(edges * 100f) + " %";
			float labelWidth = ui.widthScaled(label, TextScale.SMALL) + 2f * LABEL_PAD;
			float labelX = Math.clamp(dotX - labelWidth / 2f, padX - 2f, padX + PAD_SIDE + 2f - labelWidth);
			float labelY = Math.max(padY + 2f, dotY - DOT - 18f);
			ui.roundedSmooth(labelX, labelY, labelWidth, 13f, 4f, Ui.withOpacity(0xFF0A0A10, 0.85f));
			ui.textAtScaled(label, labelX + LABEL_PAD, labelY + 3f, TextScale.SMALL, 0xFFFFFFFF);
		}
	}

	private void drawOverlays(Ui ui, int mouseX, int mouseY) {
		for (Control control : controls()) {
			if (control.isOverlayOpen()) {
				control.renderOverlay(ui, mouseX, mouseY);
			}
		}
	}

	private List<Control> controls() {
		List<Control> all = new ArrayList<>();
		all.add(shownControl);
		for (Row row : top) {
			if (!row.setting().isDisabled()) {
				all.add(row.control());
			}
		}
		for (Row row : rows) {
			all.add(row.control());
		}
		for (Row row : besidePad) {
			all.add(row.control());
		}
		for (Row row : switches) {
			all.add(row.control());
		}
		all.add(reset);
		all.add(done);
		ColoredMood.Preset current = menu.coloredPreset();
		if (current != null && current.suggested() != null) {
			all.add(useColors);
		}
		return all;
	}

	private void movePadTo(double x, double y) {
		int side = PAD_SIDE - 2 * PAD_INSET;
		menu.setColoredMood(ColoredMood.padValue((float) x - padX - PAD_INSET, side), ColoredMood.padValue(padY + PAD_SIDE - PAD_INSET - (float) y, side));
		changed();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = mouseInSheetX(event.x());
		double y = mouseInSheetY(event.y());
		int button = ScreenInput.button(event);
		for (Control control : controls()) {
			if (control.isOverlayOpen()) {
				control.mouseClicked(x, y, button);
				pressed = control.isOverlayOpen() ? control : null;
				return true;
			}
		}
		padFocused = false;
		if (button != 0) {
			return true;
		}
		if (x >= padX && x < padX + PAD_SIDE && y >= padY && y < padY + PAD_SIDE) {
			draggingPad = true;
			padFocused = true;
			movePadTo(x, y);
			return true;
		}
		ColoredMood.Preset[] presets = ColoredMood.Preset.values();
		for (int i = 0; i < presets.length; i++) {
			int[] box = presetBoxes[i];
			if (box != null && x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3]) {
				menu.applyColoredPreset(presets[i]);
				UiSounds.click();
				changed();
				return true;
			}
		}
		for (Control control : controls()) {
			if (control.mouseClicked(x, y, button)) {
				pressed = control;
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		double x = mouseInSheetX(event.x());
		double y = mouseInSheetY(event.y());
		if (draggingPad) {
			movePadTo(x, y);
			return true;
		}
		if (pressed != null) {
			pressed.mouseDragged(x, y);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingPad = false;
		if (pressed != null) {
			Control control = pressed;
			pressed = null;
			control.mouseReleased(mouseInSheetX(event.x()), mouseInSheetY(event.y()));
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (Control control : controls()) {
			if (control.wantsKeyboard() && control.keyPressed(event.key(), event.modifiers())) {
				return true;
			}
		}
		if (padFocused) {
			float speed = menu.coloredSpeed();
			float edges = menu.coloredEdges();
			switch (event.key()) {
				case InputConstants.KEY_LEFT -> speed -= PAD_ARROW_STEP;
				case InputConstants.KEY_RIGHT -> speed += PAD_ARROW_STEP;
				case InputConstants.KEY_UP -> edges += PAD_ARROW_STEP;
				case InputConstants.KEY_DOWN -> edges -= PAD_ARROW_STEP;
				default -> {
					return super.keyPressed(event);
				}
			}
			menu.setColoredMood(Math.clamp(speed, 0f, 1f), Math.clamp(edges, 0f, 1f));
			changed();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		for (Control control : controls()) {
			if (control.wantsKeyboard() && control.charTyped(event.codepoint())) {
				return true;
			}
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		FeatureManager.saveSoon();
		ColoredFlow.ask();
		minecraft.gui.setScreen(parent);
	}

	/** @return whether the HUD steps aside for a screen: while only the sample of the menu is shown */
	public static boolean hidesHud(Screen screen) {
		return screen instanceof ColoredCustomizeScreen sheet && sheet.shown == Shown.MENU;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
