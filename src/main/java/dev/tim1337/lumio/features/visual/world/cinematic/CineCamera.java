package dev.tim1337.lumio.features.visual.world.cinematic;

import dev.tim1337.lumio.features.music.LyricCutFade;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.api.experimental.motion.Easing;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.features.music.LyricTimes;
import dev.tim1337.lumio.features.music.MusicService;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The camera of the film, frame by frame: it starts in the first shot, fading in from black, runs the shots with their slow moves, cuts or glides
 * when the director says so, slows to a stop while the song is paused, and when the film stops the view is back in the player's eyes at once. It
 * never looks up into the sky. Every move runs on real elapsed time with a clamped step, and nothing is created per frame.
 */
final class CineCamera {
	enum Phase {
		OFF, RUNNING
	}

	/** The film fades in from black over this time when it starts. */
	private static final float FADE_IN_SECONDS = 0.4f;
	/** When the view of the player is lost for this long, the camera waits for a cut coming this soon, or glides this quickly elsewhere. */
	private static final float LOST_AFTER = 0.25f;
	private static final float LOST_WAIT = 1.5f;
	private static final float LOST_GLIDE = 0.7f;
	/** Shot candidates tested per frame while planning the next shot, and the block tests that takes. */
	private static final int PLAN_CANDIDATES = 2;
	private static final int PLAN_BUDGET = PLAN_CANDIDATES * CineScout.COST;
	/** A plan is made again this often, as the player and the world change; the first a moment after a cut. */
	private static final float PLAN_AGAIN = 4f;
	private static final float PLAN_FIRST = 0.5f;
	/** How quickly the camera's center follows the player, per second. */
	private static final float FOLLOW_RATE = 6f;
	private static final double SNAP_DISTANCE_SQUARED = 64.0;
	/** The view is checked every few frames. */
	private static final int VIEW_EVERY = 3;
	/** A walking player is one faster than this, in blocks per tick. */
	private static final double WALKING = 0.05;
	/** Handheld sway, in degrees. */
	private static final float SWAY = 0.18f;

	final CinePose out = new CinePose();
	private final CinePose from = new CinePose();
	private final CinePose eyes = new CinePose();
	private final CinePose shotPose = new CinePose();
	final Shot shot = new Shot();
	private final Shot next = new Shot();
	final CineScout scout = new CineScout();
	final CineDirector director;
	private final CinematicCameraFeature feature;
	private final AnimClock clock = new AnimClock();
	private final Spring timeScale = Spring.smooth(Spring.GENTLE);
	private final Spring fov = Spring.smooth(Spring.GENTLE);
	Phase phase = Phase.OFF;
	private float phaseTime;
	/** Motion time of the shot, slowed while paused, and the real time it has run. */
	private float shotTime;
	float shotAge;
	/** When the last hard cut was, by the clock; 0 before the first. */
	private long cutAt;
	private float planAge;
	private boolean gliding;
	private float glideTime;
	private float glideLength;
	private double focusX;
	private double focusY;
	private double focusZ;
	private boolean focused;
	private float lost;
	private int frame;
	/** The time step of this frame, in seconds. */
	private float step;
	private int pathSide = 1;
	private boolean wasWalking;
	/** The game's own field of view this frame. */
	float gameFov = 70f;
	/** Player requests, taken at the next allowed moment. */
	boolean nextRequested;
	boolean holding;
	/** The lyric of the shot, relative to the player's eyes, and the one of a shot glided to, taken when the next line starts. */
	private double anchorX;
	private double anchorY;
	private double anchorZ;
	private float anchorUnit;
	private boolean anchored;
	private boolean anchorPending;
	private int anchorLine = Integer.MIN_VALUE;
	/** How many shots took their lyric spot, for the log. */
	private int shots;

	int shots() {
		return shots;
	}

	CineCamera(CinematicCameraFeature feature) {
		this.feature = feature;
		this.director = new CineDirector(feature);
	}

	boolean running() {
		return phase != Phase.OFF;
	}

