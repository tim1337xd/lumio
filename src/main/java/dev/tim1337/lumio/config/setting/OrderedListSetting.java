package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.OrderedChoice;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.OrderedListControl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An ordered list of parts, such as the scoreboard lines or the info bar segments: every part is listed, each switched on or off, in the chosen
 * order. Repeatable parts (a spacer) can be added any number of times and removed again. A part can carry its own small settings, edited in its
 * expanded row. Persisted as the ordered ids of all rows and the ids switched off; repeatable parts get a numeric suffix ("space#2").
 */
public class OrderedListSetting extends Setting<Void> implements OrderedChoice {
	/** A part: its persisted id, title, icon, whether it is on by default, whether it can be added more than once, and its own settings. */
	public record Option(String id, String title, String icon, boolean onByDefault, boolean repeatable, List<Setting<?>> settings) {
		public Option(String id, String title, String icon, boolean onByDefault, boolean repeatable) {
			this(id, title, icon, onByDefault, repeatable, List.of());
		}

		public Option(String id, String title, String icon, boolean onByDefault) {
			this(id, title, icon, onByDefault, false, List.of());
		}

		/** @return the same part with its own settings, shown when its row is expanded */
		public Option with(List<Setting<?>> own) {
			return new Option(id, title, icon, onByDefault, repeatable, List.copyOf(own));
		}
	}

	/** The parts, in their order; an add-on's parts are inserted while the game starts ({@link #addOption}). */
	private final List<Option> options;
	/** Every row in order: all parts that are not repeatable, plus each added repeatable part. */
	private final List<String> rows = new ArrayList<>();
	/** The rows switched off. */
	private final Set<String> off = new HashSet<>();
	/** The default layout, with repeatable parts listed by their option id: what is on, and where, before anything was chosen. */
	private List<String> defaultLayout;
	private int nextInstance = 1;
	/** Counts changes, so readers can keep what they derived from the list until it changes. */
	private int version;
	/** The rows switched on, in order, rebuilt only after a change: read every frame by the info bar. */
	private List<String> enabledView = List.of();
	/** The shown rows, rebuilt with {@link #enabledView}. */
	private List<String> visibleView = List.of();
	private int enabledVersion = -1;

	public OrderedListSetting(String key, Component label, List<Option> options) {
		super(key, label, null);
		this.options = new ArrayList<>(options);
		reset();
	}

	/** Sets the parts that are on by default, in order, including repeatable ones (once per occurrence); the others follow switched off. */
	public OrderedListSetting defaultLayout(List<String> layout) {
		defaultLayout = List.copyOf(layout);
		reset();
		return this;
	}

	private void changed() {
		version++;
	}

	/** @return a number that changes whenever the list changes */
	public int version() {
		return version;
	}

