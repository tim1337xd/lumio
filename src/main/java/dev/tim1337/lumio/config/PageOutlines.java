package dev.tim1337.lumio.config;

import dev.tim1337.lumio.api.experimental.util.Logs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Reads the mod's and every add-on's page outlines ({@code assets/<id>/page_outlines.txt}) and gives them to their features, once, when those are made. */
public final class PageOutlines {
	private static final String FILE = "/assets/%s/page_outlines.txt";

	private PageOutlines() {
	}

	/** @return the outlines of a mod or an add-on; none when it has no file or the file cannot be read, and its pages then stay as they are */
	public static Map<String, PageOutline> of(String modId) {
		try (InputStream in = PageOutlines.class.getResourceAsStream(String.format(FILE, modId))) {
			return in == null ? Map.of() : PageOutline.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException | IllegalArgumentException e) {
			Logs.warn("Could not read the page outlines of {}", modId, e);
			return Map.of();
		}
	}

	/** Gives the features their outlines. */
	public static void apply(String modId) {
		Map<String, PageOutline> outlines = of(modId);
		for (Feature feature : FeatureManager.features()) {
			PageOutline outline = outlines.get(feature.id());
			if (outline != null) {
				feature.useOutline(outline);
			}
		}
	}
}
