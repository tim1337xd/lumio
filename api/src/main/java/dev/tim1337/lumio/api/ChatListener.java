package dev.tim1337.lumio.api;

import net.minecraft.network.chat.Component;

/**
 * Reads what the server sends: chat messages and the action bar. Called on the game's thread, once per message; it must not throw and never
 * changes the message. Text other players can type arrives here too, so a listener checks who wrote a message before it reacts.
 */
public interface ChatListener {
	/** A chat message from the server (not the action bar). */
	default void onServerMessage(Component message) {
	}

	/** A line added to the chat, also the ones the game adds itself. */
	default void onChatLine(Component message) {
	}

	/** A text the server shows in the action bar. */
	default void onActionBar(Component message) {
	}
}
