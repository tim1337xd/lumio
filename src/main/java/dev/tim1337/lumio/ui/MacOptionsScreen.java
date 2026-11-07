package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The vanilla options in the style of phone settings: pages on the left, grouped on cards, each with a chevron after its name, and the page's
 * settings on the right on their own cards, with a search field above them. It edits the same options as the vanilla screens, and anything it
 * does not cover is one click away in those screens.
 */
public class MacOptionsScreen extends Screen {
	private static final int MARGIN = 28;
	private static final int SIDE_WIDTH = 236;
	private static final int ITEM_HEIGHT = 34;
	private static final int GROUP_GAP = 16;
	private static final int PAGE_TOP = 96;
	private static final int DONE_WIDTH = 84;
	/** The game keeps an option's name and value text in private fields; they are looked up once. */
	private static final Field CAPTION_FIELD = optionField("caption");
	private static final Field TO_STRING_FIELD = optionField("toString");

	private enum Kind {
		SLIDER, TOGGLE, CYCLE, LINK
	}

	private static final class Row {
		final Kind kind;
		final String label;
		final OptionInstance<?> option;
		final Supplier<Screen> link;
		AbstractWidget widget;
		final Spring knob = Spring.smooth(Spring.SNAPPY);
		boolean knobSet;
		// position from the last draw
		int x;
		int y;
		int width;
		int height;
		/** The option's name and its value's text, made once and again only when the value changes, not every frame. */
		String caption;
		Object shownValue;
		String valueText;

		Row(Kind kind, String label, OptionInstance<?> option, Supplier<Screen> link) {
			this.kind = kind;
			this.label = label;
			this.option = option;
			this.link = link;
		}

		int rowHeight() {
			return kind == Kind.SLIDER ? 46 : 36;
		}
	}

	private record Section(String title, List<Row> rows) {
	}

	private static final class Page {
		final String name;
		final String description;
		final String icon;
		final int rgb;
		final int group;
		final List<Section> sections = new ArrayList<>();

		Page(String name, String description, String icon, int rgb, int group) {
			this.name = name;
			this.description = description;
			this.icon = icon;
			this.rgb = rgb;
			this.group = group;
		}
	}

	private final ScreenFit fitView = new ScreenFit();
	private final Screen parent;
	private final Options options;
	private final List<Page> pages = new ArrayList<>();
	private int selected;
	private final Spring highlight = Spring.smooth(Spring.DEFAULT);
	private final Spring open = Spring.smooth(Spring.DEFAULT);
	private final Spring close = Spring.smooth(Spring.DEFAULT);
	private boolean closing;
	private boolean highlightSet;
	private final float[] itemHover = new float[8];
	private double scroll;
	private double scrollTarget;
	private int contentHeight;
	private long last = System.nanoTime();

	private String query = "";
	private boolean searching;
	private Row dragging;
	private Row pressedWidget;
	private final List<Row> placed = new ArrayList<>();
	private int doneX;
	private int doneY;

	public MacOptionsScreen(Screen parent, Options options) {
		super(Component.translatable("options.title"));
		this.parent = parent;
		this.options = options;
		build();
	}

	/** Opens a page directly, for the test that screenshots every page. */
	public MacOptionsScreen page(int index) {
		selected = Mth.clamp(index, 0, pages.size() - 1);
		return this;
	}

	// ------------------------------------------------------------------------------------------------------------- content

	/** @return a row matching the option's type: a slider for ranges, a switch for booleans, and a cycling row for other enumerated values */
	@SuppressWarnings("rawtypes")
	private Row auto(OptionInstance<?> option) {
		Kind kind = option.get() instanceof Boolean ? Kind.TOGGLE : option.values() instanceof OptionInstance.SliderableValueSet ? Kind.SLIDER : Kind.CYCLE;
		return new Row(kind, "", option, null);
	}

	private Row slider(OptionInstance<?> option, String label) {
		return auto(option);
	}

	private Row toggle(OptionInstance<Boolean> option, String label) {
		return auto(option);
	}

