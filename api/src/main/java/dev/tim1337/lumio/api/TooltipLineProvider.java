package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.setting.AddonSetting;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Adds lines to the tooltips of items. What the game sends stays as it is: the lines go into one separate block, which the player can place, hide or
 * show only while Shift is held. It is asked a few times per second while an item is hovered, on the game's thread: it must not block, so it reads what
 * it has and fetches the rest in the background.
 */
public interface TooltipLineProvider {
	/** Adds the lines of a hovered item, or none. */
	void addLines(ItemStack stack, List<TooltipLine> lines);

	/** @return the settings of these lines, shown in the card of the item tooltips under "Added lines"; they are saved with the add-on's feature */
	default List<AddonSetting<?>> lineSettings() {
		return List.of();
	}
}
