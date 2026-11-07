package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.Control;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** The card at the top of the music page with the song that plays now; it has no value. */
final class NowPlayingSetting extends Setting<Void> {
	private final MusicFeature music;

	NowPlayingSetting(MusicFeature music) {
		super("now_playing_card", Component.literal("Now playing"), null);
		this.music = music;
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new NowPlayingControl(music);
	}
}
