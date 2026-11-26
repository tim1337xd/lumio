package dev.tim1337.lumio.features.music;

import java.util.ArrayList;
import java.util.List;

/** Lets tests outside the music package read what the Cut-out preset knows. */
public final class CutoutTestAccess {
	private CutoutTestAccess() {
	}

	/** @return every word that has a symbol, English and German */
	public static List<String> allSymbolWords() {
		List<String> all = new ArrayList<>();
		for (int i = 0; i < CutoutLexicon.SYMBOLS_COUNT; i++) {
			all.addAll(List.of(CutoutLexicon.symbolWords(i)));
		}
		return all;
	}
}