	/** Starts the film: plans the first shot at once and starts in it, fading in from black. */
	void start(Minecraft client, float partial) {
		phase = Phase.RUNNING;
		phaseTime = 0f;
		focused = false;
		gliding = false;
		nextRequested = false;
		holding = false;
		lost = 0f;
		clock.reset();
		director.reset();
		follow(client.player, partial, 0f);
		readEyes(client.player, partial);
		out.set(eyes);
		timeScale.snap(1f);
		if (plan(client, true) && scout.take(client.level, focusX, focusY, focusZ, shot)) {
			startShot(true);
		} else {
			// nowhere to film from: a crane above the player, the safest place there is
			shot.type = ShotType.WIDE_CRANE;
			shot.angle = 0.0;
			shot.radius = 2.0;
			shot.height = 2.0;
			shot.maxHeight = feature.maxHeight();
			shot.maxDistance = feature.maxDistance();
			scout.anchor(client.level, shot, focusX, focusY, focusZ);
			startShot(true);
		}
		fov.snap(poseFov(client));
	}

	/** Ends the film: the view is back in the player's eyes at once. */
	void stop() {
		phase = Phase.OFF;
	}

	/** @return how visible the lyrics and the song card are now, around a cut */
	float lyricFade() {
		return LyricCutFade.alpha(director.untilCut(shotAge, holding, nextRequested), sinceCutMs());
	}

	/** @return how long ago the last cut was, or {@link LyricCutFade#NONE} */
	long sinceCutMs() {
		return cutAt == 0L ? LyricCutFade.NONE : System.currentTimeMillis() - cutAt;
	}

	/** Takes the shot just found as the current one. @param cut whether it is a hard cut, after which the lyric starts afresh there */
	private void startShot(boolean cut) {
		shotTime = 0f;
		shotAge = 0f;
		planAge = 0f;
		lost = 0f;
		if (cut) {
			adoptAnchor();
			cutAt = System.currentTimeMillis();
			ModFeatures.MUSIC.freshLyrics();
		} else {
			anchorPending = true;
		}
	}

	/** The camera's turning in the shot so far, for the log: its yaw at the last frame, the fastest turn and the yaw range. */
	private float turnYaw = Float.NaN;
	private float turnFirst;
	private float turnMost;
	private float turnLeast;
	private float turnHighest;

	/** Notes how fast and how far the camera turns in the shot, leaving out the glides between shots. */
	private void measureTurn(float dt) {
		if (gliding || dt <= 0f) {
			return;
		}
		if (Float.isNaN(turnYaw)) {
			turnYaw = out.yaw;
			turnFirst = out.yaw;
			return;
		}
		turnMost = Math.max(turnMost, Math.abs(Mth.wrapDegrees(out.yaw - turnYaw)) / dt);
		float turned = Mth.wrapDegrees(out.yaw - turnFirst);
		turnLeast = Math.min(turnLeast, turned);
		turnHighest = Math.max(turnHighest, turned);
		turnYaw = out.yaw;
	}

	private void adoptAnchor() {
		if (Debug.on() && !Float.isNaN(turnYaw)) {
			Debug.log("Cinematic shot {} motion: fastest turn {} degrees a second, whole turn {} degrees", shots, Math.round(turnMost * 100f) / 100f,
				Math.round((turnHighest - turnLeast) * 100f) / 100f);
		}
		turnYaw = Float.NaN;
		turnMost = 0f;
		turnLeast = 0f;
		turnHighest = 0f;
		anchored = shot.hasAnchor;
		shots++;
		if (Debug.on()) {
			Debug.log("Cinematic shot {}: {}, lyric {} at {} {} {} from the player", shots, shot.type, shot.anchorFallback ? "at the safe spot" : "in the open",
				Math.round(shot.anchorX - focusX), Math.round(shot.anchorY - focusY), Math.round(shot.anchorZ - focusZ));
		}
		anchorPending = false;
		anchorX = shot.anchorX - focusX;
		anchorY = shot.anchorY - focusY;
		anchorZ = shot.anchorZ - focusZ;
		anchorUnit = shot.anchorUnit;
		anchorLine = LyricTimes.mainIndexAt(MusicService.lyrics(), ModFeatures.MUSIC.lyricClockMs());
	}

