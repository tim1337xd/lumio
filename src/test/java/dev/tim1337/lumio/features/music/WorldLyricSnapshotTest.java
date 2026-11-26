package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** A look copies every section its rays pass: a section left out would read as air. */
class WorldLyricSnapshotTest {
	private static final int SECTION_BLOCKS = 16;
	private static final double REACH = 128.0;
	private static final double SAMPLE_STEP = 0.05;

	@Test
	void keysKeepTheirCoordinates() {
		int[][] sections = {{0, 0, 0}, {-1, -4, -1}, {1875000, 19, -1875000}, {-30, 24, 77}};
		for (int[] s : sections) {
			long key = WorldLyricSnapshot.key(s[0], s[1], s[2]);
			assertEquals(s[0], WorldLyricSnapshot.keyX(key));
			assertEquals(s[1], WorldLyricSnapshot.keyY(key));
			assertEquals(s[2], WorldLyricSnapshot.keyZ(key));
		}
	}

	@Test
	void everySectionARayPassesIsNamed() {
		Random random = new Random(7L);
		for (int ray = 0; ray < 500; ray++) {
			double x = (random.nextDouble() - 0.5) * 2000.0;
			double y = (random.nextDouble() - 0.5) * 300.0;
			double z = (random.nextDouble() - 0.5) * 2000.0;
			double dx = random.nextGaussian();
			double dy = random.nextGaussian();
			double dz = random.nextGaussian();
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			dx /= length;
			dy /= length;
			dz /= length;
			Set<Long> named = new HashSet<>();
			WorldLyricSnapshot.sectionsAlong(x, y, z, dx, dy, dz, REACH, named::add);
			for (double along = 0.0; along <= REACH; along += SAMPLE_STEP) {
				long key = WorldLyricSnapshot.key(section(x + dx * along), section(y + dy * along), section(z + dz * along));
				assertTrue(named.contains(key), "ray " + ray + " at " + along);
			}
		}
	}

	@Test
	void aRayAlongAnAxisStaysInItsRow() {
		Set<Long> named = new HashSet<>();
		WorldLyricSnapshot.sectionsAlong(8.0, 8.0, 8.0, 1.0, 0.0, 0.0, 39.0, named::add);
		assertEquals(Set.of(WorldLyricSnapshot.key(0, 0, 0), WorldLyricSnapshot.key(1, 0, 0), WorldLyricSnapshot.key(2, 0, 0)), named);
	}

	private static int section(double block) {
		return Math.floorDiv((int) Math.floor(block), SECTION_BLOCKS);
	}
}
