package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.menu.Presets;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Step two: the look of the menus, panels and HUD, with the same rows as the menu settings: the theme as cards, the accent color or the theme's colors, the glow and the sounds.
 * Everything applies at once, the setup's own window too; the preview on the right shows a settings window and a hotbar over the main menu's world.
 */
final class LookStep implements SetupStep {
	private static final int GAP = 16;
	/** The left column's share of the width, as in the mockup (1.35 to 1). */
	private static final float LEFT_SHARE = 1.35f / 2.35f;
	private static final int LABEL_GAP = 12;
	private static final int SECTION_GAP = 12;
	private static final int CARD_PAD = 10;
	private static final int ROW_HEIGHT = 28;
	private static final float RADIUS = Radius.LG;
	/** The preset cards at the top: their height, padding, radio and the gap between them. */
	private static final int PRESET_HEIGHT = 36;
	private static final int PRESET_PAD = 10;
	private static final int PRESET_RADIO = 12;
	private static final int PRESET_GAP = 8;

	private record Row(Setting<?> setting, Control control) {
	}

	private final SetupScreen screen;
	private final Row theme;
	private final List<Row> rows = new ArrayList<>();
	/** The control a press went to, which then gets the drag and the release. */
	private Control pressed;

	LookStep(SetupScreen screen) {
		this.screen = screen;
		List<Setting<?>> settings = ModFeatures.MENU.setupRows();
		theme = new Row(settings.getFirst(), settings.getFirst().createControl(LookStep::lookChanged));
		for (Setting<?> setting : settings.subList(1, settings.size())) {
			rows.add(new Row(setting, setting.createControl(LookStep::lookChanged)));
		}
	}

	/** What is picked here is the look of everything: what follows the menu's color does so again. */
	private static void lookChanged() {
		MenuFeature.hudFollowsMenu();
		FeatureManager.saveSoon();
	}

	@Override
	public String name() {
		return "Look";
	}

	@Override
	public void draw(SetupKit kit, int x, int y, int width, int height, boolean wide) {
		int left = wide ? Math.round((width - GAP) * LEFT_SHARE) : width;
		int top = kit.heading("Choose your look", "Menus, panels and the HUD follow it. It changes right away, even this window.", x, y, left);
		top += SECTION_GAP;
		kit.label("START FROM A PRESET", x, top);
		top += LABEL_GAP;
		top = drawPresets(kit, x, top, left) + SECTION_GAP;
		kit.label("THEME", x, top);
		top += LABEL_GAP;
		int mouseX = kit.live ? kit.mouseX : -1;
		int mouseY = kit.live ? kit.mouseY : -1;
		// a list that is open lies over everything else, which does not react under it
		Control open = openOverlay();
		int underX = open != null ? -1 : mouseX;
		int underY = open != null ? -1 : mouseY;
		theme.control().place(kit.ui, x, top, left);
		theme.control().renderSafely(kit.ui, underX, underY);
		top += theme.control().height() + SECTION_GAP;
		drawRows(kit, x, top, left, underX, underY);
		if (wide) {
			int previewX = x + left + GAP;
			kit.label("PREVIEW", previewX, y);
			SetupPreview.look(kit, previewX, y + LABEL_GAP, width - left - GAP, height - LABEL_GAP);
		}
		if (open != null) {
			open.renderOverlay(kit.ui, mouseX, mouseY);
		}
	}

