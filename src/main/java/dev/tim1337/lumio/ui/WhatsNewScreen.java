package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.Changelog;
import dev.tim1337.lumio.api.Changelog.Entry;
import dev.tim1337.lumio.api.Changelog.Kind;
import dev.tim1337.lumio.api.Changelog.Release;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.update.Updates;

import net.fabricmc.loader.api.FabricLoader;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * "What is new" (mockup WhatsNew in the design folder, without its pictures): a tab per mod with a changelog (Lumio first, no tab row with Lumio alone), the released versions
 * on the left (newest first, a "New" chip on those not seen yet), the picked one on the right in the groups Added, Changed and Fixed, an
 * entry with a link opening its page. Shown once after an update on the tab of the mod that changed, and always from Home. Text only: the
 * changelog files ({@link Changelog}) hold no pictures, so the jars do not grow with every release.
 */
public class WhatsNewScreen extends Screen {
	private static final int WIDTH = 620;
	private static final int HEIGHT = 420;
	private static final int EDGE = 12;
	private static final int PAD = 20;
	private static final int HEADER = 52;
	private static final int FOOTER = 48;
	private static final int LIST_WIDTH = 170;
	private static final int LIST_ROW = 34;
	private static final int TAB_HEIGHT = 22;
	private static final int TAB_PAD = 10;
	private static final int DOT = 4;
	private static final int GROUP_HEAD = 22;
	private static final int ENTRY_LINE = 12;
	private static final int ENTRY_PAD = 9;
	/** An entry wraps to two lines at most. */
	private static final int ENTRY_LINES = 2;
	private static final int OPEN_WIDTH = 50;
	private static final int BUTTON_HEIGHT = 24;
	private static final int DONE_WIDTH = 72;
	private static final int FULL_WIDTH = 118;
	private static final float SCROLL_STEP = 40f;
	private static final float SCROLL_RATE = 14f;
	private static final String[] GROUP_LABELS = {"ADDED", "CHANGED", "FIXED"};
	private static final String ADDON_PREFIX = "Lumio: ";
	private static final String NOTE = "A dot on Home marks something new";

	/** The mods' files, read once. */
	private static List<WhatsNew.Mod> mods;

	private final Screen parent;
	private final WhatsNew model;
	private final List<String> tabs;
	private final Spring open = Spring.bouncy(Spring.GENTLE);
	private final Spring close = Spring.smooth(Spring.DEFAULT);
	private boolean closing;
	private long last = System.nanoTime();
	private int tab;
	private int picked;
	private float listScroll;
	private float listTarget;
	private float detailScroll;
	private float detailTarget;
	private List<WhatsNew.Item> items = List.of();
	// where things were drawn in the last frame, for clicks
	private int[] tabLefts = new int[0];
	private int[] tabWidths = new int[0];
	private final List<int[]> openBoxes = new ArrayList<>();
	private final List<Feature> openTargets = new ArrayList<>();

	public WhatsNewScreen(Screen parent) {
		super(Component.literal("What is new"));
		this.parent = parent;
		this.model = new WhatsNew(mods(), WhatsNew.readSeen(ModFeatures.MENU.whatsNewSeen()), Updates.releaseBuild());
		this.tabs = model.tabs();
		// opened by the player only (Home, its dot), always on Lumio; from now on everything counts as seen, so the dot goes, while an add-on's
		// tab keeps its dot for this look
		pickTab(0);
		ModFeatures.MENU.whatsNewSeen(WhatsNew.writeSeen(model.allSeen()));
		unseenFor = null;
	}

	/** The saved seen versions the answer of {@link #unseen} was worked out for. */
	private static String unseenFor;
	private static boolean unseen;

	/**
	 * @return whether a mod has a version the player has not seen in What is new: the quiet dot on Home and its button. The window never opens
	 *         by itself; the player opens it.
	 */
	public static boolean unseen() {
		String saved = ModFeatures.MENU.whatsNewSeen();
		if (!saved.equals(unseenFor)) {
			unseenFor = saved;
			unseen = new WhatsNew(mods(), WhatsNew.readSeen(saved), Updates.releaseBuild()).changedTab() != null;
		}
		return unseen;
	}

	/** On a first start (the setup opens) nothing is new: every mod counts as seen at its newest version. */
	public static void firstStart() {
		WhatsNew model = new WhatsNew(mods(), WhatsNew.readSeen(ModFeatures.MENU.whatsNewSeen()), Updates.releaseBuild());
		ModFeatures.MENU.whatsNewSeen(WhatsNew.writeSeen(model.allSeen()));
	}

