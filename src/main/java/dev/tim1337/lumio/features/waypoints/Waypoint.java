package dev.tim1337.lumio.features.waypoints;

/** One marked block: where it is, its name, its own color (0 for its group's), whether it is hidden and whether its label is left out. */
public final class Waypoint {
	/** The longest name kept; longer ones from imports are cut. */
	static final int MAX_NAME = 48;

	private int x;
	private int y;
	private int z;
	private String name;
	private int rgb;
	private boolean hidden;
	/** Only for an add-on's own temporary point that asked for no label; never stored. */
	private boolean noLabel;

	public Waypoint(int x, int y, int z, String name) {
		this.x = x;
		this.y = y;
		this.z = z;
		rename(name);
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int z() {
		return z;
	}

	public void moveTo(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public String name() {
		return name;
	}

	public void rename(String name) {
		String trimmed = name == null ? "" : name.strip();
		this.name = trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
	}

	/** @return the point's own color, or 0 when it has its group's */
	public int rgb() {
		return rgb;
	}

	public void rgb(int rgb) {
		this.rgb = rgb & 0xFFFFFF;
	}

	public boolean hidden() {
		return hidden;
	}

	public void hidden(boolean hidden) {
		this.hidden = hidden;
	}

	/** @return whether the point is drawn without its label */
	public boolean noLabel() {
		return noLabel;
	}

	public void noLabel(boolean noLabel) {
		this.noLabel = noLabel;
	}

	/** @return the squared distance from a position to the middle of the block */
	public double distanceSq(double px, double py, double pz) {
		double dx = x + 0.5 - px;
		double dy = y + 0.5 - py;
		double dz = z + 0.5 - pz;
		return dx * dx + dy * dy + dz * dz;
	}

	/** @return "12, 140, -84" */
	public String coords() {
		return x + ", " + y + ", " + z;
	}

	public Waypoint copy() {
		Waypoint copy = new Waypoint(x, y, z, name);
		copy.rgb = rgb;
		copy.hidden = hidden;
		copy.noLabel = noLabel;
		return copy;
	}
}