	/** @return the pose for this frame; false once the film is over */
	boolean frame(Minecraft client, float partial) {
		Player player = client.player;
		float dt = clock.tick();
		frame++;
		step = dt;
		readEyes(player, partial);
		follow(player, partial, dt);
		phaseTime += dt;
		float scale = timeScale.step(director.paused ? 0f : 1f, dt);
		shotTime += dt * scale;
		shotAge += dt;
		planAge += dt;
		direct(client);
		if (!scout.planning() && planAge >= PLAN_FIRST || scout.done() && planAge >= PLAN_AGAIN) {
			plan(client, false);
			planAge = PLAN_FIRST;
		}
		scout.step(client.level, PLAN_BUDGET);
		if (anchorPending && LyricTimes.mainIndexAt(MusicService.lyrics(), ModFeatures.MUSIC.lyricClockMs()) != anchorLine) {
			adoptAnchor();
		}

		shot.pose(shotTime, focusX, focusY, focusZ, aspect(client), shotPose);
		if (feature.handheld()) {
			float time = shotAge;
			shotPose.yaw += SWAY * (float) (Math.sin(time * 0.61) + 0.4 * Math.sin(time * 1.73 + 1.1));
			shotPose.pitch += SWAY * 0.7f * (float) (Math.sin(time * 0.77 + 0.6) + 0.4 * Math.sin(time * 2.1));
		}
		// the sway never tips the view into the sky
		shotPose.pitch = Math.max(Shot.MOST_UP, shotPose.pitch);
		if (gliding) {
			glideTime += dt;
			out.blend(from, shotPose, Easing.smooth(glideTime / glideLength));
			gliding = glideTime < glideLength;
		} else {
			out.set(shotPose);
		}
		out.fov = fov.step(out.fov, dt);
		if (Debug.on()) {
			measureTurn(dt);
		}
		if (frame % VIEW_EVERY == 0) {
			keepView(client);
		}
		watchPlayer(client, dt);
		return true;
	}

	/** A shot the player has left (behind a block that appeared, out of the picture as they walked) ends early: a film of no one is no film. */
	private void watchPlayer(Minecraft client, float dt) {
		if (gliding || client.level == null) {
			playerGone = 0f;
			return;
		}
		watchEvery += dt;
		if (watchEvery < WATCH_SECONDS) {
			return;
		}
		boolean seen = LyricFrame.playerInFrame(out, aspect(client), focusX, focusY, focusZ) && LyricFrame.playerSeen(client.level, out, focusX, focusY, focusZ);
		playerGone = seen ? 0f : playerGone + watchEvery;
		watchEvery = 0f;
		if (playerGone >= GONE_SECONDS) {
			playerGone = 0f;
			nextRequested = true;
		}
	}

	/** How often a running shot looks for the player, and how long they may be gone before it ends. */
	private static final float WATCH_SECONDS = 0.2f;
	private static final float GONE_SECONDS = 0.6f;
	private float watchEvery;
	private float playerGone;

	/** Asks the director what to do and does it: a hard cut to the planned shot, or a glide into it. */
	private void direct(Minecraft client) {
		CineDirector.Action action = director.update(shotAge, holding, nextRequested);
		if (action == CineDirector.Action.NONE) {
			return;
		}
		nextRequested = false;
		if (!takeNext(client)) {
			return;
		}
		if (action == CineDirector.Action.CUT) {
			gliding = false;
			shot.copy(next);
			startShot(true);
			// a cut changes the lens at once; within a shot and in glides it changes smoothly
			fov.snap(poseFov(client));
		} else {
			glide(director.glideSeconds);
		}
	}

	/** @return the field of view of the current shot at its start */
	private float poseFov(Minecraft client) {
		shot.pose(0f, focusX, focusY, focusZ, aspect(client), shotPose);
		return shotPose.fov;
	}

	private void glide(float seconds) {
		from.set(out);
		shot.copy(next);
		startShot(false);
		gliding = true;
		glideTime = 0f;
		glideLength = seconds;
	}

