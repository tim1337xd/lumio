package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;

/**
 * A small button that opens the mod settings: on the game's own main menu the mod's icon in the row of its small buttons (language and the
 * others), so the settings stay easy to find with the classic main menu, and a round gear at the top right of the world and server screens. Closing the settings
 * returns to the screen it was opened from.
 */
public final class MenuGear {
	private static final int SIZE = 24;
	private static final int MARGIN = 8;
	/** On the game's main menu: as large as its small icon buttons (language and the others), in their row before the first of them. */
	private static final int BUTTON_SIZE = 20;
	private static final int BUTTON_GAP = 4;
	/** The corner rounding of the mod's icon tile, as a share of its size. */
	private static final float TILE_ROUNDING = 0.27f;
	private static final String LANGUAGE_KEY = "options.language";
	private static final Component TOOLTIP = Component.literal("Lumio settings");

	private MenuGear() {
	}

	public static boolean wanted(Screen screen) {
		return screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof SelectWorldScreen;
	}

	/** @return where the button is on a screen, as x, y and size: next to the main menu's buttons, or at the top right */
	private static int[] place(Screen screen) {
		if (screen instanceof TitleScreen) {
			AbstractWidget language = null;
			for (AbstractWidget widget : Screens.getWidgets(screen)) {
				if (widget.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(LANGUAGE_KEY)) {
					language = widget;
				}
			}
			if (language != null) {
				// the row of small buttons the language button is in: the gear goes before the first of them
				int left = language.getX();
				for (AbstractWidget widget : Screens.getWidgets(screen)) {
					if (widget.getY() == language.getY() && widget.getWidth() == language.getWidth() && widget.getX() < left) {
						left = widget.getX();
					}
				}
				return new int[]{left - BUTTON_GAP - BUTTON_SIZE, language.getY(), BUTTON_SIZE};
			}
		}
		return new int[]{screen.width - SIZE - MARGIN, MARGIN, SIZE};
	}

	private static boolean over(int[] place, double mouseX, double mouseY) {
		return mouseX >= place[0] && mouseX < place[0] + place[2] && mouseY >= place[1] && mouseY < place[1] + place[2];
	}

	/** Adds the button to the screens that should have it when they open. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!wanted(screen)) {
				return;
			}
			int[] place = place(screen);
			float[] hover = {0f};
			long[] stamp = {System.nanoTime()};
			boolean title = screen instanceof TitleScreen;
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, partial) -> draw(graphics, place, title, mouseX, mouseY, hover, stamp));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (ScreenInput.button(event) == 0 && over(place, event.x(), event.y())) {
					UiSounds.click();
					open(s);
					return false;
				}
				return true;
			});
		});
	}

	private static void draw(GuiGraphicsExtractor graphics, int[] place, boolean title, int mouseX, int mouseY, float[] hover, long[] stamp) {
		Minecraft client = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - stamp[0]) / 1_000_000_000f);
		stamp[0] = now;
		hover[0] += ((over(place, mouseX, mouseY) ? 1f : 0f) - hover[0]) * (1f - (float) Math.exp(-dt * 12f));
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), dt, false);
		int x = place[0];
		int y = place[1];
		int size = place[2];
		if (title) {
			drawModIcon(ui, x, y, size, hover[0]);
			if (over(place, mouseX, mouseY)) {
				// drawn here over everything: a tooltip requested from the game comes too late, the screen has drawn its tooltip by now
				graphics.nextStratum();
				graphics.tooltip(client.font, List.of(ClientTooltipComponent.create(TOOLTIP.getVisualOrderText())), mouseX, mouseY,
					DefaultTooltipPositioner.INSTANCE, null);
			}
			return;
		}
		int icon = size - 10;
		// same surface and glow as the clock of the mod's main menu
		HudStyle.panel(ui, x, y, size, size, size / 2f, HudStyle.opacity(), UiGlow.Target.TITLE, 1f);
		if (hover[0] > 0.01f) {
			ui.rounded(x, y, size, size, size / 2f, Ui.withOpacity(ui.accent, 0.30f * hover[0]));
		}
		ui.icon("gear", x + (size - icon) / 2, y + (size - icon) / 2, icon, Ui.mix(HudStyle.secondary(), HudStyle.text(), hover[0]));
	}

	/** The mod's icon as in the header of its windows: a sparkle on a tile in the accent, lighter at the top, brighter on hover. */
	public static void drawModIcon(Ui ui, int x, int y, int size, float hover) {
		float radius = size * TILE_ROUNDING;
		int light = Ui.mix(ui.accent, 0xFFFFFFFF, 0.15f + 0.15f * hover);
		int dark = Ui.mix(ui.accent, 0xFF000000, 0.3f - 0.15f * hover);
		ui.shadow(x, y + 1, size, size, radius, 4, 0.3f);
		ui.roundedGradient(x, y, size, size, radius, light, dark);
		int icon = Math.round(size * 0.55f);
		ui.icon("sparkle", x + (size - icon) / 2, y + (size - icon) / 2, icon, 0xFFFFFFFF);
	}

	private static void open(Screen from) {
		MacScreen.openFrom(from);
	}
}
