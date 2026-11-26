package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.spi.Backend;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The lines Lumio and its add-ons add to the chat: every one starts with "[Lumio] ", the brackets grey and the name in the menu's accent color (as it
 * is when the line is posted), so the player always knows which lines are the mod's. Only for the mod's own lines: never for a message of the game or
 * of a server, which stay exactly as they come. Toasts and notifications are not chat lines and take no prefix.
 *
 * @since 1.10.0
 */
public final class LumioChat {
	/** The name in the prefix. */
	public static final String NAME = "Lumio";
	/** The color of an alert's text when it has none of its own. */
	public static final int ALERT_RGB = 0xFF5555;

	private LumioChat() {
	}

	/**
	 * @return a new "[Lumio] " prefix: grey brackets, and the name as a soft, still gradient over its letters, from the menu's accent to a lighter
	 *         shade of it (or the Colored look's first two colors), bright enough on the dark chat
	 */
	public static MutableComponent prefix() {
		int[] colors = Backend.get().chatNameColors();
		MutableComponent name = Component.empty();
		for (int i = 0; i < NAME.length(); i++) {
			float share = i / (float) (NAME.length() - 1);
			int rgb = mix(colors[0], colors[1], share);
			name.append(Component.literal(String.valueOf(NAME.charAt(i))).withStyle(style -> style.withColor(rgb)));
		}
		return Component.literal("[").withStyle(ChatFormatting.GRAY).append(name).append(Component.literal("] ").withStyle(ChatFormatting.GRAY));
	}

	private static int mix(int a, int b, float share) {
		int r = Math.round((a >> 16 & 0xFF) * (1 - share) + (b >> 16 & 0xFF) * share);
		int g = Math.round((a >> 8 & 0xFF) * (1 - share) + (b >> 8 & 0xFF) * share);
		int bl = Math.round((a & 0xFF) * (1 - share) + (b & 0xFF) * share);
		return r << 16 | g << 8 | bl;
	}

	/** @return the message with the prefix before it; the message itself is unchanged */
	public static MutableComponent prefixed(Component message) {
		return prefix().append(message);
	}

	/** Posts a line to the chat as it is, after the prefix. Safe from any thread; nothing is posted without a player. */
	public static void line(Component message) {
		post(prefixed(message));
	}

	/** Posts an information line: the message in grey where it has no color of its own. */
	public static void info(Component message) {
		post(prefix().append(Component.empty().withStyle(ChatFormatting.GRAY).append(message)));
	}

	/** Posts an alert: the message in red where it has no color of its own. */
	public static void alert(Component message) {
		post(prefix().append(Component.empty().withStyle(style -> style.withColor(ALERT_RGB)).append(message)));
	}

	private static void post(Component line) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.player.sendSystemMessage(line);
			}
		});
	}
}
