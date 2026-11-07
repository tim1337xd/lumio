package dev.tim1337.lumio.features.misc;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Borderless fullscreen: the window loses its frame and covers the whole monitor it is on, so the game looks fullscreen without the monitor
 * switching video modes, which makes alt-tabbing fast. While enabled, the fullscreen key (F11) toggles it.
 */
public class BorderlessFeature extends Feature {
	private final BooleanSetting startBorderless = add(new BooleanSetting("start_borderless", Component.literal("Start in borderless"), true), "General");
	private final ButtonSetting switchNow = add(new ButtonSetting("borderless_now", Component.literal("Borderless fullscreen"), Component.literal("Toggle"), false, () -> {
		if (ModFeatures.BORDERLESS != null) {
			ModFeatures.BORDERLESS.switchBorderless();
		}
	}), "General");
	/** Whether the window is currently borderless; persisted so the game starts the way it was left. */
	private final BooleanSetting active = add(new BooleanSetting("borderless_active", Component.literal("Borderless now"), false), "General");

	/** Whether this feature made the window borderless, and the previous window geometry to restore. */
	private boolean applied;
	private int savedX;
	private int savedY;
	private int savedWidth;
	private int savedHeight;
	private boolean started;
	/** Target window position and size while borderless: the whole monitor, in window system coordinates. */
	private int targetX;
	private int targetY;
	private int targetWidth;
	private int targetHeight;
	private int checkTicks;

	public BorderlessFeature() {
		super("borderless", "Borderless fullscreen", "A fullscreen without a frame: the window is as large as the screen. The key for fullscreen (F11) goes in and out of it.", false);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("fullscreen");
		summary("Fullscreen without a frame, switched with F11");
		active.visibleWhen(() -> false);
		switchNow.setDescription("F11 does the same while this feature is on");
	}

	/** Toggles borderless fullscreen. */
	public void switchBorderless() {
		active.set(!active.get());
		FeatureManager.saveSoon();
	}

	/** @return whether the fullscreen key is handled by this feature, which is the case while it is enabled */
	public boolean takesFullscreenKey() {
		return isEnabled();
	}

	/** Called every tick: brings the window into the requested state. */
	public void tick(Minecraft client) {
		if (client.getWindow() == null) {
			return;
		}
		if (!started) {
			started = true;
			if (isEnabled() && startBorderless.get() && !client.getWindow().isFullscreen()) {
				active.set(true);
			} else if (isEnabled() && !startBorderless.get()) {
				active.set(false);
			}
		}
		boolean wanted = isEnabled() && active.get() && !client.getWindow().isFullscreen();
		if (wanted && !applied) {
			apply(client);
		} else if (!wanted && applied) {
			restore(client);
		} else if (wanted && applied && ++checkTicks >= 10) {
			checkTicks = 0;
			keepOnScreen(client);
		}
	}

	private void apply(Minecraft client) {
		long window = client.getWindow().handle();
		int[] place = BorderlessWindow.place(window);
		savedX = place[0];
		savedY = place[1];
		savedWidth = place[2];
		savedHeight = place[3];
		int[] monitor = BorderlessWindow.monitorAt(window, place);
		if (monitor == null) {
			return;
		}
		BorderlessWindow.frameless(window, true);
		targetX = monitor[0];
		targetY = monitor[1];
		targetWidth = monitor[2];
		targetHeight = monitor[3];
		BorderlessWindow.put(window, targetX, targetY, targetWidth, targetHeight);
		applied = true;
		checkTicks = 0;
	}

	/**
	 * The window system may place the window slightly off when its frame changes (removing the border shifts the content by the border width), and
	 * the window then sticks out of the monitor. Position and size are checked and corrected if they differ from the target.
	 */
	private void keepOnScreen(Minecraft client) {
		long window = client.getWindow().handle();
		int[] place = BorderlessWindow.place(window);
		if (place[0] == targetX && place[1] == targetY && place[2] == targetWidth && place[3] == targetHeight) {
			return;
		}
		Debug.log("Borderless window was at {},{} {}x{}, put to {},{} {}x{}", place[0], place[1], place[2], place[3], targetX, targetY, targetWidth, targetHeight);
		BorderlessWindow.put(window, targetX, targetY, targetWidth, targetHeight);
	}

	private void restore(Minecraft client) {
		long window = client.getWindow().handle();
		BorderlessWindow.frameless(window, false);
		if (savedWidth > 0 && savedHeight > 0) {
			BorderlessWindow.put(window, savedX, savedY, savedWidth, savedHeight);
		}
		applied = false;
	}

	/** Its rows are in the Window group of the menu's Advanced. */
	@Override
	public Feature viewOf(Setting<?> setting) {
		return ModFeatures.MENU.viewOf(setting);
	}

	@Override
	public Feature homePage() {
		return ModFeatures.MENU;
	}
}
