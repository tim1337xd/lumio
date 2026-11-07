package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.Simulation;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.hud.HudSimulation;

import net.minecraft.util.Mth;

import java.util.List;

/**
 * The Simulate menu of the HUD editor, under its toolbar button: "Off" and every activity, grouped (Fishing, Mining, Slayer, Farming), with
 * the options of the chosen activity under it where they change the HUD, such as the pickaxe ability being ready. Picking one shows the elements of
 * that activity with sample content and hides the others in the editor.
 */
final class HudEditSimulate {
	private static final int WIDTH = 210;
	private static final int ROW = 20;
	private static final int HEADER = 16;
	private static final int PAD = 6;
	private static final int OPTION_INDENT = 14;
	/** The smallest the menu is scaled to; below it, it would be too small to read, and it reaches past the screen instead. */
	private static final float MIN_FIT = 0.6f;
	private static final String OFF = "Off (what I'm doing now)";
	/** Row ids: OFF, then every activity by its ordinal from ACTIVITY, then the options from OPTION. */
	private static final int OFF_ROW = 0;
	private static final int ACTIVITY = 100;
	private static final int OPTION = 1000;
	/** Row ids of the moments, which play once when clicked. */
	private static final int MOMENT = 2000;
	private static final String MOMENTS = "MOMENTS";

	private final HudEditScreen screen;
	private final HudEditHits hits = new HudEditHits();
	private final Spring shown = Spring.smooth(Spring.SNAPPY);
	private boolean open;
	private int anchorX;
	private int anchorY;
	private int x;
	private int y;
	private int height;
	private int hoveredRow = -1;
	/** How much the menu is scaled down to fit under the toolbar on a short screen. */
	private float fit = 1f;

	HudEditSimulate(HudEditScreen screen) {
		this.screen = screen;
	}

	boolean isOpen() {
		return open;
	}

	void toggle(int buttonX, int buttonBottom) {
		open = !open;
		anchorX = buttonX;
		anchorY = buttonBottom + 4;
		UiSounds.play(open ? UiSounds.Cue.OPEN : UiSounds.Cue.CLOSE);
	}

	void close() {
		open = false;
	}

	boolean contains(double mouseX, double mouseY) {
		return open && mouseX >= x && mouseX < x + WIDTH * fit && mouseY >= y && mouseY < y + height * fit;
	}

	/** @return the row under a point on screen, in the menu's own (unscaled) coordinates */
	private int rowAt(double mouseX, double mouseY) {
		return hits.at(x + (mouseX - x) / fit, y + (mouseY - y) / fit);
	}

	/** @return the menu's height for the activities and the options of the chosen one */
	private int measure() {
		int rows = 1;
		int headers = 0;
		String group = "";
		for (HudSimulation.Entry entry : HudSimulation.entries()) {
			if (!entry.simulation().group().equals(group)) {
				group = entry.simulation().group();
				headers++;
			}
			rows++;
			if (entry == HudSimulation.running()) {
				rows += entry.simulation().options().size();
			}
		}
		// the moments, under their own header
		if (!HudSimulation.moments().isEmpty()) {
			rows += HudSimulation.moments().size();
			headers++;
		}
		return 2 * PAD + rows * ROW + headers * HEADER;
	}

