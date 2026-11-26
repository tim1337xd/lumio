package dev.tim1337.lumio.features.waypoints;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of waypoints that are shown together: where they show (its areas), how many at once, when the next one comes, how they look and whether a
 * map shows them. A read-only group's points cannot be changed, only hidden.
 */
public final class WaypointGroup {
	/** The area that is everywhere: every island of the add-on's world, or every server and world. */
	public static final String EVERYWHERE = "everywhere";
	/** Without an add-on that knows the places: the server or the singleplayer world the group was made on. */
	public static final String THIS_SERVER = "server";
	public static final String THIS_WORLD = "world";
	static final int MAX_NAME = 32;
	/** The most points a group keeps; an import of more is cut. */
	public static final int MAX_POINTS = 2000;

	/** Which of the points are shown. */
	public enum Show {
		ALL("All", "Every point at once"),
		NEXT("Next", "The next points of a route, the rest hidden"),
		NEAREST("Nearest", "The closest points");

		private final String label;
		private final String hint;

		Show(String label, String hint) {
			this.label = label;
			this.hint = hint;
		}

		public String label() {
			return label;
		}

		public String hint() {
			return hint;
		}
	}

	/** When a route goes on to its next point. */
	public enum Advance {
		REACHED("Near", "Close to it, or once you pass it"),
		MINED("Mined", "When its block changes nearby"),
		KEY("Key", "With the Next waypoint key");

		private final String label;
		private final String hint;

		Advance(String label, String hint) {
			this.label = label;
			this.hint = hint;
		}

		public String label() {
			return label;
		}

		public String hint() {
			return hint;
		}
	}

	/** Whether and how a map of the points is shown in the HUD. */
	public enum MapMode {
		OFF("Off"), CORNER("Corner"), BIG("On key");

		private final String label;

		MapMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** The parts of the look, each on or off. */
	public enum Look {
		BLOCK("Block"), BEAM("Beam"), LINE("Line to next"), THROUGH_WALLS("Through walls");

		private final String label;

		Look(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		int bit() {
			return 1 << ordinal();
		}
	}

	/** What the label above a point shows. */
	public enum Label {
		NAME("Name"), NAME_DISTANCE("Name + distance"), OFF("Off");

		private final String label;

