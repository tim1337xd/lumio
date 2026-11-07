package dev.tim1337.lumio.features.visual.world;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Tracks which item the first person hand renders, and related render context. */
public final class HeldItem {
	/** The item the first person hand is currently rendering, which differs from the held item while switching slots; or null. */
	private static ItemStack drawnStack;

	/** Set while an entity is rendered into a screen, such as the player in the inventory. */
	private static boolean guiEntity;

	private HeldItem() {
	}

	public static void setGuiEntity(boolean value) {
		guiEntity = value;
	}

	/** @return whether the entity being submitted is rendered into a screen, with an orthographic projection rather than the world camera */
	public static boolean isGuiEntity() {
		return guiEntity;
	}

	public static void setDrawnStack(ItemStack stack) {
		drawnStack = stack;
	}

	public static ItemStack drawnStack() {
		return drawnStack;
	}

	public static boolean isFirstPerson(ItemDisplayContext context) {
		return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
	}
}
