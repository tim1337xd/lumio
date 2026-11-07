package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.setting.TickTilesSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Picture tiles to tick, one or more, in one row: each with its picture, a tick box in its corner, its name and what it is. A ticked tile has an
 * accent frame; a tile that cannot be used now is greyed and says why instead. A click ticks or unticks a usable tile.
 */
public class TickTilesControl extends Control {
	private static final int GAP = 8;
	private static final int PAD = 8;
	private static final int TICK = 10;
	private static final int NAME_GAP = 8;
	private static final int NAME_HEIGHT = 11;
	private static final int LINE = 9;
	private static final int TEXT_LINES = 2;
	/** Height of a picture relative to its width, and its bounds in menu pixels. */
	private static final float PICTURE_ASPECT = 0.32f;
	private static final int MIN_PICTURE_HEIGHT = 30;
	private static final int MAX_PICTURE_HEIGHT = 52;
	private static final int HINT_HEIGHT = 12;
	/** A tile that cannot be used is drawn this faint. */
	private static final float UNUSABLE = 0.45f;
	/** Compact tiles: the picture's height, the gap before the name, and the line under the tiles. */
	private static final int COMPACT_PICTURE = 30;
	private static final int COMPACT_NAME_GAP = 6;
	private static final int NOTE_GAP = 8;
	private static final int NOTE_LINE = 10;
	private static final int NOTE_LINES = 3;

	private final TickTilesSetting setting;
	private final Runnable onChanged;
	private final List<TickTilesSetting.Tile> tiles;
	/** The text lines of every tile, its description and its note while unusable, wrapped when measured. */
	private final List<List<String>> lines = new ArrayList<>();
	private final List<List<String>> notes = new ArrayList<>();
	/** The compact note's lines, wrapped for the width they were made for. */
	private List<String> noteLines = List.of();
	private int noteWidth = -1;
	private int tileHeight;
	private int pictureHeight;
	private int measuredRoom = -1;

