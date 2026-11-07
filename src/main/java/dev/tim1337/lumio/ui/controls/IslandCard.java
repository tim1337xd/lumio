package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.PlaceFeatureProvider;
import dev.tim1337.lumio.api.PlaceGroup;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.feature.FeatureView;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.menu.FeatureListFeature;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The card on the home page with the features for where the player is: in an add-on's world (on a SkyBlock island), the groups of features
 * the add-on says fit the place ({@link PlaceFeatureProvider}), with what the player is doing right now first, each group with a link to its
 * page and its first few features; elsewhere, the world features as quick switches. Rebuilt only when the island, the order or an opened group changes.
 */
final class IslandCard {
	private static final int HEAD = 36;
	private static final int GROUP_HEAD = 22;
	private static final int MORE_ROW = 20;
	private static final int QUIET_ROW = 30;
	private static final int PAD = 10;
	private static final int TILE = 20;
	/** How many features a group shows before "Show all". */
	private static final int FIRST = 3;
	private static final float TINT_LEFT = 0.2f;
	private static final float TINT_RIGHT = 0.05f;
	private static final int MOST_LINKS = 16;

	/** A group of the card: an activity's features, the page they are on, and its texts. */
	private record Group(String name, FeatureListFeature page, List<Feature> features, FeatureListControl list, String link, String more) {
	}

	private final Runnable onChanged;
	private final List<Group> groups = new ArrayList<>();
	/** Groups opened with "Show all", by name. */
	private final Set<String> expanded = new HashSet<>();
	private String key;
	private boolean onIsland;
	private String caption = "";
	private String title = "";
	private String headLink = "";
	private FeatureListFeature headPage;
	private int rgb;
	private String icon = "map";
	// the links drawn in the last frame, for clicks: where they are and what they open
	private final int[] linkX = new int[MOST_LINKS];
	private final int[] linkY = new int[MOST_LINKS];
	private final int[] linkWidth = new int[MOST_LINKS];
	private final int[] linkHeight = new int[MOST_LINKS];
	private final Runnable[] linkAction = new Runnable[MOST_LINKS];
	private final float[] linkHover = new float[MOST_LINKS];
	private int links;

	IslandCard(Runnable onChanged) {
		this.onChanged = onChanged;
	}

	/** Reads where the player is and what they are doing, and rebuilds the groups when that changed. Called once per second. */
	void refresh() {
		PlaceFeatureProvider place = Addons.placeFeatures();
		boolean skyBlock = place != null && place.active();
		String island = skyBlock ? place.placeName() : null;
		String wanted = skyBlock + "|" + (skyBlock ? place.key() : "") + "|" + expanded;
		if (wanted.equals(key)) {
			return;
		}
		key = wanted;
		groups.clear();
		onIsland = skyBlock;
		if (!skyBlock) {
			// outside an add-on's world: the world features as quick switches
			headPage = ModFeatures.WORLD;
			caption = "";
			title = "Quick switches";
			headLink = "All world features";
			rgb = ModFeatures.WORLD.iconRgb();
			icon = ModFeatures.WORLD.iconName();
			List<Feature> features = ModFeatures.WORLD.listedFeatures();
			groups.add(new Group("", ModFeatures.WORLD, features, new FeatureListControl(features, onChanged).embedded(), "", ""));
			return;
		}
		caption = "You are in";
		FeatureListFeature main = (FeatureListFeature) place.page();
		title = island == null || island.isBlank() ? main.nameText() : island;
		headLink = "";
		headPage = main;
		for (PlaceGroup group : place.groups()) {
			if (!(group.page() instanceof FeatureListFeature page)) {
				continue;
			}
			List<Feature> all = new ArrayList<>();
			for (FeatureView feature : group.features()) {
				Feature known = Adapters.featureOf(feature);
				if (known != null) {
					all.add(known);
				}
			}
			if (all.isEmpty()) {
				continue;
			}
			boolean open = expanded.contains(group.name()) || all.size() <= FIRST;
			List<Feature> shown = open ? all : all.subList(0, FIRST);
			groups.add(new Group(group.name(), page, all, new FeatureListControl(shown, onChanged).embedded(),
				"All " + group.name().toLowerCase(Locale.ROOT) + " features", open ? "" : "Show all (" + all.size() + ")"));
		}
		FeatureListFeature first = groups.isEmpty() ? main : groups.getFirst().page();
		rgb = first.iconRgb();
		icon = first.iconName();
	}

	/** @return the card's height; lays out its lists for the width */
	int height(Ui ui, int width) {
		int height = HEAD;
		if (onIsland && groups.isEmpty()) {
			return height + QUIET_ROW;
		}
		for (Group group : groups) {
			group.list().place(ui, 0, 0, width);
			height += (group.name().isEmpty() ? 0 : GROUP_HEAD) + group.list().height() + (group.more().isEmpty() ? 0 : MORE_ROW);
		}
		return height;
	}

