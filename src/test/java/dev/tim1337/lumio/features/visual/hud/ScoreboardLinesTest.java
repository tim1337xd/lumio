package dev.tim1337.lumio.features.visual.hud;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Lines of a scoreboard read as label and value. */
class ScoreboardLinesTest {
	/** A line can reach the debug switch, which needs the game's registries. */
	@org.junit.jupiter.api.BeforeAll
	static void game() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	@Test
	void aLabelWithAValueIsSplit() {
		assertEquals(7, ScoreboardLines.valueStart("Purse: 1,204,553"));
		assertEquals(6, ScoreboardLines.valueStart("Bits: 12,400"));
	}

	@Test
	void otherLinesStayWhole() {
		assertEquals(-1, ScoreboardLines.valueStart(" 7:20pm"));
		assertEquals(-1, ScoreboardLines.valueStart("Late Summer 14th"));
		assertEquals(-1, ScoreboardLines.valueStart("Objective: "));
		assertEquals(-1, ScoreboardLines.valueStart(": 12"));
		assertEquals(-1, ScoreboardLines.valueStart(""));
	}

	@Test
	void theValueKeepsTheColorOfItsLabel() {
		assertEquals("§6", ScoreboardLines.lastColor("§6Purse"));
		assertEquals("§6§l", ScoreboardLines.lastColor("§c§6§lPurse"));
		assertEquals("", ScoreboardLines.lastColor("Purse"));
		assertEquals("§r", ScoreboardLines.lastColor("§6Pu§rrse"));
	}

	@Test
	void theValueOfALineWrittenInOnePieceKeepsItsCodes() {
		ScoreboardLines.Split split = ScoreboardLines.split(Component.literal("§fPurse: §61,204,553"));
		assertEquals("§fPurse", split.label().getString());
		assertEquals("§f§61,204,553", split.value().getString());
	}

	@Test
	void aValueInAPartOfItsOwnStartsAfresh() {
		ScoreboardLines.Split split = ScoreboardLines.split(Component.literal("§ePurse: ").append(Component.literal("1,204")));
		assertEquals("1,204", split.value().getString());
	}

	@Test
	void aGainIsNotPartOfTheValue() {
		assertEquals("+5", ScoreboardLines.gainOf("12,480,553 (+5)"));
		assertEquals("", ScoreboardLines.gainOf("12,480,553"));
		assertEquals("+1.2k", ScoreboardLines.gainOf("Bits: 4,200 (+1.2k)"));
		ScoreboardLines.Split split = ScoreboardLines.split(net.minecraft.network.chat.Component.literal("Purse: 12,480,553 (+5)"));
		assertEquals("12,480,553", split.value().getString());
		assertEquals("+5", split.gain());
	}

	@Test
	void longValuesAreWrittenShorterReadably() {
		assertEquals("12.5M", ScoreboardLines.shortened("12,480,553"));
		assertEquals("1.2B", ScoreboardLines.shortened("1,200,000,000"));
		assertEquals("12.5K", ScoreboardLines.shortened("12,500"));
		assertEquals("9,999", ScoreboardLines.shortened("9,999"));
		assertEquals("The End", ScoreboardLines.shortened("The End"));
	}

	@Test
	void theLocationSymbolAfterWhatShowsNothing() {
		for (String line : new String[]{"\u23E3 Village", " \u23E3 Village", "\u00A77 \u23E3 \u00A7aVillage", "\u200E \u23E3 Village", "\uE100 \u23E3 Village", "\uD83C\uDFAD \u23E3 Village", "\u0444 Village", "\uD83D\uDCCD Village"}) {
			ScoreboardLines.Icon icon = ScoreboardLines.locationIcon(net.minecraft.network.chat.Component.literal(line));
			assertEquals("pin", icon.name(), line);
			assertEquals("Village", dev.tim1337.lumio.api.experimental.util.TextColors.noColors(icon.rest().getString()), line);
		}
	}

	@Test
	void theLocationSymbolOnlyAtTheStartOfALine() {
		for (String line : new String[]{"Purse: \u23E3 1,000", "Late \u23E3 Summer", "\u0444\u043E\u0442\u043E", "\u2600 Sunny", "Village", "", "   "}) {
			assertEquals(null, ScoreboardLines.locationIcon(net.minecraft.network.chat.Component.literal(line)), line);
		}
	}