	/** @return the y below the presets as cards side by side: a click starts from one, and its whole setup applies at once */
	private int drawPresets(SetupKit kit, int x, int y, int width) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		int count = Presets.ALL.size();
		int cardWidth = (width - PRESET_GAP * (count - 1)) / count;
		for (int i = 0; i < count; i++) {
			Presets.Preset preset = Presets.ALL.get(i);
			int cardX = x + i * (cardWidth + PRESET_GAP);
			float hover = kit.area("preset" + i, cardX, y, cardWidth, PRESET_HEIGHT, SetupKit.CARD_RADIUS, () -> {
				screen.choices.pickPreset(preset);
				lookChanged();
			});
			float chosen = kit.follow("preset_on" + i, screen.choices.preset() == preset ? 1f : 0f);
			kit.card(cardX, y, cardWidth, PRESET_HEIGHT, chosen, hover);
			int room = cardWidth - 2 * PRESET_PAD - PRESET_RADIO - 6;
			ui.textAtScaledBold(kit.fit(preset.name(), room, SetupKit.BODY), cardX + PRESET_PAD, y + 7, SetupKit.BODY, palette.text());
			ui.textAtScaled(kit.fit(preset.note(), room, SetupKit.SMALL), cardX + PRESET_PAD, y + 20, SetupKit.SMALL, palette.textSecondary());
			kit.radio(cardX + cardWidth - PRESET_PAD - PRESET_RADIO, y + (PRESET_HEIGHT - PRESET_RADIO) / 2, PRESET_RADIO, chosen);
		}
		return y + PRESET_HEIGHT;
	}

	/** The rows in one card, as on the page of the menu settings: the name on the left, the control on the right. */
	private void drawRows(SetupKit kit, int x, int y, int width, int mouseX, int mouseY) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		int shown = 0;
		for (Row row : rows) {
			shown += row.setting().isVisible() ? 1 : 0;
		}
		int cardHeight = shown * ROW_HEIGHT;
		ui.rounded(x, y, width, cardHeight, RADIUS, palette.card());
		ui.ring(x, y, width, cardHeight, RADIUS, 1, palette.cardBorder());
		int inner = width - 2 * CARD_PAD;
		int rowY = y;
		for (Row row : rows) {
			if (!row.setting().isVisible()) {
				continue;
			}
			Control control = row.control();
			String label = row.setting().labelText();
			int controlWidth = control.inlineWidth(ui, inner - ui.width(label) - CARD_PAD);
			control.place(ui, x + width - CARD_PAD - controlWidth, rowY, controlWidth);
			control.place(ui, x + width - CARD_PAD - controlWidth, rowY + (ROW_HEIGHT - control.height()) / 2, controlWidth);
			ui.textMiddle(label, x + CARD_PAD, rowY + ROW_HEIGHT / 2, palette.text());
			control.renderSafely(ui, mouseX, mouseY);
			rowY += ROW_HEIGHT;
		}
	}

	private Control openOverlay() {
		for (Row row : rows) {
			if (row.setting().isVisible() && row.control().isOverlayOpen()) {
				return row.control();
			}
		}
		return null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		Control open = openOverlay();
		if (open != null) {
			// a list that is open closes first, as anywhere in the menu; a press beside it closes it
			if (event.key() == InputConstants.KEY_ESCAPE) {
				open.mouseClicked(-1, -1, 0);
			} else {
				open.keyPressed(event.key(), event.modifiers());
			}
			return true;
		}
		for (Row row : rows) {
			if (row.control().wantsKeyboard()) {
				return row.control().keyPressed(event.key(), event.modifiers());
			}
		}
		return false;
	}

	@Override
	public boolean charTyped(int codepoint) {
		for (Row row : rows) {
			if (row.control().wantsKeyboard()) {
				return row.control().charTyped(codepoint);
			}
		}
		return false;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY) {
		Control open = openOverlay();
		if (open != null) {
			open.mouseClicked(mouseX, mouseY, 0);
			pressed = open.isOverlayOpen() ? open : null;
			return true;
		}
		if (theme.control().mouseClicked(mouseX, mouseY, 0)) {
			pressed = theme.control();
			return true;
		}
		for (Row row : rows) {
			if (row.setting().isVisible() && row.control().mouseClicked(mouseX, mouseY, 0)) {
				pressed = row.control();
				return true;
			}
		}
		return false;
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY) {
		if (pressed != null) {
			pressed.mouseDragged(mouseX, mouseY);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		if (pressed != null) {
			pressed.mouseReleased(mouseX, mouseY);
			pressed = null;
		}
	}
}
