package dev.tim1337.lumio;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.features.misc.BorderlessFeature;
import dev.tim1337.lumio.features.misc.CursorFeature;
import dev.tim1337.lumio.features.misc.DebugFeature;
import dev.tim1337.lumio.features.misc.KeepServerPacksFeature;
import dev.tim1337.lumio.features.misc.keybinds.CommandKeybindsFeature;
import dev.tim1337.lumio.features.music.MusicFeature;
import dev.tim1337.lumio.features.visual.hud.ActionBarHudFeature;
import dev.tim1337.lumio.features.visual.hud.ArmorHudFeature;
import dev.tim1337.lumio.features.visual.hud.BossBarHudFeature;
import dev.tim1337.lumio.features.visual.hud.HotbarFeature;
import dev.tim1337.lumio.features.visual.hud.InfoBarFeature;
import dev.tim1337.lumio.features.visual.hud.InventoryHudFeature;
import dev.tim1337.lumio.features.visual.hud.ScoreboardHudFeature;
import dev.tim1337.lumio.features.visual.hud.StatusBarsHudFeature;
import dev.tim1337.lumio.features.visual.hud.TabListHudFeature;
import dev.tim1337.lumio.features.visual.screen.ChatFeature;
import dev.tim1337.lumio.features.visual.screen.InventoryScreenFeature;
import dev.tim1337.lumio.features.visual.screen.ItemTooltipsFeature;
import dev.tim1337.lumio.features.visual.screen.MenusFeature;
import dev.tim1337.lumio.features.visual.screen.NotificationsFeature;
import dev.tim1337.lumio.features.visual.world.AmbienceFeature;
import dev.tim1337.lumio.features.visual.world.ArrowTrailFeature;
import dev.tim1337.lumio.features.visual.world.BlockOverlayFeature;
import dev.tim1337.lumio.features.visual.world.HeldItemOutlineFeature;
import dev.tim1337.lumio.features.visual.world.HeldItemTransformFeature;
import dev.tim1337.lumio.features.visual.world.SkyFeature;
import dev.tim1337.lumio.features.visual.world.WalkEffectFeature;
import dev.tim1337.lumio.features.visual.world.cinematic.CinematicCameraFeature;
import dev.tim1337.lumio.menu.ConfigsFeature;
import dev.tim1337.lumio.menu.FeatureListFeature;
import dev.tim1337.lumio.menu.HomeFeature;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.EditableHud;


import java.util.ArrayList;
import java.util.List;

/**
 * Registry of every feature of the mod and of the menu pages that list them. Features are created once, in declaration order, and look each other
 * up through these constants, so order matters: during construction a feature may only use features declared above it.
 */
public final class ModFeatures {
	private ModFeatures() {
	}

	/** Forces class initialization, which creates every feature below. Any static access does the same; this method makes the intent explicit. */
	public static void createAll() {
	}

	public static final HomeFeature HOME = FeatureManager.register(new HomeFeature());
	public static final HeldItemTransformFeature HELD_ITEM_TRANSFORM = FeatureManager.register(new HeldItemTransformFeature());
	public static final HeldItemOutlineFeature HELD_ITEM_OUTLINE = FeatureManager.register(new HeldItemOutlineFeature());
	public static final SkyFeature SKY = FeatureManager.register(new SkyFeature());
	public static final AmbienceFeature AMBIENCE = FeatureManager.register(new AmbienceFeature());
	public static final ArrowTrailFeature ARROW_TRAILS = FeatureManager.register(new ArrowTrailFeature());
	public static final WalkEffectFeature WALK = FeatureManager.register(new WalkEffectFeature());
	public static final BlockOverlayFeature BLOCK_OVERLAY = FeatureManager.register(new BlockOverlayFeature());
	public static final InfoBarFeature INFO_BAR = FeatureManager.register(new InfoBarFeature());
	public static final HotbarFeature HOTBAR = FeatureManager.register(new HotbarFeature());
	public static final ArmorHudFeature ARMOR = FeatureManager.register(new ArmorHudFeature());
	public static final StatusBarsHudFeature STATUS = FeatureManager.register(new StatusBarsHudFeature());
	public static final InventoryHudFeature INVENTORY = FeatureManager.register(new InventoryHudFeature());
	public static final InventoryScreenFeature INVENTORY_SCREEN = FeatureManager.register(new InventoryScreenFeature());
	public static final ItemTooltipsFeature ITEM_TOOLTIPS = FeatureManager.register(new ItemTooltipsFeature());
	public static final ScoreboardHudFeature SCOREBOARD = FeatureManager.register(new ScoreboardHudFeature());
	public static final TabListHudFeature TAB_LIST = FeatureManager.register(new TabListHudFeature());
	public static final ActionBarHudFeature ACTION_BAR = FeatureManager.register(new ActionBarHudFeature());
	public static final BossBarHudFeature BOSS_BARS = FeatureManager.register(new BossBarHudFeature());
	public static final NotificationsFeature NOTIFICATIONS = FeatureManager.register(new NotificationsFeature());
	public static final MusicFeature MUSIC = FeatureManager.register(new MusicFeature());
	public static final CinematicCameraFeature CINEMATIC = FeatureManager.register(new CinematicCameraFeature());
	public static final ChatFeature CHAT = FeatureManager.register(new ChatFeature());
	public static final MenusFeature MENUS = FeatureManager.register(new MenusFeature());
	public static final CursorFeature NO_CURSOR_RESET = FeatureManager.register(new CursorFeature());
	public static final BorderlessFeature BORDERLESS = FeatureManager.register(new BorderlessFeature());
	public static final KeepServerPacksFeature KEEP_SERVER_PACKS = FeatureManager.register(new KeepServerPacksFeature());
	public static final CommandKeybindsFeature COMMAND_KEYBINDS = FeatureManager.register(new CommandKeybindsFeature());
	public static final DebugFeature DEBUG = FeatureManager.register(new DebugFeature());

