package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.setting.Setting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A config from before the Held item page keeps its values, and looks as it did. */
class HeldItemSettingsTest {
	@BeforeAll
	static void start() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	private static Object value(String key) {
		for (Setting<?> setting : ModFeatures.HELD_ITEM_TRANSFORM.settings()) {
			if (setting.key().equals(key)) {
				return setting.get();
			}
		}
		throw new IllegalStateException(key);
	}

	@Test
	void anOldConfigKeepsItsValuesAndLooksTheSame() {
		JsonObject old = JsonParser.parseString("{\"enabled\":true,\"size\":0.6,\"x\":0.2,\"y\":-0.1,\"z\":0.3,\"swing_rate\":0.5,\"ignore_haste\":true,\"cooldown_dip\":true,\"show_hand\":true,\"hand_size\":1.2,\"hand_x\":0.1}").getAsJsonObject();
		ModFeatures.HELD_ITEM_TRANSFORM.load(old);
		assertEquals(0.6f, value("size"));
		assertEquals(0.2f, value("x"), "the stored place stays applied");
		assertEquals(-0.1f, value("y"));
		assertEquals(0.5f, value("swing_rate"), "the swing speed as it was");
		assertEquals(true, value("ignore_haste"));
		assertEquals(true, value("show_hand"));
		// what is new changes nothing
		assertEquals(1f, value("size_x"));
		assertEquals(1f, value("size_y"));
		assertEquals(1f, value("size_z"));
		assertEquals(HeldItemTransformFeature.SwingLook.GAME, value("swing_look"));
		assertEquals(true, value("equip_animation"));
	}
}
