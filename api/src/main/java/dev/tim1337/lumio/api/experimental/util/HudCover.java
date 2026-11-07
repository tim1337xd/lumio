package dev.tim1337.lumio.api.experimental.util;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Screens that cover the whole game, under which the HUD is not drawn at all: a full overlay with its own inventory has nothing to share the screen
 * with. An add-on registers the check once, when it starts; it is asked once per frame while a screen is open, so it must be cheap.
 */
public final class HudCover {
	private static final List<Predicate<Screen>> COVERS = new ArrayList<>();

	private HudCover() {
	}

	/** Adds a check that says whether a screen covers the HUD. */
	public static void add(Predicate<Screen> covers) {
		COVERS.add(covers);
	}

	/** @return whether the HUD is hidden under this screen */
	public static boolean covers(Screen screen) {
		for (int i = 0; i < COVERS.size(); i++) {
			if (COVERS.get(i).test(screen)) {
				return true;
			}
		}
		return false;
	}
}
