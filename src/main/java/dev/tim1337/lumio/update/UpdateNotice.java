package dev.tim1337.lumio.update;

import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * The title screen's notice of an update, a box in Lumio's look: the new version, the first lines of its notes, What's new, and Update (Update
 * all with an add-on), Download (the release page in the browser), Later and Skip this version. While the update downloads it shows how far it
 * is, then "Ready: installs when you close the game" with Cancel, or why it failed. Sizes are in the title screen's layout units.
 */
public final class UpdateNotice {
	static final float WIDTH = 400f;
	private static final float PAD = 18f;
	private static final float RADIUS = 10f;
	private static final float TEXT = 1f / 9.6f;
	private static final float TITLE_SIZE = 16f * TEXT;
	private static final float BODY_SIZE = 12.5f * TEXT;
	private static final float TITLE_ROW = 24f;
	private static final float LINE = 18f;
	private static final float GAP = 10f;
	private static final float BUTTON = 30f;
	private static final float BUTTON_PAD = 12f;
	private static final float BUTTON_GAP = 8f;
	private static final float BUTTON_RADIUS = 6f;
	private static final float BAR = 6f;
	private static final int NOTE_LINES = 3;
	private static final int MOST_BUTTONS = 4;
	private static final String RELEASE_PAGES = "https://github.com/" + UpdateSources.OWNER + "/";
	private static final int BACKGROUND = 0xEE15181F;
	private static final int BORDER = 0x26FFFFFF;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int SECONDARY = 0xB3FFFFFF;
	private static final int BUTTON_COLOR = 0x1FFFFFFF;
	private static final int FAILED = 0xFFFF453A;

	private enum Action {
		UPDATE, DOWNLOAD, LATER, SKIP, CANCEL
	}

	/** Where the buttons and the link were drawn (x, y, width), for clicks. */
	private final float[][] buttons = new float[MOST_BUTTONS][3];
	private final Action[] actions = new Action[MOST_BUTTONS];
	private int buttonCount;
	private float linkX;
	private float linkY;
	private float linkWidth;
	private String linkUrl;
	private float left;
	private float top;
	private float height;
	private List<String> notes = List.of();
	private Object notesFor;

	/** @return whether the notice has something to show */
	public static boolean shown() {
		Updater updater = Updates.updater();
		return updater != null && updater.noticeShown();
	}

	/** Draws the notice with its top right corner here. */
	public void draw(Ui ui, float right, float topEdge, int mouseX, int mouseY) {
		Updater updater = Updates.updater();
		if (updater == null || !updater.noticeShown()) {
			buttonCount = 0;
			linkUrl = null;
			return;
		}
		List<Updater.Offer> offers = updater.offers();
		Updater.Stage stage = updater.stage();
		left = right - WIDTH;
		top = topEdge;
		List<String> lines = noteLines(offers);
		height = PAD + TITLE_ROW + (stage == Updater.Stage.OFFER ? lines.size() * LINE + LINE : LINE) + GAP + BUTTON + PAD;
		ui.shadow(Math.round(left), Math.round(top), Math.round(WIDTH), Math.round(height), RADIUS, 12, 0.5f);
		ui.roundedSmooth(left, top, WIDTH, height, RADIUS, BACKGROUND);
		ui.ring(Math.round(left), Math.round(top), Math.round(WIDTH), Math.round(height), RADIUS, 1, BORDER);
		float x = left + PAD;
		float y = top + PAD;
		ui.textAtScaledBold(ui.fitScaled(title(offers), WIDTH - 2 * PAD, TITLE_SIZE), x, y, TITLE_SIZE, TEXT_COLOR);
		y += TITLE_ROW;
		buttonCount = 0;
		linkUrl = null;
		switch (stage) {
			case OFFER -> {
				for (String line : lines) {
					ui.textAtScaled(ui.fitScaled(line, WIDTH - 2 * PAD, BODY_SIZE), x, y, BODY_SIZE, SECONDARY);
					y += LINE;
				}
				String page = offers.isEmpty() ? "" : offers.getFirst().pick().release().page();
				if (page.startsWith(RELEASE_PAGES)) {
					linkUrl = page;
					linkX = x;
					linkY = y;
					linkWidth = ui.widthScaled("What's new", BODY_SIZE);
					boolean over = over(mouseX, mouseY, linkX, linkY - 2f, linkWidth, LINE);
					ui.textAtScaled("What's new", x, y, BODY_SIZE, over ? TEXT_COLOR : ui.accent());
				}
				y += LINE + GAP;
				float bx = x;
				bx = button(ui, offers.size() > 1 ? "Update all" : "Update", Action.UPDATE, bx, y, true, mouseX, mouseY);
				bx = button(ui, "Download", Action.DOWNLOAD, bx, y, false, mouseX, mouseY);
				bx = button(ui, "Later", Action.LATER, bx, y, false, mouseX, mouseY);
				button(ui, "Skip this version", Action.SKIP, bx, y, false, mouseX, mouseY);
			}
			case DOWNLOADING -> {
				ui.textAtScaled("Downloading… " + Math.round(updater.progress() * 100f) + " %", x, y, BODY_SIZE, SECONDARY);
				y += LINE + GAP;
				float barTop = y + (BUTTON - BAR) / 2f;
				ui.roundedSmooth(x, barTop, WIDTH - 2 * PAD, BAR, BAR / 2f, BUTTON_COLOR);
				ui.roundedSmooth(x, barTop, Math.max(BAR, (WIDTH - 2 * PAD) * updater.progress()), BAR, BAR / 2f, ui.accent());
			}
			case READY -> {
				ui.textAtScaled("Ready: installs when you close the game", x, y, BODY_SIZE, SECONDARY);
				y += LINE + GAP;
				button(ui, "Cancel", Action.CANCEL, x, y, false, mouseX, mouseY);
			}
			case FAILED -> {
				ui.textAtScaled(ui.fitScaled("Update failed: " + updater.problem(), WIDTH - 2 * PAD, BODY_SIZE), x, y, BODY_SIZE, FAILED);
				y += LINE + GAP;
				float bx = button(ui, "Later", Action.LATER, x, y, false, mouseX, mouseY);
				button(ui, "Remove download", Action.CANCEL, bx, y, false, mouseX, mouseY);
			}
			default -> {
			}
		}
	}

