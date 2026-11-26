package dev.tim1337.lumio.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a feature's page is laid out: its short groups, its sub-pages, what is under Advanced, and the names rows go by there. Written down in one
 * file per mod, so that the pages can be read and checked in one place, and the saved keys never change. A line reads
 * {@code features | place | group | key | name | tooltip}, the place being Main, Advanced, Name or {@code Page <title> @<icon>} (a sub-page, opened from
 * a row of the page's overview; the icon, optional, on its first row, and on a page with tabs {@code > <tab>}, the tab of that row), with several features separated by commas, the name left out where it stays, and the tooltip (a longer explanation shown on hover of the name) only where one is needed. Lines
 * are in page order, and {@code #} starts a comment. Plain text, so it is unit tested.
 */
public final class PageOutline {
	/** One row: where it goes (and on which sub-page), the name it goes by there (null to keep its own), and the longer explanation shown on hover of its name, or null. */
	public record Row(String key, String group, String label, Place place, SubPage page, String tooltip) {
		public Row(String key, String group, String label, Place place) {
			this(key, group, label, place, null, null);
		}

		public boolean advanced() {
			return place == Place.ADVANCED;
		}
	}

	/**
	 * A sub-page: its title, the icon of its row in the overview, and the tab its row stands on when the page has tabs ({@code > <tab>} after the
	 * icon), or null for a page without tabs. A row on a tabbed page that names no tab would stand under every tab.
	 */
	public record SubPage(String title, String icon, String tab) {
	}

	/** Where a row goes: on the page, on one of its sub-pages, under Advanced, or nowhere new, a page that stays as it is taking only a new name for it. */
	public enum Place {
		MAIN, PAGE, ADVANCED, NAME
	}

	private static final Map<String, Place> PLACES = Map.of("Main", Place.MAIN, "Advanced", Place.ADVANCED, "Name", Place.NAME);
	private static final String PAGE = "Page ";
	private static final char TAB_MARK = '>';
	private static final String DEFAULT_ICON = "sliders";
	private static final int FEATURES = 0;
	private static final int PLACE = 1;
	private static final int GROUP = 2;
	private static final int KEY = 3;
	private static final int LABEL = 4;
	private static final int TOOLTIP = 5;

	private final List<Row> rows = new ArrayList<>();

	public List<Row> rows() {
		return rows;
	}

	/** @return the rows on the page itself */
	public List<Row> main() {
		return rows.stream().filter(row -> row.place() == Place.MAIN).toList();
	}

	/** @return whether the page only takes new names, and stays as it is */
	public boolean onlyNames() {
		return rows.stream().allMatch(row -> row.place() == Place.NAME);
	}

	/** @return the rows under Advanced */
	public List<Row> advanced() {
		return rows.stream().filter(Row::advanced).toList();
	}

	/** @return the sub-pages, in the order they first appear, each with its rows in order */
	public Map<SubPage, List<Row>> pages() {
		Map<SubPage, List<Row>> pages = new LinkedHashMap<>();
		for (Row row : rows) {
			if (row.place() == Place.PAGE) {
				pages.computeIfAbsent(row.page(), page -> new ArrayList<>()).add(row);
			}
		}
		return pages;
	}

	/**
	 * @return the outlines in a text, by feature id
	 * @throws IllegalArgumentException for a line that cannot be read: the file ships with the mod, so that is a mistake to be found by the tests, not something to get past
	 */
	public static Map<String, PageOutline> parse(String text) {
		Map<String, PageOutline> outlines = new LinkedHashMap<>();
		// one sub-page per title and feature, its icon from its first row
		Map<String, SubPage> subPages = new LinkedHashMap<>();
		for (String raw : text.split("\n")) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] parts = line.split("\\|", -1);
			if (parts.length < KEY + 1 || parts.length > TOOLTIP + 1) {
				throw new IllegalArgumentException("Not a line of an outline: " + line);
			}
			String placeText = parts[PLACE].strip();
			Place place = placeText.startsWith(PAGE) ? Place.PAGE : PLACES.get(placeText);
			if (place == null) {
				throw new IllegalArgumentException("Not a place: " + line);
			}
			String key = parts[KEY].strip();
			if (key.isEmpty()) {
				throw new IllegalArgumentException("No key: " + line);
			}
			String label = parts.length > LABEL && !parts[LABEL].isBlank() ? parts[LABEL].strip() : null;
			for (String feature : parts[FEATURES].split(",")) {
				String id = feature.strip();
				SubPage page = null;
				if (place == Place.PAGE) {
					String rest = placeText.substring(PAGE.length()).strip();
					int tabAt = rest.indexOf(TAB_MARK);
					String tab = tabAt < 0 ? null : rest.substring(tabAt + 1).strip();
					if (tab != null && tab.isEmpty()) {
						throw new IllegalArgumentException("A sub-page with an empty tab: " + line);
					}
					rest = tabAt < 0 ? rest : rest.substring(0, tabAt).strip();
					int at = rest.indexOf('@');
					String title = (at < 0 ? rest : rest.substring(0, at)).strip();
					String icon = at < 0 ? null : rest.substring(at + 1).strip();
					if (title.isEmpty()) {
						throw new IllegalArgumentException("A sub-page without a title: " + line);
					}
					SubPage known = subPages.get(id + "|" + title);
					page = known != null ? known : new SubPage(title, icon != null ? icon : DEFAULT_ICON, tab);
					subPages.put(id + "|" + title, page);
				}
				outlines.computeIfAbsent(id, unused -> new PageOutline()).rows.add(new Row(key, parts[GROUP].strip(), label, place, page,
					parts.length > TOOLTIP && !parts[TOOLTIP].isBlank() ? parts[TOOLTIP].strip() : null));
			}
		}
		return outlines;
	}
}