	/** @return whether the next shot was found: the plan is finished at once if it is not yet, and the best still valid shot is taken */
	private boolean takeNext(Minecraft client) {
		if (!scout.planning()) {
			plan(client, true);
		}
		if (!scout.done()) {
			scout.step(client.level, Integer.MAX_VALUE);
		}
		boolean found = scout.take(client.level, focusX, focusY, focusZ, next);
		planAge = 0f;
		return found;
	}

	/**
	 * If the player can no longer be seen (they walked behind something), the camera waits for a cut coming soon, or glides quickly to another
	 * spot. It never cuts in the middle of a line.
	 */
	private void keepView(Minecraft client) {
		boolean seen = CineRays.clear(client.level, out.x, out.y, out.z, focusX, focusY + Shot.HEAD, focusZ)
			|| CineRays.clear(client.level, out.x, out.y, out.z, focusX, focusY + Shot.BODY, focusZ);
		lost = seen ? 0f : lost + VIEW_EVERY * step;
		if (lost < LOST_AFTER || gliding || director.secondsToNextCut() <= LOST_WAIT) {
			return;
		}
		if (takeNext(client)) {
			glide(LOST_GLIDE);
		}
	}

	/** Starts a plan for the next shot from where the player is now. @param now whether it is needed at once (the first shot, a cut) */
	private boolean plan(Minecraft client, boolean now) {
		Player player = client.player;
		CineScout.Brief brief = scout.brief();
		brief.eyeX = focusX;
		brief.eyeY = focusY;
		brief.eyeZ = focusZ;
		brief.lookYaw = Math.toRadians(player.getYRot());
		double vx = player.getX() - player.xo;
		double vz = player.getZ() - player.zo;
		boolean walking = feature.allowsWalking() && Math.sqrt(vx * vx + vz * vz) > WALKING;
		if (walking && !wasWalking) {
			// the film keeps to one side of the path while the player walks, like film's 180 degree rule
			pathSide = shot.angle >= 0 ? 1 : -1;
		}
		wasWalking = walking;
		brief.walking = walking;
		brief.walkYaw = Math.atan2(-vx, vz);
		brief.pathSide = pathSide;
		brief.inFluid = player.isInWater() || player.isInLava();
		brief.day = feature.daylight(client);
		brief.aspect = aspect(client);
		brief.maxHeight = feature.maxHeight();
		brief.maxDistance = feature.maxDistance();
		feature.allowedShots(brief.allowed);
		brief.lastType = phase == Phase.OFF ? null : shot.type;
		brief.lastAngle = phase == Phase.OFF ? Double.NaN : shot.angle;
		brief.cardStays = feature.titleCard() && feature.songInfoAlways();
		director.brief(brief);
		scout.begin();
		if (now) {
			scout.step(client.level, Integer.MAX_VALUE);
		}
		return scout.done();
	}

	private void readEyes(Player player, float partial) {
		eyes.x = Mth.lerp(partial, player.xo, player.getX());
		eyes.y = Mth.lerp(partial, player.yo, player.getY()) + player.getEyeHeight();
		eyes.z = Mth.lerp(partial, player.zo, player.getZ());
		eyes.yaw = player.getViewYRot(partial);
		eyes.pitch = player.getViewXRot(partial);
		eyes.fov = gameFov;
	}

	/** The camera's center follows the player's eyes calmly, or jumps when they are far away (a teleport). */
	private void follow(Player player, float partial, float dt) {
		double x = Mth.lerp(partial, player.xo, player.getX());
		double y = Mth.lerp(partial, player.yo, player.getY()) + player.getEyeHeight();
		double z = Mth.lerp(partial, player.zo, player.getZ());
		double away = (x - focusX) * (x - focusX) + (y - focusY) * (y - focusY) + (z - focusZ) * (z - focusZ);
		if (!focused || away > SNAP_DISTANCE_SQUARED) {
			focusX = x;
			focusY = y;
			focusZ = z;
			focused = true;
			return;
		}
		double t = 1.0 - Math.exp(-dt * FOLLOW_RATE);
		focusX += (x - focusX) * t;
		focusY += (y - focusY) * t;
		focusZ += (z - focusZ) * t;
	}

