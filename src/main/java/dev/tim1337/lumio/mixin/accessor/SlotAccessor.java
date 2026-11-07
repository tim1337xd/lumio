package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.inventory.Slot;

/** Makes a slot's screen position writable, so the styled inventory can move slots to space out its sections. */
@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("y")
	void lumio$setY(int y);

	@Mutable
	@Accessor("x")
	void lumio$setX(int x);
}
