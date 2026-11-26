package dev.tim1337.lumio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.Map;

class PageOutlineTest {
	@Test
	void readsRowsInOrder() {
		Map<String, PageOutline> outlines = PageOutline.parse("""
			# a comment
			sky | Main | Sky | theme | Style

			sky | Main | Sky | mode
			sky | Advanced | Lightning | lightning |
			""");
		PageOutline sky = outlines.get("sky");
		assertEquals(2, sky.main().size());
		assertEquals("Style", sky.main().getFirst().label());
		assertNull(sky.main().get(1).label());
		assertEquals("lightning", sky.advanced().getFirst().key());
		assertEquals("Lightning", sky.advanced().getFirst().group());
		assertNull(sky.advanced().getFirst().label());
	}

	@Test
	void oneLineCanServeSeveralFeatures() {
		Map<String, PageOutline> outlines = PageOutline.parse("mining_profit, farming_profit | Main | Look | look | Style\n");
		assertEquals("look", outlines.get("mining_profit").main().getFirst().key());
		assertEquals("look", outlines.get("farming_profit").main().getFirst().key());
	}

	@Test
	void aBrokenLineIsAMistake() {
		assertThrows(IllegalArgumentException.class, () -> PageOutline.parse("sky | Main\n"));
		assertThrows(IllegalArgumentException.class, () -> PageOutline.parse("sky | Elsewhere | Sky | theme\n"));
		assertThrows(IllegalArgumentException.class, () -> PageOutline.parse("sky | Main | Sky |  \n"));
	}

	@Test
	void subPagesKeepTheirRowsInOrderWithTheIconOfTheirFirstRow() {
		PageOutline sky = PageOutline.parse("""
			sky | Main | | sky_preview
			sky | Page Clouds @layers | Clouds | cloud_style | Style
			sky | Page Night sky @moon | Night sky | star_density
			sky | Page Clouds | Shape | gaps
			""").get("sky");
		assertEquals(1, sky.main().size());
		var pages = sky.pages();
		assertEquals(2, pages.size());
		var clouds = pages.keySet().iterator().next();
		assertEquals("Clouds", clouds.title());
		assertEquals("layers", clouds.icon());
		assertEquals(java.util.List.of("cloud_style", "gaps"), pages.get(clouds).stream().map(PageOutline.Row::key).toList());
		assertEquals("Style", pages.get(clouds).getFirst().label());
		assertThrows(IllegalArgumentException.class, () -> PageOutline.parse("sky | Page  | Sky | theme\n"));
	}
}
