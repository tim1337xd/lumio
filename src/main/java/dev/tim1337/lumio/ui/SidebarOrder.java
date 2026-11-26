package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.menu.FeatureListFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The order and the groups of the pages in the menu's sidebar: Home alone on top, then LUMIO (World, Interface, Music, and Extras with Chat,
 * Keybinds and Waypoints, which are the mod's own and no add-on), ADD-ONS (SkyBlock, the pages other add-ons register in the order they came,
 * Debug last: real add-ons only) and, fixed at the bottom, TOOLS (the HUD editor, Configs, the menu settings; see {@link #TOOLS}). A page's
 * categories stay right under it. Plain logic, tested without the game.
 */
final class SidebarOrder {
	/** The groups of the sidebar, in their order, with the small label over each; Home has none. */
	enum Group {
		TOP(null), LUMIO("LUMIO"), ADDONS("ADD-ONS"), TOOLS("TOOLS");

		private final String label;

		Group(String label) {
			this.label = label;
		}

		/** @return the label over the group, or null for the group without one */
		String label() {
			return label;
		}
	}

	private static final String HOME = "home";
	private static final String CONFIGS = "configs";
	private static final String DEBUG = "debug";
	/** The main mod's own feature pages, in their order. */
	private static final List<String> LUMIO_PAGES = List.of("world", "interface", "music_page", "extras");
	/** The tools at the bottom, in their order: the HUD editor is an action and no page, so the menu places it; the two pages follow it. */
	static final List<String> TOOLS = List.of("Edit HUD layout", "Configs", "Menu settings");
	/** The add-on pages with a fixed place at the start of their group; the others follow in the order they were registered. */
	private static final List<String> FIRST_ADDONS = List.of("skyblock");

	private SidebarOrder() {
	}

	/** @return the group of a page that is no category of another page */
	static Group group(String id) {
		if (id.equals(HOME)) {
			return Group.TOP;
		}
		if (id.equals(CONFIGS)) {
			return Group.TOOLS;
		}
		return LUMIO_PAGES.contains(id) ? Group.LUMIO : Group.ADDONS;
	}

	/** @return a page's place inside its group; pages with the same place keep the order they came in */
	static int rank(String id) {
		int lumio = LUMIO_PAGES.indexOf(id);
		if (lumio >= 0) {
			return lumio;
		}
		int addon = FIRST_ADDONS.indexOf(id);
		if (addon >= 0) {
			return addon;
		}
		return id.equals(DEBUG) ? FIRST_ADDONS.size() + 1 : FIRST_ADDONS.size();
	}

	/** @return whether a page shows the "DEV" tag instead of a count: the private debug add-on's page */
	static boolean devPage(String id) {
		return id.equals(DEBUG);
	}

	/**
	 * @param pages  the pages as they were registered
	 * @param id     a page's id
	 * @param parent the page a page is a category of, or null
	 * @return the pages in the sidebar's order: by group, then by their place in it, each followed by its categories in their own order
	 */
	static <T> List<T> ordered(List<T> pages, Function<T, String> id, Function<T, T> parent) {
		List<T> tops = new ArrayList<>();
		for (T page : pages) {
			if (parent.apply(page) == null) {
				tops.add(page);
			}
		}
		// a stable sort: pages with the same group and place stay in the order they were registered
		tops.sort((a, b) -> {
			int byGroup = group(id.apply(a)).compareTo(group(id.apply(b)));
			return byGroup != 0 ? byGroup : Integer.compare(rank(id.apply(a)), rank(id.apply(b)));
		});
		List<T> ordered = new ArrayList<>(pages.size());
		for (T top : tops) {
			ordered.add(top);
			for (T page : pages) {
				if (parent.apply(page) == top) {
					ordered.add(page);
				}
			}
		}
		// a category whose page is not listed keeps a place at the end rather than being lost
		for (T page : pages) {
			if (!ordered.contains(page)) {
				ordered.add(page);
			}
		}
		return ordered;
	}

	/** Puts the menu's pages into the sidebar's order. */
	static void sort(List<Feature> pages) {
		List<Feature> ordered = ordered(pages, Feature::id, page -> page instanceof FeatureListFeature list ? list.parentPage() : null);
		pages.clear();
		pages.addAll(ordered);
	}
}
