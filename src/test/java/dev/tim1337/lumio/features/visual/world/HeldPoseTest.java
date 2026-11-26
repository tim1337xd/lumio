package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemDisplayContext;

/** Which bows get the pose of the game's bow in the hand: only those whose own model has exactly a flat item's pose. */
class HeldPoseTest {
	private static final String FLAT = "flat item";
	private static final String BOW = "bow";

	@Test
	void aBowWithAFlatItemsPoseGetsTheBowsPose() {
		assertTrue(HeldPose.replaces("flat item", FLAT, BOW), "the same pose as a flat item, by value");
	}

	@Test
	void aBowWithItsOwnPoseOrTheBowsPoseIsLeftAlone() {
		assertFalse(HeldPose.replaces("a pose of its own", FLAT, BOW));
		assertFalse(HeldPose.replaces("bow", FLAT, BOW));
		assertFalse(HeldPose.replaces(null, FLAT, BOW));
	}

	@Test
	void nothingChangesWhereAFlatItemAndABowAreHeldTheSameWay() {
		assertFalse(HeldPose.replaces("same", "same", "same"));
		assertFalse(HeldPose.replaces("flat item", FLAT, null), "nor without the game's bow to take the pose from");
	}

	@Test
	void onlyItemsInAHandAreLookedAt() {
		for (ItemDisplayContext context : ItemDisplayContext.values()) {
			boolean hand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
				|| context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
			assertTrue(HeldPose.inHand(context) == hand, context.name());
		}
	}
}
