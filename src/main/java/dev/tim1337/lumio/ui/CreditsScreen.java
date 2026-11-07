package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.Logs;

import net.fabricmc.loader.api.FabricLoader;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Credits for the projects and people the mod's design and parts are based on, in a glass window like the changelog. */
public class CreditsScreen extends Screen {
	private static final int WIDTH = 560;
	private static final int HEIGHT = 440;
	private static final int PAD = 24;

	/** A credited name and what was taken from it, as inspiration or as a part. */
	private record Credit(String name, String text) {
	}

	private record Section(String title, List<Credit> credits) {
	}

	/** The credits of the main mod itself; an add-on's own follow while it is installed (see {@link #sections}). */
	private static final List<Section> OWN = List.of(
		new Section("Made by", List.of(
			new Credit("tim1337", "The idea, the design and the testing."))),
		new Section("Parts and services", List.of(
			new Credit("Fabric", "The loader and the API."),
			new Credit("LRCLIB", "Synced lyrics."),
			new Credit("Spotify Web API", "What plays."),
			new Credit("Cider", "What plays, and its lyrics."),
			new Credit("iTunes Search API", "Song covers."),
			new Credit("Inter, Kalam and Poppins", "The fonts."),
			new Credit("GNU Unifont", "The shapes of some server symbols."))),
		new Section("Thanks", List.of(
			new Credit("Everyone who tried it", "Every screenshot and idea made it better.")))
	);
	/** The most credits read from one add-on, so a broken file cannot fill the screen. */
	private static final int MOST_ADDON_CREDITS = 100;
	private static final String SECTION_MARK = "##";
	private static final String NAME_END = "|";

	/** The sections shown: the mod's own, then each installed add-on's; made once, the first time the screen is drawn. */
	private static List<Section> sections;

	/**
	 * @return the sections shown. An add-on brings its credits in assets/&lt;add-on id&gt;/credits.txt: a line "## Section" before its credits,
	 *         and a line "Name | what was taken from it" for each; its sections are titled with the add-on's name.
	 */
	private static List<Section> sections() {
		if (sections != null) {
			return sections;
		}
		List<Section> all = new ArrayList<>(OWN);
		for (AddonInfo addon : Addons.loaded()) {
			FabricLoader.getInstance().getModContainer(addon.id()).flatMap(mod -> mod.findPath("assets/" + addon.id() + "/credits.txt"))
				.ifPresent(path -> {
					try {
						all.addAll(parse(Files.readAllLines(path, StandardCharsets.UTF_8), addon.name() + " · "));
					} catch (Exception e) {
						Logs.warn("The credits of {} could not be read", addon.id(), e);
					}
				});
		}
		sections = List.copyOf(all);
		return sections;
	}

	/** @return the sections of a credits.txt, their titles starting with the prefix; other lines are left out */
	private static List<Section> parse(List<String> lines, String prefix) {
		List<Section> found = new ArrayList<>();
		List<Credit> current = null;
		int credits = 0;
		for (String raw : lines) {
			String line = raw.strip();
			if (line.startsWith(SECTION_MARK)) {
				current = new ArrayList<>();
				found.add(new Section(prefix + line.substring(SECTION_MARK.length()).strip(), current));
			} else if (current != null && line.contains(NAME_END) && credits < MOST_ADDON_CREDITS) {
				int end = line.indexOf(NAME_END);
				current.add(new Credit(line.substring(0, end).strip(), line.substring(end + 1).strip()));
				credits++;
			}
		}
		found.removeIf(section -> section.credits().isEmpty());
		return found;
	}

	private final Screen parent;
	private final Spring open = Spring.bouncy(Spring.GENTLE);
	private final Spring close = Spring.smooth(Spring.DEFAULT);
	private boolean closing;
	private double scroll;
	private double scrollTarget;
	private int contentHeight;
	private long last = System.nanoTime();

	public CreditsScreen(Screen parent) {
		super(Component.literal("Credits"));
		this.parent = parent;
	}

	private int boxWidth() {
		return Math.min(WIDTH, width - 24);
	}

	private int boxHeight() {
		return Math.min(HEIGHT, height - 24);
	}

	private int left() {
		return (width - boxWidth()) / 2;
	}

	private int top() {
		return (height - boxHeight()) / 2;
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

		ui.textAtScaled("Credits", x + PAD, y + 18, TextScale.TITLE, HudStyle.text());
		ui.text("Who made it, and what it uses", x + PAD, y + 46, HudStyle.secondary());

		int top = y + 68;
		int bottom = y + h - 52;
		int textWidth = w - PAD * 2 - 26;
		graphics.enableScissor(x + 2, top, x + w - 2, bottom);
		int cy = top - (int) scroll;
		for (Section section : sections()) {
			ui.textAtScaled(section.title(), x + PAD, cy, TextScale.HEADING, HudStyle.text());
			cy += 26;
			for (Credit credit : section.credits()) {
				ui.circle(x + PAD + 4, cy + 5, 3, ui.accent);
				ui.text(credit.name(), x + PAD + 16, cy, HudStyle.text());
				cy += 14;
				for (String part : wrap(ui, credit.text(), textWidth)) {
					ui.text(part, x + PAD + 16, cy, HudStyle.secondary());
					cy += 13;
				}
				cy += 8;
			}
			cy += 12;
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
