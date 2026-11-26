package dev.tim1337.lumio.features.visual.screen.chat;

import dev.tim1337.lumio.api.LumioChat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lines of Lumio's own that end in a word to click, such as "Undo": the click is handled here, on this client, and never becomes a command or a
 * packet. Only the last few lines keep their action.
 */
public final class ChatActions {
	private static final int KEPT = 16;
	private static final Deque<Action> RECENT = new ArrayDeque<>();

	/** A line, the word at its end that can be clicked, and what the click does. */
	public record Action(String text, String word, Runnable run) {
	}

	private ChatActions() {
	}

	/** Posts a Lumio line ("[Lumio] ...") that ends in a word to click, underlined in the accent. */
	public static void post(Component message, String word, int accentRgb, Runnable action) {
		MutableComponent body = Component.empty().append(message).append(" ")
			.append(Component.literal(word).withStyle(style -> style.withColor(accentRgb).withUnderlined(true)));
		// kept by the text the chat will show, prefix and all
		String shown = LumioChat.prefixed(body).getString();
		synchronized (RECENT) {
			if (RECENT.size() >= KEPT) {
				RECENT.removeFirst();
			}
			RECENT.addLast(new Action(shown, word, action));
		}
		LumioChat.line(body);
	}

	/** @return the action of a line by its text, or null */
	public static Action of(String text) {
		synchronized (RECENT) {
			for (Action action : RECENT) {
				if (action.text().equals(text)) {
					return action;
				}
			}
		}
		return null;
	}

	/** Forgets an action once it ran, so a second click does nothing. */
	public static void used(Action action) {
		synchronized (RECENT) {
			RECENT.remove(action);
		}
	}
}
