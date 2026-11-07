import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Lumio's own glyphs for symbols that servers put in their text and Inter does not have (Hypixel's location pin "⏣", the moon, cloud, rain,
 * snow, the star and the swords). Without them the game takes these from its own font, whose bitmaps keep their small size while Inter is drawn
 * larger, so they come out tiny. This draws them at the cap height of every sized font of the mod, as white shapes the text color tints, and puts a
 * bitmap provider for them into each font, before the game's font.
 *
 * Run from the project folder after changing a font or a symbol: {@code java tools/FontSymbols.java}
 */
public final class FontSymbols {
	private static final Path FONTS = Path.of("src/main/resources/assets/lumio/font");
	private static final Path PICTURES = Path.of("src/main/resources/assets/lumio/textures/font/symbols");
	/**
	 * The symbols, in the order of their cells; the same as MissingGlyphs.OWN_GLYPHS: the first seven drawn here, the others the game takes from Unifont,
	 * which leaves them small within their box (taken from Unifont's own glyph, cropped to its ink and scaled to the capitals' height).
	 */
	private static final String SYMBOLS = "⏣☽☁☂❄✦⚔\u1805\u2E15\u2748\u2741\u2618\u2727\u269A\u2672\u0D60\u26C3\u2AFD\u272F\u23E2\u3020";
	/** Inter's cap height as a share of its size. */
	private static final double CAP_HEIGHT = 0.7275;
	/** The game puts a font's baseline 7 units below the top of a line, before a provider's own shift. */
	private static final double BASELINE = 7.0;
	private static final Pattern INTER = Pattern.compile("\\{\"type\":\"ttf\",\"file\":\"lumio:inter(?:_bold|_semibold)?\\.ttf\",\"size\":([0-9.]+),\"oversample\":([0-9.]+),\"shift\":\\[[0-9.]+,([0-9.]+)\\]\\}");
	private static final Pattern OURS = Pattern.compile("\\{\"type\":\"bitmap\",\"file\":\"lumio:font/symbols/[^\"]*\"[^}]*\\},");
	private static final String GAME_FONT = "{\"type\":\"reference\",\"id\":\"minecraft:default\"}";

	private FontSymbols() {
	}

	public static void main(String[] args) throws IOException {
		Files.createDirectories(PICTURES);
		// the sheets are made anew: a height no font uses any more leaves none behind
		try (var old = Files.list(PICTURES)) {
			for (Path picture : old.filter(file -> file.toString().endsWith(".png")).toList()) {
				Files.delete(picture);
			}
		}
		TreeSet<Integer> heights = new TreeSet<>();
		List<Path> fonts = new ArrayList<>();
		try (var files = Files.list(FONTS)) {
			files.filter(file -> file.toString().endsWith(".json")).sorted().forEach(fonts::add);
		}
		int changed = 0;
		for (Path font : fonts) {
			String compact = Files.readString(font, StandardCharsets.UTF_8).replaceAll("\\s+", "");
			compact = OURS.matcher(compact).replaceAll("");
			Matcher inter = INTER.matcher(compact);
			if (!inter.find() || !compact.contains(GAME_FONT)) {
				continue;
			}
			double size = Double.parseDouble(inter.group(1));
			double oversample = Double.parseDouble(inter.group(2));
			double shift = Double.parseDouble(inter.group(3));
			double cap = size * CAP_HEIGHT;
			int height = Math.max(5, (int) Math.round(cap * 1.12));
			// centered on the capitals: their middle is half the cap height above the baseline
			double middle = BASELINE + shift - cap / 2.0;
			int ascent = (int) Math.round(BASELINE - (middle - height / 2.0));
			// a font drawn scaled up has its glyphs made larger (its oversample): the picture is as many pixels high as the glyph is drawn
			int pixels = sheetFor((int) Math.round(height * Math.max(1.0, oversample)));
			heights.add(pixels);
			String provider = "{\"type\":\"bitmap\",\"file\":\"lumio:font/symbols/" + pixels + ".png\",\"ascent\":" + Math.min(ascent, height) + ",\"height\":" + height
				+ ",\"chars\":[\"" + escaped(SYMBOLS) + "\"]},";
			String text = compact.replace(GAME_FONT, provider + GAME_FONT);
			Files.writeString(font, text, StandardCharsets.UTF_8);
			changed++;
		}
		// text drawn in the game's font: its own font with the symbols at the capitals' height (7 of 8 units), the game's font for the rest
		heights.add(GAME_SHEET);
		Files.writeString(FONTS.resolve("symbols.json"), "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"lumio:font/symbols/" + GAME_SHEET
			+ ".png\",\"ascent\":7,\"height\":7,\"chars\":[\"" + escaped(SYMBOLS) + "\"]}," + GAME_FONT + "]}", StandardCharsets.UTF_8);
		for (int height : heights) {
			ImageIO.write(sheet(height), "png", PICTURES.resolve(height + ".png").toFile());
		}
		System.out.println("fonts " + changed + ", pictures " + heights.size());
	}

