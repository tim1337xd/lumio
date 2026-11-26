package dev.tim1337.lumio.api;

import java.util.List;

/**
 * An info card of the tab list: a title and its rows.
 *
 * @param title the card's heading ("Skills"), or "" for none
 * @param rows  its rows, in order
 * @param slot  the card's fixed place among the cards by its kind, from 0 on, the same whichever other cards there are: the list gives each card
 *              its column by it, so cards do not jump between columns; -1 for a card without one, which comes after those that have one, in
 *              the order it was first seen
 */
public record TabCard(String title, List<Row> rows, int slot) {
	public TabCard {
		rows = List.copyOf(rows);
	}

	/** A card without a fixed place. */
	public TabCard(String title, List<Row> rows) {
		this(title, rows, -1);
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
