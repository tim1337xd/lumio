package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;

/** Where the list of suggestions lies: it draws and takes clicks by this. */
@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionsListAccessor {
	@Accessor("rect")
	Rect2i lumio$rect();
}