	/** @return whether the shot's lyric is placed; its place is read with {@link #anchorX} and the others */
	boolean anchored() {
		return anchored && phase != Phase.OFF;
	}

	double anchorX() {
		return focusX + anchorX;
	}

	double anchorY() {
		return focusY + anchorY;
	}

	double anchorZ() {
		return focusZ + anchorZ;
	}

	float anchorUnit() {
		return anchorUnit;
	}

	/** @return how dark the picture is as the film fades in from black at its start, from 1 to 0 */
	float blackness() {
		return phase == Phase.OFF ? 0f : 1f - Easing.smooth(phaseTime / FADE_IN_SECONDS);
	}

	// ------------------------------------------------------------------------------------------------------ lyrics in the frame

	/** Poses along the shot's planned path at which a lyric line is checked, from now to the end of its time. */
	private static final int LYRIC_SAMPLES = 5;
	/** The character's box around the eyes, in blocks: half its width, how far above the eyes and below them it reaches. */
	private static final double BODY_HALF = 0.35;
	private static final double BODY_ABOVE = 0.3;
	private static final double BODY_BELOW = 1.55;
	/** The share of a lyric box's half size where its text's corners are, inside its glow: the rays for blocks aim there. */
	private static final double TEXT_SHARE = 0.8;
	private final LyricFrame lyricFrame = new LyricFrame();
	private final CinePose samplePose = new CinePose();
	private final float[] projected = new float[2];
	/** Why the last lyric spot measured failed, for the debug log: at a sample pose it left the safe part, was behind the camera or on the song card,
	 * covered the character, or a block hid it; empty when it passed. */
	String lyricMiss = "";

	/**
	 * Measures a lyric line's box along the shot's planned path, from now over the time it shows (see {@link LyricFrame}).
	 *
	 * @return its least margin to the safe part of the picture over that time, as a share of it: negative by as much as it reaches past the safe
	 *         part, or {@link LyricFrame#OUTSIDE} when it lands on the song card or the character, or a block hides it, at any of the poses
	 */
	float lyricMargin(Level level, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight, float seconds,
					  float aspect, float picture) {
		return lyricMargin(level, shot, x, y, z, rightX, rightZ, halfWidth, halfHeight, seconds, aspect, picture);
	}

	/** Like {@link #lyricMargin}, along the path of a shot that may differ from the one running (a shorter push-in being tried). */
	private float lyricMargin(Level level, Shot path, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight,
							  float seconds, float aspect, float picture) {
		lyricMiss = "";
		float least = Float.MAX_VALUE;
		for (int k = 0; k < LYRIC_SAMPLES; k++) {
			float t = shotTime + seconds * k / (LYRIC_SAMPLES - 1f);
			path.pose(t, focusX, focusY, focusZ, aspect, samplePose);
			samplePose.pitch = Math.max(Shot.MOST_UP, samplePose.pitch);
			float margin = lyricFrame.margin(samplePose, aspect, picture, x, y, z, rightX, rightZ, halfWidth, halfHeight, projected);
			String miss = "";
			if (margin <= LyricFrame.OUTSIDE) {
				miss = "behind the camera or on the song card";
			} else if (onCharacter(aspect, picture)) {
				miss = "on the character";
			} else if (!seen(level, x, y, z, rightX, rightZ, halfWidth, halfHeight)) {
				miss = "behind blocks";
			}
			if (!miss.isEmpty()) {
				lyricMiss = miss + " at " + Math.round(t * 10f) / 10f + " s";
				return LyricFrame.OUTSIDE;
			}
			if (margin < 0f && lyricMiss.isEmpty()) {
				// only past the edge: measured on, so spots that just miss can be told apart by how far they do
				lyricMiss = "past the edge by " + Math.round(-margin * 100f) / 100f + " at " + Math.round(t * 10f) / 10f + " s";
			}
			least = Math.min(least, margin);
		}
		return least;
	}

