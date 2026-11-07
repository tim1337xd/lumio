package dev.tim1337.lumio.features.visual.world.cinematic;

import net.minecraft.util.Mth;

/**
 * A planned shot: its type, where the camera is relative to the player (around them, how far, how high) and how it moves, which third the player
 * sits on, and where the shot's lyric stands. The pose at any moment of the shot follows from these; the player's eyes are the center of it all.
 * Mutable and reused: the scout fills its candidates in place.
 */
final class Shot {
	/** Fields of view of the shot types, in degrees. */
	private static final float FOV_WIDE = 68f;
	private static final float FOV_NORMAL = 56f;
	private static final float FOV_SHOULDER = 60f;
	private static final float FOV_LOW = 64f;
	private static final float FOV_CLOSE = 46f;
	/** A push in never comes closer to the player than this, in blocks; the moves themselves are in {@link CineMotion}. */
	private static final double CLOSEST_PUSH = 1.9;
	/**
	 * The camera never looks up into the sky nor steeply down: its pitch stays within these, in degrees (the game's pitch, down is positive); the
	 * crane looks down less, so the horizon stays in its frame.
	 */
	static final float MOST_UP = -15f;
	static final float MOST_DOWN = 30f;
	private static final float CRANE_MOST_DOWN = 20f;
	/** How far ahead of the player the over-the-shoulder shot looks. */
	private static final double SHOULDER_AHEAD = 12.0;
	/** The player's head and body, relative to their eyes. */
	static final double HEAD = 0.15;
	static final double BODY = -0.85;
	/** A little room above the player's head: the view is raised by this share of the field of view. */
	private static final float HEADROOM = 0.06f;

	ShotType type = ShotType.WIDE_CRANE;
	/** Direction from the player to the camera, in radians, and the horizontal and vertical distance from the player's eyes. */
	double angle;
	double radius;
	double height;
	/** The arc the camera moves around the player over the shot's move, in radians, and its direction. */
	double arc;
	int turn = 1;
	/** How much of its move the shot makes: all of it, or a very slight drift for a locked-off shot (see {@link CineMotion}). */
	double move = 1.0;
	/**
	 * A push-in shortened during the shot so a lyric line stays in the picture: from this point of the move on (and the push reached there), the
	 * camera pushes only this share as far. The push so far is kept, so the camera never jumps.
	 */
	double pushFrom;
	double pushBase;
	double pushScale = 1.0;
	/** Which third the player is on: 1 left, -1 right; the lyric is on the other. */
	int side = 1;
	/** The direction the player looked or walked when the shot was planned, in radians: for the shots that follow it. */
	double lookYaw;
	/** The highest the camera may go above the player's eyes, and the furthest from them, by the rules where the game runs. */
	double maxHeight;
	double maxDistance;
	/** The shot's lyric: a point in the open area on the other third, and its size in blocks per text unit. */
	double anchorX;
	double anchorY;
	double anchorZ;
	float anchorUnit;
	boolean hasAnchor;
	/** Whether the lyric stands at the safe spot in the lower third, since no open spot was free. */
	boolean anchorFallback;
	/** The scout's score, and whether the shot passed its checks. */
	float score;
	boolean valid;

	void copy(Shot other) {
		type = other.type;
		angle = other.angle;
		radius = other.radius;
		height = other.height;
		arc = other.arc;
		move = other.move;
		pushFrom = other.pushFrom;
		pushBase = other.pushBase;
		pushScale = other.pushScale;
		turn = other.turn;
		side = other.side;
		lookYaw = other.lookYaw;
		maxHeight = other.maxHeight;
		maxDistance = other.maxDistance;
		anchorX = other.anchorX;
		anchorY = other.anchorY;
		anchorZ = other.anchorZ;
		anchorUnit = other.anchorUnit;
		hasAnchor = other.hasAnchor;
		anchorFallback = other.anchorFallback;
		score = other.score;
		valid = other.valid;
	}

