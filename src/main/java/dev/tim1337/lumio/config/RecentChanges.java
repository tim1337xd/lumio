package dev.tim1337.lumio.config;

import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.menu.FeatureListFeature;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The settings changed last in the menu, for the home page, kept for the session, with the value before and after each change and when it
 * happened, so a change can be undone. Changes are only collected while the menu handles a click or a key ({@link #begin}, {@link #end}); a
 * single input that changes many settings at once (loading a config, a reset) is not listed, since it would push out everything else. Several
 * changes of the same setting in a row are one entry, from the first old value to the latest new one.
 */
public final class RecentChanges {
	/** How many changes are kept; the home page shows the newest few and the rest on request. */
	public static final int KEPT = 20;
	/** Inputs changing more settings than this are bulk changes and not listed. */
	private static final int MAX_PER_INPUT = 3;

	/** A value as the list shows it: its text, null when it has no short form, and the color before it, -1 for none. */
	public record Value(String text, int swatch) {
		private static Value of(Setting<?> setting) {
			return new Value(setting.valueText(), setting.valueSwatch());
		}
	}

	/** A listed change: of a setting, or of a whole feature turned on or off (a toggle). */
	public static final class Change {
		private final Feature feature;
		private final Setting<?> setting;
		private final boolean toggle;
		private final String place;
		private final Value before;
		/** The saved value before the change, which undoing loads again; null if the setting cannot be loaded. */
		private final JsonElement undo;
		private final boolean wasOn;
		private Value after;
		private JsonElement saved;
		private boolean on;
		private long millis;

		private Change(Pending pending, Value after, JsonElement saved, boolean on, long millis) {
			this.feature = pending.owner;
			this.setting = pending.shown;
			this.toggle = pending.toggle;
			this.place = placeOf(pending.owner);
			this.before = pending.before;
			this.undo = pending.saved;
			this.wasOn = pending.wasOn;
			this.after = after;
			this.saved = saved;
			this.on = on;
			this.millis = millis;
		}

		/** @return the feature, or the feature a part belongs to */
		public Feature feature() {
			return feature;
		}

		/** @return the setting as the menu shows it: a multi-select list rather than one of its options */
		public Setting<?> setting() {
			return setting;
		}

		/** @return whether a whole feature was turned on or off */
		public boolean toggle() {
			return toggle;
		}

		/** @return whether a toggled feature is on now */
		public boolean on() {
			return on;
		}

		/** @return the page the feature is listed on, such as "SkyBlock  ›  Slayer", or empty for a feature that is a page of its own */
		public String place() {
			return place;
		}

		/** @return the value before the change; its text is null when the value has no short form */
		public Value before() {
			return before;
		}

		public Value after() {
			return after;
		}

		/** @return when the change was made, in milliseconds since the epoch */
		public long millis() {
			return millis;
		}

		/** @return whether the change can be undone */
		public boolean undoable() {
			return toggle || undo != null;
		}

		private Object key() {
			return toggle ? feature : setting;
		}

		/** @return whether the setting is back at its value before the change */
		private boolean undone() {
			return toggle ? on == wasOn : undo != null && undo.equals(saved);
		}
	}

	/** A setting changed by the input that is handled: what it is and its value before. */
	private record Pending(Feature owner, Setting<?> shown, boolean toggle, Value before, JsonElement saved, boolean wasOn) {
		private Object key() {
			return toggle ? owner : shown;
		}
	}

	private static final List<Change> CHANGES = new ArrayList<>();
	/** A read-only view of the changes, made once: the home page reads it every frame. */
	private static final List<Change> VIEW = Collections.unmodifiableList(CHANGES);
	/** The settings the input changed so far, so each is looked up once. */
	private static final List<Setting<?>> SEEN = new ArrayList<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static int depth;
	private static boolean overflow;
	/** While a change is undone, the setting's own change is not listed again. */
	private static boolean restoring;
	private static int version;

	private RecentChanges() {
	}

	/** Starts collecting the settings an input changes. Inputs can nest (the menu passes them on to the window next to it). */
	public static void begin() {
		if (depth++ == 0) {
			SEEN.clear();
			PENDING.clear();
			overflow = false;
		}
	}

	/** Ends an input and lists the settings it changed, unless they were too many. */
	public static void end() {
		if (depth == 0 || --depth > 0) {
			return;
		}
		if (!overflow) {
			long now = System.currentTimeMillis();
			for (Pending pending : PENDING) {
				note(pending, now);
			}
		}
		SEEN.clear();
		PENDING.clear();
	}

	/** Called by every setting just before it is set, so its old value is known; does nothing unless the menu handles an input. */
	public static void changing(Setting<?> setting) {
		if (depth == 0 || overflow || restoring || !setting.listedInRecent() || SEEN.contains(setting)) {
			return;
		}
		SEEN.add(setting);
		Pending pending = pendingFor(setting);
		if (pending == null) {
			return;
		}
		for (Pending other : PENDING) {
			if (other.key() == pending.key()) {
				return;
			}
		}
		if (PENDING.size() == MAX_PER_INPUT) {
			overflow = true;
			return;
		}
		PENDING.add(pending);
	}

	/** @return the listed changes, newest first */
	public static List<Change> list() {
		return VIEW;
	}

	/** @return a number that changes whenever the list does, so the home page knows when to update what it built from it */
	public static int version() {
		return version;
	}

	/** Empties the list. */
	public static void clear() {
		CHANGES.clear();
		version++;
	}

	/** Sets a change's setting back to its value before the change and removes the change. @return whether it could be undone */
	public static boolean undo(Change change) {
		if (!change.undoable()) {
			return false;
		}
		restoring = true;
		try {
			if (change.toggle) {
				change.feature.setEnabled(change.wasOn);
			} else {
				change.setting.load(change.undo);
			}
		} finally {
			restoring = false;
		}
		CHANGES.remove(change);
		version++;
		return true;
	}

	/** Looks up the feature a setting belongs to and keeps its value before the change, or returns null for a setting of no feature. */
	private static Pending pendingFor(Setting<?> setting) {
		for (Feature feature : FeatureManager.features()) {
			for (Setting<?> own : feature.settings()) {
				Setting<?> shown = own == setting ? own : own instanceof MultiSelectSetting multi && multi.options().contains(setting) ? own : null;
				if (shown != null) {
					Feature owner = feature.isChild() ? feature.parent() : feature;
					// a feature's switch, or the switch of a part when the feature is turned on or off as a whole
					boolean toggle = shown == setting && setting.key().equals("enabled");
					return toggle ? new Pending(owner, shown, true, null, null, owner.isAnyOn())
						: new Pending(owner, shown, false, Value.of(shown), shown.save(), false);
				}
			}
		}
		return null;
	}

	private static void note(Pending pending, long now) {
		boolean on = pending.toggle && pending.owner.isAnyOn();
		Value after = pending.toggle ? null : Value.of(pending.shown);
		JsonElement saved = pending.toggle ? null : pending.shown.save();
		if (pending.toggle ? on == pending.wasOn : Objects.equals(saved, pending.saved) && Objects.equals(after, pending.before)) {
			return;
		}
		Change newest = CHANGES.isEmpty() ? null : CHANGES.getFirst();
		if (newest != null && newest.key() == pending.key()) {
			// the same setting again: one entry from its first old value to the latest new one, gone when it is back where it started
			newest.after = after;
			newest.saved = saved;
			newest.on = on;
			newest.millis = now;
			if (newest.undone()) {
				CHANGES.removeFirst();
			}
		} else {
			CHANGES.addFirst(new Change(pending, after, saved, on, now));
			while (CHANGES.size() > KEPT) {
				CHANGES.removeLast();
			}
		}
		version++;
	}

	/** @return the page that lists the feature, a group page before its parent, or empty for a feature that is a page of its own */
	private static String placeOf(Feature feature) {
		FeatureListFeature found = null;
		for (Feature page : FeatureManager.features()) {
			if (page instanceof FeatureListFeature list && (list.holds(feature) || list.holdsAll(feature)) && (found == null || list.parentPage() != null)) {
				found = list;
			}
		}
		return found == null ? "" : found.path();
	}
}