	/**
	 * @return whether the camera at the sample pose sees the box's middle and its text's four corners past every block: one ray to the middle alone
	 *         passes between the leaves of a tree that hides half the line
	 */
	private boolean seen(Level level, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight) {
		if (!CineRays.clear(level, samplePose.x, samplePose.y, samplePose.z, x, y, z)) {
			return false;
		}
		for (int corner = 0; corner < 4; corner++) {
			double side = ((corner & 1) == 0 ? -halfWidth : halfWidth) * TEXT_SHARE;
			double up = ((corner & 2) == 0 ? -halfHeight : halfHeight) * TEXT_SHARE;
			if (!CineRays.clear(level, samplePose.x, samplePose.y, samplePose.z, x + rightX * side, y + up, z + rightZ * side)) {
				return false;
			}
		}
		return true;
	}

	/** @return whether the box measured last covers the character at the sample pose */
	private boolean onCharacter(float aspect, float picture) {
		return characterBox(samplePose, aspect, picture, bodyBox) && lyricFrame.overlaps(bodyBox[0], bodyBox[1], bodyBox[2], bodyBox[3]);
	}

	/**
	 * Projects the character's box through a camera pose.
	 *
	 * @param box its bounds in picture units: left, bottom, right, top
	 * @return false when none of it is in front of the camera
	 */
	private boolean characterBox(CinePose pose, float aspect, float picture, float[] box) {
		box[0] = Float.MAX_VALUE;
		box[1] = Float.MAX_VALUE;
		box[2] = -Float.MAX_VALUE;
		box[3] = -Float.MAX_VALUE;
		for (int corner = 0; corner < 8; corner++) {
			double cx = focusX + ((corner & 1) == 0 ? -BODY_HALF : BODY_HALF);
			double cy = focusY + ((corner & 2) == 0 ? -BODY_BELOW : BODY_ABOVE);
			double cz = focusZ + ((corner & 4) == 0 ? -BODY_HALF : BODY_HALF);
			if (!LyricFrame.project(pose, aspect, picture, cx, cy, cz, projected)) {
				continue;
			}
			box[0] = Math.min(box[0], projected[0]);
			box[2] = Math.max(box[2], projected[0]);
			box[1] = Math.min(box[1], projected[1]);
			box[3] = Math.max(box[3], projected[1]);
		}
		return box[0] < box[2];
	}

	// ------------------------------------------------------------------------------------------------------ the lines on screen

	private final float[] bodyBox = new float[4];

	/**
	 * Projects a line standing in the world through the camera's pose now.
	 *
	 * @param box its bounds in picture units: left, bottom, right, top
	 * @return false when part of it is behind the camera
	 */
	boolean boxNow(double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight, float aspect, float picture,
				   float[] box) {
		return boxAt(out, x, y, z, rightX, rightZ, halfWidth, halfHeight, aspect, picture, box);
	}

	/** Like {@link #boxNow}, through a given pose of the camera. */
	private boolean boxAt(CinePose pose, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight, float aspect,
						  float picture, float[] box) {
		box[0] = Float.MAX_VALUE;
		box[1] = Float.MAX_VALUE;
		box[2] = -Float.MAX_VALUE;
		box[3] = -Float.MAX_VALUE;
		for (int corner = 0; corner < 4; corner++) {
			double side = (corner & 1) == 0 ? -halfWidth : halfWidth;
			double up = (corner & 2) == 0 ? -halfHeight : halfHeight;
			if (!LyricFrame.project(pose, aspect, picture, x + rightX * side, y + up, z + rightZ * side, projected)) {
				return false;
			}
			box[0] = Math.min(box[0], projected[0]);
			box[2] = Math.max(box[2], projected[0]);
			box[1] = Math.min(box[1], projected[1]);
			box[3] = Math.max(box[3], projected[1]);
		}
		return true;
	}