	public TickTilesControl(TickTilesSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
		this.tiles = setting.tiles();
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	public boolean bare() {
		return true;
	}

	private int tileWidth() {
		return (width - GAP * (tiles.size() - 1)) / tiles.size();
	}

	private int tilesTop() {
		return setting.hint() != null ? y + HINT_HEIGHT : y;
	}

	@Override
	protected int measure(Ui ui, int width) {
		this.width = width;
		int room = tileWidth() - 2 * PAD;
		if (setting.isCompact()) {
			pictureHeight = COMPACT_PICTURE;
			tileHeight = PAD + pictureHeight + COMPACT_NAME_GAP + NAME_HEIGHT + PAD;
			if (setting.note() != null && width != noteWidth) {
				noteWidth = width;
				noteLines = ui.wrapScaled(setting.note(), width - 8, TextScale.SMALL, NOTE_LINES);
			}
			return tileHeight + (setting.note() != null ? NOTE_GAP + noteLines.size() * NOTE_LINE : 0);
		}
		pictureHeight = Mth.clamp(Math.round(room * PICTURE_ASPECT), MIN_PICTURE_HEIGHT, MAX_PICTURE_HEIGHT);
		if (room != measuredRoom) {
			measuredRoom = room;
			lines.clear();
			notes.clear();
			for (TickTilesSetting.Tile tile : tiles) {
				lines.add(ui.wrapScaled(tile.description(), room, TextScale.SMALL, TEXT_LINES));
				notes.add(tile.unusableNote() == null ? List.of() : ui.wrapScaled(tile.unusableNote(), room, TextScale.SMALL, TEXT_LINES));
			}
		}
		tileHeight = PAD + pictureHeight + NAME_GAP + NAME_HEIGHT + TEXT_LINES * LINE + PAD;
		return (setting.hint() != null ? HINT_HEIGHT : 0) + tileHeight;
	}

	private int tileX(int i) {
		return x + i * (tileWidth() + GAP);
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		if (setting.hint() != null) {
			// on the right of the group, as a note to its title
			ui.textAtScaled(setting.hint(), x + width - ui.widthScaled(setting.hint(), TextScale.SMALL), y - 2, TextScale.SMALL, palette.textSecondary());
		}
		int tileWidth = tileWidth();
		int room = tileWidth - 2 * PAD;
		int top = tilesTop();
		for (int i = 0; i < tiles.size(); i++) {
			TickTilesSetting.Tile tile = tiles.get(i);
			boolean usable = tile.available();
			boolean ticked = tile.setting().get() && usable;
			int tx = tileX(i);
			float hover = hover(ui, i, usable && mouseX >= tx && mouseX < tx + tileWidth && mouseY >= top && mouseY < top + tileHeight);
			float fade = usable ? 1f : UNUSABLE;
			if (ticked) {
				ui.rounded(tx - 2, top - 2, tileWidth + 4, tileHeight + 4, Radius.LG + 2, Ui.withOpacity(ui.accent, 0.2f));
			}
			ui.rounded(tx, top, tileWidth, tileHeight, Radius.LG, palette.card());
			if (hover > 0f || ticked) {
				ui.rounded(tx, top, tileWidth, tileHeight, Radius.LG, Ui.withOpacity(palette.hover(), ticked ? 1f : hover));
			}
			ui.ring(tx, top, tileWidth, tileHeight, Radius.LG, 1, ticked ? ui.accent : palette.cardBorder());
			if (tile.picture() != null) {
				ui.graphics.enableScissor(tx + PAD, top + PAD, tx + PAD + room, top + PAD + pictureHeight);
				tile.picture().draw(ui, tx + PAD, top + PAD, room, pictureHeight);
				ui.graphics.disableScissor();
				if (!usable) {
					ui.rounded(tx + PAD, top + PAD, room, pictureHeight, Radius.SM, Ui.withOpacity(palette.card(), 1f - UNUSABLE));
				}
			}
			// the tick box in the picture's corner
			int boxX = tx + PAD + room - TICK - 4;
			int boxY = top + PAD + 4;
			if (ticked) {
				ui.rounded(boxX, boxY, TICK, TICK, Radius.pill(TICK), ui.accent);
				ui.icon("tick", boxX + 1, boxY + 1, TICK - 2, 0xFFFFFFFF);
			} else {
				ui.rounded(boxX, boxY, TICK, TICK, Radius.pill(TICK), Ui.withOpacity(0xFF000000, 0.35f));
				ui.ring(boxX, boxY, TICK, TICK, Radius.pill(TICK), 1, Ui.withOpacity(0xFFFFFFFF, 0.75f * fade));
			}
			if (setting.isCompact()) {
				// the name centered under the picture, nothing more
				String name = ui.fitScaled(tile.name(), room, TextScale.SMALL);
				float nameWidth = ticked ? ui.boldWidthScaled(name, TextScale.SMALL) : ui.widthScaled(name, TextScale.SMALL);
				int nameColor = Ui.withOpacity(ticked ? palette.text() : palette.textSecondary(), fade);
				float nameX = tx + (tileWidth - nameWidth) / 2f;
				int compactY = top + PAD + pictureHeight + COMPACT_NAME_GAP;
				if (ticked) {
					ui.textAtScaledBold(name, nameX, compactY, TextScale.SMALL, nameColor);
				} else {
					ui.textAtScaled(name, nameX, compactY, TextScale.SMALL, nameColor);
				}
				continue;
			}
			int nameY = top + PAD + pictureHeight + NAME_GAP;
			ui.textAtScaledBold(ui.fit(tile.name(), room), tx + PAD, nameY, 1f, Ui.withOpacity(palette.text(), fade));
			List<String> text = usable || notes.get(i).isEmpty() ? lines.get(i) : notes.get(i);
			for (int k = 0; k < text.size(); k++) {
				ui.textAtScaled(text.get(k), tx + PAD, nameY + NAME_HEIGHT + k * LINE, TextScale.SMALL, palette.textSecondary());
			}
		}
		if (setting.isCompact() && setting.note() != null) {
			// the note wraps instead of being cut
			for (int k = 0; k < noteLines.size(); k++) {
				ui.textAtScaled(noteLines.get(k), x + 4, top + tileHeight + NOTE_GAP + k * NOTE_LINE, TextScale.SMALL, palette.textSecondary());
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		int tileWidth = tileWidth();
		int top = tilesTop();
		for (int i = 0; i < tiles.size(); i++) {
			int tx = tileX(i);
			if (mouseX >= tx && mouseX < tx + tileWidth && mouseY >= top && mouseY < top + tileHeight) {
				TickTilesSetting.Tile tile = tiles.get(i);
				if (!tile.available()) {
					return true;
				}
				boolean now = !tile.setting().get();
				tile.setting().set(now);
				UiSounds.toggle(now);
				onChanged.run();
				return true;
			}
		}
		return false;
	}
}
