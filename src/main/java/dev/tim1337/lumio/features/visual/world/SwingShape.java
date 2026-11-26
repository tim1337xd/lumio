package dev.tim1337.lumio.features.visual.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * The game's numbers for the hand, for what the game does not draw itself: the arm that holds an item. In the world an item's
 * swing is the game's own code, and a small item only travels a shorter way ({@link #movementShare}). Only how the hand is drawn.
 */
public final class SwingShape {
	/** The least of the game's movement a small item keeps, or the swing would hardly move it at all. */
	private static final float LEAST_MOVEMENT = 0.4f;
	// from where the game holds an item to the arm that holds it, so that the fist is around the item's handle
	private static final float ITEM_TO_ARM_ACROSS = 0.15f;
	private static final float ITEM_TO_ARM_UP = -0.17f;
	private static final float ITEM_TO_ARM_DEPTH = 0.06f;
	// the game's turns of the arm, and its place in the player model
	private static final float ARM_TURN_Y = 45f;
	private static final float ARM_MODEL_ACROSS = -1f;
	private static final float ARM_MODEL_UP = 3.6f;
	private static final float ARM_MODEL_DEPTH = 3.5f;
	private static final float ARM_TURN_Z = 120f;
	private static final float ARM_TURN_X = 200f;
	private static final float ARM_TURN_Y_LAST = -135f;
	private static final float ARM_MODEL_LAST = 5.6f;

	private SwingShape() {
	}

	/** @return how much of the game's movement an item of a size makes, for its swing and its drop when changed: all of it from the game's size up */
	public static float movementShare(float size) {
		return size >= 1f ? 1f : Math.max(size, LEAST_MOVEMENT);
	}

	/** Places the arm that holds an item, from the item's place in the hand: the same turns as the game's empty hand. */
	public static void placeArmAtItem(PoseStack pose, int side) {
		pose.translate(side * ITEM_TO_ARM_ACROSS, ITEM_TO_ARM_UP, ITEM_TO_ARM_DEPTH);
		pose.mulPose(Axis.YP.rotationDegrees(side * ARM_TURN_Y));
		placeArmModel(pose, side);
	}

	private static void placeArmModel(PoseStack pose, int side) {
		pose.translate(side * ARM_MODEL_ACROSS, ARM_MODEL_UP, ARM_MODEL_DEPTH);
		pose.mulPose(Axis.ZP.rotationDegrees(side * ARM_TURN_Z));
		pose.mulPose(Axis.XP.rotationDegrees(ARM_TURN_X));
		pose.mulPose(Axis.YP.rotationDegrees(side * ARM_TURN_Y_LAST));
		pose.translate(side * ARM_MODEL_LAST, 0f, 0f);
	}
}
