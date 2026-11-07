package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.feature.FeatureView;

import java.util.List;
import java.util.function.Supplier;

/**
 * A choice of the first setup's "How you play" step: the add-on's world ("Hypixel SkyBlock") or one thing the player does there ("Mining").
 *
 * @param id        its id
 * @param name      its name ("Making coins")
 * @param shortName its short name in the summary ("Coins")
 * @param note      one short line under its name, or ""
 * @param icon      the icon of its tile
 * @param light     the top color of its tile, 0xRRGGBB
 * @param dark      the bottom color of its tile, 0xRRGGBB
 * @param features  the features the choice turns on, in order; asked once, after every feature exists
 */
public record SetupChoice(String id, String name, String shortName, String note, String icon, int light, int dark,
						  Supplier<List<FeatureView>> features) {
}
