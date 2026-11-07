package dev.tim1337.lumio.mixin.accessor;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What an advancement toast shows, to draw it in the mod's style. */
@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {
	@Accessor("advancement")
	AdvancementHolder lumio$advancement();

	@Accessor("iconItem")
	ItemStack lumio$icon();
}
