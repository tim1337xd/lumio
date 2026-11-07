package dev.tim1337.lumio.api;

import com.mojang.brigadier.suggestion.Suggestions;

import java.util.concurrent.CompletableFuture;

/**
 * Adds entries to the game's command suggestions while the player types a command. Suggestions are only shown, never sent: the player still
 * picks and sends the command.
 */
public interface SuggestionProvider {
	/**
	 * @param server the server's suggestions for the input
	 * @param input  the text in the chat field
	 * @param cursor where the cursor is in it
	 * @return the suggestions with the provider's entries merged in, or the server's unchanged
	 */
	CompletableFuture<Suggestions> add(CompletableFuture<Suggestions> server, String input, int cursor);

	/** @return whether the provider merged these suggestions; their order is then kept as it is */
	boolean merged(Suggestions suggestions);
}
