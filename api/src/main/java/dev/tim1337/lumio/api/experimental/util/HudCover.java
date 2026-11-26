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

	private static final List<java.util.function.Consumer<List<int[]>>> AREAS = new ArrayList<>();

	/**
	 * Adds a source of the places an add-on's own menu overlays take on the screen right now (buttons around the inventory, a panel beside a
	 * chest), each as x, y, width, height in GUI units. A HUD element behind a menu fades or hides where it lies under one of them, as it does
	 * under the menu's window. Asked once per frame while a menu with slots is open: it must be cheap and should reuse its boxes.
	 */
	public static void addAreas(java.util.function.Consumer<List<int[]>> areas) {
		AREAS.add(areas);
	}

	/** Collects the places of the add-ons' menu overlays into a list. */
	public static void areas(List<int[]> out) {
		for (int i = 0; i < AREAS.size(); i++) {
			AREAS.get(i).accept(out);
		}
	}

	private static final List<Predicate<Screen>> REPLACED = new ArrayList<>();

	/**
	 * Adds a check that says whether an overlay of the add-on stands in place of a menu's own window, which is then not on the screen: the HUD
	 * behind the menu keeps clear of the overlay's places (see {@link #addAreas}) and not of the window that is not shown.
	 */
	public static void addReplaced(Predicate<Screen> replaced) {
		REPLACED.add(replaced);
	}

	/** @return whether an overlay stands in place of this menu's own window */
	public static boolean replaced(Screen screen) {
		for (int i = 0; i < REPLACED.size(); i++) {
			if (REPLACED.get(i).test(screen)) {
				return true;
			}
		}
		return false;
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
