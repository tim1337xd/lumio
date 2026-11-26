package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The screen overlays that moved from the Camera module to the Visual Filter keep their values, and nothing else changes for the player. */
class VisualFilterMigrationTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void onlyACameraWithOverlaysSetMoves() {
		assertTrue(VisualFilterFeature.cameraHadOverlays(JsonParser.parseString("{\"enabled\": true, \"fire\": \"HIDE\"}").getAsJsonObject()));
		assertTrue(VisualFilterFeature.cameraHadOverlays(JsonParser.parseString("{\"portal\": \"HIDE\"}").getAsJsonObject()));
		assertFalse(VisualFilterFeature.cameraHadOverlays(JsonParser.parseString("{\"fire\": \"NORMAL\", \"full_bright\": true}").getAsJsonObject()));
	}

	@Test
	void theValuesStayAndTheRestOfTheFilterStaysOff() {
		VisualFilterFeature filter = new VisualFilterFeature();
		filter.takeOverFromCamera(JsonParser.parseString("{\"enabled\": true, \"fire\": \"LOWER\", \"underwater\": \"HIDE\"}").getAsJsonObject());
		assertTrue(filter.isEnabled());
		assertEquals(VisualFilterFeature.Fire.LOWER, filter.fire());
		assertTrue(filter.hideUnderwater());
		assertFalse(filter.hidePortal());
		assertFalse(filter.fireIcon());
		JsonObject saved = filter.save();
		for (String off : new String[]{"falling_blocks", "lightning", "experience_orbs", "death_animation", "explosion_particles"}) {
			assertFalse(saved.get(off).getAsBoolean(), off);
		}
	}

	@Test
	void itsOwnSettingsWin() {
		VisualFilterFeature filter = new VisualFilterFeature();
		filter.load(JsonParser.parseString("{\"enabled\": false, \"fire\": \"NORMAL\"}").getAsJsonObject());
		filter.takeOverFromCamera(JsonParser.parseString("{\"enabled\": true, \"fire\": \"HIDE\"}").getAsJsonObject());
		assertFalse(filter.isEnabled());
	}
}