	// Feature pages: each lists its features with a toggle and a settings button.
	public static final FeatureListFeature WORLD = FeatureManager.register(new FeatureListFeature("world", "World",
		"What is added to the game world: the sky, the ambience, trails, the outline of the item you hold, and the cinematic camera.", SystemColors.PURPLE_RGB, "sparkle")
		.category("Environment", SKY, AMBIENCE)
		.category("Effects", ARROW_TRAILS, WALK, HELD_ITEM_OUTLINE, HELD_ITEM_TRANSFORM, BLOCK_OVERLAY)
		.category("Camera", CINEMATIC));
	public static final FeatureListFeature INTERFACE = FeatureManager.register(new FeatureListFeature("interface", "Interface",
		"The bars and panels drawn over the game. Move them in the editor of the layout.", SystemColors.TEAL_RGB, "gauge")
		.category("Bars", HOTBAR, STATUS, ACTION_BAR, INFO_BAR)
		.category("Panels", BOSS_BARS, ARMOR, INVENTORY, SCOREBOARD, TAB_LIST)
		.category("Screens", INVENTORY_SCREEN, ITEM_TOOLTIPS, MENUS, NOTIFICATIONS));
	public static final FeatureListFeature MUSIC_PAGE = FeatureManager.register(new FeatureListFeature("music_page", "Music",
		"The song that plays, and where its lyrics show.", SystemColors.PINK_RGB, "note")
		.category("", MUSIC)
		// the feature's switch sits in the page header, on every tab
		.inline(MUSIC).switchInHeader(MUSIC));
	public static final FeatureListFeature CHAT_PAGE = FeatureManager.register(new FeatureListFeature("chat_page", "Chat",
		"Messages stay as the game sends them; Lumio adds the rest.", SystemColors.INDIGO_RGB, "bubble").direct(CHAT));
	public static final FeatureListFeature KEYBINDS_PAGE = FeatureManager.register(new FeatureListFeature("keybinds_page", "Keybinds",
		"Put a command on a key.", SystemColors.GRAY_RGB, "keyboard").direct(COMMAND_KEYBINDS));
	public static final ConfigsFeature CONFIGS = FeatureManager.register(new ConfigsFeature());
	public static final MenuFeature MENU = FeatureManager.register(new MenuFeature());

	static {
		// Only home, the feature list and the menu settings appear in the sidebar; every other page is reached from the feature list
		for (Feature feature : FeatureManager.features()) {
			if (feature != HOME && !(feature instanceof FeatureListFeature) && feature != MENU && feature != CONFIGS) {
				feature.unlist();
			}
		}
	}
	/** @return every HUD element that can be moved and resized in the HUD editor */
	public static List<EditableHud> editableHuds() {
		List<EditableHud> huds = new ArrayList<>(List.of(INFO_BAR, MUSIC, HOTBAR, HOTBAR.nameHud()));
		huds.addAll(STATUS.editables());
		huds.addAll(List.of(ACTION_BAR, ARMOR, INVENTORY, SCOREBOARD, TAB_LIST));
		// the add-ons' elements (the SkyBlock panels)
		huds.addAll(Addons.huds().keySet());
		huds.addAll(List.of(BOSS_BARS, HELD_ITEM_TRANSFORM, CHAT));
		return huds;
	}
}