	/** The few sizes the glyphs are drawn at; a font takes the smallest one that is not smaller than its glyphs, which the game then shrinks a little. */
	private static final int[] SHEETS = {8, 12, 16, 24, 32};
	/** The sheet the game's-font symbols are drawn from: sharp at the usual GUI scales. */
	private static final int GAME_SHEET = 16;
	/** How much higher than wide a Unifont pixel may become, so a wide symbol keeps most of the capitals' height. */
	private static final double MOST_SQUEEZE = 1.15;

	private static int sheetFor(int pixels) {
		for (int sheet : SHEETS) {
			if (sheet >= pixels) {
				return sheet;
			}
		}
		return SHEETS[SHEETS.length - 1];
	}

	private static String escaped(String text) {
		StringBuilder out = new StringBuilder();
		text.codePoints().forEach(c -> out.append(String.format(Locale.ROOT, "\\u%04x", c)));
		return out.toString();
	}

	/** All symbols in one row of square cells as high as the glyphs. */
	private static BufferedImage sheet(int height) {
		int cells = SYMBOLS.codePointCount(0, SYMBOLS.length());
		BufferedImage image = new BufferedImage(height * cells, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setColor(Color.WHITE);
		for (int i = 0; i < cells; i++) {
			AffineTransform before = g.getTransform();
			g.translate(i * height, 0);
			g.scale(height / 100.0, height / 100.0);
			g.fill(shape(SYMBOLS.codePointAt(SYMBOLS.offsetByCodePoints(0, i))));
			g.setTransform(before);
		}
		g.dispose();
		return image;
	}

	/** Unifont's glyphs, by code point: 16 rows of 8 or 16 pixels, from the game's own Unifont (its assets as Gradle keeps them). */
	private static java.util.Map<Integer, String> unifontGlyphs;

	/** @return a symbol from Unifont: its pixels as squares, cropped to its ink, as large as the box allows, left-aligned */
	private static Shape unifont(int symbol) {
		String hex = unifontHex().get(symbol);
		if (hex == null) {
			throw new IllegalStateException("Unifont has no glyph for " + Integer.toHexString(symbol));
		}
		int columns = hex.length() / 16 * 4;
		int minX = columns, minY = 16, maxX = -1, maxY = -1;
		boolean[][] ink = new boolean[16][columns];
		for (int y = 0; y < 16; y++) {
			long row = Long.parseLong(hex.substring(y * columns / 4, (y + 1) * columns / 4), 16);
			for (int x = 0; x < columns; x++) {
				if ((row >> (columns - 1 - x) & 1) != 0) {
					ink[y][x] = true;
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		int high = maxY - minY + 1;
		int wide = maxX - minX + 1;
		// a wide glyph fills the box's width and is squeezed a little across so it keeps most of the capitals' height (the trapezium)
		double across = 100.0 / Math.max(high, wide);
		double scale = Math.min(100.0 / high, across * MOST_SQUEEZE);
		// a glyph lower than the box stands on its bottom, as the capitals do
		double top = 100.0 - high * scale;
		Area area = new Area();
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				if (ink[y][x]) {
					area.add(new Area(new Rectangle2D.Double((x - minX) * across, top + (y - minY) * scale, across, scale)));
				}
			}
		}
		return area;
	}

	private static java.util.Map<Integer, String> unifontHex() {
		if (unifontGlyphs != null) {
			return unifontGlyphs;
		}
		unifontGlyphs = new java.util.HashMap<>();
		try {
			Path assets = Path.of(System.getProperty("user.home"), ".gradle", "caches", "fabric-loom", "assets");
			String hash = null;
			try (var indexes = Files.list(assets.resolve("indexes"))) {
				for (Path index : indexes.toList()) {
					Matcher found = Pattern.compile("\"minecraft/font/unifont\\.zip\":\\s*\\{\"hash\":\\s*\"([0-9a-f]{40})\"").matcher(Files.readString(index));
					if (found.find()) {
						hash = found.group(1);
					}
				}
			}
			if (hash == null) {
				throw new IllegalStateException("Unifont is not among the game's assets in " + assets);
			}
			try (var zip = new java.util.zip.ZipFile(assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash).toFile())) {
				for (var entry : java.util.Collections.list(zip.entries())) {
					if (!entry.getName().endsWith(".hex")) {
						continue;
					}
					try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(zip.getInputStream(entry), StandardCharsets.US_ASCII))) {
						for (String line = reader.readLine(); line != null; line = reader.readLine()) {
							int colon = line.indexOf(':');
							if (colon > 0) {
								unifontGlyphs.putIfAbsent(Integer.parseInt(line.substring(0, colon), 16), line.substring(colon + 1));
							}
						}
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return unifontGlyphs;
	}

	/** One symbol in a box of 100 by 100, left-aligned so the game measures it tightly. */
	private static Shape shape(int symbol) {
		return switch (symbol) {
			case '⏣' -> pin();
			case '☽' -> moon();
			case '☁' -> cloud();
			case '☂' -> umbrella();
			case '❄' -> snowflake();
			case '✦' -> star();
			case '⚔' -> swords();
			default -> unifont(symbol);
		};
	}

	/** A map pin: a round head that narrows to a point at the bottom, with a hole. */
	private static Shape pin() {
		GeneralPath outline = new GeneralPath();
		double r = 34;
		double cx = 37;
		double cy = 36;
		outline.moveTo(cx, 100);
		outline.curveTo(cx - 14, 82, cx - r, 62, cx - r, cy);
		outline.append(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 180, -180, Arc2D.OPEN), true);
		outline.curveTo(cx + r, 62, cx + 14, 82, cx, 100);
		outline.closePath();
		Area pin = new Area(outline);
		pin.subtract(new Area(new Ellipse2D.Double(cx - 13, cy - 13, 26, 26)));
		return pin;
	}

	private static Shape moon() {
		Area moon = new Area(new Ellipse2D.Double(0, 4, 92, 92));
		moon.subtract(new Area(new Ellipse2D.Double(30, -6, 92, 92)));
		return moon;
	}

	private static Shape cloud() {
		Area cloud = new Area(new RoundRectangle2D.Double(0, 54, 100, 36, 36, 36));
		cloud.add(new Area(new Ellipse2D.Double(8, 36, 38, 38)));
		cloud.add(new Area(new Ellipse2D.Double(30, 16, 50, 50)));
		cloud.add(new Area(new Ellipse2D.Double(64, 40, 34, 34)));
		return cloud;
	}

	private static Shape umbrella() {
		Area umbrella = new Area(new Arc2D.Double(0, 6, 100, 84, 0, 180, Arc2D.CHORD));
		umbrella.add(new Area(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(handle())));
		return umbrella;
	}

	private static Shape handle() {
		GeneralPath handle = new GeneralPath();
		handle.moveTo(50, 46);
		handle.lineTo(50, 86);
		handle.quadTo(50, 96, 40, 96);
		handle.quadTo(30, 96, 30, 86);
		return handle;
	}

	private static Shape snowflake() {
		Area flake = new Area();
		BasicStroke stroke = new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		for (int arm = 0; arm < 6; arm++) {
			double angle = Math.toRadians(arm * 60 - 90);
			double ex = 50 + Math.cos(angle) * 44;
			double ey = 50 + Math.sin(angle) * 44;
			flake.add(new Area(stroke.createStrokedShape(new Line2D.Double(50, 50, ex, ey))));
			double bx = 50 + Math.cos(angle) * 28;
			double by = 50 + Math.sin(angle) * 28;
			for (int side = -1; side <= 1; side += 2) {
				double branch = angle + side * Math.toRadians(45);
				flake.add(new Area(stroke.createStrokedShape(new Line2D.Double(bx, by, bx + Math.cos(branch) * 14, by + Math.sin(branch) * 14))));
			}
		}
		return flake;
	}

	/** A four-pointed star with thin curved sides. */
	private static Shape star() {
		GeneralPath star = new GeneralPath();
		star.moveTo(50, 0);
		star.quadTo(56, 44, 100, 50);
		star.quadTo(56, 56, 50, 100);
		star.quadTo(44, 56, 0, 50);
		star.quadTo(44, 44, 50, 0);
		star.closePath();
		return star;
	}

	private static Shape swords() {
		Area swords = new Area();
		BasicStroke blade = new BasicStroke(14f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
		BasicStroke guard = new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		swords.add(new Area(blade.createStrokedShape(new Line2D.Double(8, 8, 78, 78))));
		swords.add(new Area(blade.createStrokedShape(new Line2D.Double(92, 8, 22, 78))));
		swords.add(new Area(guard.createStrokedShape(new Line2D.Double(62, 86, 86, 62))));
		swords.add(new Area(guard.createStrokedShape(new Line2D.Double(14, 62, 38, 86))));
		swords.add(new Area(guard.createStrokedShape(new Line2D.Double(80, 80, 94, 94))));
		swords.add(new Area(guard.createStrokedShape(new Line2D.Double(20, 80, 6, 94))));
		return swords;
	}
}
