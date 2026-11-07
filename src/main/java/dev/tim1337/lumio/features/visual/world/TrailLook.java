package dev.tim1337.lumio.features.visual.world;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** The trails' looks. Plain choices, so it is unit tested. */
public final class TrailLook {
	/** How a trail is drawn. */
	public enum Kind {
		SILK("Silk"), STARDUST("Stardust"), COMET("Comet"), LIGHTNING("Lightning"), HELIX("Helix"), ECHO("Echo");

		private final Component name;

		Kind(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** Where Radiance's color comes from. */
	public enum Colors {
		RARITY("Rarity"), ACCENT("Accent"), CUSTOM("Custom");

		private final Component name;

		Colors(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** Where the color of the trails comes from. */
	public enum TrailColors {
		ACCENT("Accent"), CUSTOM("Custom");

		private final Component name;

		TrailColors(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** The style of one source: the page's, or one of its own. */
	public enum OwnKind {
		SAME("Same", null), SILK("Silk", Kind.SILK), STARDUST("Stardust", Kind.STARDUST), COMET("Comet", Kind.COMET), LIGHTNING("Lightning", Kind.LIGHTNING),
		HELIX("Helix", Kind.HELIX), ECHO("Echo", Kind.ECHO);

		private final Component name;
		private final Kind kind;

		OwnKind(String name, Kind kind) {
			this.name = Component.literal(name);
			this.kind = kind;
		}

		public Component displayName() {
			return name;
		}

		/** @return this source's style, with the page's standing in for Same */
		public Kind or(Kind page) {
			return kind == null ? page : kind;
		}
	}

	/** The color of one source: the page's, or one of its own. */
	public enum OwnColors {
		SAME("Same"), ACCENT("Accent"), CUSTOM("Custom");

		private final Component name;

		OwnColors(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	private static final long MILLIS_PER_TICK = 50L;

	private TrailLook() {
	}

	/** @return every how many ticks a trail whose arrow stopped gives up a point, so that all of it is gone after the fade time */
	public static int retractEvery(float fadeMillis, int points) {
		return Math.max(1, Math.round(fadeMillis / (Math.max(1, points) * (float) MILLIS_PER_TICK)));
	}

	/** @return the color of an item's rarity, as 0xRRGGBB: the color of its name as the server sends it, or the game's rarity */
	public static int rarityRgb(ItemStack stack) {
		Optional<Integer> named = stack.getHoverName().visit((Style style, String text) -> {
			if (text.isBlank()) {
				return Optional.empty();
			}
			if (text.length() > 1 && text.charAt(0) == '§') {
				ChatFormatting code = ChatFormatting.getByCode(text.charAt(1));
				TextColor color = code == null ? null : TextColor.fromLegacyFormat(code);
				if (color != null) {
					return Optional.of(color.getValue());
				}
			}
			return style.getColor() == null ? Optional.empty() : Optional.of(style.getColor().getValue());
		}, Style.EMPTY);
		if (named.isPresent()) {
			return named.get();
		}
		TextColor own = TextColor.fromLegacyFormat(stack.getRarity().color());
		return own == null ? 0xFFFFFF : own.getValue();
	}
}
