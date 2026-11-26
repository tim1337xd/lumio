package dev.tim1337.lumio.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Versions in SemVer's order, betas before the release; a developer's build is known by its alpha stage or its build part. */
class LumioVersionTest {
	private static LumioVersion v(String text) {
		return LumioVersion.parse(text);
	}

	@Test
	void ordered() {
		assertTrue(v("1.0.0-beta.2").compareTo(v("1.0.0-beta.1")) > 0);
		assertTrue(v("1.0.0").compareTo(v("1.0.0-beta.9")) > 0, "the release after its betas");
		assertTrue(v("1.0.1-beta.1").compareTo(v("1.0.0")) > 0);
		assertTrue(v("1.0.0-beta.1").compareTo(v("1.0.0-alpha")) > 0);
		assertTrue(v("1.10.0").compareTo(v("1.9.3")) > 0, "numbers, not text");
		assertEquals(0, v("v1.0.0-beta.2").compareTo(v("1.0.0-beta.2")), "a tag's v");
		assertEquals(v("1.0.0-beta.1"), v("v1.0.0-beta.1+mc26.3"), "the tag of the first beta");
	}

	@Test
	void notVersions() {
		assertNull(v("latest"));
		assertNull(v("1.0"));
		assertNull(v(null));
		assertNull(v("1.0.0-gamma.1"));
	}

	@Test
	void developerBuilds() {
		assertTrue(LumioVersion.devBuild("1.0.1-alpha+build.31"));
		assertTrue(LumioVersion.devBuild("1.0.1-alpha"));
		assertTrue(LumioVersion.devBuild("1.0.0-beta.2+build.156"));
		assertTrue(LumioVersion.devBuild("not a version"));
		assertFalse(LumioVersion.devBuild("1.0.0-beta.2"));
		assertFalse(LumioVersion.devBuild("1.0.0"));
	}

	@Test
	void shown() {
		assertEquals("1.0.0 Beta 2", v("1.0.0-beta.2").display());
		assertEquals("1.0.0-beta.2", v("1.0.0-beta.2").toString());
	}
}
