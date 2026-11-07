package dev.tim1337.lumio.features.visual.screen;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.ui.NotificationEditScreen;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Settings for the notifications at the top right (a saved config, pest spawns, the Will-o'-wisp, errors): their sound, duration, size and
 * position, which is set in a dedicated editor opened with a button here.
 */
public class NotificationsFeature extends Feature {
	/** Notification sound. */
	public enum Sound {
		CHIME("Soft chime"),
		PLING("Pling"),
		BELL("Bell"),
		ORB("Experience"),
		NONE("None");

		private final String title;

		Sound(String title) {
			this.title = title;
		}

		public Component displayName() {
			return Component.literal(title);
		}
	}

	private final EnumSetting<Sound> sound = add(new EnumSetting<>("sound", Component.literal("Sound"), Sound.CHIME, Sound::displayName, EnumSetting.Style.DROPDOWN), "Sound");
	private final FloatSetting volume = add(new FloatSetting("volume", Component.literal("Volume"), 0.7f, 0.05f, 1f, 2).percent(), "Sound");
	private final BooleanSetting errorSound = add(new BooleanSetting("error_sound", Component.literal("Error sound"), true), "Sound");
	private final FloatSetting seconds = add(new FloatSetting("seconds", Component.literal("Duration"), 6.5f, 2f, 15f, 1), "General");
	private final FloatSetting scale = add(new FloatSetting("scale", Component.literal("Size"), 1f, 0.6f, 1.5f, 2), "General");
	private final BooleanSetting glow = add(new BooleanSetting("glow", Component.literal("Glow"), false), "General");
	private final FloatSetting fromRight = add(new FloatSetting("from_right", Component.literal("From the right edge"), 10f, -4000f, 4000f, 0), "Place");
	private final FloatSetting fromTop = add(new FloatSetting("from_top", Component.literal("From the top edge"), 10f, -4000f, 4000f, 0), "Place");

	public NotificationsFeature() {
		super("notifications", "Notifications", "The notices at the top right: their sound, how long they stay, how large they are, and where.", false, false);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("bell");
		summary("The notices at the top right of the screen");
		volume.under(sound, () -> sound.get() != Sound.NONE);
		errorSound.under(sound, () -> sound.get() != Sound.NONE);
		fromRight.visibleWhen(() -> false);
		fromTop.visibleWhen(() -> false);
		add(new ButtonSetting("edit_place", Component.literal("Where the notices are"), Component.literal("Edit position"), false, () -> {
			var client = Minecraft.getInstance();
			client.setScreenAndShow(new NotificationEditScreen(client.gui.screen()));
		}), "Place");
		add(new ButtonSetting("test", Component.literal("A notice to see it"), Component.literal("Show one"), false,
			() -> Toasts.info("Notification", "This is how a notice looks and sounds.")), "Place");
		add(new ButtonSetting("reset_place", Component.literal("Back to the top right"), Component.literal("Reset"), false, () -> {
			fromRight.reset();
			fromTop.reset();
			FeatureManager.saveSoon();
		}), "Place");
	}

	public float seconds() {
		return seconds.get();
	}

	public float scale() {
		return scale.get();
	}

	public boolean glow() {
		return glow.get();
	}

	public float fromRight() {
		return fromRight.get();
	}

	public float fromTop() {
		return fromTop.get();
	}

	/** Sets the notification position, from the position editor. */
	public void place(float right, float top) {
		fromRight.set(right);
		fromTop.set(top);
		FeatureManager.saveSoon();
	}

	/** @return whether notifications are at their default top-right position, which moves below the clock and gear on menu screens */
	public boolean atDefaultPlace() {
		return fromRight.get() == 10f && fromTop.get() == 10f;
	}

	/** Plays the configured notification sound. */
	public void playSound(boolean error) {
		Sound chosen = sound.get();
		if (chosen == Sound.NONE) {
			return;
		}
		if (chosen == Sound.CHIME) {
			UiSounds.play(error && errorSound.get() ? UiSounds.Cue.FAIL : UiSounds.Cue.DONE);
			return;
		}
		SoundEvent event = switch (chosen) {
			case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
			case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
			default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
		};
		float pitch = error && errorSound.get() ? 0.7f : 1.2f;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume.get()));
	}
}
