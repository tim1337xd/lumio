package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.PageOutlines;
import dev.tim1337.lumio.config.setting.Setting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every settings page and sub-page has rows, and building their controls throws nothing: an empty page (as after the settings card came in, when
 * the rows of the list pages were read from the wrong place) fails here, without starting the game.
 */
public class SettingsPagesTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
		PageOutlines.apply("lumio");
	}

	/** @return the settings a page shows as rows, as the menu picks them: shown, and not the feature's own switch */
	static List<Setting<?>> rows(Feature page) {
		List<Setting<?>> rows = new ArrayList<>();
		for (Setting<?> setting : page.shownSettings()) {
			if (setting.isVisible() && !setting.key().equals("enabled")) {
				rows.add(setting);
			}
		}
		for (Feature child : page.children()) {
			for (Setting<?> setting : child.settings()) {
				if (setting.isVisible()) {
					rows.add(setting);
				}
			}
		}
		return rows;
	}

	public static void buildsWithRows(Feature page) {
		List<Setting<?>> rows = assertDoesNotThrow(() -> rows(page), page.id());
		// a feature whose rows all live on the Developer page has none of its own
		assertFalse(rows.isEmpty() && (page.outline() == null || page.outline().developer().isEmpty()), page.id() + " has no rows");
		for (Setting<?> setting : rows) {
			assertDoesNotThrow(() -> setting.createControl(() -> {
			}), page.id() + " / " + setting.key());
		}
	}

	@Test
	void everyListPageAndItsSubPagesHaveRows() {
		int pages = 0;
		for (Feature feature : FeatureManager.features()) {
			if (feature instanceof FeatureListFeature list) {
				buildsWithRows(list);
				pages++;
				for (FeatureListFeature sub : list.subPages()) {
					buildsWithRows(sub);
					pages++;
				}
			}
		}
		assertTrue(pages >= 4, "found only " + pages + " pages");
	}

	@Test
	void everyFeatureBuildsItsSettings() {
		for (Feature feature : FeatureManager.features()) {
			assertDoesNotThrow(() -> {
				for (Setting<?> setting : rows(feature)) {
					setting.createControl(() -> {
					});
				}
			}, feature.id());
		}
	}

	/** A page with an outline has its rows, and so has what is under its Advanced. */
	@Test
	void pagesWithAnOutlineAndTheirAdvancedHaveRows() {
		for (Feature feature : FeatureManager.features()) {
			if (feature.outline() != null) {
				buildsWithRows(feature);
				// what is under Advanced may all depend on a choice, such as the flames of a style
				for (Setting<?> setting : feature.pageAdvanced().settings()) {
					assertDoesNotThrow(() -> setting.createControl(() -> {
					}), feature.id() + " / " + setting.key());
				}
			}
		}
	}

}