		Label(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	static final int DEFAULT_LOOK = Look.BLOCK.bit() | Look.LINE.bit();
	/** How many points Next and Nearest show, at most and at first. */
	public static final int MOST_SHOWN = 3;
	public static final int DEFAULT_SHOWN = 2;
	/** The label's size, as a factor, its bounds and its start. */
	public static final float LEAST_LABEL_SIZE = 0.6f;
	public static final float MOST_LABEL_SIZE = 1.6f;
	public static final float DEFAULT_LABEL_SIZE = 1f;

	private final String id;
	private String name;
	private String icon = "pin";
	private int rgb;
	private boolean enabled = true;
	private final List<String> areas = new ArrayList<>();
	private Show show = Show.NEXT;
	/** How many points Next and Nearest show. */
	private int nextCount = DEFAULT_SHOWN;
	private Label label = Label.NAME;
	private boolean labelBackground;
	/** Whether the route starts at its first point again when the player joins a new lobby. */
	private boolean restartInNewLobby;
	private float labelSize = DEFAULT_LABEL_SIZE;
	private Advance advance = Advance.REACHED;
	/** How near a point counts as reached, in blocks across. */
	private int reach = RouteProgress.DEFAULT_REACH;
	/** Where the route is, while the game runs. */
	private final RouteProgress progress = new RouteProgress();
	private int look = DEFAULT_LOOK;
	private MapMode map = MapMode.OFF;
	private final List<Waypoint> points = new ArrayList<>();
	/** The index of the next point of a route. */
	private int next;
	/** Where the points came from ("imported from SkyHanni"), or "". */
	private String source = "";
	private boolean readOnly;

	public WaypointGroup(String id, String name, int rgb) {
		this.id = id;
		rename(name);
		this.rgb = rgb & 0xFFFFFF;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public void rename(String name) {
		String trimmed = name == null ? "" : name.strip();
		this.name = trimmed.isEmpty() ? "Waypoints" : trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
	}

	public String icon() {
		return icon;
	}

	public void icon(String icon) {
		this.icon = icon;
	}

	public int rgb() {
		return rgb;
	}

	public void rgb(int rgb) {
		this.rgb = rgb & 0xFFFFFF;
	}

	/** @return the color a point is drawn in: its own, or the group's */
	public int rgbOf(Waypoint point) {
		return point.rgb() != 0 ? point.rgb() : rgb;
	}

	public boolean enabled() {
		return enabled;
	}

	public void enabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** @return the keys of the areas it shows in: {@link #EVERYWHERE}, {@link #THIS_SERVER}, {@link #THIS_WORLD} or places of the add-on */
	public List<String> areas() {
		return areas;
	}

	public Show show() {
		return show;
	}

	public void show(Show show) {
		this.show = show;
	}

	public int nextCount() {
		return nextCount;
	}

	public void nextCount(int count) {
		nextCount = Math.clamp(count, 1, MOST_SHOWN);
	}

	public Label label() {
		return label;
	}

	public void label(Label label) {
		this.label = label;
	}

	public boolean restartInNewLobby() {
		return restartInNewLobby;
	}

	public void restartInNewLobby(boolean on) {
		restartInNewLobby = on;
	}

	/** Starts the route at its first point again, as if it had just been started: where the player was at is forgotten. The points stay. */
	public void restart() {
		next = 0;
		progress.reset();
	}

	public boolean labelBackground() {
		return labelBackground;
	}

	public void labelBackground(boolean on) {
		labelBackground = on;
	}

	public float labelSize() {
		return labelSize;
	}

	public void labelSize(float size) {
		labelSize = Float.isFinite(size) ? Math.clamp(size, LEAST_LABEL_SIZE, MOST_LABEL_SIZE) : DEFAULT_LABEL_SIZE;
	}

	public Advance advance() {
		return advance;
	}

	public void advance(Advance advance) {
		this.advance = advance;
	}

	public int reach() {
		return reach;
	}

	public void reach(int blocks) {
		reach = Math.clamp(blocks, RouteProgress.MIN_REACH, RouteProgress.MAX_REACH);
	}

	RouteProgress progress() {
		return progress;
	}

	public boolean has(Look part) {
		return (look & part.bit()) != 0;
	}

	public void set(Look part, boolean on) {
		look = on ? look | part.bit() : look & ~part.bit();
	}

	int lookBits() {
		return look;
	}

	void lookBits(int bits) {
		look = bits & ((1 << Look.values().length) - 1);
	}

	public MapMode map() {
		return map;
	}

	public void map(MapMode map) {
		this.map = map;
	}

	public List<Waypoint> points() {
		return points;
	}

	/** @return the index of the next point of a route, kept within the points */
	public int next() {
		return points.isEmpty() ? 0 : Math.floorMod(next, points.size());
	}

	public void next(int index) {
		next = points.isEmpty() ? 0 : Math.floorMod(index, points.size());
	}

	public String source() {
		return source;
	}

	public void source(String source) {
		this.source = source == null ? "" : source;
	}

	/** @return whether a point is drawn: not hidden */
	public boolean drawn(int index) {
		return !points.get(index).hidden();
	}

	public boolean readOnly() {
		return readOnly;
	}

	public void readOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	/** @return the number of points that are not hidden */
	public int shownCount() {
		int count = 0;
		for (Waypoint point : points) {
			if (!point.hidden()) {
				count++;
			}
		}
		return count;
	}

	/** Adds a point, if there is room. @return whether it was added */
	public boolean add(Waypoint point) {
		if (readOnly || points.size() >= MAX_POINTS) {
			return false;
		}
		points.add(point);
		return true;
	}

	/** Moves a point to another place in the order, keeping the next point of a route on the same point. */
	public void move(int from, int to) {
		if (readOnly || from == to || from < 0 || to < 0 || from >= points.size() || to >= points.size()) {
			return;
		}
		Waypoint nextPoint = points.isEmpty() ? null : points.get(next());
		points.add(to, points.remove(from));
		next = nextPoint == null ? 0 : points.indexOf(nextPoint);
	}

	public void remove(int index) {
		if (readOnly || index < 0 || index >= points.size()) {
			return;
		}
		points.remove(index);
		if (next > index) {
			next--;
		}
		next(next);
	}
}
