package dev.tim1337.lumio.mixin.accessor;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.util.FormattedCharSequence;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The text of a system toast, to draw it in the mod's style. */
@Mixin(SystemToast.class)
public interface SystemToastAccessor {
	@Accessor("titleLines")
	List<FormattedCharSequence> lumio$titleLines();

	@Accessor("messageLines")
	List<FormattedCharSequence> lumio$messageLines();
}