	@Test
	void theSymbolInThePrefixAndTheNameInTheSuffix() {
		var line = net.minecraft.network.chat.Component.empty().append(net.minecraft.network.chat.Component.literal(" \u00A77\u200B\u23E3\uFE0F ")).append(net.minecraft.network.chat.Component.literal("\u00A7r")).append(net.minecraft.network.chat.Component.literal("\u00A75Void Sepulture"));
		ScoreboardLines.Icon icon = ScoreboardLines.locationIcon(line);
		assertEquals("pin", icon.name());
		assertEquals(0xAAAAAA, icon.rgb());
		assertEquals("Void Sepulture", dev.tim1337.lumio.api.experimental.util.TextColors.noColors(icon.rest().getString()));
	}

	@Test
	void colorCodesBetweenThemAndExtraFillers() {
		var line = net.minecraft.network.chat.Component.literal("\u200E\uD83C\uDFAD \u00A7f\u2003\u00A77\u23E3\u00A7r \u200D\u00A7dVoid Sepulture");
		ScoreboardLines.Icon icon = ScoreboardLines.locationIcon(line);
		assertEquals("pin", icon.name());
		assertEquals("Void Sepulture", dev.tim1337.lumio.api.experimental.util.TextColors.noColors(icon.rest().getString()));
	}

	@Test
	void theNameStaysAsSent() {
		var line = net.minecraft.network.chat.Component.literal("\u00A77\u23E3 \u00A7bThe Mist  (x2)");
		ScoreboardLines.Icon icon = ScoreboardLines.locationIcon(line);
		assertEquals("\u00A7bThe Mist  (x2)", icon.rest().getString().replace("\u00A77", ""));
	}

	@Test
	void thePinHasTheSymbolsColor() {
		assertEquals(0xAAAAAA, ScoreboardLines.locationIcon(net.minecraft.network.chat.Component.literal("\u00A77\u23E3 \u00A7bHub")).rgb());
		assertEquals(0x55FFFF, ScoreboardLines.locationIcon(net.minecraft.network.chat.Component.literal("\u23E3 Hub").withStyle(net.minecraft.ChatFormatting.AQUA)).rgb());
		assertEquals(-1, ScoreboardLines.locationIcon(net.minecraft.network.chat.Component.literal("\u23E3 Hub")).rgb());
	}

	@Test
	void aLeadingSymbolIsTheLinesIcon() {
		ScoreboardLines.Icon area = ScoreboardLines.icon(net.minecraft.network.chat.Component.literal(" \u23E3 The End"));
		assertEquals("pin", area.name());
		assertEquals("The End", area.rest().getString());
		ScoreboardLines.Icon colored = ScoreboardLines.icon(net.minecraft.network.chat.Component.literal("\u00A77 \u23E3 \u00A7bThe End"));
		assertEquals("pin", colored.name());
		assertEquals("\u00A7bThe End".length(), colored.rest().getString().length());
		assertEquals(null, ScoreboardLines.icon(net.minecraft.network.chat.Component.literal("Late Summer 14th")));
		assertEquals("pin", ScoreboardLines.iconName(0x1F4CD));
	}

	private static final java.util.function.Predicate<String> NOTHING_KNOWN = name -> false;

	private static ScoreboardLines.LocationFound found(String line, String area) {
		return ScoreboardLines.findLocation(line, java.util.List.of(area), NOTHING_KNOWN);
	}

	private static String rest(net.minecraft.network.chat.Component line, String area) {
		return ScoreboardLines.locationIcon(line, java.util.List.of(area), NOTHING_KNOWN).rest().getString().replaceAll("§.", "");
	}

	@Test
	void theAreaNameTellsTheLineWhateverStandsBeforeIt() {
		assertEquals(ScoreboardLines.LocationRule.CURRENT_AREA, found("⏣ Your Island", "Your Island").rule());
		assertEquals(ScoreboardLines.LocationRule.CURRENT_AREA, found("ф The Rift", "The Rift").rule());
		assertEquals(ScoreboardLines.LocationRule.CURRENT_AREA, found("⌘ Village", "Village").rule(), "a symbol nobody has seen yet");
		assertEquals(ScoreboardLines.LocationRule.CURRENT_AREA, found("Hub", "Hub").rule(), "nothing before it");
		assertEquals(ScoreboardLines.LocationRule.CURRENT_AREA, found("§7⏣ §bYour Island", "your island").rule(), "colors, and the case of the letters");
	}

