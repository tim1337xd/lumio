package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Exposes the position of a container screen's slot area, for content drawn next to it. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int lumio$left();

	@Accessor("topPos")
	int lumio$top();

	@Accessor("imageWidth")
	int lumio$width();

	@Accessor("imageHeight")
	int lumio$height();

	@Accessor("hoveredSlot")
	Slot lumio$hoveredSlot();

	@Mutable
	@Accessor("imageHeight")
	void lumio$setHeight(int height);

	@Accessor("topPos")
	void lumio$setTop(int top);

	@Accessor("leftPos")
	void lumio$setLeft(int left);

	@Mutable
	@Accessor("imageWidth")
	void lumio$setWidth(int width);
}
