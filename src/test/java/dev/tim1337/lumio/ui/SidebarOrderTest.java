package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The order and the groups of the pages in the menu's sidebar. */
class SidebarOrderTest {
	/** Categories and the page each belongs to. */
	private static final Map<String, String> PARENTS = Map.of("chat_page", "extras", "keybinds_page", "extras", "fishing", "skyblock", "slayer", "skyblock");
	private static final Function<String, String> PARENT = PARENTS::get;

	private static List<String> ordered(String... pages) {
		return SidebarOrder.ordered(List.of(pages), page -> page, PARENT);
	}

	@Test
	void homeThenLumioThenAddonsThenTools() {
		assertEquals(List.of("home", "world", "interface", "music_page", "extras", "chat_page", "keybinds_page", "skyblock", "fishing", "slayer", "debug", "configs"),
			ordered("home", "world", "interface", "skyblock", "fishing", "slayer", "music_page", "debug", "extras", "chat_page", "keybinds_page", "configs"));
	}

	@Test
	void theGroupsAreNamed() {
		assertNull(SidebarOrder.group("home").label(), "Home stands alone, without a label");
		assertEquals("LUMIO", SidebarOrder.group("world").label());
		assertEquals("LUMIO", SidebarOrder.group("interface").label());
		assertEquals("LUMIO", SidebarOrder.group("music_page").label());
		assertEquals("ADD-ONS", SidebarOrder.group("skyblock").label());
		assertEquals("LUMIO", SidebarOrder.group("extras").label(), "Extras (Chat, Keybinds, Waypoints) is the mod's own, no add-on");
		assertEquals("ADD-ONS", SidebarOrder.group("debug").label());
		assertEquals("TOOLS", SidebarOrder.group("configs").label());
	}

	@Test
	void otherAddonsComeAfterSkyBlockInTheOrderTheyRegisteredAndDebugIsLast() {
		assertEquals(List.of("home", "world", "extras", "skyblock", "zeta", "alpha", "debug", "configs"),
			ordered("home", "debug", "zeta", "world", "alpha", "configs", "extras", "skyblock"));
		assertEquals(SidebarOrder.Group.ADDONS, SidebarOrder.group("zeta"), "a page of another add-on is under ADD-ONS");
	}

	@Test
	void theOrderDoesNotDependOnWhatIsInstalled() {
		assertEquals(List.of("home", "world", "interface", "music_page", "extras", "configs"), ordered("home", "world", "interface", "music_page", "extras", "configs"));
		assertEquals(List.of("home", "world", "interface", "music_page", "extras", "skyblock", "configs"),
			ordered("home", "world", "interface", "music_page", "extras", "configs", "skyblock"));
	}

	@Test
	void addOnsHoldsOnlyRealAddOns() {
		for (String own : List.of("world", "interface", "music_page", "extras")) {
			assertEquals(SidebarOrder.Group.LUMIO, SidebarOrder.group(own), own);
		}
		assertEquals(List.of("home", "world", "interface", "music_page", "extras", "chat_page", "keybinds_page", "configs"),
			ordered("home", "extras", "chat_page", "keybinds_page", "world", "interface", "music_page", "configs"), "Extras stands below Music");
		assertEquals(List.of("Edit HUD layout", "Configs", "Menu settings"), SidebarOrder.TOOLS);
	}

	@Test
	void aCategoryStaysUnderItsPageAndIsNeverLost() {
		List<String> pages = ordered("home", "chat_page", "extras", "keybinds_page", "fishing");
		assertEquals(List.of("home", "extras", "chat_page", "keybinds_page", "fishing"), pages, "a category whose page is not listed comes last");
	}

	@Test
	void onlyTheDebugPageHasTheDevTag() {
		assertTrue(SidebarOrder.devPage("debug"));
		assertFalse(SidebarOrder.devPage("skyblock"));
		assertFalse(SidebarOrder.devPage("extras"));
	}
}