	/**
	 * Adds a part after another one (at the end when that one is not there), such as a segment an add-on brings. Only while the settings are
	 * made, before they are loaded: the list goes back to its default.
	 */
	public void addOption(Option option, String afterId) {
		int at = options.size();
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).id().equals(afterId)) {
				at = i + 1;
				break;
			}
		}
		options.add(at, option);
		reset();
	}

	/**
	 * @return whether a row is shown: rows of parts nobody offers (an add-on that is not installed) stay in the saved list with their place
	 *         and state, so nothing is lost when it comes back, but are not shown or used
	 */
	private boolean shown(String row) {
		return option(row) != null;
	}

	/** @return the rows switched on, in order; a repeatable part appears once per use. The list is shared and must not be changed */
	public List<String> enabledInOrder() {
		if (enabledVersion != version) {
			List<String> on = new ArrayList<>(rows.size());
			List<String> visible = new ArrayList<>(rows.size());
			for (String id : rows) {
				if (!shown(id)) {
					continue;
				}
				visible.add(id);
				if (!off.contains(id)) {
					on.add(id);
				}
			}
			enabledView = Collections.unmodifiableList(on);
			visibleView = Collections.unmodifiableList(visible);
			enabledVersion = version;
		}
		return enabledView;
	}

	/** @return the shown rows in order, switched on or off. The list is shared and must not be changed */
	public List<String> rows() {
		enabledInOrder();
		return visibleView;
	}

	/** @return whether a row is switched on */
	public boolean isOn(String id) {
		return rows.contains(id) && !off.contains(id);
	}

	/** Switches a row on or off. */
	public void setOn(String id, boolean on) {
		if (!rows.contains(id) || on != off.contains(id)) {
			return;
		}
		if (on) {
			off.remove(id);
		} else {
			off.add(id);
		}
		changed();
	}

	/** @return the option of a row; repeatable parts are looked up by their id with suffix */
	public Option option(String id) {
		String base = baseId(id);
		for (Option option : options) {
			if (option.id().equals(base)) {
				return option;
			}
		}
		return null;
	}

	private static String baseId(String id) {
		int mark = id.indexOf('#');
		return mark >= 0 ? id.substring(0, mark) : id;
	}

	/** @return the repeatable parts, which can be added any number of times */
	public List<Option> repeatables() {
		List<Option> out = new ArrayList<>();
		for (Option option : options) {
			if (option.repeatable()) {
				out.add(option);
			}
		}
		return out;
	}

	/** Appends a repeatable part, switched on. */
	public void add(Option option) {
		if (option.repeatable()) {
			rows.add(option.id() + "#" + nextInstance++);
			changed();
		}
	}

	/** Removes a repeatable part; the other parts are switched off instead. */
	public void remove(String id) {
		Option option = option(id);
		if (option != null && option.repeatable() && rows.remove(id)) {
			off.remove(id);
			changed();
		}
	}

	/** Moves a row from one place to another of the shown rows ({@link #rows}); hidden rows keep their place among the others. */
	public void move(int from, int to) {
		List<String> visible = rows();
		if (from < 0 || from >= visible.size() || to < 0 || to >= visible.size() || from == to) {
			return;
		}
		String moved = visible.get(from);
		String target = visible.get(to);
		rows.remove(moved);
		int at = rows.indexOf(target);
		rows.add(from < to ? at + 1 : at, moved);
		changed();
	}

	/** Inserts a repeatable part before another part, if that part is listed. */
	public void insertBefore(String repeatableId, String beforeId) {
		int at = rows.indexOf(beforeId);
		Option option = option(repeatableId);
		if (at >= 0 && option != null && option.repeatable()) {
			rows.add(at, option.id() + "#" + nextInstance++);
			changed();
		}
	}

	/** @return the default rows in order, with repeatable parts by their option id, and which of them are on */
	private List<String> defaultRows(Set<String> defaultOff) {
		List<String> out = new ArrayList<>();
		if (defaultLayout != null) {
			for (String id : defaultLayout) {
				Option option = option(id);
				if (option != null && (option.repeatable() || !out.contains(option.id()))) {
					out.add(option.id());
				}
			}
			for (Option option : options) {
				if (!option.repeatable() && !out.contains(option.id())) {
					out.add(option.id());
					defaultOff.add(option.id());
				}
			}
			return out;
		}
		for (Option option : options) {
			if (!option.repeatable()) {
				out.add(option.id());
				if (!option.onByDefault()) {
					defaultOff.add(option.id());
				}
			}
		}
		return out;
	}

	/** @return whether the rows are the default ones in the default order and state; repeated parts are compared by kind, not by their number */
	@Override
	public boolean isDefault() {
		Set<String> defaultOff = new HashSet<>();
		List<String> wanted = defaultRows(defaultOff);
		if (wanted.size() != rows.size()) {
			return false;
		}
		for (int i = 0; i < rows.size(); i++) {
			String id = rows.get(i);
			String base = baseId(id);
			if (!base.equals(wanted.get(i)) || off.contains(id) != (!id.contains("#") && defaultOff.contains(base))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void reset() {
		rows.clear();
		off.clear();
		nextInstance = 1;
		Set<String> defaultOff = new HashSet<>();
		for (String id : defaultRows(defaultOff)) {
			Option option = option(id);
			if (option.repeatable()) {
				rows.add(id + "#" + nextInstance++);
			} else {
				rows.add(id);
				if (defaultOff.contains(id)) {
					off.add(id);
				}
			}
		}
		changed();
	}

	@Override
	public JsonElement save() {
		JsonObject json = new JsonObject();
		JsonArray ids = new JsonArray();
		rows.forEach(ids::add);
		json.add("rows", ids);
		JsonArray hidden = new JsonArray();
		for (String id : rows) {
			if (off.contains(id)) {
				hidden.add(id);
			}
		}
		json.add("off", hidden);
		// the options known at save time, so parts added in later versions are placed by their default on load
		JsonArray known = new JsonArray();
		options.forEach(option -> known.add(option.id()));
		json.add("known", known);
		return json;
	}

	@Override
	public void load(JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			return;
		}
		try {
			JsonObject object = json.getAsJsonObject();
			List<String> loaded = new ArrayList<>();
			Set<String> loadedOff = new HashSet<>();
			Set<String> known = new HashSet<>();
			if (object.has("known") && object.get("known").isJsonArray()) {
				object.getAsJsonArray("known").forEach(id -> known.add(id.getAsString()));
			}
			if (object.has("rows") && object.get("rows").isJsonArray()) {
				object.getAsJsonArray("rows").forEach(id -> loaded.add(id.getAsString()));
				if (object.has("off") && object.get("off").isJsonArray()) {
					object.getAsJsonArray("off").forEach(id -> loadedOff.add(id.getAsString()));
				}
			} else {
				return;
			}
			rows.clear();
			off.clear();
			nextInstance = 1;
			for (String id : loaded) {
				Option option = option(id);
				if (option == null) {
					// a part nobody offers now: kept as it was, see {@link #shown}
					if (!rows.contains(id)) {
						rows.add(id);
						if (loadedOff.contains(id)) {
							off.add(id);
						}
					}
					continue;
				}
				String row = option.repeatable() ? option.id() + "#" + nextInstance++ : id;
				if (!option.repeatable() && rows.contains(row)) {
					continue;
				}
				rows.add(row);
				if (loadedOff.contains(id)) {
					off.add(row);
				}
			}
			// every part is listed: parts new since the save that are on by default go to their default place, all others follow switched off
			Set<String> defaultOff = new HashSet<>();
			List<String> defaults = defaultRows(defaultOff);
			for (int i = 0; i < defaults.size(); i++) {
				String id = defaults.get(i);
				Option option = option(id);
				if (option.repeatable() || rows.contains(id)) {
					continue;
				}
				boolean isNew = !known.isEmpty() && !known.contains(id);
				if (isNew && !defaultOff.contains(id)) {
					rows.add(Math.min(i, rows.size()), id);
				} else {
					rows.add(id);
					off.add(id);
				}
			}
		} catch (RuntimeException ignored) {
			// unreadable input leaves the rest of the list as it was loaded so far
		}
		changed();
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new OrderedListControl(this, onChanged);
	}
}
