package dev.tim1337.lumio.api.experimental.waypoint;

import dev.tim1337.lumio.api.spi.Backend;

/**
 * The look of waypoints the player set once for all of them (Waypoints › Look): the text, the marker, the beam, fading and sizes. Every kind of
 * waypoint draws by it, so they look alike; a kind may let the player set a look of its own instead ({@link #pick}). {@link #DEFAULT} is the look
 * waypoints had before there were settings for it. A style only changes how a point looks, never whether walls hide it.
 *
 * @param textSize       how large the label is, 1 for the usual size
 * @param textScaling    how the label's size changes with the distance
 * @param showName       whether the label has the point's name
 * @param showDistance   whether the label may have the distance (where the kind of waypoint shows one)
 * @param textShadow     whether a label without a background has its soft shadow
 * @param textBackground whether every label stands on a dark box
 * @param marker         what marks the block
 * @param lineWidth      how wide the outline is, in screen pixels
 * @param fill           how strong the box's fill is, 1 for the usual strength, 0 for none
 * @param beam           whether beams show at all
 * @param beamHeight     how high a beam reaches, in blocks
 * @param beamStrength   how strong a beam is, 1 for the usual strength
 * @param fadeNear       whether a label fades out where the player stands at its point
 * @param maxDistance    how far a point is drawn, in blocks; 0 for any distance
 * @param iconSize       how large the icon of an "icon only" marker is, 1 for the usual size
 */
public record WaypointStyle(float textSize, TextScaling textScaling, boolean showName, boolean showDistance, boolean textShadow, boolean textBackground,
	Marker marker, float lineWidth, float fill, boolean beam, float beamHeight, float beamStrength, boolean fadeNear, int maxDistance, float iconSize) {

	/** How a label's size on the screen changes with the distance; the text size scales all of them. */
	public enum TextScaling {
		/** Truly the same size on the screen at any distance. */
		STEADY("Steady"),
		/** Full size close by, down to about two thirds far away: as waypoints always were. */
		SMALLER_FAR("Smaller far away"),
		/** Shrinking with the distance like real text in the world. */
		SIGN("Like a sign");

		private final String label;

		TextScaling(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** What marks a point's block. */
	public enum Marker {
		/** A filled box with an outline. */
		BOX("Box"),
		/** The outline alone. */
		OUTLINE("Outline"),
		/** A beam of light alone, on every point. */
		BEAM("Beam"),
		/** Nothing on the block: the label alone, with the point's icon. */
		ICON("Icon only");

		private final String label;

		Marker(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** The outline's width before there was a setting for it, in screen pixels. */
	public static final float DEFAULT_LINE_WIDTH = 2f;
	/** A label fades out closer than this, in blocks, down to nothing at {@link #GONE_NEAR_BLOCKS}. */
	public static final double FADE_NEAR_BLOCKS = 2.5;
	public static final double GONE_NEAR_BLOCKS = 1.0;
	/** A label that shrinks like a sign has its full size up to here, in blocks, and never gets smaller than the least size. */
	private static final double FULL_SIZE_BLOCKS = 8.0;
	private static final float LEAST_SHRUNK_SIZE = 0.2f;

	/** The look waypoints had before it could be set. */
	public static final WaypointStyle DEFAULT = new WaypointStyle(1f, TextScaling.SMALLER_FAR, true, true, true, false, Marker.BOX, DEFAULT_LINE_WIDTH, 1f, true,
		WaypointLook.BEAM_HEIGHT, 1f, true, 0, 1f);

	/** @return the look set under Waypoints › Look, as it is now */
	public static WaypointStyle general() {
		return Backend.get().waypointStyle();
	}

	/** @return the look a kind of waypoint draws by: the general one, or its own where the player switched "Use the general look" off */
	public static WaypointStyle pick(boolean useGeneral, WaypointStyle general, WaypointStyle own) {
		return useGeneral || own == null ? general : own;
	}

	/** @return whether the block gets its box (filled or not) */
	public boolean boxed() {
		return marker == Marker.BOX || marker == Marker.OUTLINE;
	}

	/** @return a fill's opacity by this look: none for the outline alone */
	public float fillOpacity(float usual) {
		return marker == Marker.BOX ? Math.clamp(usual * fill, 0f, 1f) : 0f;
	}

	/**
	 * @param ownBeam whether the point (its group) has a beam of its own
	 * @return whether a beam stands on the point: its own while beams are on; on every point with the beam marker; never with the icon alone
	 */
	public boolean beamOn(boolean ownBeam) {
		return beam && marker != Marker.ICON && (ownBeam || marker == Marker.BEAM);
	}

	/** @return whether a point this far away is drawn at all */
	public boolean inReach(double distance) {
		return maxDistance <= 0 || distance <= maxDistance;
	}

	/** @return how large a label is at a distance, in GUI pixels per label pixel */
	public float labelScale(double distance) {
		return switch (textScaling) {
			case STEADY -> textSize;
			case SMALLER_FAR -> textSize * WaypointLook.labelSize(distance);
			case SIGN -> textSize * (float) Math.clamp(FULL_SIZE_BLOCKS / Math.max(FULL_SIZE_BLOCKS, distance), LEAST_SHRUNK_SIZE, 1.0);
		};
	}

	/** @return how much of a label shows this close to its point: all of it, or fading out where the player stands at it */
	public float nearOpacity(double distance) {
		return fadeNear ? (float) Math.clamp((distance - GONE_NEAR_BLOCKS) / (FADE_NEAR_BLOCKS - GONE_NEAR_BLOCKS), 0.0, 1.0) : 1f;
	}

	/** @return whether labels show an icon before their text */
	public boolean iconLabel() {
		return marker == Marker.ICON;
	}
}
