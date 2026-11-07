package dev.tim1337.lumio.api.feature;

import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.api.spi.Backend;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A page of the menu's sidebar listing an add-on's features in categories, like the main mod's pages. Make it with {@link #create}, fill it with
 * the builder methods, and register it with {@link dev.tim1337.lumio.api.AddonContext#registerPage}.
 */
public interface AddonPage extends FeatureView {
	/** @return a new page; rgb is the color of its icon tile, icon one of the mod's icons by name */
	static AddonPage create(String id, String name, String description, int rgb, String icon) {
		return Backend.get().features().page(id, name, description, rgb, icon);
	}

	/** Puts settings at the top of the page, above the features, in a group of their own (such as a master switch). @return this */
	AddonPage lead(String section, AddonSetting<?>... settings);

	/** Adds a category of features. @return this */
	AddonPage category(String name, AddonFeature... features);

	/** Puts every feature of the page under a switch: while it is off, they are all off and hidden. @return this */
	AddonPage master(Toggle switchOn);

	/** Makes a category a page of its own under this one, shown as a tile; current tells when it is where the player is. @return this */
	AddonPage page(String category, String description, int rgb, String icon, BooleanSupplier current);

	/** Adds a line under a category page's features that links to another category page. @return this */
	AddonPage pageNote(String category, String note, String link);

	/** Shows the page as an overview: its lead settings and the category pages as tiles. @return this */
	AddonPage overview();

	/** @return the category pages made by {@link #page} */
	List<? extends AddonPage> subPages();
}