	/**
	 * @return whether two lines standing in the world stay apart on the screen along the shot's planned path from now over some seconds, by a gap
	 *         of a share of the first one's height; a pose where one of them is behind the camera does not count
	 */
	boolean linesApart(double ax, double ay, double az, double aRightX, double aRightZ, double aHalfWidth, double aHalfHeight, double bx, double by,
					   double bz, double bRightX, double bRightZ, double bHalfWidth, double bHalfHeight, float seconds, float gapShare, float aspect,
					   float picture) {
		for (int k = 0; k < LYRIC_SAMPLES; k++) {
			float t = shotTime + seconds * k / (LYRIC_SAMPLES - 1f);
			shot.pose(t, focusX, focusY, focusZ, aspect, samplePose);
			samplePose.pitch = Math.max(Shot.MOST_UP, samplePose.pitch);
			if (!boxAt(samplePose, ax, ay, az, aRightX, aRightZ, aHalfWidth, aHalfHeight, aspect, picture, boxA)
				|| !boxAt(samplePose, bx, by, bz, bRightX, bRightZ, bHalfWidth, bHalfHeight, aspect, picture, boxB)) {
				continue;
			}
			float gap = gapShare * (boxA[3] - boxA[1]);
			if (boxA[0] < boxB[2] + gap && boxA[2] > boxB[0] - gap && boxA[1] < boxB[3] + gap && boxA[3] > boxB[1] - gap) {
				return false;
			}
		}
		return true;
	}

	private final float[] boxA = new float[4];
	private final float[] boxB = new float[4];

	/**
	 * @return how much deeper than the player a line must stand (as a share of the player's distance) for half its width to take only a share of
	 *         the picture's half width now; at least 1
	 */
	double fitDepthFactor(double halfWidth, double share, float aspect) {
		double depth = Math.max(1.0, LyricFrame.depth(out, focusX, focusY, focusZ));
		double tan = Math.tan(Math.toRadians(out.fov) / 2.0);
		return Math.max(1.0, halfWidth / (share * tan * aspect) / depth);
	}

	/**
	 * Finds the world spot behind a place in the picture now, as far from the camera as the focus (times a factor): the opposite of
	 * {@link LyricFrame#project}. A line placed there starts inside the picture, also when the camera looks down at the player.
	 *
	 * @param across its place across the picture, -1 (left) to 1 (right)
	 * @param up     its place up the picture, -1 (bottom) to 1 (top)
	 * @param spot   receives x, y, z and the camera's level right direction (x, z)
	 */
	void pictureSpot(float across, float up, double depthFactor, float aspect, float picture, double[] spot) {
		LyricFrame.unproject(out, aspect, picture, across, up, Math.max(1.0, LyricFrame.depth(out, focusX, focusY, focusZ)) * depthFactor, spot);
	}

	/** Finds spots for lines in a push-in, and shortens the push-in when no spot keeps a line in the picture. */
	private final PushInSpots pushInSpots = new PushInSpots();

	/** @return whether the shot running is a push-in that still has some way to go */
	boolean pushingIn() {
		return shot.type == ShotType.CLOSE_PUSH_IN && shot.move > CineMotion.STILL_MOVE && CineMotion.progress(shotTime) < 1.0;
	}

	/**
	 * Finds a spot for a line during a push-in that keeps it fully in the picture for as long as it shows (see {@link PushInSpots}); when it takes a
	 * shorter push-in, the shot is shortened from now on.
	 *
	 * @param out receives x, y, z (the line's middle), its right direction (x, z), its size (1 its own) and the share of the push-in kept
	 */
	boolean pushInSpot(Level level, double halfWidth, double halfHeight, float seconds, float aspect, float picture, double[] out) {
		boolean found = pushInSpots.find(shot, shotTime, seconds, focusX, focusY, focusZ, aspect, picture, halfWidth, halfHeight,
			(path, x, y, z, rightX, rightZ, width, height) -> lyricMargin(level, path, x, y, z, rightX, rightZ, width, height, seconds, aspect, picture),
			out);
		if (found && out[6] < 1.0) {
			shot.shortenPush(CineMotion.progress(shotTime) * shot.move, out[6]);
		}
		return found;
	}

	/** @return the point the camera looks at during the shot: the player, at their feet */
	double focusX() {
		return focusX;
	}

	double focusY() {
		return focusY;
	}

	double focusZ() {
		return focusZ;
	}

	static float aspect(Minecraft client) {
		var window = client.getWindow();
		return window.getWidth() / (float) Math.max(1, window.getHeight());
	}
}
