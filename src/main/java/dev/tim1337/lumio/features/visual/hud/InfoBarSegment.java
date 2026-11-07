package dev.tim1337.lumio.features.visual.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * The parts the info bar can show: its own, and the ones add-ons register while the game starts (SkyBlock's purse, date, ...). Their ids are
 * persisted. Each has a fixed index into the bar's value arrays, which are {@link #MOST} long, so they never grow.
 */
final class InfoBarSegment {
	/** The most segments the bar has, its own and the add-ons' together. */
	static final int MOST = 32;
	/** The widest a text segment is drawn, in GUI pixels, unless it says otherwise. */
	static final float TEXT_MAX = 110f;

	/** What a segment is, for the drawing of the bar's own ones; every add-on's segment is a {@link #TEXT}. */
	enum Kind {
		CLOCK, FPS, PING, TPS, AREA, SONG, COORDS, MEMORY, CPS, TEXT
	}

	private static final List<InfoBarSegment> ALL = new ArrayList<>();

	static final InfoBarSegment CLOCK = builtIn(Kind.CLOCK, "clock", "Clock", "Time", "clock");
	static final InfoBarSegment FPS = builtIn(Kind.FPS, "fps", "FPS", "FPS", "gauge");
	static final InfoBarSegment PING = builtIn(Kind.PING, "ping", "Ping", "Ping", "bolt");
	static final InfoBarSegment TPS = builtIn(Kind.TPS, "tps", "TPS", "TPS", "timer");
	static final InfoBarSegment AREA = builtIn(Kind.AREA, "area", "Area or server", "Area", "pin");
	static final InfoBarSegment SONG = builtIn(Kind.SONG, "song", "Song", "Song", "note");
	static final InfoBarSegment COORDS = builtIn(Kind.COORDS, "coords", "Coordinates", "Position", "move");
	static final InfoBarSegment MEMORY = builtIn(Kind.MEMORY, "memory", "Memory used", "Memory", "layers");
	static final InfoBarSegment CPS = builtIn(Kind.CPS, "cps", "Clicks per second", "CPS", "cursor");

	final String id;
	final String title;
	/** Short name in the stacked style, left of the value. */
	final String label;
	final String icon;
	final Kind kind;
	/** The widest its text is drawn, in GUI pixels. */
	final float widest;
	/** Whether its text is a number whose digits roll when it changes. */
	final boolean digits;
	/** The color of its icon as 0xAARRGGBB, made readable on the bar; 0 for the usual faint white. */
	final int iconColor;
	private final int index;

	private InfoBarSegment(Kind kind, String id, String title, String label, String icon, float widest, boolean digits, int iconColor, int index) {
		this.kind = kind;
		this.digits = digits;
		this.iconColor = iconColor;
		this.id = id;
		this.title = title;
		this.label = label;
		this.icon = icon;
		this.widest = widest;
		this.index = index;
	}

	private static InfoBarSegment builtIn(Kind kind, String id, String title, String label, String icon) {
		InfoBarSegment segment = new InfoBarSegment(kind, id, title, label, icon, TEXT_MAX, false, 0, ALL.size());
		ALL.add(segment);
		return segment;
	}

	/** Adds an add-on's segment; only while the game starts. @return it, or null when the id is taken or there is no room left */
	static InfoBarSegment register(String id, String title, String label, String icon, float widest, boolean digits, int iconColor) {
		if (byId(id) != null || ALL.size() >= MOST) {
			return null;
		}
		InfoBarSegment segment = new InfoBarSegment(Kind.TEXT, id, title, label, icon, widest, digits, iconColor, ALL.size());
		ALL.add(segment);
		return segment;
	}

	/** @return its index into the bar's value arrays */
	int ordinal() {
		return index;
	}

	/** @return the segment with this persisted id, or null */
	static InfoBarSegment byId(String id) {
		for (int i = 0; i < ALL.size(); i++) {
			if (ALL.get(i).id.equals(id)) {
				return ALL.get(i);
			}
		}
		return null;
	}

	/** @return how many segments there are */
	static int count() {
		return ALL.size();
	}

	static InfoBarSegment of(int ordinal) {
		return ALL.get(ordinal);
	}
}
