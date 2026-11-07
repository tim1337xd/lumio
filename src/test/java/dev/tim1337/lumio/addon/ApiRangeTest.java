package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.ApiVersion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An add-on runs only with the API versions it names, written as in fabric.mod.json; a range that cannot be read accepts nothing. */
class ApiRangeTest {
	private static final String USUAL = ">=1.0.0 <2.0.0";

	@Test
	void theUsualRange() {
		assertTrue(ApiRange.accepts(USUAL, new ApiVersion(1, 0, 0)));
		assertTrue(ApiRange.accepts(USUAL, new ApiVersion(1, 7, 3)));
		assertFalse(ApiRange.accepts(USUAL, new ApiVersion(2, 0, 0)), "a new major version may have removed something the add-on uses");
		assertFalse(ApiRange.accepts(USUAL, new ApiVersion(0, 9, 0)));
	}

	@Test
	void aNewerMinimum() {
		assertFalse(ApiRange.accepts(">=1.2.0 <2.0.0", new ApiVersion(1, 1, 5)), "an add-on that needs 1.2 does not run on 1.1");
		assertTrue(ApiRange.accepts(">=1.2 <2", new ApiVersion(1, 2, 0)), "a missing patch counts as 0");
	}

	@Test
	void otherForms() {
		assertTrue(ApiRange.accepts("*", new ApiVersion(3, 1, 0)));
		assertTrue(ApiRange.accepts("1.0.0", new ApiVersion(1, 0, 0)));
		assertFalse(ApiRange.accepts("=1.0.0", new ApiVersion(1, 0, 1)));
		assertTrue(ApiRange.accepts(" >=1.0.0\t<2.0.0 ", new ApiVersion(1, 0, 0)), "any spaces between the conditions");
	}

	@Test
	void unreadableAcceptsNothing() {
		assertFalse(ApiRange.accepts(null, new ApiVersion(1, 0, 0)));
		assertFalse(ApiRange.accepts("", new ApiVersion(1, 0, 0)));
		assertFalse(ApiRange.accepts(">=one", new ApiVersion(1, 0, 0)));
		assertFalse(ApiRange.accepts("~1.0", new ApiVersion(1, 0, 0)));
	}
}