	private Row widget(OptionInstance<?> option) {
		return auto(option);
	}

	/** @return a private field of the game's options, made accessible once, or null if it cannot be read */
	private static Field optionField(String name) {
		try {
			Field field = OptionInstance.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** @return the option's name as the game displays it */
	private static String captionOf(OptionInstance<?> option) {
		try {
			if (CAPTION_FIELD != null) {
				return ((Component) CAPTION_FIELD.get(option)).getString();
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// falls back to the option's own text below
		}
		String text = option.toString();
		int colon = text.indexOf(":");
		return colon > 0 ? text.substring(0, colon) : text;
	}

	/** @return the row's option name, made once per row */
	private static String captionOf(Row row) {
		if (row.caption == null) {
			row.caption = captionOf(row.option);
		}
		return row.caption;
	}

	/** @return the option's current value as the game displays it: "5 Chunks", "ON", "100%"; made again only when the value changes */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String valueTextOf(Row row) {
		Object value = row.option.get();
		if (row.valueText != null && Objects.equals(value, row.shownValue)) {
			return row.valueText;
		}
		String text = String.valueOf(value);
		if (TO_STRING_FIELD != null) {
			try {
				String full = ((Component) ((Function) TO_STRING_FIELD.get(row.option)).apply(value)).getString();
				String caption = captionOf(row);
				text = full.startsWith(caption) ? withoutLeadingSeparator(full.substring(caption.length())) : full;
			} catch (ReflectiveOperationException | RuntimeException e) {
				// keeps the plain value
			}
		}
		row.shownValue = value;
		row.valueText = text;
		return text;
	}

	/** @return the text without the colons and white space it starts with ("Render Distance: 5 Chunks" leaves ": 5 Chunks") */
	private static String withoutLeadingSeparator(String text) {
		int start = 0;
		while (start < text.length()) {
			char c = text.charAt(start);
			// a colon, a space, or one of the control white spaces from tab to carriage return
			if (c != ':' && c != ' ' && (c < '\t' || c > '\r')) {
				break;
			}
			start++;
		}
		return text.substring(start);
	}

	private Row link(String label, Supplier<Screen> screen) {
		return new Row(Kind.LINK, label, null, screen);
	}

	/** @return a factory for Sodium's video settings screen (or the one replacing it, such as Reese's Sodium Options), or null without Sodium */
	private Supplier<Screen> sodiumVideo() {
		try {
			Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen");
			Method create = type.getMethod("createScreen", Screen.class);
			return () -> {
				try {
					return (Screen) create.invoke(null, this);
				} catch (ReflectiveOperationException | RuntimeException e) {
					return new OptionsScreen(this, options, false);
				}
			};
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	private static String translated(String key) {
		return Component.translatable(key).getString();
	}

	private void build() {
		Minecraft client = Minecraft.getInstance();

		Page video = new Page("Video", "Picture, speed and details", "eye", SystemColors.BLUE_RGB, 0);
		video.sections.add(new Section("Picture", List.of(widget(options.graphicsPreset()), widget(options.renderDistance()), widget(options.simulationDistance()),
			widget(options.framerateLimit()), toggle(options.enableVsync(), translated("options.vsync")), toggle(options.fullscreen(), translated("options.fullscreen")),
			widget(options.guiScale()), widget(options.fov()), widget(options.gamma()))));
		video.sections.add(new Section("Details", List.of(widget(options.cloudStatus()), widget(options.particles()), toggle(options.ambientOcclusion(), translated("options.ao")),
			toggle(options.entityShadows(), translated("options.entityShadows")), toggle(options.vignette(), translated("options.vignette")), widget(options.biomeBlendRadius()),
			toggle(options.bobView(), translated("options.viewBobbing")))));
		// Sodium replaces the video settings screen and the vanilla button opens Sodium's screen; do the same here
		Supplier<Screen> sodium = sodiumVideo();
		if (sodium != null) {
			video.sections.add(0, new Section("Sodium", List.of(link("Video settings of Sodium", sodium))));
		}
		pages.add(video);

		Page sound = new Page("Sound", "Volume and device", "note", SystemColors.PINK_RGB, 0);
		List<Row> master = new ArrayList<>();
		master.add(slider(options.getSoundSourceOptionInstance(SoundSource.MASTER), translated("soundCategory.master")));
		sound.sections.add(new Section("General", master));
		List<Row> categories = new ArrayList<>();
		for (SoundSource source : SoundSource.values()) {
			if (source != SoundSource.MASTER) {
				categories.add(slider(options.getSoundSourceOptionInstance(source), translated("soundCategory." + source.getName())));
			}
		}
		sound.sections.add(new Section("Categories", categories));
		sound.sections.add(new Section("Device", List.of(widget(options.soundDevice()), toggle(options.showSubtitles(), translated("options.showSubtitles")),
			toggle(options.directionalAudio(), translated("options.directionalAudio")), widget(options.musicFrequency()), widget(options.musicToast()))));
		pages.add(sound);

		Page controls = new Page("Controls", "Mouse, keys and movement", "move", SystemColors.GREEN_RGB, 1);
		controls.sections.add(new Section("Mouse", List.of(slider(options.sensitivity(), translated("options.sensitivity")), toggle(options.invertMouseY(), translated("options.invertMouseY")),
			toggle(options.invertMouseX(), translated("options.invertMouseX")), toggle(options.rawMouseInput(), translated("options.rawMouseInput")), toggle(options.discreteMouseScroll(), translated("options.discrete_mouse_scroll")))));
		controls.sections.add(new Section("Movement", List.of(toggle(options.toggleSprint(), translated("key.sprint") + " (toggle)"), toggle(options.toggleCrouch(), translated("key.sneak") + " (toggle)"),
			toggle(options.autoJump(), translated("options.autoJump")))));
		controls.sections.add(new Section("Keys", List.of(link("Key binds", () -> new KeyBindsScreen(this, options)))));
		pages.add(controls);

		Page chat = new Page("Chat", "How the chat looks", "quote", SystemColors.PURPLE_RGB, 1);
		chat.sections.add(new Section("Chat", List.of(widget(options.chatVisibility()), slider(options.chatOpacity(), translated("options.chat.opacity")), slider(options.chatScale(), translated("options.chat.scale")),
			slider(options.chatWidth(), translated("options.chat.width")), slider(options.chatLineSpacing(), translated("options.chat.line_spacing")),
			toggle(options.chatColors(), translated("options.chat.color")), toggle(options.chatLinks(), translated("options.chat.links")), toggle(options.autoSuggestions(), translated("options.autoSuggestCommands")))));
		pages.add(chat);

		Page access = new Page("Accessibility", "Help with seeing and hearing", "shield", SystemColors.TEAL_RGB, 1);
		access.sections.add(new Section("Display", List.of(toggle(options.highContrast(), translated("options.accessibility.high_contrast")), slider(options.screenEffectScale(), translated("options.screenEffectScale")),
			slider(options.fovEffectScale(), translated("options.fovEffectScale")), slider(options.damageTiltStrength(), translated("options.damageTiltStrength")),
			toggle(options.hideLightningFlash(), translated("options.hideLightningFlashes")), toggle(options.hideSplashTexts(), translated("options.accessibility.hide_splash_texts")))));
		access.sections.add(new Section("Hearing", List.of(widget(options.narrator()))));
		pages.add(access);

		Page more = new Page("More", "The other screens of the game", "sliders", SystemColors.GRAY_RGB, 2);
		more.sections.add(new Section("Screens of the game", List.of(link("All options of the game", () -> new OptionsScreen(this, options, false)),
			link("Key binds", () -> new KeyBindsScreen(this, options)))));
		pages.add(more);
	}

	// ------------------------------------------------------------------------------------------------------------- layout

	private int contentX() {
		return MARGIN + SIDE_WIDTH + 28;
	}

	private int contentWidth() {
		return Math.min(660, width - contentX() - MARGIN);
	}

	private int contentTop() {
		return 76;
	}

	private int contentBottom() {
		return height - 18;
	}

	private boolean gapBefore(int index) {
		return index > 0 && pages.get(index).group != pages.get(index - 1).group;
	}

	/** @return top of the sidebar item at an index; fractional indices interpolate, for the sliding highlight */
	private float itemY(float index) {
		int low = Mth.clamp((int) Math.floor(index), 0, pages.size() - 1);
		int high = Mth.clamp(low + 1, 0, pages.size() - 1);
		float lowY = PAGE_TOP;
		float highY = PAGE_TOP;
		for (int i = 0; i <= high; i++) {
			if (i > 0) {
				float step = ITEM_HEIGHT + 2 + (gapBefore(i) ? GROUP_GAP : 0);
				if (i <= low) {
					lowY += step;
				}
				highY += step;
			}
		}
		return Mth.lerp(Mth.clamp(index - low, 0f, 1f), lowY, highY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			options.save();
			UiSounds.play(UiSounds.Cue.CLOSE);
		}
	}

	// ------------------------------------------------------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		// screens smaller than the layout needs are scaled down as a whole
		fitView.begin(this, 760, 420);
		var fitPose = graphics.pose();
		fitPose.pushMatrix();
		fitPose.scale(fitView.fit, fitView.fit);
		try {
			renderFitted(graphics, Math.round(mouseX / fitView.fit), Math.round(mouseY / fitView.fit), partialTick);
		} finally {
			fitPose.popMatrix();
			Ui.setZoom(1f);
			fitView.end(this);
		}
	}

	private void renderFitted(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		Ui.setZoom(fitView.fit);
		Ui ui = new Ui(graphics, font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), dt, true);
		float grown = open.follow(ui, 1f);
		float gone = close.follow(ui, closing ? 1f : 0f);
		if (closing && gone > 0.97f) {
			minecraft.gui.setScreen(parent);
			return;
		}
		float fade = Mth.clamp(Math.min(grown, 1f - gone), 0f, 1f);
		scroll += (scrollTarget - scroll) * (1f - Math.exp(-dt * 14f));
		Palette palette = ui.palette;

		var pose = graphics.pose();
		pose.pushMatrix();
		float scale = 0.97f + 0.03f * Math.min(grown, 1.03f) - 0.03f * gone;
		pose.translate(width / 2f, height / 2f);
		pose.scale(scale, scale);
		pose.translate(-width / 2f, -height / 2f);
		Ui.setAlpha(fade);

		// darken the background slightly so the cards are readable over any panorama
		ui.rounded(0, 0, width, height, 0f, Ui.withOpacity(0xFF000000, 0.32f));

		Page page = pages.get(selected);
		ui.text("SETTINGS", MARGIN, 22, palette.textSecondary());
		ui.textAtScaled(searching() ? "Search" : page.name, MARGIN, 36, TextScale.TITLE, palette.text());
		ui.text(ui.fit(searching() ? "Every page of the options" : page.description, SIDE_WIDTH), MARGIN, 64, palette.textSecondary());

		drawSide(ui, mouseX, mouseY, fade);
		drawContent(ui, mouseX, mouseY, partialTick);

		Ui.setAlpha(1f);
		pose.popMatrix();
	}

	private boolean searching() {
		return !query.isBlank();
	}

	private void drawSide(Ui ui, int mouseX, int mouseY, float fade) {
		Palette palette = ui.palette;
		if (!highlightSet) {
			highlight.snap(selected);
			highlightSet = true;
		}
		float at = highlight.follow(ui, selected);
		// one card per group
		int start = 0;
		for (int i = 1; i <= pages.size(); i++) {
			if (i == pages.size() || gapBefore(i)) {
				int top = Math.round(itemY(start)) - 3;
				int bottom = Math.round(itemY(i - 1)) + ITEM_HEIGHT + 3;
				ui.rounded(MARGIN, top, SIDE_WIDTH, bottom - top, Radius.XL, Ui.withOpacity(0xFFFFFFFF, palette.glass() ? 0.09f : 0.06f));
				ui.ring(MARGIN, top, SIDE_WIDTH, bottom - top, Radius.XL, 1, Ui.withOpacity(0xFFFFFFFF, 0.10f));
				start = i;
			}
		}
		if (!searching()) {
			ui.rounded(MARGIN + 4, Math.round(itemY(at)), SIDE_WIDTH - 8, ITEM_HEIGHT, Radius.MD, ui.accent);
		}
		for (int i = 0; i < pages.size(); i++) {
			Page page = pages.get(i);
			int y = Math.round(itemY(i));
			boolean on = i == selected && !searching();
			boolean over = mouseX >= MARGIN && mouseX < MARGIN + SIDE_WIDTH && mouseY >= y && mouseY < y + ITEM_HEIGHT;
			itemHover[i] = ui.approach(itemHover[i], over && !on ? 1f : 0f, 8f);
			if (itemHover[i] > 0.01f) {
				ui.rounded(MARGIN + 4, y, SIDE_WIDTH - 8, ITEM_HEIGHT, Radius.MD, Ui.withOpacity(0xFFFFFFFF, 0.08f * itemHover[i]));
			}
			ui.rounded(MARGIN + 12, y + (ITEM_HEIGHT - 22) / 2, 22, 22, Radius.SM, 0xFF000000 | page.rgb);
			ui.icon(page.icon, MARGIN + 16, y + (ITEM_HEIGHT - 14) / 2, 14, 0xFFFFFFFF);
			ui.textMiddle(page.name, MARGIN + 44, y + ITEM_HEIGHT / 2, on ? 0xFFFFFFFF : palette.text());
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(MARGIN + SIDE_WIDTH - 20f, y + ITEM_HEIGHT / 2f);
			pose.rotate((float) (-Math.PI / 2.0));
			ui.icon("chevron", -5, -5, 10, on ? 0xCCFFFFFF : Ui.withOpacity(palette.textSecondary(), 0.8f));
			pose.popMatrix();
		}

		// Done button at the top right, in the same see-through style as the search field next to it
		doneX = contentX() + contentWidth() - DONE_WIDTH;
		doneY = 22;
		boolean overDone = mouseX >= doneX && mouseX < doneX + DONE_WIDTH && mouseY >= doneY && mouseY < doneY + 30;
		ui.rounded(doneX, doneY, DONE_WIDTH, 30, Radius.LG, Ui.withOpacity(0xFFFFFFFF, overDone ? 0.14f : 0.09f));
		ui.ring(doneX, doneY, DONE_WIDTH, 30, Radius.LG, 1, overDone ? ui.accent : Ui.withOpacity(0xFFFFFFFF, 0.12f));
		ui.textCentered("Done", doneX + DONE_WIDTH / 2, doneY + 15, palette.text());
	}

	private List<Section> visibleSections() {
		if (!searching()) {
			return pages.get(selected).sections;
		}
		String needle = query.toLowerCase(Locale.ROOT).trim();
		List<Section> found = new ArrayList<>();
		for (Page page : pages) {
			List<Row> rows = new ArrayList<>();
			for (Section section : page.sections) {
				for (Row row : section.rows()) {
					String name = labelOf(row);
					if (!name.isEmpty() && name.toLowerCase(Locale.ROOT).contains(needle)) {
						rows.add(row);
					}
				}
			}
			if (!rows.isEmpty()) {
				found.add(new Section(page.name, rows));
			}
		}
		return found;
	}

	private String labelOf(Row row) {
		return row.option != null ? captionOf(row) : row.label;
	}

	/** @return the vanilla widget of an option, which is never drawn: it knows how to cycle to the next value, as on a click */
	private AbstractWidget widgetOf(Row row) {
		if (row.widget == null && row.option != null) {
			row.widget = row.option.createButton(options, 0, 0, 100);
		}
		return row.widget;
	}

	private void drawContent(Ui ui, int mouseX, int mouseY, float partialTick) {
		Palette palette = ui.palette;
		int x = contentX();
		int w = contentWidth();

		// search field above the settings
		int fieldY = 22;
		int fieldW = w - DONE_WIDTH - 10;
		boolean overField = mouseX >= x && mouseX < x + fieldW && mouseY >= fieldY && mouseY < fieldY + 30;
		ui.rounded(x, fieldY, fieldW, 30, Radius.LG, Ui.withOpacity(0xFFFFFFFF, searching ? 0.14f : overField ? 0.12f : 0.09f));
		ui.ring(x, fieldY, fieldW, 30, Radius.LG, 1, searching ? ui.accent : Ui.withOpacity(0xFFFFFFFF, 0.12f));
		ui.icon("search", x + 11, fieldY + 8, 14, palette.textSecondary());
		String shown = query.isEmpty() ? "Search settings" : query + ((System.currentTimeMillis() / 500) % 2 == 0 && searching ? "|" : "");
		ui.textMiddle(ui.fit(shown, fieldW - 50), x + 32, fieldY + 15, query.isEmpty() ? palette.textSecondary() : palette.text());

		int top = contentTop();
		int bottom = contentBottom();
		placed.clear();
		ui.graphics.enableScissor(x - 4, top, x + w + 4, bottom);
		int y = top - (int) scroll + 4;
		List<Section> sections = visibleSections();
		for (Section section : sections) {
			ui.text(section.title().toUpperCase(Locale.ROOT), x + 6, y, palette.textSecondary());
			y += 16;
			int cardTop = y;
			int cardHeight = 0;
			for (Row row : section.rows()) {
				cardHeight += row.rowHeight();
			}
			ui.rounded(x, cardTop, w, cardHeight, Radius.XL, Ui.withOpacity(0xFFFFFFFF, palette.glass() ? 0.09f : 0.06f));
			ui.ring(x, cardTop, w, cardHeight, Radius.XL, 1, Ui.withOpacity(0xFFFFFFFF, 0.10f));
			int rowY = cardTop;
			for (int r = 0; r < section.rows().size(); r++) {
				Row row = section.rows().get(r);
				row.x = x;
				row.y = rowY;
				row.width = w;
				row.height = row.rowHeight();
				placed.add(row);
				if (rowY + row.height > top - 10 && rowY < bottom + 10) {
					drawRow(ui, row, mouseX, mouseY, partialTick);
				} else if (row.widget != null) {
					row.widget.setY(-1000);
				}
				if (r > 0) {
					ui.fill(x + 14, rowY, w - 28, 1, Ui.withOpacity(0xFFFFFFFF, 0.16f));
				}
				rowY += row.height;
			}
			y = cardTop + cardHeight + 20;
		}
		if (sections.isEmpty()) {
			ui.textCentered("Nothing found", x + w / 2, top + 60, palette.textSecondary());
		}
		contentHeight = y + (int) scroll - top;
		ui.graphics.disableScissor();
		int max = Math.max(0, contentHeight - (bottom - top));
		scrollTarget = Mth.clamp(scrollTarget, 0, max);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private double valueOf(Row row) {
		return Mth.clamp(((OptionInstance.SliderableValueSet) row.option.values()).toSliderValue(row.option.get()), 0.0, 1.0);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void drawRow(Ui ui, Row row, int mouseX, int mouseY, float partialTick) {
		Palette palette = ui.palette;
		int x = row.x + 14;
		int right = row.x + row.width - 14;
		int middle = row.y + row.height / 2;
		boolean over = mouseX >= row.x && mouseX < row.x + row.width && mouseY >= row.y && mouseY < row.y + row.height && mouseY >= contentTop() && mouseY < contentBottom();
		String label = labelOf(row);
		switch (row.kind) {
			case SLIDER -> {
				double value = valueOf(row);
				ui.text(label, x, row.y + 9, palette.text());
				ui.textRight(valueTextOf(row), right, row.y + 14, palette.textSecondary());
				int trackY = row.y + 32;
				float grip = dragging == row ? 1f : over ? 0.4f : 0f;
				float radius = 7f + 1.5f * grip;
				// keep the knob on the track: its center travels the line minus its radius at each end
				int low = x + 8;
				int high = right - 8;
				int knobX = low + (int) Math.round(value * (high - low));
				ui.rounded(x, trackY - 2, right - x, 4, Radius.pill(4), Ui.withOpacity(0xFFFFFFFF, 0.24f));
				if (knobX > x) {
					ui.rounded(x, trackY - 2, knobX - x, 4, Radius.pill(4), ui.accent);
				}
				ui.roundedSmooth(knobX - radius, trackY + 1 - radius, radius * 2, radius * 2, radius, 0x30000000);
				ui.roundedSmooth(knobX - radius, trackY - radius, radius * 2, radius * 2, radius, 0xFFFFFFFF);
			}
			case TOGGLE -> {
				boolean on = (Boolean) row.option.get();
				ui.textMiddle(label, x, middle, palette.text());
				if (!row.knobSet) {
					row.knob.snap(on ? 1f : 0f);
					row.knobSet = true;
				}
				float amount = row.knob.follow(ui, on ? 1f : 0f);
				int trackWidth = 42;
				int trackX = right - trackWidth;
				ui.rounded(trackX, middle - 12, trackWidth, 24, Radius.pill(24), Ui.mix(Ui.withOpacity(0xFFFFFFFF, 0.2f), ui.accent, Mth.clamp(amount, 0f, 1f)));
				float knobX = trackX + 12f + amount * (trackWidth - 24f);
				ui.roundedSmooth(knobX - 10f, middle - 9.5f, 20f, 20f, Radius.pill(20f), 0x30000000);
				ui.roundedSmooth(knobX - 10f, middle - 10f, 20f, 20f, Radius.pill(20f), 0xFFFFFFFF);
			}
			case CYCLE -> {
				if (over) {
					ui.rounded(row.x + 4, row.y + 3, row.width - 8, row.height - 6, Radius.MD, Ui.withOpacity(0xFFFFFFFF, 0.08f));
				}
				ui.textMiddle(label, x, middle, palette.text());
				ui.textRight(ui.fit(valueTextOf(row), row.width / 2 - 40), right - 16, middle, palette.textSecondary());
				var pose = ui.graphics.pose();
				pose.pushMatrix();
				pose.translate(right - 6f, middle);
				pose.rotate((float) (-Math.PI / 2.0));
				ui.icon("chevron", -5, -5, 10, Ui.withOpacity(palette.textSecondary(), 0.8f));
				pose.popMatrix();
			}
			default -> {
				if (over) {
					ui.rounded(row.x + 4, row.y + 3, row.width - 8, row.height - 6, Radius.MD, Ui.withOpacity(0xFFFFFFFF, 0.08f));
				}
				ui.textMiddle(label, x, middle, palette.text());
				var pose = ui.graphics.pose();
				pose.pushMatrix();
				pose.translate(right - 6f, middle);
				pose.rotate((float) (-Math.PI / 2.0));
				ui.icon("chevron", -5, -5, 10, Ui.withOpacity(palette.textSecondary(), 0.8f));
				pose.popMatrix();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------------- input

	private boolean inContent(double mouseX, double mouseY) {
		return mouseX >= contentX() - 4 && mouseX < contentX() + contentWidth() + 4 && mouseY >= contentTop() && mouseY < contentBottom();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void setFromMouse(Row row, double mouseX) {
		int low = row.x + 14 + 8;
		int high = row.x + row.width - 14 - 8;
		double fraction = Mth.clamp((mouseX - low) / (double) (high - low), 0.0, 1.0);
		OptionInstance option = row.option;
		Object next = ((OptionInstance.SliderableValueSet) option.values()).fromSliderValue(fraction);
		if (!next.equals(option.get())) {
			option.set(next);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		fitView.begin(this, 760, 420);
		try {
			return doMouseClicked(fitView.event(event), doubleClick);
		} finally {
			fitView.end(this);
		}
	}

	private boolean doMouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (ScreenInput.button(event) != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		if (mx >= doneX && mx < doneX + DONE_WIDTH && my >= doneY && my < doneY + 30) {
			UiSounds.click();
			onClose();
			return true;
		}
		// sidebar pages
		if (mx >= MARGIN && mx < MARGIN + SIDE_WIDTH) {
			for (int i = 0; i < pages.size(); i++) {
				int y = Math.round(itemY(i));
				if (my >= y && my < y + ITEM_HEIGHT) {
					if (i != selected || searching()) {
						selected = i;
						query = "";
						searching = false;
						scroll = 0;
						scrollTarget = 0;
						UiSounds.tick(i / (float) Math.max(1, pages.size() - 1));
					}
					return true;
				}
			}
		}
		// search field
		if (mx >= contentX() && mx < contentX() + contentWidth() - DONE_WIDTH - 10 && my >= 22 && my < 52) {
			searching = true;
			return true;
		}
		searching = false;
		if (inContent(mx, my)) {
			for (Row row : placed) {
				if (my < row.y || my >= row.y + row.height) {
					continue;
				}
				switch (row.kind) {
					case SLIDER -> {
						dragging = row;
						setFromMouse(row, mx);
						return true;
					}
					case TOGGLE -> {
						// a toggle row is only made for an option of true and false
						@SuppressWarnings("unchecked")
						OptionInstance<Boolean> option = (OptionInstance<Boolean>) row.option;
						boolean now = !option.get();
						option.set(now);
						UiSounds.play(now ? UiSounds.Cue.ON : UiSounds.Cue.OFF);
						return true;
					}
					case LINK -> {
						UiSounds.click();
						minecraft.gui.setScreen(row.link.get());
						return true;
					}
					case CYCLE -> {
						// move the invisible vanilla widget onto the row and let it handle the click, cycling to the next value
						AbstractWidget widget = widgetOf(row);
						widget.setX(row.x);
						widget.setY(row.y);
						widget.setWidth(row.width);
						UiSounds.click();
						if (widget.mouseClicked(event, doubleClick)) {
							pressedWidget = row;
						}
						return true;
					}
					default -> {
						return true;
					}
				}
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		fitView.begin(this, 760, 420);
		try {
			return doMouseDragged(fitView.event(event), dragX / fitView.fit, dragY / fitView.fit);
		} finally {
			fitView.end(this);
		}
	}

	private boolean doMouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging != null) {
			setFromMouse(dragging, event.x());
			return true;
		}
		if (pressedWidget != null && pressedWidget.widget != null) {
			return pressedWidget.widget.mouseDragged(event, dragX, dragY);
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		fitView.begin(this, 760, 420);
		try {
			return doMouseReleased(fitView.event(event));
		} finally {
			fitView.end(this);
		}
	}

	private boolean doMouseReleased(MouseButtonEvent event) {
		dragging = null;
		if (pressedWidget != null && pressedWidget.widget != null) {
			pressedWidget.widget.mouseReleased(event);
			pressedWidget = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		fitView.begin(this, 760, 420);
		try {
			return doMouseScrolled(mouseX / fitView.fit, mouseY / fitView.fit, scrollX, scrollY);
		} finally {
			fitView.end(this);
		}
	}

	private boolean doMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget = Math.max(0, scrollTarget - scrollY * 48);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (searching && event.codepoint() >= 32 && query.length() < 40) {
			query += event.codepointAsString();
			scroll = 0;
			scrollTarget = 0;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			if (searching || !query.isEmpty()) {
				searching = false;
				query = "";
			} else {
				onClose();
			}
			return true;
		}
		if (searching && event.key() == InputConstants.KEY_BACKSPACE) {
			query = query.isEmpty() ? "" : query.substring(0, query.length() - 1);
			return true;
		}
		if (searching && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			searching = false;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		Ui.setAlpha(1f);
		options.save();
	}
}
