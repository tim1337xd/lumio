package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The editor's All panels: off by default, the "preview" tag only on panels shown for their example data, and nothing kept after the editor closes. */
class HudEditPreviewTest {
	private static final String GENERAL = "Pet";
	private static final String SPLITS = "Splits";
	private static final String MINING = "Mining ability";
	private static final List<String> PANELS = List.of(GENERAL, SPLITS, MINING);

	@AfterEach
	void close() {
		HudEditPreview.closed();
	}

	@Test
	void onThePrivateIslandOnlyTheGeneralPanelsAreOwn() {
		assertFalse(HudEditPreview.allPanels(), "All panels starts off");
		// the private island: only the general panel shows by itself
		Set<String> shown = new HashSet<>(Set.of(GENERAL));
		HudEditPreview.noteFrame(PANELS, shown::contains, false);
		assertFalse(HudEditPreview.preview(GENERAL, true));
		// All panels on: the dungeon and mining panels show, tagged as previews; the general one is not
		HudEditPreview.setAllPanels(true);
		shown.addAll(Set.of(SPLITS, MINING));
		HudEditPreview.noteFrame(PANELS, shown::contains, false);
		assertTrue(HudEditPreview.preview(SPLITS, true));
		assertTrue(HudEditPreview.preview(MINING, true));
		assertFalse(HudEditPreview.preview(GENERAL, true));
		// a panel that is not drawn has no tag
		assertFalse(HudEditPreview.preview(SPLITS, false));
	}

	@Test
	void inTheDungeonItsPanelsAreOwn() {
		Set<String> shown = Set.of(GENERAL, SPLITS);
		HudEditPreview.noteFrame(PANELS, shown::contains, false);
		HudEditPreview.setAllPanels(true);
		assertFalse(HudEditPreview.preview(SPLITS, true));
		assertTrue(HudEditPreview.preview(MINING, true));
	}

	@Test
	void aSimulationIsNotTakenForOwnPanels() {
		HudEditPreview.noteFrame(PANELS, Set.of(GENERAL)::contains, false);
		// while simulating, the simulated panel shows, but it is example data
		HudEditPreview.noteFrame(PANELS, Set.of(GENERAL, MINING)::contains, true);
		assertTrue(HudEditPreview.preview(MINING, true));
	}

	@Test
	void closingTheEditorTurnsAllPanelsOffAndForgets() {
		HudEditPreview.noteFrame(PANELS, Set.of(GENERAL)::contains, false);
		HudEditPreview.setAllPanels(true);
		HudEditPreview.closed();
		assertFalse(HudEditPreview.allPanels(), "All panels never stays on into the next visit or the HUD");
		// nothing noted any more: the next visit notes afresh
		HudEditPreview.noteFrame(PANELS, Set.of(GENERAL, SPLITS)::contains, false);
		assertFalse(HudEditPreview.preview(SPLITS, true));
	}
}
