package dev.tim1337.lumio.api.experimental.util;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * What stands at a place in the world but is drawn with the HUD, such as a label above a boss: it lies under every element of the HUD. The main mod draws these before all of them, and what follows starts a layer of its own,
 * so that also the text of a label stays under a panel. Added once when the game starts.
 */
public final class WorldLabels {
	private static final List<HudElement> LAYERS = new ArrayList<>();

	private WorldLabels() {
	}

	/** Adds what draws labels in the world. */
	public static void add(HudElement layer) {
		LAYERS.add(layer);
	}

	/** Draws all labels; called by the main mod, first of the HUD. */
	public static void draw(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (LAYERS.isEmpty()) {
			return;
		}
		for (int i = 0; i < LAYERS.size(); i++) {
			LAYERS.get(i).extractRenderState(graphics, tracker);
		}
		graphics.nextStratum();
	}
}