	@Test
	void theNameIsWhatStays() {
		var plain = net.minecraft.network.chat.Component.literal("⏣ Your Island");
		assertEquals("Your Island", rest(plain, "Your Island"));
		assertEquals("The Rift", rest(net.minecraft.network.chat.Component.literal("ф The Rift"), "The Rift"));
		assertEquals("pin", ScoreboardLines.locationIcon(plain, java.util.List.of("Your Island"), NOTHING_KNOWN).name());
	}

	@Test
	void zeroWidthAndFillerBeforeTheGlyph() {
		String line = "​️ §7⏣​ §bYour Island";
		assertEquals(ScoreboardLines.LocationRule.CURRENT_AREA, found(line, "Your Island").rule());
		assertEquals("Your Island", rest(net.minecraft.network.chat.Component.literal(line), "Your Island"));
		assertEquals("Your Island", rest(net.minecraft.network.chat.Component.literal("🎂⏣ Your Island"), "Your Island"), "a filler emoji");
	}

	@Test
	void theGlyphAndTheNameInDifferentParts() {
		var line = net.minecraft.network.chat.Component.empty()
			.append(net.minecraft.network.chat.Component.literal("​§7⏣ "))
			.append(net.minecraft.network.chat.Component.literal("​"))
			.append(net.minecraft.network.chat.Component.literal("§bYour Island"));
		var icon = ScoreboardLines.locationIcon(line, java.util.List.of("Your Island"), NOTHING_KNOWN);
		assertEquals("Your Island", icon.rest().getString().replaceAll("§.", ""));
		assertEquals(0xAAAAAA, icon.rgb());
		var split = net.minecraft.network.chat.Component.empty()
			.append(net.minecraft.network.chat.Component.literal("⏣ §bYour "))
			.append(net.minecraft.network.chat.Component.literal("Island"));
		assertEquals("Your Island", ScoreboardLines.locationIcon(split, java.util.List.of("Your Island"), NOTHING_KNOWN).rest().getString().replaceAll("§.", ""));
	}

	@Test
	void theColorOfALineWithoutASymbolIsTheNames() {
		var icon = ScoreboardLines.locationIcon(net.minecraft.network.chat.Component.literal("​§bHub"), java.util.List.of("Hub"), NOTHING_KNOWN);
		assertEquals(0x55FFFF, icon.rgb());
	}

	@Test
	void otherLinesWithTheNameAreNotIt() {
		assertEquals(null, found("Warp to Hub", "Hub"));
		assertEquals(null, found("Purse: 12 Hub", "Hub"));
		assertEquals(null, found("Your Island is nice", "Your Island"));
	}

	@Test
	void anAreaNotKnownYetIsToldByTheKnownNames() {
		java.util.function.Predicate<String> known = name -> name.equals("Your Island");
		assertEquals(ScoreboardLines.LocationRule.KNOWN_AREA, ScoreboardLines.findLocation("⌘ Your Island", java.util.List.of(""), known).rule());
		assertEquals(ScoreboardLines.LocationRule.KNOWN_AREA, ScoreboardLines.findLocation("​️⌘ Your Island", java.util.List.of(), known).rule());
		assertEquals(null, ScoreboardLines.findLocation("Your Island", java.util.List.of(), known), "no symbol: nothing says it is the location");
	}

	@Test
	void anUnknownArea() {
		assertEquals(ScoreboardLines.LocationRule.SYMBOL, found("⏣ Somewhere New", "Hub").rule(), "Hypixel's own symbol");
		assertEquals(null, found("⌘ Somewhere New", "Hub"), "neither its name nor its symbol is known");
		assertEquals(null, found("Somewhere New", ""));
	}

	@Test
	void aValueIsTheSameWithAndWithoutAGain() {
		String without = ScoreboardLines.split(net.minecraft.network.chat.Component.literal("§fPurse: §6689,000")).value().getString();
		String with = ScoreboardLines.split(net.minecraft.network.chat.Component.literal("§fPurse: §6689,000 §e(+12)")).value().getString();
		assertEquals(without, with);
		assertEquals("+12", ScoreboardLines.split(net.minecraft.network.chat.Component.literal("§fPurse: §6689,000 §e(+12)")).gain());
		assertEquals(without, ScoreboardLines.split(net.minecraft.network.chat.Component.literal("§fPurse: §6689,000 (+12)")).value().getString());
	}
}