	void draw(Ui ui, int mouseX, int mouseY) {
		float amount = Mth.clamp(shown.follow(ui, open ? 1f : 0f), 0f, 1f);
		if (amount <= 0.01f) {
			hits.clear();
			return;
		}
		height = measure();
		// on a short screen the menu is scaled down to fit under the toolbar
		fit = Math.min(1f, Math.min((screen.height - 4f - anchorY) / height, (screen.width - 8f) / WIDTH));
		fit = Math.max(fit, MIN_FIT);
		x = Mth.clamp(anchorX, 4, Math.max(4, screen.width - Math.round(WIDTH * fit) - 4));
		y = Mth.clamp(anchorY, 4, Math.max(4, screen.height - Math.round(height * fit) - 4));
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		// it unfolds from the button it belongs to
		pose.translate(x, y);
		pose.scale(fit, fit * (0.85f + 0.15f * amount));
		pose.translate(-x, -y);
		float zoom = Ui.zoom();
		Ui.setZoom(zoom * fit);
		HudEditScreen.surface(ui, x, y, WIDTH, height, Radius.MD, amount);
		Palette palette = ui.palette;
		int rowY = y + PAD;
		// the rows from the last frame, under the mouse
		hoveredRow = open && contains(mouseX, mouseY) ? rowAt(mouseX, mouseY) : -1;
		hits.clear();
		rowY = row(ui, OFF_ROW, OFF, x + PAD, rowY, HudSimulation.running() == null, amount, mouseX, mouseY);
		String group = "";
		List<HudSimulation.Entry> entries = HudSimulation.entries();
		for (int index = 0; index < entries.size(); index++) {
			HudSimulation.Entry entry = entries.get(index);
			Simulation simulation = entry.simulation();
			if (!simulation.group().equals(group)) {
				group = simulation.group();
				ui.sectionLabel(group.toUpperCase(java.util.Locale.ROOT), x + PAD + 4, rowY + 4, Ui.withOpacity(palette.textSecondary(), amount));
				rowY += HEADER;
			}
			boolean chosen = entry == HudSimulation.running();
			rowY = row(ui, ACTIVITY + index, simulation.name(), x + PAD, rowY, chosen && simulation.options().isEmpty(), amount, mouseX, mouseY);
			if (chosen) {
				for (int i = 0; i < simulation.options().size(); i++) {
					rowY = row(ui, OPTION + i, simulation.options().get(i), x + PAD + OPTION_INDENT, rowY, HudSimulation.option() == i, amount, mouseX, mouseY);
				}
			}
		}
		List<HudSimulation.Moment> moments = HudSimulation.moments();
		if (!moments.isEmpty()) {
			ui.sectionLabel(MOMENTS, x + PAD + 4, rowY + 4, Ui.withOpacity(palette.textSecondary(), amount));
			rowY += HEADER;
		}
		for (int i = 0; i < moments.size(); i++) {
			rowY = row(ui, MOMENT + i, moments.get(i).name(), x + PAD, rowY, false, amount, mouseX, mouseY);
		}
		Ui.setZoom(zoom);
		pose.popMatrix();
	}

	/** Draws a row with a radio mark where it is chosen; @return the top of the next row */
	private int row(Ui ui, int id, String text, int rowX, int rowY, boolean chosen, float amount, int mouseX, int mouseY) {
		int rowWidth = x + WIDTH - PAD - rowX;
		boolean over = hoveredRow == id;
		if (over) {
			ui.rounded(rowX, rowY, rowWidth, ROW, Radius.SM, Ui.withOpacity(ui.palette.hover(), amount));
		}
		if (chosen) {
			ui.circle(rowX + 7, rowY + ROW / 2, 3, Ui.withOpacity(ui.accent, amount));
		}
		ui.textMiddle(ui.fit(text, rowWidth - 20), rowX + 16, rowY + ROW / 2, Ui.withOpacity(chosen ? ui.palette.text() : Ui.mix(ui.palette.textSecondary(),
			ui.palette.text(), over ? 1f : 0.5f), amount));
		hits.add(id, rowX, rowY, rowWidth, ROW);
		return rowY + ROW;
	}

	/** @return whether the click was in the menu */
	boolean click(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		// the menu stays open after a pick, so activities can be compared one after the other; a click outside it or Escape closes it
		int id = rowAt(mouseX, mouseY);
		if (id == OFF_ROW) {
			HudSimulation.stop();
			UiSounds.click();
		} else if (id >= MOMENT) {
			HudSimulation.playMoment(id - MOMENT);
			UiSounds.click();
		} else if (id >= OPTION) {
			if (HudSimulation.running() != null) {
				HudSimulation.start(HudSimulation.running(), id - OPTION);
			}
			UiSounds.click();
		} else if (id >= ACTIVITY) {
			HudSimulation.start(HudSimulation.entries().get(id - ACTIVITY), 0);
			UiSounds.click();
		}
		return true;
	}
}
