package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.Changelog;
import dev.tim1337.lumio.api.Changelog.Release;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the "What is new" window shows, worked out without the game: a tab per mod with a changelog (Lumio first, then the add-ons; no tab row
 * with Lumio alone); released versions only in a release build (a development build also shows "In development"); a version newer
 * than the one last seen of its mod is new, and a tab with a new version has a dot. Plain logic, unit tested.
 */
public final class WhatsNew {
	private static final DateTimeFormatter SHOWN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	/** A mod with its changelog. */
	public record Mod(String id, String name, List<Release> releases) {
	}

	/** A version in a list: the mod it belongs to, and whether it is new to the player. */
	public record Item(Mod mod, Release release, boolean isNew) {
	}

	private final List<Mod> mods;
	private final Map<String, String> seen;

	/**
	 * @param mods         Lumio first, then the add-ons with a readable changelog
	 * @param seen         the version last seen per mod id
	 * @param releaseBuild whether this is a release build: "In development" is then left out
	 */
	public WhatsNew(List<Mod> mods, Map<String, String> seen, boolean releaseBuild) {
		List<Mod> shown = new ArrayList<>();
		for (Mod mod : mods) {
			List<Release> releases = mod.releases().stream().filter(release -> !releaseBuild || !release.inDevelopment()).toList();
			if (!releases.isEmpty()) {
				shown.add(new Mod(mod.id(), mod.name(), releases));
			}
		}
		this.mods = List.copyOf(shown);
		this.seen = Map.copyOf(seen);
	}

	/** @return the tabs: one per mod, by name, Lumio first; with Lumio alone the window shows no tab row */
	public List<String> tabs() {
		List<String> tabs = new ArrayList<>();
		for (Mod mod : mods) {
			tabs.add(mod.name());
		}
		return tabs;
	}

	/** @return the versions of a tab, newest first: its mod's, in the order of its file */
	public List<Item> items(String tab) {
		List<Item> items = new ArrayList<>();
		for (Mod mod : mods) {
			if (mod.name().equals(tab)) {
				for (Release release : mod.releases()) {
					items.add(new Item(mod, release, isNew(mod, release)));
				}
			}
		}
		return items;
	}

	/** @return whether a tab has a version newer than the one last seen */
	public boolean hasNew(String tab) {
		return items(tab).stream().anyMatch(Item::isNew);
	}

	/** @return the tab of the first mod with something new (Lumio before the add-ons), or null when nothing is new */
	public String changedTab() {
		for (Mod mod : mods) {
			if (hasNew(mod.name())) {
				return mod.name();
			}
		}
		return null;
	}

	/**
	 * A released version is new when it comes before the one last seen in its mod's file (the file lists the newest first); with none seen,
	 * only the newest is. "In development" never is: it changes all the time.
	 */
	private boolean isNew(Mod mod, Release release) {
		if (release.inDevelopment()) {
			return false;
		}
		String last = seen.get(mod.id());
		Release newest = newestReleased(mod);
		if (last == null) {
			return release == newest;
		}
		for (Release each : mod.releases()) {
			if (each.version().equals(last)) {
				return false;
			}
			if (each == release) {
				return true;
			}
		}
		return false;
	}

	private static Release newestReleased(Mod mod) {
		return mod.releases().stream().filter(release -> !release.inDevelopment()).findFirst().orElse(null);
	}

	/** @return every mod seen up to its newest released version: once the window was opened, and on a first start */
	public Map<String, String> allSeen() {
		Map<String, String> after = new LinkedHashMap<>(seen);
		for (Mod mod : mods) {
			Release newest = newestReleased(mod);
			if (newest != null) {
				after.put(mod.id(), newest.version());
			}
		}
		return after;
	}

	/** @return the seen versions from their saved text, "lumio=1.0.0-beta.2;lumio-skyblock=1.0.0-beta.2"; parts that cannot be read are left out */
	public static Map<String, String> readSeen(String saved) {
		Map<String, String> seen = new LinkedHashMap<>();
		if (saved == null) {
			return seen;
		}
		for (String part : saved.split(";")) {
			int at = part.indexOf('=');
			if (at > 0 && at < part.length() - 1) {
				seen.put(part.substring(0, at).strip(), part.substring(at + 1).strip());
			}
		}
		return seen;
	}

	/** @return the seen versions as saved */
	public static String writeSeen(Map<String, String> seen) {
		StringBuilder text = new StringBuilder();
		seen.forEach((id, version) -> text.append(text.isEmpty() ? "" : ";").append(id).append('=').append(version));
		return text.toString();
	}

	/** @return how a version reads: "1.0.0 Beta 2", "In development" */
	public static String versionShown(String version) {
		if (Changelog.IN_DEVELOPMENT.equalsIgnoreCase(version)) {
			return Changelog.IN_DEVELOPMENT;
		}
		String[] parts = version.split("-", 2);
		if (parts.length < 2) {
			return version;
		}
		String[] stage = parts[1].split("\\.", 2);
		String name = stage[0].isEmpty() ? "" : Character.toUpperCase(stage[0].charAt(0)) + stage[0].substring(1);
		return parts[0] + " " + name + (stage.length > 1 ? " " + stage[1] : "");
	}

	/** @return how a date reads: "24 Nov 2026"; the text as it is when it is no date */
	public static String dateShown(String date) {
		try {
			return LocalDate.parse(date).format(SHOWN_DATE);
		} catch (DateTimeParseException e) {
			return date;
		}
	}
}
