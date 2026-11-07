package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.CommandSuggestions;

/** The game's open list of suggestions, to put it by the chat's input. */
@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
	@Accessor("suggestions")
	CommandSuggestions.SuggestionsList lumio$suggestions();
}