	void draw(Ui ui, int mouseX, int mouseY, int x, int y, int width) {
		Palette palette = ui.palette;
		links = 0;
		int cardHeight = height(ui, width);
		int color = 0xFF000000 | rgb;
		ui.rounded(x, y, width, cardHeight, Radius.LG, palette.card());
		// the header row in the island's color, fading from left to right, inside the card's rounded border
		ui.roundedTopBand(x, y, width, HEAD - 1, Radius.LG, Ui.withOpacity(color, TINT_LEFT), Ui.withOpacity(color, TINT_RIGHT));
		ui.ring(x, y, width, cardHeight, Radius.LG, 1, palette.cardBorder());
		ui.fill(x + 1, y + HEAD - 1, width - 2, 1, palette.cardBorder());
		ui.iconTile(icon, x + PAD, y + (HEAD - TILE) / 2, TILE, TILE - 8, Radius.SM, color, 0xFFFFFFFF);
		int textX = x + PAD + TILE + 8;
		int right = x + width - PAD;
		// measured regular but drawn bold: its share of the room is what bold adds
		int room = right - textX - (headLink.isEmpty() ? 0 : ui.width(headLink) + 8) - Math.max(0, Math.round(ui.boldWidthScaled(title, TextScale.BODY)) - ui.width(title));
		if (caption.isEmpty()) {
			ui.textAtScaledBold(ui.fit(title, room), textX, y + HEAD / 2f - 4f, 1f, palette.text());
		} else {
			// "You are in" in a light tint of the island's color, its name in the text color
			int tint = palette.light() ? Ui.mix(color, 0xFF000000, 0.25f) : Ui.mix(color, 0xFFFFFFFF, 0.55f);
			ui.textAtScaled(caption, textX, y + 7, TextScale.SMALL, tint);
			ui.textAtScaledBold(ui.fit(title, room), textX, y + 18, 1f, palette.text());
		}
		if (!headLink.isEmpty()) {
			link(ui, headLink, right - ui.width(headLink), y, HEAD, mouseX, mouseY, () -> MacScreen.showPage(headPage));
		}
		int top = y + HEAD;
		if (onIsland && groups.isEmpty()) {
			String quiet = "Nothing special here.";
			ui.textMiddle(quiet, x + PAD, top + QUIET_ROW / 2, palette.textSecondary());
			link(ui, "All features", x + PAD + ui.width(quiet) + 6, top, QUIET_ROW, mouseX, mouseY, () -> MacScreen.showPage(headPage));
			return;
		}
		for (int i = 0; i < groups.size(); i++) {
			Group group = groups.get(i);
			if (!group.name().isEmpty()) {
				if (i > 0) {
					ui.fill(x + 1, top, width - 2, 1, palette.cardBorder());
				}
				ui.sectionLabel(group.name().toUpperCase(Locale.ROOT), x + PAD, top + 8, palette.textSecondary());
				FeatureListFeature page = group.page();
				link(ui, group.link(), right - ui.width(group.link()), top, GROUP_HEAD, mouseX, mouseY, () -> MacScreen.showPage(page));
				top += GROUP_HEAD;
			}
			group.list().place(ui, x, top, width);
			group.list().render(ui, mouseX, mouseY);
			top += group.list().height();
			if (!group.more().isEmpty()) {
				String name = group.name();
				link(ui, group.more(), x + PAD, top, MORE_ROW, mouseX, mouseY, () -> {
					expanded.add(name);
					refresh();
				});
				top += MORE_ROW;
			}
		}
	}

	/** Draws a link in the text color, underlined on hover, and remembers it for clicks. */
	private void link(Ui ui, String text, int lx, int ly, int rowHeight, int mouseX, int mouseY, Runnable action) {
		if (links >= MOST_LINKS) {
			return;
		}
		int i = links++;
		int width = ui.width(text);
		boolean over = mouseX >= lx && mouseX < lx + width && mouseY >= ly && mouseY < ly + rowHeight;
		linkHover[i] = ui.approach(linkHover[i], over ? 1f : 0f, Ui.HOVER_RATE);
		int middle = ly + rowHeight / 2;
		ui.textMiddle(text, lx, middle, ui.palette.text());
		if (linkHover[i] > 0.01f) {
			ui.fill(lx, middle + 5, width, 1, Ui.withOpacity(ui.palette.text(), linkHover[i]));
		}
		linkX[i] = lx;
		linkY[i] = ly;
		linkWidth[i] = width;
		linkHeight[i] = rowHeight;
		linkAction[i] = action;
	}

	/** @return whether the click was on the card */
	boolean click(double mouseX, double mouseY, int button) {
		for (Group group : groups) {
			if (group.list().contains(mouseX, mouseY)) {
				return group.list().mouseClicked(mouseX, mouseY, button);
			}
		}
		for (int i = 0; i < links; i++) {
			if (mouseX >= linkX[i] && mouseX < linkX[i] + linkWidth[i] && mouseY >= linkY[i] && mouseY < linkY[i] + linkHeight[i]) {
				UiSounds.click();
				linkAction[i].run();
				return true;
			}
		}
		return false;
	}
}