	private static String title(List<Updater.Offer> offers) {
		if (offers.isEmpty()) {
			return "Lumio update";
		}
		Updater.Offer first = offers.getFirst();
		String name = first.source() == UpdateSources.LUMIO ? "Lumio" : "Lumio: Hypixel SkyBlock";
		String title = name + " " + first.pick().version().display() + " is available";
		return offers.size() > 1 ? title + ", with its add-on" : title;
	}

	/** @return the first lines of the notes, made once per offer */
	private List<String> noteLines(List<Updater.Offer> offers) {
		if (notesFor != offers) {
			notesFor = offers;
			notes = offers.isEmpty() ? List.of() : new ArrayList<>(offers.getFirst().pick().release().firstLines(NOTE_LINES));
		}
		return notes;
	}

	/** Draws a button and keeps where it is. @return where the next one starts */
	private float button(Ui ui, String label, Action action, float x, float y, boolean primary, int mouseX, int mouseY) {
		float width = ui.widthScaled(label, BODY_SIZE) + 2 * BUTTON_PAD;
		boolean over = over(mouseX, mouseY, x, y, width, BUTTON);
		int fill = primary ? ui.accent() : over ? Ui.withOpacity(0xFFFFFFFF, 0.2f) : BUTTON_COLOR;
		ui.roundedSmooth(x, y, width, BUTTON, BUTTON_RADIUS, primary && over ? Ui.mix(fill, 0xFFFFFFFF, 0.15f) : fill);
		ui.textAtScaled(label, x + BUTTON_PAD, y + (BUTTON - 9f * BODY_SIZE) / 2f, BODY_SIZE, TEXT_COLOR);
		if (buttonCount < MOST_BUTTONS) {
			buttons[buttonCount][0] = x;
			buttons[buttonCount][1] = y;
			buttons[buttonCount][2] = width;
			actions[buttonCount] = action;
			buttonCount++;
		}
		return x + width + BUTTON_GAP;
	}

	private static boolean over(double mouseX, double mouseY, float x, float y, float width, float height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** @return whether a click (in layout units) was the notice's */
	public boolean click(double mouseX, double mouseY) {
		if (!shown()) {
			return false;
		}
		if (linkUrl != null && over(mouseX, mouseY, linkX, linkY - 2f, linkWidth, LINE)) {
			openPage(linkUrl);
			return true;
		}
		for (int i = 0; i < buttonCount; i++) {
			if (over(mouseX, mouseY, buttons[i][0], buttons[i][1], buttons[i][2], BUTTON)) {
				UiSounds.click();
				run(actions[i]);
				return true;
			}
		}
		return over(mouseX, mouseY, left, top, WIDTH, height);
	}

	private void run(Action action) {
		Updater updater = Updates.updater();
		switch (action) {
			case UPDATE -> Updates.update();
			case DOWNLOAD -> {
				String page = updater.offers().isEmpty() ? "" : updater.offers().getFirst().pick().release().page();
				if (page.startsWith(RELEASE_PAGES)) {
					openPage(page);
				}
			}
			case LATER -> Updates.later();
			case SKIP -> Updates.skip();
			case CANCEL -> Updates.cancel();
		}
	}

	/** Opens a release page after the game's confirmation, as every link from outside data. */
	private static void openPage(String url) {
		Minecraft client = Minecraft.getInstance();
		try {
			ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), java.net.URI.create(url));
		} catch (IllegalArgumentException e) {
			// a page that is no address is not opened
		}
	}
}
