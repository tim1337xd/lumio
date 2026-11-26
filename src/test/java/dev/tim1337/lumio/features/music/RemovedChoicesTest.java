package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tim1337.lumio.config.setting.EnumSetting;

import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** Choices of the lyrics in the world that were removed: a config that still holds one loads as the choice that stands in for it. */
class RemovedChoicesTest {
	@Test
	void aSavedPresetThatIsGoneIsClassic() {
		for (String gone : new String[] {"CONSTELLATION", "AURORA", "HORIZON"}) {
			EnumSetting<WorldLyricPreset> preset = new EnumSetting<>("world_preset", Component.literal("Preset"), WorldLyricPreset.CLASSIC,
				value -> Component.literal(value.label()), EnumSetting.Style.SEGMENTED);
			preset.load(new JsonPrimitive(gone));
			assertEquals(WorldLyricPreset.CLASSIC, preset.get(), gone);
			preset.load(new JsonPrimitive("CUTOUT"));
			assertEquals(WorldLyricPreset.CUTOUT, preset.get(), "the presets that stay load as saved");
		}
	}
}
