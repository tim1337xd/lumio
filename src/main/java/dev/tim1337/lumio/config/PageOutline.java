package dev.tim1337.lumio.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a feature's page is laid out: its short groups, what is under Advanced, and the names rows go by there. Written down in one file per mod, so that the pages can be read and checked in one place,
 * and the saved keys never change. A line reads {@code features | place | group | key | name}, the place being Main, Advanced, Developer or Name, with several features separated by commas and the name left out where it stays. Lines are
 * in page order, and {@code #} starts a comment. Plain text, so it is unit tested.
 */
public final class PageOutline {
	/** One row: where it goes, and the name it goes by there, or null to keep its own. */
	public record Row(String key, String group, String label, Place place) {
		public boolean advanced() {
			return place == Place.ADVANCED;
		}
	}

	/** Where a row goes: on the page, under Advanced, on the Developer page, or nowhere new, a page that stays as it is taking only a new name for it. */
	public enum Place {
		MAIN, ADVANCED, DEVELOPER, NAME
	}

	private static final Map<String, Place> PLACES = Map.of("Main", Place.MAIN, "Advanced", Place.ADVANCED, "Developer", Place.DEVELOPER, "Name", Place.NAME);
	private static final int FEATURES = 0;
	private static final int PLACE = 1;
	private static final int GROUP = 2;
	private static final int KEY = 3;
	private static final int LABEL = 4;

	private final List<Row> rows = new ArrayList<>();

	public List<Row> rows() {
		return rows;
	}

	/** @return the rows on the page itself */
	public List<Row> main() {
		return rows.stream().filter(row -> row.place() == Place.MAIN).toList();
	}

	/** @return the rows on the Developer page */
	public List<Row> developer() {
		return rows.stream().filter(row -> row.place() == Place.DEVELOPER).toList();
	}

	/** @return whether the page only takes new names, and stays as it is */
	public boolean onlyNames() {
		return rows.stream().allMatch(row -> row.place() == Place.NAME);
	}

	/** @return the rows under Advanced */
	public List<Row> advanced() {
		return rows.stream().filter(Row::advanced).toList();
	}

	/**
	 * @return the outlines in a text, by feature id
	 * @throws IllegalArgumentException for a line that cannot be read: the file ships with the mod, so that is a mistake to be found by the tests, not something to get past
	 */
	public static Map<String, PageOutline> parse(String text) {
		Map<String, PageOutline> outlines = new LinkedHashMap<>();
		for (String raw : text.split("\n")) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] parts = line.split("\\|", -1);
			if (parts.length < KEY + 1 || parts.length > LABEL + 1) {
				throw new IllegalArgumentException("Not a line of an outline: " + line);
			}
			Place place = PLACES.get(parts[PLACE].strip());
			if (place == null) {
				throw new IllegalArgumentException("Not a place: " + line);
			}
			String key = parts[KEY].strip();
			if (key.isEmpty()) {
				throw new IllegalArgumentException("No key: " + line);
			}
			String label = parts.length > LABEL && !parts[LABEL].isBlank() ? parts[LABEL].strip() : null;
			Row row = new Row(key, parts[GROUP].strip(), label, place);
			for (String feature : parts[FEATURES].split(",")) {
				outlines.computeIfAbsent(feature.strip(), id -> new PageOutline()).rows.add(row);
			}
		}
		return outlines;
	}
}
