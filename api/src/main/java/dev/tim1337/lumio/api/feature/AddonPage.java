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

	/**
	 * Flags a category of the page as work in progress: its tab gets a small tag after its name, a note stands at the top of it ("These
	 * features are still being tested ...", with a button to where problems are reported), and each of its features gets the tag in the lists
	 * and the search results. Only tags and the note: nothing about the features changes. Take the call out to take them away.
	 *
	 * @return this
	 * @since 1.22.0
	 */
	default AddonPage workInProgress(String category) {
		return this;
	}

	/** Shows the page's categories as tabs, one at a time, however few settings it has. @return this */
	default AddonPage tabbed() {
		return this;
	}

	/**
	 * Lists this page under another one in the sidebar, after its category pages, such as a page of its own that belongs to the SkyBlock page.
	 * Called before the parent's category pages are first asked for. @return this
	 */
	default AddonPage under(AddonPage parent) {
		return this;
	}

	/** Shows a "You are in ..." chip in the page's header while the player is where its features are used (checked now and then). @return this */
	default AddonPage here(BooleanSupplier current) {
		return this;
	}

	/** @return the category pages made by {@link #page} */
	List<? extends AddonPage> subPages();
}
