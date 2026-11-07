package dev.tim1337.lumio.features.visual.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;

/**
 * The game's numbers for the hand, for what the game does not draw itself: the menu's preview of a swing, the empty hand's size and the arm that holds an item. In the world an item's
 * swing is the game's own code, and a small item only travels a shorter way ({@link #movementShare}). Only how the hand is drawn.
 */
public final class SwingShape {
	/** The least of the game's movement a small item keeps, or the swing would hardly move it at all. */
	private static final float LEAST_MOVEMENT = 0.4f;
	// where the game holds the empty hand, for sizing it around itself
	private static final float HAND_ACROSS = 0.64f;
	private static final float HAND_UP = -0.6f;
	private static final float HAND_DEPTH = -0.72f;
	// the game's swing of the empty hand
	private static final float HAND_SWING_ACROSS = -0.3f;
	private static final float HAND_SWING_UP = 0.4f;
	private static final float HAND_SWING_DEPTH = -0.4f;
	private static final float HAND_SWING_TURN_Y = 70f;
	private static final float HAND_SWING_TURN_Z = -20f;
	// where the game holds an item
	private static final float ITEM_ACROSS = 0.56f;
	private static final float ITEM_UP = -0.52f;
	private static final float ITEM_DEPTH = -0.72f;
	// from there to the arm that holds it, so that the fist is around the item's handle
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

	/** Where the game holds an item, for the preview. */
	public static void holdItem(PoseStack pose, int side) {
		pose.translate(side * ITEM_ACROSS, ITEM_UP, ITEM_DEPTH);
	}

	/** The game's empty hand during a swing, for the preview. */
	public static void placeEmptyHand(PoseStack pose, int side, float progress) {
		float root = Mth.sqrt(progress);
		float late = Mth.sin(root * Mth.PI);
		pose.translate(side * (HAND_SWING_ACROSS * late + HAND_ACROSS), HAND_SWING_UP * Mth.sin(root * Mth.TWO_PI) + HAND_UP, HAND_SWING_DEPTH * Mth.sin(progress * Mth.PI) + HAND_DEPTH);
		pose.mulPose(Axis.YP.rotationDegrees(side * (ARM_TURN_Y + late * HAND_SWING_TURN_Y)));
		pose.mulPose(Axis.ZP.rotationDegrees(side * Mth.sin(progress * progress * Mth.PI) * HAND_SWING_TURN_Z));
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
