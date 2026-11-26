package dev.tim1337.lumio.ui;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The HUD editor's All panels and its "preview" tags. By default the editor shows only what shows here (the panels of the current place and the
 * general ones); All panels adds every other panel with its example data. While neither All panels, a simulation nor a sample from the list is
 * asked for, the editor notes which panels show on their own; any panel shown beyond those is example data and gets a "preview" tag. All panels
 * is off whenever the editor opens: it never stays on from an earlier visit, and nothing of it reaches the HUD after Done.
 */
public final class HudEditPreview {
	public static final String TAG = "preview";

	private static boolean allPanels;
	/** The panels that showed on their own in the last frame without samples. */
	private static final Set<Object> OWN = Collections.newSetFromMap(new IdentityHashMap<>());

	private HudEditPreview() {
	}

	/** @return whether the editor's All panels is on */
	public static boolean allPanels() {
		return allPanels;
	}

	public static void setAllPanels(boolean on) {
		allPanels = on;
	}

	/**
	 * Called once per editor frame: while nothing asks for samples, notes which panels show on their own.
	 *
	 * @param sampling whether a simulation runs or a panel's sample was asked for from the list
	 */
	public static <T> void noteFrame(List<T> elements, Predicate<T> shown, boolean sampling) {
		if (allPanels || sampling) {
			return;
		}
		OWN.clear();
		for (T element : elements) {
			if (shown.test(element)) {
				OWN.add(element);
			}
		}
	}

	/** @return whether a shown panel shows example data only because the editor asked for it: it gets the "preview" tag */
	public static boolean preview(Object element, boolean shown) {
		return shown && !OWN.contains(element);
	}

	/** The editor closed: All panels goes off and what was noted is forgotten. */
	public static void closed() {
		allPanels = false;
		OWN.clear();
	}
}
