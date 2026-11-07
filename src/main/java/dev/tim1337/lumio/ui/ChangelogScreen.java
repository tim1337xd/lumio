package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.util.ModVersion;

import net.fabricmc.loader.api.FabricLoader;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The changelog in a glass window over the screen it was opened from. The text comes from the bundled {@code changelog.txt}: a line starting with
 * ## begins a section, either "In development" (the changes since the last release) or a released version with its date ("## 1.2.0 | 2026-11-05"),
 * and the following lines list the changes, prefixed with + for new, * for changed and ! for fixed. The newest section is open, older ones are
 * collapsed under their version and date and open with a click; the changes are grouped under small labels instead of their symbols.
 */
public class ChangelogScreen extends Screen {
	private static final int WIDTH = 560;
	private static final int HEIGHT = 440;
	private static final int PAD = 24;
	private static final int SECTION_HEAD = 26;
	private static final int GROUP_HEAD = 16;
	private static final int LINE_HEIGHT = 14;
	private static final int LINE_GAP = 4;
	private static final int GROUP_GAP = 6;
	private static final int SECTION_GAP = 16;
	private static final int CHEVRON = 9;
	/** Where the changes start, right of the chevron of their section. */
	private static final int TEXT_INDENT = CHEVRON + 8;
	/** The groups of a section in their order: the symbol in changelog.txt and the label shown. */
	private static final String GROUP_SYMBOLS = "+*!";
	private static final String[] GROUP_LABELS = {"NEW", "CHANGED", "FIXED"};

	/** A section: its title (a version, or "In development"), the date after it (none while in development), and its changes per group. */
	private record Entry(String title, String note, List<List<String>> groups) {
	}

	private static List<Entry> entries;
	private final Screen parent;
	private final Spring open = Spring.bouncy(Spring.GENTLE);
	private final Spring close = Spring.smooth(Spring.DEFAULT);
	private boolean closing;
	private double scroll;
	private double scrollTarget;
	private int contentHeight;
	private long last = System.nanoTime();
	/** Wrapped lines of every section, per group, for the width they were wrapped at. */
	private List<List<List<List<String>>>> wrapped;
	private int wrappedWidth = -1;
	/** Which sections are open: the newest one from the start. */
	private final Set<Integer> expanded = new HashSet<>(Set.of(0));
	/** Where the section headers were drawn in the last frame, for clicks; -1 for one that was not drawn. */
	private int[] headerTops = new int[0];

	public ChangelogScreen(Screen parent) {
		super(Component.literal("What is new"));
		this.parent = parent;
	}

	private static List<Entry> load() {
		if (entries != null) {
			return entries;
		}
		List<Entry> found = new ArrayList<>();
		try (InputStream in = ChangelogScreen.class.getResourceAsStream("/assets/" + Lumio.MOD_ID + "/changelog.txt")) {
			if (in != null) {
				parse(in, "", found);
			}
		} catch (Exception e) {
			Logs.warn("The changelog could not be read", e);
		}
		// the add-ons' own changelogs (assets/<add-on id>/changelog.txt) follow, each section titled with the add-on's name
		for (AddonInfo addon : Addons.loaded()) {
			FabricLoader.getInstance().getModContainer(addon.id()).flatMap(mod -> mod.findPath("assets/" + addon.id() + "/changelog.txt"))
				.ifPresent(path -> {
					try (InputStream in = Files.newInputStream(path)) {
						parse(in, addon.name() + " · ", found);
					} catch (Exception e) {
						Logs.warn("The changelog of {} could not be read", addon.id(), e);
					}
				});
		}
		entries = found;
		return found;
	}

