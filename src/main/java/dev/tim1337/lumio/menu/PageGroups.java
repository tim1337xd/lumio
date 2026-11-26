package dev.tim1337.lumio.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The named groups of a long page's feature list: features that declare a group ({@code Feature.pageGroup}) are listed under that group's
 * name, the groups one under the other on the same page. The order is the code's: a group stands where its first feature stands in the
 * page's list, and the features of a group keep the list's order. A short page stays one plain list. Plain data, tested without the game.
 */
public final class PageGroups {
	/** From this many features on, a page's list is split into its groups. */
	public static final int FROM = 8;
	/** The group of a feature on a grouped page that declared none, so every feature has exactly one. */
	public static final String OTHER = "Other";

	private PageGroups() {
	}

	/**
	 * @param features the page's features, in its order
	 * @param groupOf  the group a feature declared, or null
	 * @return the groups in order, each with its features; null when the page stays one plain list (too short, or no feature declared a group)
	 */
	public static <T> Map<String, List<T>> split(List<T> features, Function<T, String> groupOf) {
		if (features.size() < FROM) {
			return null;
		}
		boolean declared = false;
		for (T feature : features) {
			declared |= named(groupOf.apply(feature));
		}
		if (!declared) {
			return null;
		}
		Map<String, List<T>> groups = new LinkedHashMap<>();
		for (T feature : features) {
			String group = groupOf.apply(feature);
			groups.computeIfAbsent(named(group) ? group : OTHER, key -> new ArrayList<>()).add(feature);
		}
		return groups;
	}

	private static boolean named(String group) {
		return group != null && !group.isBlank();
	}
}
