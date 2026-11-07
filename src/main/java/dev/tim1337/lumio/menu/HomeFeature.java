package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.HomeSetting;
import dev.tim1337.lumio.ui.ChangelogScreen;
import dev.tim1337.lumio.util.ModVersion;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The first page of the menu: status tiles, the features for where the player is, the song that plays and the settings changed last (see
 * {@link dev.tim1337.lumio.ui.controls.HomeControl}). The prebuilt looks are on the configs page. It has no settings of its own.
 */
public class HomeFeature extends Feature {
	/** The changelog, next to the title as in the mockup; the HUD editor is in the sidebar. */
	private static final List<HeaderButton> HEADER_BUTTONS = List.of(
		new HeaderButton("Changelog", false, () -> {
			Minecraft client = Minecraft.getInstance();
			client.setScreenAndShow(new ChangelogScreen(client.gui.screen()));
		}));

	public HomeFeature() {
		super("home", "Home", "What is going on, and the switches for where you are right now.", false, false);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("info");
		add(new HomeSetting(), "");
	}

	/** The home page is the mod's own page: it carries the mod's name. */
	@Override
	public String headerTitle() {
		return "Lumio";
	}

	@Override
	public String headerChip() {
		return ModVersion.shortDisplay();
	}

	@Override
	public String headerChipTooltip() {
		return ModVersion.build().isEmpty() ? null : ModVersion.build();
	}

	@Override
	public List<HeaderButton> headerButtons() {
		return HEADER_BUTTONS;
	}
}
