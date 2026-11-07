package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;

import java.util.List;

/** The item kinds a feature applies to: tools, weapons, blocks, food and the rest. */
final class ItemKinds {
	private final BooleanSetting tools = new BooleanSetting("for_tools", Component.literal("Tools"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting weapons = new BooleanSetting("for_weapons", Component.literal("Weapons"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting blocks = new BooleanSetting("for_blocks", Component.literal("Blocks"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting food = new BooleanSetting("for_food", Component.literal("Food and drinks"), true, BooleanSetting.Style.CHECKBOX);
	private final BooleanSetting other = new BooleanSetting("for_other", Component.literal("Everything else"), true, BooleanSetting.Style.CHECKBOX);
	private final MultiSelectSetting setting = new MultiSelectSetting("affects", Component.literal("Used for"), List.of(tools, weapons, blocks, food, other));

	/** @return the setting to add to the feature, in its own group */
	MultiSelectSetting setting() {
		return setting;
	}

	/** @return the item in the hand that an item rendered in the given context belongs to */
	static ItemStack stackInHand(ItemDisplayContext context) {
		// in first person the rendered item counts: while switching slots, the old item is still visible lowering
		ItemStack drawn = HeldItem.drawnStack();
		if (drawn != null && HeldItem.isFirstPerson(context)) {
			return drawn;
		}
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return ItemStack.EMPTY;
		}
		boolean right = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		boolean main = right == (player.getMainArm() == HumanoidArm.RIGHT);
		return player.getItemInHand(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
	}

	static ItemStack mainHandStack() {
		var player = Minecraft.getInstance().player;
		return player == null ? ItemStack.EMPTY : player.getItemInHand(InteractionHand.MAIN_HAND);
	}

	/** @return whether the item is a weapon: swords, spears and the like, plus ranged ones; bows, crossbows and tridents have no tool or weapon component to detect them by */
	static boolean isWeapon(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS) || stack.is(ItemTags.BOW_ENCHANTABLE) || stack.is(ItemTags.CROSSBOW_ENCHANTABLE)
			|| stack.is(ItemTags.TRIDENT_ENCHANTABLE) || stack.is(ItemTags.MACE_ENCHANTABLE)
			|| stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem || stack.getItem() instanceof MaceItem
			|| stack.has(DataComponents.WEAPON) && !stack.has(DataComponents.TOOL);
	}

	/** @return whether the item's kind is one of the selected kinds */
	boolean affects(ItemStack stack) {
		if (tools.get() && weapons.get() && blocks.get() && food.get() && other.get()) {
			return true;
		}
		if (stack.isEmpty()) {
			return other.get(); // empty hand
		}
		if (isWeapon(stack)) {
			return weapons.get();
		}
		if (stack.has(DataComponents.TOOL)) {
			return tools.get();
		}
		if (stack.getItem() instanceof BlockItem) {
			return blocks.get();
		}
		if (stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE)) {
			return food.get();
		}
		return other.get();
	}
}
