package dev.tim1337.lumio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.config.setting.Setting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The page outlines name real features and keys, and a page with an outline leaves no row out: every setting that shows has its place, on the page, under Advanced or in the Look sheet. */
class PageOutlinesTest {
	/** Rows made for the page that hold no value of their own. */
	private static final List<String> ONLY_OPENS = List.of("look_row", "bars_row", "lines_row", "lines_chips");

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void outlinesNameRealFeaturesAndKeysAndLeaveNoRowOut() {
		Map<String, PageOutline> outlines = PageOutlines.of("lumio");
		Map<String, Feature> features = new HashMap<>();
		for (Feature feature : FeatureManager.features()) {
			features.put(feature.id(), feature);
		}
		List<String> wrong = new ArrayList<>();
		outlines.forEach((id, outline) -> {
			Feature feature = features.get(id);
			assertNotNull(feature, "no feature " + id);
			feature.useOutline(outline);
			List<String> placed = new ArrayList<>();
			for (PageOutline.Row row : outline.rows()) {
				placed.add(row.key());
				// the search goes through a feature's settings: what a row shows must be among them, or be a row that only opens or shows others
				Setting<?> shown = feature.settingOf(row.key());
				if (shown != null && !feature.settings().contains(shown) && !ONLY_OPENS.contains(row.key())) {
					wrong.add(id + "." + row.key() + " cannot be found by the search");
				}
				// keys are made when the game registers them
				if (feature.settingOf(row.key()) == null && !row.key().startsWith("key.")) {
					wrong.add(id + "." + row.key() + " does not exist");
				}
				if (feature.settingOf(row.key()) instanceof MultiSelectSetting chips) {
					chips.options().forEach(option -> placed.add(option.key()));
				}
			}
			if (outline.onlyNames()) {
				return;
			}
			for (Setting<?> setting : feature.settings()) {
				if (setting.isVisible() && !setting.key().equals("enabled") && !setting.key().equals("reset_page") && !placed.contains(setting.key()) && feature.viewOf(setting) == null) {
					wrong.add(id + "." + setting.key() + " has no place");
				}
			}
		});
		assertEquals(List.of(), wrong);
	}
}