	/** Reads the sections of a changelog.txt into the list; their titles start with the prefix. */
	private static void parse(InputStream in, String prefix, List<Entry> into) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		Entry current = null;
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.strip();
			if (line.startsWith("##")) {
				String[] parts = line.substring(2).strip().split("\\|", 2);
				String title = prefix + parts[0].strip();
				String note = parts.length > 1 ? parts[1].strip() : "";
				List<List<String>> groups = new ArrayList<>();
				for (int i = 0; i < GROUP_LABELS.length; i++) {
					groups.add(new ArrayList<>());
				}
				current = new Entry(title, note, groups);
				into.add(current);
			} else if (current != null && line.length() > 2 && GROUP_SYMBOLS.indexOf(line.charAt(0)) >= 0) {
				current.groups().get(GROUP_SYMBOLS.indexOf(line.charAt(0))).add(line.substring(1).strip());
			}
		}
	}

	private int left() {
		return (width - Math.min(WIDTH, width - 24)) / 2;
	}

	private int top() {
		return (height - Math.min(HEIGHT, height - 24)) / 2;
	}

	private int boxWidth() {
		return Math.min(WIDTH, width - 24);
	}

	private int boxHeight() {
		return Math.min(HEIGHT, height - 24);
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

	/** @return the text wrapped into lines no wider than the width */
	private static List<String> wrap(Ui ui, String text, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && ui.width(candidate) > width) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
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
		scroll += (scrollTarget - scroll) * (1f - Math.exp(-dt * 14f));

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

		ui.textAtScaled("What is new", x + PAD, y + 18, TextScale.TITLE, HudStyle.text());
		ui.text("Version " + ModVersion.display(), x + PAD, y + 46, HudStyle.secondary());

		int top = y + 68;
		int bottom = y + h - 52;
		int textWidth = w - PAD * 2 - 26 - TEXT_INDENT;
		graphics.enableScissor(x + 2, top, x + w - 2, bottom);
		int cy = top - (int) scroll;
		List<Entry> all = load();
		// Wrap once per width instead of every frame, and only draw what is visible: wrapping and drawing the whole changelog every frame (a few
		// thousand words measured one by one) made the screen slow to open and scroll.
		if (wrapped == null || wrappedWidth != textWidth) {
			wrapped = new ArrayList<>();
			wrappedWidth = textWidth;
			for (Entry entry : all) {
				List<List<List<String>>> groups = new ArrayList<>();
				for (List<String> group : entry.groups()) {
					List<List<String>> lines = new ArrayList<>();
					for (String line : group) {
						lines.add(wrap(ui, line, textWidth));
					}
					groups.add(lines);
				}
				wrapped.add(groups);
			}
		}
		if (headerTops.length != all.size()) {
			headerTops = new int[all.size()];
		}
		int[] groupColors = {SystemColors.GREEN, ui.accent, SystemColors.ORANGE};
		int textX = x + PAD + TEXT_INDENT;
		for (int e = 0; e < all.size(); e++) {
			Entry entry = all.get(e);
			boolean opened = expanded.contains(e);
			headerTops[e] = cy + SECTION_HEAD >= top && cy <= bottom ? cy : -1;
			if (headerTops[e] >= 0) {
				// a chevron that points down while the section is open, then the version (or "In development") and its date
				pose.pushMatrix();
				pose.translate(x + PAD + CHEVRON / 2f, cy + 8f);
				if (!opened) {
					pose.rotate((float) (-Math.PI / 2.0));
				}
				ui.icon("chevron", -CHEVRON / 2, -CHEVRON / 2, CHEVRON, HudStyle.secondary());
				pose.popMatrix();
				ui.textAtScaled(entry.title(), textX, cy, TextScale.HEADING, HudStyle.text());
				if (!entry.note().isEmpty()) {
					ui.text("·  " + entry.note(), textX + Math.round(ui.widthScaled(entry.title(), TextScale.HEADING)) + 8, cy + 3, HudStyle.secondary());
				}
			}
			cy += SECTION_HEAD;
			if (opened) {
				for (int g = 0; g < GROUP_LABELS.length; g++) {
					List<List<String>> lines = wrapped.get(e).get(g);
					if (lines.isEmpty()) {
						continue;
					}
					if (cy + GROUP_HEAD >= top && cy <= bottom) {
						ui.sectionLabel(GROUP_LABELS[g], textX, cy, groupColors[g]);
					}
					cy += GROUP_HEAD;
					for (List<String> parts : lines) {
						int height = parts.size() * LINE_HEIGHT + LINE_GAP;
						if (cy + height >= top && cy <= bottom) {
							int py = cy;
							for (String part : parts) {
								ui.text(part, textX, py, HudStyle.text());
								py += LINE_HEIGHT;
							}
						}
						cy += height;
					}
					cy += GROUP_GAP;
				}
			}
			cy += SECTION_GAP;
		}
		contentHeight = cy + (int) scroll - top;
		graphics.disableScissor();
		int max = Math.max(0, contentHeight - (bottom - top));
		scrollTarget = Mth.clamp(scrollTarget, 0, max);
		if (max > 0) {
			int bar = Math.max(24, (bottom - top) * (bottom - top) / contentHeight);
			int barY = top + (int) ((bottom - top - bar) * (scroll / max));
			ui.rounded(x + w - 12, barY, 4, bar, Radius.XXS, Ui.withOpacity(HudStyle.secondary(), 0.7f));
		}

		// close button
		int bw = 96;
		int bx = x + w - PAD - bw;
		int by = y + h - 42;
		boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 28;
		Glass.button(ui, bx, by, bw, 28, Radius.MD, over ? 1f : 0f, 1f);
		ui.textCentered("Done", bx + bw / 2, by + 14, HudStyle.text());
		Ui.setAlpha(1f);
		pose.popMatrix();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// a section's header opens or closes it
		if (ScreenInput.button(event) == 0 && event.x() >= left() + PAD && event.x() < left() + boxWidth() - PAD) {
			for (int e = 0; e < headerTops.length; e++) {
				if (headerTops[e] >= 0 && event.y() >= headerTops[e] - 4 && event.y() < headerTops[e] + SECTION_HEAD - 6) {
					if (!expanded.remove(e)) {
						expanded.add(e);
					}
					UiSounds.click();
					return true;
				}
			}
		}
		int bw = 96;
		int bx = left() + boxWidth() - PAD - bw;
		int by = top() + boxHeight() - 42;
		if (ScreenInput.button(event) == 0 && event.x() >= bx && event.x() < bx + bw && event.y() >= by && event.y() < by + 28) {
			UiSounds.click();
			onClose();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget = Math.max(0, scrollTarget - scrollY * 48);
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