	/** Keeps the camera within the distance rules for the whole shot, its moves included. */
	void limit() {
		height = Math.min(height, maxHeight);
		// the crane rises during the shot: the rules hold up to where it gets in the longest shot
		double top = type == ShotType.WIDE_CRANE ? Math.min(maxHeight, height + CineMotion.CRANE_RISE * move) : height;
		double room = Math.sqrt(Math.max(0.25, maxDistance * maxDistance - top * top));
		radius = Math.min(radius, room);
	}

	/** @return how far the push-in has come at a point of the move, shortened from where it was shortened */
	double pushed(double moved) {
		return moved <= pushFrom ? Math.min(moved, pushBase + Math.max(0.0, moved - pushFrom)) : pushBase + (moved - pushFrom) * pushScale;
	}

	/**
	 * Shortens the rest of the push-in: from a point of the move on, the camera pushes only a share as far as it would have. Where it is now stays
	 * the same.
	 */
	void shortenPush(double movedNow, double share) {
		pushBase = pushed(movedNow);
		pushFrom = movedNow;
		pushScale *= share;
	}

	/**
	 * Sets the pose of the shot at a moment.
	 *
	 * @param t      seconds into the shot
	 * @param aspect the screen's width by its height, for the thirds
	 */
	void pose(float t, double eyeX, double eyeY, double eyeZ, float aspect, CinePose out) {
		// how far the move has come: it starts and ends softly, then the camera holds
		double moved = CineMotion.progress(t) * move;
		double a = angle + turn * arc * moved;
		double r = radius;
		double h = height;
		float fov = FOV_NORMAL;
		double aimX = eyeX;
		double aimY = eyeY + BODY * 0.3;
		double aimZ = eyeZ;
		switch (type) {
			case WIDE_CRANE -> {
				h = Math.min(maxHeight, height + CineMotion.CRANE_RISE * moved);
				fov = FOV_WIDE - CineMotion.CRANE_ZOOM * (float) moved;
			}
			case OVER_SHOULDER -> {
				a = angle;
				r = Math.max(CLOSEST_PUSH, radius - CineMotion.SHOULDER_PUSH * moved);
				fov = FOV_SHOULDER;
				// where the player looks, so the frame shows what they see
				aimX = eyeX - Math.sin(lookYaw) * SHOULDER_AHEAD;
				aimZ = eyeZ + Math.cos(lookYaw) * SHOULDER_AHEAD;
				aimY = eyeY;
			}
			case LOW_HERO -> {
				fov = FOV_LOW;
				aimY = eyeY + HEAD;
			}
			case CLOSE_PUSH_IN -> {
				r = Math.max(CLOSEST_PUSH, radius - CineMotion.PUSH_IN * pushed(moved));
				fov = FOV_CLOSE;
				aimY = eyeY;
			}
			case TOP_DOWN -> {
				fov = FOV_SHOULDER;
				r = radius + CineMotion.TOP_DRIFT * moved;
				a = angle;
			}
			default -> {
			}
		}
		out.x = eyeX + Math.sin(a) * r;
		out.y = eyeY + h;
		out.z = eyeZ + Math.cos(a) * r;
		out.fov = fov;
		out.lookAt(aimX, aimY, aimZ);
		// the player on a third rather than in the middle, with some room above the head
		double halfWidth = Math.tan(Math.toRadians(fov) / 2.0) * aspect;
		out.yaw += (float) (side * Math.toDegrees(Math.atan(halfWidth / 3.0)));
		out.pitch -= fov * HEADROOM;
		out.pitch = Mth.clamp(out.pitch, MOST_UP, type == ShotType.WIDE_CRANE ? CRANE_MOST_DOWN : MOST_DOWN);
	}

	/**
	 * @return the yaw of the other third relative to the view's center, in degrees, towards which the lyric stands: the view is turned by a third
	 *         away from the player, so the other third lies as far again in the same direction
	 */
	float lyricYawOffset(float fov, float aspect) {
		double halfWidth = Math.tan(Math.toRadians(fov) / 2.0) * aspect;
		return (float) (side * Math.toDegrees(Math.atan(halfWidth / 3.0)));
	}
}