	/** @return Lumio's changelog and those of the add-ons that ship a readable one; a missing or broken file leaves its tab out */
	private static List<WhatsNew.Mod> mods() {
		if (mods != null) {
			return mods;
		}
		List<WhatsNew.Mod> found = new ArrayList<>();
		read(Lumio.MOD_ID, "Lumio", found);
		for (AddonInfo addon : Addons.loaded()) {
			// the tab by the add-on's own name, without "Lumio: " before it
			read(addon.id(), addon.name().startsWith(ADDON_PREFIX) ? addon.name().substring(ADDON_PREFIX.length()) : addon.name(), found);
		}
		mods = List.copyOf(found);
		return mods;
	}

	private static void read(String modId, String name, List<WhatsNew.Mod> into) {
		FabricLoader.getInstance().getModContainer(modId).flatMap(mod -> mod.findPath(Changelog.path(modId))).ifPresent(path -> {
			try (InputStream in = Files.newInputStream(path)) {
				into.add(new WhatsNew.Mod(modId, name, Changelog.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))));
			} catch (Exception e) {
				Logs.warn("The changelog of {} could not be read; its tab is left out", modId, e);
			}
		});
	}

	private void pickTab(int index) {
		tab = tabs.isEmpty() ? 0 : Mth.clamp(index, 0, tabs.size() - 1);
		items = tabs.isEmpty() ? List.of() : model.items(tabs.get(tab));
		picked = 0;
		listScroll = listTarget = 0f;
		detailScroll = detailTarget = 0f;
	}

	private int boxWidth() {
		return Math.min(WIDTH, width - 2 * EDGE);
	}

	private int boxHeight() {
		return Math.min(HEIGHT, height - 2 * EDGE);
	}

	private int left() {
		return (width - boxWidth()) / 2;
	}

	private int top() {
		return (height - boxHeight()) / 2;
	}

	private int listLeft() {
		return left() + PAD;
	}

	private int bodyTop() {
		return top() + HEADER;
	}

	private int bodyBottom() {
		return top() + boxHeight() - FOOTER;
	}

	private int detailLeft() {
		return listLeft() + LIST_WIDTH + PAD;
	}

	private int detailRight() {
		return left() + boxWidth() - PAD;
	}

	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			UiSounds.play(UiSounds.Cue.CLOSE);
			}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), dt, true);
		float grown = open.follow(ui, 1f);
		float gone = close.follow(ui, closing ? 1f : 0f);
		if (closing && gone > 0.97f) {
			minecraft.gui.setScreen(parent);
			return;
		}
		float fade = Mth.clamp(Math.min(grown, 1f - gone), 0f, 1f);
		float ease = 1f - (float) Math.exp(-dt * SCROLL_RATE);
		listScroll += (listTarget - listScroll) * ease;
		detailScroll += (detailTarget - detailScroll) * ease;

		int x = left();
		int y = top();
		int w = boxWidth();
		int h = boxHeight();
		var pose = graphics.pose();
		pose.pushMatrix();
		float scale = 0.92f + 0.08f * Math.min(grown, 1.04f) - 0.06f * gone;
		pose.translate(x + w / 2f, y + h / 2f);
		pose.scale(scale, scale);
		pose.translate(-(x + w / 2f), -(y + h / 2f));
		if (HudStyle.palette().glass()) {
			Glass.body(ui, x, y, w, h, Radius.XXL, fade, true, UiGlow.Target.MENU);
			Glass.edges(ui, x, y, w, h, Radius.XXL, fade, true, UiGlow.Target.MENU);
		} else {
			HudStyle.panel(ui, x, y, w, h, Radius.XXL, HudStyle.opacity() * fade, UiGlow.Target.MENU, fade);
		}
		Ui.setAlpha(fade);
		ui.textAtScaledBold("What is new", x + PAD, y + 18, TextScale.TITLE, HudStyle.text());
		drawTabs(ui, mouseX, mouseY);
		if (items.isEmpty()) {
			ui.text("No changes to show.", listLeft(), bodyTop() + 8, HudStyle.secondary());
		} else {
			drawList(ui, graphics, mouseX, mouseY);
			ui.fill(detailLeft() - PAD / 2, bodyTop(), 1, bodyBottom() - bodyTop() - 8, Ui.withOpacity(HudStyle.secondary(), 0.2f));
			drawDetail(ui, graphics, mouseX, mouseY);
		}
		drawFooter(ui, mouseX, mouseY);
		Ui.setAlpha(1f);
		pose.popMatrix();
	}

	/** The tabs at the top right, as a segmented control; a dot on a tab with something new. */
	private void drawTabs(Ui ui, int mouseX, int mouseY) {
		int count = tabs.size();
		if (count < 2) {
			// Lumio alone: no tab row, just its list
			tabLefts = new int[0];
			tabWidths = new int[0];
			return;
		}
		if (tabLefts.length != count) {
			tabLefts = new int[count];
			tabWidths = new int[count];
		}
		int total = 0;
		for (int i = 0; i < count; i++) {
			tabWidths[i] = ui.width(tabs.get(i)) + 2 * TAB_PAD + (model.hasNew(tabs.get(i)) ? DOT + 4 : 0);
			total += tabWidths[i];
		}
		int right = left() + boxWidth() - PAD;
		int at = right - total - 4;
		int tabTop = top() + 16;
		ui.rounded(at - 2, tabTop - 2, total + 8, TAB_HEIGHT + 4, Radius.SM, Ui.withOpacity(HudStyle.secondary(), 0.12f));
		at += 2;
		for (int i = 0; i < count; i++) {
			tabLefts[i] = at;
			boolean on = i == tab;
			boolean over = !on && mouseX >= at && mouseX < at + tabWidths[i] && mouseY >= tabTop && mouseY < tabTop + TAB_HEIGHT;
			if (on || over) {
				ui.rounded(at, tabTop, tabWidths[i], TAB_HEIGHT, Radius.XS, Ui.withOpacity(HudStyle.text(), on ? 0.16f : 0.07f));
			}
			int textColor = on ? HudStyle.text() : HudStyle.secondary();
			ui.text(tabs.get(i), at + TAB_PAD, tabTop + (TAB_HEIGHT - 8) / 2, textColor);
			if (model.hasNew(tabs.get(i))) {
				int dotX = at + TAB_PAD + ui.width(tabs.get(i)) + 4;
				ui.rounded(dotX, tabTop + TAB_HEIGHT / 2 - DOT / 2, DOT, DOT, DOT / 2f, SystemColors.GREEN);
			}
			at += tabWidths[i];
		}
	}

	/** The versions on the left: newest first, with their date (and their mod under All), a "New" chip on those not seen. */
	private void drawList(Ui ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = listLeft();
		int top = bodyTop();
		int bottom = bodyBottom() - 8;
		float most = Math.max(0, items.size() * LIST_ROW - (bottom - top));
		listTarget = Mth.clamp(listTarget, 0f, most);
		graphics.enableScissor(x - 2, top, x + LIST_WIDTH + 2, bottom);
		for (int i = 0; i < items.size(); i++) {
			int rowTop = top + i * LIST_ROW - Math.round(listScroll);
			if (rowTop + LIST_ROW < top || rowTop > bottom) {
				continue;
			}
			WhatsNew.Item item = items.get(i);
			boolean on = i == picked;
			boolean over = !on && mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= Math.max(top, rowTop) && mouseY < Math.min(bottom, rowTop + LIST_ROW - 2);
			if (on || over) {
				ui.rounded(x, rowTop, LIST_WIDTH, LIST_ROW - 2, Radius.SM, Ui.withOpacity(on ? ui.accent : HudStyle.text(), on ? 0.28f : 0.06f));
			}
			int chipRoom = item.isNew() ? 34 : 0;
			String version = fitBold(ui, WhatsNew.versionShown(item.release().version()), LIST_WIDTH - 20 - chipRoom);
			ui.textAtScaledBold(version, x + 10, rowTop + 6, TextScale.BODY, HudStyle.text());
			if (item.isNew()) {
				int chipX = x + 10 + Math.round(ui.boldWidthScaled(version, TextScale.BODY)) + 6;
				newChip(ui, chipX, rowTop + 10);
			}
			String under = item.release().inDevelopment() ? "Not released yet" : WhatsNew.dateShown(item.release().date());
			ui.textAtScaled(ui.fit(under, LIST_WIDTH - 20), x + 10, rowTop + 19, TextScale.SMALL, HudStyle.secondary());
		}
		graphics.disableScissor();
	}

	/** @return a bold text cut with an ellipsis to the width, measured as bold (the Minecraft font's bold is wider) */
	private static String fitBold(Ui ui, String text, int room) {
		if (ui.boldWidthScaled(text, TextScale.BODY) <= room) {
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && ui.boldWidthScaled(cut + "…", TextScale.BODY) > room) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.strip() + "…";
	}

	/** A small box saying "New", in the accent. */
	private static void newChip(Ui ui, int x, int middleY) {
		float textWidth = ui.boldWidthScaled("NEW", TextScale.SMALL);
		int width = Math.round(textWidth) + 8;
		ui.rounded(x, middleY - 6, width, 12, Radius.XS, ui.accent);
		ui.textAtScaledBold("NEW", x + 4, middleY - 3.5f, TextScale.SMALL, 0xFFFFFFFF);
	}

	/** The picked version on the right: its title, then Added, Changed and Fixed, each entry on one or two lines, with "Open ›" for a link. */
	private void drawDetail(Ui ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		WhatsNew.Item item = items.get(Mth.clamp(picked, 0, items.size() - 1));
		Release release = item.release();
		int x = detailLeft();
		int right = detailRight();
		int top = bodyTop();
		int bottom = bodyBottom() - 8;
		openBoxes.clear();
		openTargets.clear();
		graphics.enableScissor(x - 2, top, right + 4, bottom);
		int y = top - Math.round(detailScroll);
		String title = WhatsNew.versionShown(release.version());
		ui.textAtScaledBold(title, x, y + 2, TextScale.HEADING, HudStyle.text());
		String about = item.mod().name() + (release.inDevelopment() ? "" : " · " + WhatsNew.dateShown(release.date()));
		ui.text(about, x + Math.round(ui.boldWidthScaled(title, TextScale.HEADING)) + 8, y + 5, HudStyle.secondary());
		y += 28;
		int[] colors = {SystemColors.GREEN, SystemColors.BLUE, SystemColors.ORANGE};
		for (Kind kind : Kind.values()) {
			List<Entry> entries = release.of(kind);
			if (entries.isEmpty()) {
				continue;
			}
			int color = colors[kind.ordinal()];
			ui.rounded(x, y + 3, 6, 6, Radius.XXS, color);
			ui.sectionLabel(GROUP_LABELS[kind.ordinal()] + "  " + entries.size(), x + 12, y + 1, color);
			y += GROUP_HEAD - 6;
			for (Entry entry : entries) {
				Feature target = entry.link() == null ? null : featureOf(entry.link());
				int textRoom = right - x - (target != null ? OPEN_WIDTH + 8 : 0);
				List<String> lines = ui.wrap(entry.text(), textRoom, ENTRY_LINES);
				int rowHeight = lines.size() * ENTRY_LINE + 2 * ENTRY_PAD - 4;
				int lineY = y + ENTRY_PAD - 2;
				for (String line : lines) {
					ui.text(line, x, lineY, HudStyle.text());
					lineY += ENTRY_LINE;
				}
				if (target != null) {
					int boxX = right - OPEN_WIDTH;
					int boxY = y + rowHeight / 2 - 8;
					boolean over = mouseX >= boxX && mouseX < right && mouseY >= boxY && mouseY < boxY + 16 && mouseY >= top && mouseY < bottom;
					ui.rounded(boxX, boxY, OPEN_WIDTH, 16, Radius.XS, Ui.withOpacity(HudStyle.text(), over ? 0.16f : 0.08f));
					ui.textCentered("Open ›", boxX + OPEN_WIDTH / 2, boxY + 8, HudStyle.text());
					openBoxes.add(new int[] {boxX, boxY, OPEN_WIDTH, 16});
					openTargets.add(target);
				}
				y += rowHeight;
				ui.fill(x, y - 1, right - x, 1, Ui.withOpacity(HudStyle.secondary(), 0.14f));
			}
			y += 12;
		}
		graphics.disableScissor();
		int length = y + Math.round(detailScroll) - top;
		detailTarget = Mth.clamp(detailTarget, 0f, Math.max(0, length - (bottom - top)));
	}

	/** @return the feature a link names, or null when there is none of that id (it was renamed or its add-on is off) */
	private static Feature featureOf(String id) {
		for (Feature feature : FeatureManager.features()) {
			if (feature.id().equals(id)) {
				return feature;
			}
		}
		return null;
	}

	/** The footer: a note, "Full changelog" (the release's page, through the game's link prompt) and Done. */
	private void drawFooter(Ui ui, int mouseX, int mouseY) {
		int x = left();
		int w = boxWidth();
		int footerTop = top() + boxHeight() - FOOTER;
		ui.fill(x + 1, footerTop, w - 2, 1, Ui.withOpacity(HudStyle.secondary(), 0.2f));
		int buttonY = footerTop + (FOOTER - BUTTON_HEIGHT) / 2;
		int doneX = x + w - PAD - DONE_WIDTH;
		int fullX = doneX - 8 - FULL_WIDTH;
		boolean hasPage = releasePage() != null;
		int noteRoom = (hasPage ? fullX : doneX) - (x + PAD) - 12;
		ui.textAtScaled(ui.fit(NOTE, Math.round(noteRoom / TextScale.SMALL)), x + PAD, buttonY + 8, TextScale.SMALL, HudStyle.secondary());
		if (hasPage) {
			boolean over = inside(mouseX, mouseY, fullX, buttonY, FULL_WIDTH, BUTTON_HEIGHT);
			Glass.button(ui, fullX, buttonY, FULL_WIDTH, BUTTON_HEIGHT, Radius.MD, over ? 1f : 0f, 1f);
			ui.textCentered("Full changelog", fullX + FULL_WIDTH / 2, buttonY + BUTTON_HEIGHT / 2, HudStyle.text());
		}
		boolean overDone = inside(mouseX, mouseY, doneX, buttonY, DONE_WIDTH, BUTTON_HEIGHT);
		ui.rounded(doneX, buttonY, DONE_WIDTH, BUTTON_HEIGHT, Radius.MD, overDone ? Ui.mix(ui.accent, 0xFFFFFFFF, 0.12f) : ui.accent);
		ui.textCentered("Done", doneX + DONE_WIDTH / 2, buttonY + BUTTON_HEIGHT / 2, 0xFFFFFFFF);
	}

	/** @return the page of the picked release on GitHub, or null (in development, or a mod without a known page) */
	private String releasePage() {
		if (items.isEmpty()) {
			return null;
		}
		WhatsNew.Item item = items.get(Mth.clamp(picked, 0, items.size() - 1));
		return item.release().inDevelopment() ? null : Updates.releasePage(item.mod().id(), item.release().version());
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (ScreenInput.button(event) != 0 || closing) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		int tabTop = top() + 16;
		for (int i = 0; i < tabLefts.length; i++) {
			if (inside(mx, my, tabLefts[i], tabTop, tabWidths[i], TAB_HEIGHT)) {
				if (i != tab) {
					pickTab(i);
					UiSounds.click();
				}
				return true;
			}
		}
		if (inside(mx, my, listLeft(), bodyTop(), LIST_WIDTH, bodyBottom() - 8 - bodyTop())) {
			int row = (int) ((my - bodyTop() + listScroll) / LIST_ROW);
			if (row >= 0 && row < items.size() && row != picked) {
				picked = row;
				detailScroll = detailTarget = 0f;
				UiSounds.click();
			}
			return true;
		}
		for (int i = 0; i < openBoxes.size(); i++) {
			int[] box = openBoxes.get(i);
			if (inside(mx, my, box[0], box[1], box[2], box[3]) && my >= bodyTop() && my < bodyBottom() - 8) {
				UiSounds.click();
				open(openTargets.get(i));
				return true;
			}
		}
		int footerTop = top() + boxHeight() - FOOTER;
		int buttonY = footerTop + (FOOTER - BUTTON_HEIGHT) / 2;
		int doneX = left() + boxWidth() - PAD - DONE_WIDTH;
		int fullX = doneX - 8 - FULL_WIDTH;
		if (inside(mx, my, doneX, buttonY, DONE_WIDTH, BUTTON_HEIGHT)) {
			UiSounds.click();
			onClose();
			return true;
		}
		String page = releasePage();
		if (page != null && inside(mx, my, fullX, buttonY, FULL_WIDTH, BUTTON_HEIGHT)) {
			UiSounds.click();
			ConfirmLinkScreen.confirmLinkNow(this, java.net.URI.create(page));
			return true;
		}
		return true;
	}

	/** Opens the page or settings of a feature an entry links to; what was seen counts as seen. */
	private void open(Feature feature) {
		onClose();
		if (feature.isListed()) {
			MacScreen.select(feature);
			minecraft.setScreenAndShow(new MacScreen());
		} else {
			MacScreen.openEdit(feature);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < detailLeft() - PAD / 2.0) {
			listTarget = Math.max(0f, listTarget - (float) scrollY * SCROLL_STEP);
		} else {
			detailTarget = Math.max(0f, detailTarget - (float) scrollY * SCROLL_STEP);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		Ui.setAlpha(1f);
	}
}
