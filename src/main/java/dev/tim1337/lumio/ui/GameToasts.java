package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.mixin.accessor.AdvancementToastAccessor;
import dev.tim1337.lumio.mixin.accessor.RecipeEntryAccessor;
import dev.tim1337.lumio.mixin.accessor.RecipeToastAccessor;
import dev.tim1337.lumio.mixin.accessor.SystemToastAccessor;
import dev.tim1337.lumio.mixin.accessor.TutorialToastAccessor;

import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The game's own toasts (advancements, recipes, system notices, tutorial hints) in the style of the mod's panels, in every theme. Only their look
 * changes: the game still decides when they come, how long they stay and how they slide. A toast of another mod, and the game's music toast with its
 * moving notes, are not known here and stay as they are. While an open settings window would lie under a toast, toasts wait until there is room.
 */
public final class GameToasts {
	/** Room around a panel in the game's slot for a toast, so toasts stand apart and clear of the screen's edge. */
	private static final int SLOT_MARGIN_RIGHT = 6;
	private static final int SLOT_MARGIN_Y = 2;
	private static final int PAD = 8;
	private static final int ICON = 16;
	private static final int ICON_GAP = 8;
	/** How far apart the lines of text in a toast are, and the least room above and below them. */
	private static final int LINE_HEIGHT = 11;
	private static final int TEXT_MARGIN = 2;
	private static final int PROGRESS_HEIGHT = 2;
	private static final int PROGRESS_BOTTOM = 4;

	private GameToasts() {
	}

	/**
	 * Draws a toast at 0, 0 of the pose.
	 *
	 * @return whether it is drawn; otherwise it draws itself as the game does
	 */
	public static boolean draw(Toast toast, GuiGraphicsExtractor graphics) {
		return switch (toast) {
			case SystemToast system -> {
				SystemToastAccessor parts = (SystemToastAccessor) system;
				panel(graphics, toast, null, first(parts.lumio$titleLines()), lines(parts.lumio$messageLines()), false);
				yield true;
			}
			case AdvancementToast advancement -> {
				AdvancementToastAccessor parts = (AdvancementToastAccessor) advancement;
				DisplayInfo display = parts.lumio$advancement().value().display().orElse(null);
				if (display == null) {
					yield false;
				}
				//#if 26.3
				//$ panel(graphics, toast, parts.lumio$icon(), display.title().getString(), List.of(display.type().getDisplayName().getString()), false);
				//#else
				panel(graphics, toast, parts.lumio$icon(), display.getTitle().getString(), List.of(display.getType().getDisplayName().getString()), false);
				//#endif
				yield true;
			}
			case RecipeToast recipe -> {
				RecipeToastAccessor parts = (RecipeToastAccessor) recipe;
				List<?> items = parts.lumio$items();
				if (items.isEmpty()) {
					yield false;
				}
				ItemStack item = ((RecipeEntryAccessor) items.get(Mth.clamp(parts.lumio$shown(), 0, items.size() - 1))).lumio$unlocked();
				panel(graphics, toast, item, Component.translatable("recipe.toast.title").getString(),
					List.of(Component.translatable("recipe.toast.description").getString()), false);
				yield true;
			}
			case TutorialToast tutorial -> {
				tutorial(graphics, tutorial);
				yield true;
			}
			default -> false;
		};
	}

	private static void tutorial(GuiGraphicsExtractor graphics, TutorialToast toast) {
		TutorialToastAccessor parts = (TutorialToastAccessor) toast;
		List<String> lines = lines(parts.lumio$lines());
		String title = lines.isEmpty() ? "" : lines.getFirst();
		Ui ui = panel(graphics, toast, null, title, lines.size() > 1 ? lines.subList(1, lines.size()) : List.of(), true);
		parts.lumio$icon().extractRenderState(graphics, PAD, (toast.height() - ICON) / 2);
		if (parts.lumio$progressable()) {
			int width = toast.width() - SLOT_MARGIN_RIGHT - 2 * PAD;
			int y = toast.height() - SLOT_MARGIN_Y - PROGRESS_BOTTOM;
			ui.rounded(PAD, y, width, PROGRESS_HEIGHT, PROGRESS_HEIGHT / 2f, ui.palette.trackOff());
			ui.rounded(PAD, y, Math.round(width * Mth.clamp(parts.lumio$progress(), 0f, 1f)), PROGRESS_HEIGHT, PROGRESS_HEIGHT / 2f, ui.accent);
		}
	}

	/** Draws the panel with a toast's text beside its icon, as the mod's notifications are drawn. */
	private static Ui panel(GuiGraphicsExtractor graphics, Toast toast, ItemStack icon, String title, List<String> lines, boolean iconRoom) {
		Minecraft client = Minecraft.getInstance();
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		int width = toast.width() - SLOT_MARGIN_RIGHT;
		int height = toast.height();
		HudStyle.panel(ui, 0, SLOT_MARGIN_Y, width, height - 2 * SLOT_MARGIN_Y, Radius.LG, HudStyle.opacity(), ModFeatures.NOTIFICATIONS != null && ModFeatures.NOTIFICATIONS.glow());
		boolean withIcon = icon != null || iconRoom;
		if (icon != null) {
			graphics.fakeItem(icon, PAD, (height - ICON) / 2);
		}
		int left = PAD + (withIcon ? ICON + ICON_GAP : 0);
		int room = width - left - PAD;
		int shownLines = Math.min(lines.size(), Math.max(0, (height - 2 * (SLOT_MARGIN_Y + TEXT_MARGIN) - LINE_HEIGHT) / LINE_HEIGHT));
		int top = (height - LINE_HEIGHT * (1 + shownLines)) / 2 + 1;
		ui.textAtScaledBold(ui.fit(title, room), left, top, 1f, HudStyle.text());
		for (int i = 0; i < shownLines; i++) {
			ui.text(ui.fit(lines.get(i), room), left, top + LINE_HEIGHT * (i + 1), HudStyle.secondary());
		}
		return ui;
	}

	/** @return whether toasts wait now: an open settings window reaches where they would show */
	public static boolean waiting() {
		Minecraft client = Minecraft.getInstance();
		return client.gui.screen() instanceof MacScreen screen && screen.windowRight() > client.getWindow().getGuiScaledWidth() - Toast.DEFAULT_WIDTH;
	}

	private static String first(List<FormattedCharSequence> lines) {
		return lines.isEmpty() ? "" : text(lines.getFirst());
	}

	private static List<String> lines(List<FormattedCharSequence> lines) {
		return lines.stream().map(GameToasts::text).toList();
	}

	private static String text(FormattedCharSequence sequence) {
		StringBuilder text = new StringBuilder();
		sequence.accept((index, style, codePoint) -> {
			text.appendCodePoint(codePoint);
			return true;
		});
		return text.toString();
	}
}
