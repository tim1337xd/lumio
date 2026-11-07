package dev.tim1337.lumio.api.experimental.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Arrays;

/**
 * What a mod draws on the slots of a game's menu: called after the slots and before the item on the cursor and the tooltip, so these stay on top of it. Painters are added once at startup; they are asked for every menu on every frame, so each returns
 * at once when the menu is none of its own.
 */
public final class SlotLayer {
	/** Draws on a menu. */
	@FunctionalInterface
	public interface Painter {
		void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY);
	}

	private static Painter[] painters = new Painter[0];

	private SlotLayer() {
	}

	/** Adds a painter, at startup. */
	public static void add(Painter painter) {
		painters = Arrays.copyOf(painters, painters.length + 1);
		painters[painters.length - 1] = painter;
	}

	/** Called by the main mod while a menu is drawn, between its slots and its tooltip. */
	public static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		for (Painter painter : painters) {
			painter.draw(screen, graphics, mouseX, mouseY);
		}
	}
}
