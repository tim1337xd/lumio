package dev.tim1337.lumio.mixin.accessor;

import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.util.FormattedCharSequence;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** What a tutorial toast shows, to draw it in the mod's style. */
@Mixin(TutorialToast.class)
public interface TutorialToastAccessor {
	@Accessor("icon")
	TutorialToast.Icons lumio$icon();

	@Accessor("lines")
	List<FormattedCharSequence> lumio$lines();

	@Accessor("progress")
	float lumio$progress();

	@Accessor("progressable")
	boolean lumio$progressable();
}
