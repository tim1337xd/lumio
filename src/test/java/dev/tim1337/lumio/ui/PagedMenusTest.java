package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pages of one server menu are one screen: titles alike without their page numbers, opened right after each other. */
class PagedMenusTest {
	private static final long NOW = 1_000_000L;

	@Test
	void pagesOfOneMenuShareTheirFamily() {
		assertEquals(PagedMenus.family("Wardrobe (1/2)"), PagedMenus.family("Wardrobe (2/2)"));
		assertEquals(PagedMenus.family("§8Ender Chest (1/9)"), PagedMenus.family("Ender Chest (4/9)"), "colors do not count");
		assertEquals(PagedMenus.family("Auctions Browser"), PagedMenus.family("Auctions Browser"));
		assertEquals(PagedMenus.family("Pets (2/3)"), PagedMenus.family("Pets (3/3)"));
		assertEquals(PagedMenus.family("Bestiary ➜ Hub Page 2"), PagedMenus.family("Bestiary ➜ Hub Page 1"));
	}

	@Test
	void differentMenusAreNot() {
		assertNotEquals(PagedMenus.family("Wardrobe (1/2)"), PagedMenus.family("Ender Chest (1/9)"));
		assertNotEquals(PagedMenus.family("Storage"), PagedMenus.family("Ender Chest (1/9)"));
		assertNotEquals(PagedMenus.family("SkyBlock Menu"), PagedMenus.family("Wardrobe (1/2)"));
	}

	@Test
	void theNextPageOpensRightAfterTheLastClosed() {
		String wardrobe = PagedMenus.family("Wardrobe (1/2)");
		assertTrue(PagedMenus.nextPage(wardrobe, NOW, PagedMenus.family("Wardrobe (2/2)"), NOW + 50L));
		assertFalse(PagedMenus.nextPage(wardrobe, NOW, PagedMenus.family("Wardrobe (2/2)"), NOW + PagedMenus.CONTINUE_MILLIS + 1L),
			"opened again later: a new opening, with its animation");
		assertFalse(PagedMenus.nextPage(wardrobe, NOW, PagedMenus.family("SkyBlock Menu"), NOW + 50L), "another menu");
		assertFalse(PagedMenus.nextPage("", NOW, "", NOW + 10L), "a menu without a title is nobody's page");
	}
}
