package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.render.OutlineItem;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * A bow that is held like a bow. A server's resource pack can give a bow a model of its own, and such a model can come without the pose of a
 * bow in the hand: it then has the pose of a flat item, and the bow lies in the hand like a sheet of paper, in first and third person, for
 * every player. (Hypixel's pack does that to all SkyBlock bows.) Where a bow's own model has exactly the flat item's pose in a hand, it gets
 * the pose of the game's bow there. A model with a pose of its own is left alone, and so is everything that is not a bow.
 */
public final class HeldPose {
	/** The game's bow and a flat item, resolved for the hand asked about: only their poses are read. */
	private static final ItemStackRenderState BOW = new ItemStackRenderState();
	private static final ItemStackRenderState FLAT = new ItemStackRenderState();
	private static ItemStack bow;
	private static ItemStack flat;
	/** While the two are resolved, which comes back here. */
	private static boolean resolving;

	private HeldPose() {
	}

	/** @return whether an item is drawn in a hand, in first or third person */
	public static boolean inHand(ItemDisplayContext context) {
		return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
			|| context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
	}

	/**
	 * @param own  the pose a bow's own model has in the hand
	 * @param flat the pose of a flat item there
	 * @param bow  the pose of the game's bow there
	 * @return whether the bow gets the game's pose: its model has the flat item's, which is not a bow's
	 */
	public static boolean replaces(Object own, Object flat, Object bow) {
		return own != null && bow != null && own.equals(flat) && !own.equals(bow);
	}

	/** Called when the game has worked out how an item is drawn: gives a bow with a flat item's pose in the hand the pose of the game's bow. */
	public static void apply(ItemModelResolver resolver, ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level) {
		if (resolving || !inHand(context) || !stack.is(Items.BOW) || ModFeatures.HELD_ITEM_TRANSFORM == null || !ModFeatures.HELD_ITEM_TRANSFORM.holdsBowsAsBows()) {
			return;
		}
		// only a bow with a model of its own: the game's bow is the game's
		if (Objects.equals(stack.get(DataComponents.ITEM_MODEL), Items.BOW.components().get(DataComponents.ITEM_MODEL))) {
			return;
		}
		OutlineItem item = (OutlineItem) state;
		if (item.lumio$layerCount() == 0) {
			return;
		}
		if (bow == null) {
			bow = new ItemStack(Items.BOW);
			flat = new ItemStack(Items.PAPER);
		}
		resolving = true;
		try {
			resolver.updateForTopItem(BOW, bow, context, level, null, 0);
			resolver.updateForTopItem(FLAT, flat, context, level, null, 0);
		} finally {
			resolving = false;
		}
		ItemTransform bowPose = pose(BOW);
		ItemTransform flatPose = pose(FLAT);
		for (int i = 0; i < item.lumio$layerCount(); i++) {
			if (replaces(item.lumio$layer(i).lumio$itemTransform(), flatPose, bowPose)) {
				item.lumio$layer(i).lumio$itemTransform(bowPose);
			}
		}
	}

	private static ItemTransform pose(ItemStackRenderState state) {
		OutlineItem item = (OutlineItem) state;
		return item.lumio$layerCount() == 0 ? null : item.lumio$layer(0).lumio$itemTransform();
	}
}
