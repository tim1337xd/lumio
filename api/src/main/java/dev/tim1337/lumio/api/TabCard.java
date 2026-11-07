package dev.tim1337.lumio.api;

import java.util.List;

/**
 * An info card of the tab list: a title and its rows.
 *
 * @param title the card's heading ("Skills"), or "" for none
 * @param rows  its rows, in order
 */
public record TabCard(String title, List<Row> rows) {
	public TabCard {
		rows = List.copyOf(rows);
	}

	/**
	 * A row: a label and its value in the server's colors (the value may be null), and a progress as a share from 0 to 1 (drawn as a bar), or
	 * -1 for none; and the color of its bar as 0xRRGGBB, -1 for the accent color.
	 */
	public record Row(net.minecraft.network.chat.Component label, net.minecraft.network.chat.Component value, float progress, int barRgb) {
		/** A row whose bar has the accent color. */
		public Row(net.minecraft.network.chat.Component label, net.minecraft.network.chat.Component value, float progress) {
			this(label, value, progress, -1);
		}
	}
}
