package dev.tim1337.lumio.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The named groups of a long page: every feature in exactly one group, groups and rows in the order of the code, short pages left alone. */
class PageGroupsTest {
	/** A feature as "name:group"; no group after the colon means it declared none. */
	private static String groupOf(String feature) {
		String group = feature.substring(feature.indexOf(':') + 1);
		return group.isEmpty() ? null : group;
	}

	@Test
	void aLongPageIsSplitInTheOrderOfTheCode() {
		List<String> features = List.of("a:Items", "b:Chat", "c:Items", "d:Visual", "e:Chat", "f:Items", "g:Visual", "h:Items");
		Map<String, List<String>> groups = PageGroups.split(features, PageGroupsTest::groupOf);
		assertEquals(List.of("Items", "Chat", "Visual"), new ArrayList<>(groups.keySet()));
		assertEquals(List.of("a:Items", "c:Items", "f:Items", "h:Items"), groups.get("Items"));
		assertEquals(List.of("b:Chat", "e:Chat"), groups.get("Chat"));
	}

	@Test
	void everyFeatureIsInExactlyOneGroup() {
		List<String> features = List.of("a:Items", "b:", "c:Items", "d:Visual", "e: ", "f:Items", "g:Visual", "h:Items", "i:Chat");
		Map<String, List<String>> groups = PageGroups.split(features, PageGroupsTest::groupOf);
		List<String> all = new ArrayList<>();
		groups.values().forEach(all::addAll);
		assertEquals(features.size(), all.size());
		assertEquals(features.size(), all.stream().distinct().count());
		// one that declared none is not lost: it stands under "Other"
		assertEquals(List.of("b:", "e: "), groups.get(PageGroups.OTHER));
	}

	@Test
	void aShortPageOrOneWithoutGroupsStaysOnePlainList() {
		assertNull(PageGroups.split(List.of("a:Items", "b:Chat", "c:Items", "d:Visual", "e:Chat", "f:Items", "g:Visual"), PageGroupsTest::groupOf));
		assertNull(PageGroups.split(List.of("a:", "b:", "c:", "d:", "e:", "f:", "g:", "h:", "i:"), PageGroupsTest::groupOf));
	}
}
