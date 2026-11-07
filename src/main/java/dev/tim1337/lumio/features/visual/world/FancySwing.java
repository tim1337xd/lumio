package dev.tim1337.lumio.features.visual.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * The fancy first person swing: the item draws back, goes through a wide arc and follows through before it settles. Only how the hand is drawn: the game's attacks and their timing stay its own, and the swing's length comes from the same speed setting as
 * the game's swing. Plain numbers, so it is unit tested.
 */
public final class FancySwing {
	/** Where in the swing each pose is reached: at rest, drawn back, through the arc, followed through, and at rest again. */
	private static final float[] TIMES = {0f, 0.2f, 0.5f, 0.7f, 1f};
	// for each pose: across, up and depth in blocks, then the turns around the up, the forward and the side axis in degrees
	private static final float[][] POSES = {
		{0f, 0f, 0f, 0f, 0f, 0f},
		{0.07f, 0.1f, 0.03f, 12f, 8f, 26f},
		{-0.32f, 0.02f, -0.22f, -18f, -18f, -70f},
		{-0.42f, -0.12f, -0.12f, -26f, -24f, -92f},
		{0f, 0f, 0f, 0f, 0f, 0f}
	};
	/** How many values a pose has. */
	public static final int VALUES = 6;
	public static final int ACROSS = 0;
	public static final int UP = 1;
	public static final int DEPTH = 2;
	public static final int TURN_UP = 3;
	public static final int TURN_FORWARD = 4;
	public static final int TURN_SIDE = 5;

	private FancySwing() {
	}

	/** @return one value of the swing at a progress from 0 to 1: it eases in and out of each pose, so that it never jerks */
	public static float value(int which, float progress) {
		float at = Math.clamp(progress, 0f, 1f);
		int segment = 0;
		while (segment < TIMES.length - 2 && at >= TIMES[segment + 1]) {
			segment++;
		}
		float share = (at - TIMES[segment]) / (TIMES[segment + 1] - TIMES[segment]);
		float eased = share * share * (3f - 2f * share);
		float from = POSES[segment][which];
		return from + (POSES[segment + 1][which] - from) * eased;
	}

	/** Moves the item as the swing does, before the game's arm transform, as the game's swing does. */
	public static void move(PoseStack pose, int side, float progress, float movement) {
		pose.translate(side * value(ACROSS, progress) * movement, value(UP, progress) * movement, value(DEPTH, progress) * movement);
	}

	/** Turns the item as the swing does, after the game's arm transform. */
	public static void turn(PoseStack pose, int side, float progress) {
		pose.mulPose(Axis.YP.rotationDegrees(side * value(TURN_UP, progress)));
		pose.mulPose(Axis.ZP.rotationDegrees(side * value(TURN_FORWARD, progress)));
		pose.mulPose(Axis.XP.rotationDegrees(value(TURN_SIDE, progress)));
	}
}
