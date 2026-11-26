package dev.tim1337.lumio.api.experimental.ui;

import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.spi.Backend;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Settings rows beyond the usual ones, for a tidy settings sheet: a row with several buttons (and, if wanted, a choice in it), highlighted for the
 * row that matters most; colors for chip switches; and a question the sheet asks before something changes, with what was found and a choice.
 */
public final class SettingRows {
	/** How a button looks: a usual one, the main one (in the accent) or one that removes something (red). */
	public enum Kind {
		NORMAL, PRIMARY, DANGER
	}

	/** A button of a row: its name, its look and what a click does. */
	public record Button(String label, Kind kind, Runnable action) {
	}

	/**
	 * A question before something changes: the title and under it what was found, a choice between a few options (or none), and
	 * Cancel and a confirming button.
	 *
	 * @param options the options to choose from, such as Merge and Replace; empty for none
	 * @param confirm the name of the confirming button, such as "Import"
	 * @param chosen  what a click on the confirming button does, with the option chosen (0 without options)
	 */
	public record Question(String title, String detail, List<String> options, int option, String confirm, IntConsumer chosen) {
		public Question {
			options = List.copyOf(options);
		}
	}

	/** What the main mod provides for the rows. */
	public interface Provider {
		AddonSetting<Void> buttons(String key, Component label, Supplier<String> subtext, boolean highlighted, Choice<?> inline,
			Supplier<List<Button>> buttons);

		void colors(MultiToggle chips, int[] rgb);

		void ask(Question question);
	}

	private SettingRows() {
	}

	/**
	 * @param label       the row's title
	 * @param subtext     the line under the title, read when drawn (null or empty for none)
	 * @param highlighted whether the row stands out, in the accent
	 * @param inline      a choice shown as a small segmented control before the buttons, or null
	 * @param buttons     the buttons, read when drawn (they may change, such as Delete becoming Undo)
	 * @return a row of buttons, which has no value of its own
	 */
	public static AddonSetting<Void> buttons(String key, Component label, Supplier<String> subtext, boolean highlighted, Choice<?> inline,
		Supplier<List<Button>> buttons) {
		return Backend.get().rows().buttons(key, label, subtext, highlighted, inline, buttons);
	}

	/** Colors the chips of a chip switch, one 0xRRGGBB per option, in their order. */
	public static void colors(MultiToggle chips, int... rgb) {
		Backend.get().rows().colors(chips, rgb);
	}

	/** Asks a question in the open settings sheet, under the row whose button was clicked; nothing changes before the player confirms. */
	public static void ask(Question question) {
		Backend.get().rows().ask(question);
	}
}
