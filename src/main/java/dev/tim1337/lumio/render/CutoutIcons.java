package dev.tim1337.lumio.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The symbols of the Cut-out preset of the lyrics in the world: ninety small flat pictures for common lyric words (a car, a heart, a flame, the
 * moon, ...), drawn here from plain shapes, each in a box of 64 by 64. Our own drawings: no picture, font or icon of anyone else is used. They
 * are white on nothing, so a word's tone colors them. Their order is the order of the words' lists in
 * {@code CutoutLexicon}. No weapons, no drugs, nothing explicit, and no brand: a brand name in a song stands beside the thing in general.
 */
public final class CutoutIcons {
	/** How many symbols there are, and how many stand in a row of their picture. */
	public static final int COUNT = 90;
	public static final int ACROSS = 8;
	/** The box every symbol is drawn in. */
	private static final float BOX = 64f;

	private CutoutIcons() {
	}

	/** @return all symbols in one picture, white on nothing: {@link #ACROSS} in a row, each in a cell of the given size */
	public static BufferedImage sheet(int cell) {
		int rows = (COUNT + ACROSS - 1) / ACROSS;
		BufferedImage image = new BufferedImage(ACROSS * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
		for (int icon = 0; icon < COUNT; icon++) {
			Graphics2D g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.setClip(icon % ACROSS * cell, icon / ACROSS * cell, cell, cell);
			g.translate(icon % ACROSS * cell, icon / ACROSS * cell);
			// a little room around each, so no symbol touches its neighbor when the picture is drawn smaller
			g.translate(cell * 0.06, cell * 0.06);
			g.scale(cell * 0.88 / BOX, cell * 0.88 / BOX);
			g.setColor(Color.WHITE);
			draw(icon, g);
			g.dispose();
		}
		return image;
	}

	// ------------------------------------------------------------------------------------------------------- shapes

	private static void circle(Graphics2D g, double x, double y, double r) {
		g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
	}

	private static void oval(Graphics2D g, double x, double y, double rx, double ry) {
		g.fill(new Ellipse2D.Double(x - rx, y - ry, 2 * rx, 2 * ry));
	}

	private static void box(Graphics2D g, double x, double y, double w, double h, double corner) {
		g.fill(new RoundRectangle2D.Double(x, y, w, h, 2 * corner, 2 * corner));
	}

	private static Path2D path(double... points) {
		Path2D path = new Path2D.Double();
		path.moveTo(points[0], points[1]);
		for (int i = 2; i < points.length; i += 2) {
			path.lineTo(points[i], points[i + 1]);
		}
		return path;
	}

	private static void shape(Graphics2D g, double... points) {
		Path2D path = path(points);
		path.closePath();
		g.fill(path);
	}

	private static void line(Graphics2D g, double width, double... points) {
		g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(path(points));
	}

	private static void arc(Graphics2D g, double width, double x, double y, double r, double from, double sweep) {
		g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Arc2D.Double(x - r, y - r, 2 * r, 2 * r, from, sweep, Arc2D.OPEN));
	}

	/** What is drawn in the run is cut out of the symbol instead of added to it. */
	private static void hole(Graphics2D g, Runnable cut) {
		var before = g.getComposite();
		g.setComposite(AlphaComposite.Clear);
		cut.run();
		g.setComposite(before);
	}

	private static void star(Graphics2D g, double x, double y, double outer, double inner, int points) {
		double[] corners = new double[points * 4];
		for (int i = 0; i < points * 2; i++) {
			double angle = Math.PI * i / points - Math.PI / 2;
			double r = i % 2 == 0 ? outer : inner;
			corners[2 * i] = x + Math.cos(angle) * r;
			corners[2 * i + 1] = y + Math.sin(angle) * r;
		}
		shape(g, corners);
	}

	private static void cloud(Graphics2D g, double x, double y, double s) {
		circle(g, x + 16 * s, y + 20 * s, 11 * s);
		circle(g, x + 30 * s, y + 14 * s, 14 * s);
		circle(g, x + 43 * s, y + 21 * s, 10 * s);
		box(g, x + 8 * s, y + 20 * s, 42 * s, 11 * s, 5.5 * s);
	}

	// -------------------------------------------------------------------------------------------------- the symbols

	/** Draws a symbol, white, into the box of 64 by 64. */
	private static void draw(int icon, Graphics2D g) {
		switch (icon) {
			case 0 -> { // car
				shape(g, 14, 30, 21, 16, 43, 16, 51, 30);
				box(g, 4, 28, 56, 18, 7);
				circle(g, 18, 46, 8);
				circle(g, 46, 46, 8);
				hole(g, () -> {
					circle(g, 18, 46, 3.5);
					circle(g, 46, 46, 3.5);
					shape(g, 20, 28, 24, 20, 30, 20, 30, 28);
					shape(g, 34, 20, 40, 20, 45, 28, 34, 28);
				});
			}
			case 1 -> { // heart
				circle(g, 20, 23, 14);
				circle(g, 44, 23, 14);
				shape(g, 7.5, 29.5, 56.5, 29.5, 32, 58);
			}
			case 2 -> { // fire
				Path2D flame = new Path2D.Double();
				flame.moveTo(34, 3);
				flame.curveTo(36, 16, 52, 24, 52, 40);
				flame.curveTo(52, 52, 43, 61, 32, 61);
				flame.curveTo(21, 61, 12, 52, 12, 41);
				flame.curveTo(12, 32, 18, 26, 21, 20);
				flame.curveTo(23, 26, 25, 29, 28, 30);
				flame.curveTo(27, 20, 28, 10, 34, 3);
				g.fill(flame);
				hole(g, () -> {
					Path2D inner = new Path2D.Double();
					inner.moveTo(33, 34);
					inner.curveTo(36, 40, 41, 43, 41, 49);
					inner.curveTo(41, 54, 37, 57, 32, 57);
					inner.curveTo(27, 57, 23, 54, 23, 49);
					inner.curveTo(23, 44, 30, 41, 33, 34);
					g.fill(inner);
				});
			}
			case 3 -> { // moon
				circle(g, 30, 32, 26);
				hole(g, () -> circle(g, 42, 24, 22));
			}
			case 4 -> { // sun
				circle(g, 32, 32, 13);
				for (int i = 0; i < 8; i++) {
					double angle = Math.PI * i / 4;
					line(g, 5, 32 + Math.cos(angle) * 20, 32 + Math.sin(angle) * 20, 32 + Math.cos(angle) * 28, 32 + Math.sin(angle) * 28);
				}
			}
			case 5 -> star(g, 32, 34, 29, 12.5, 5);
			case 6 -> { // money: a banknote
				box(g, 2, 14, 60, 36, 5);
				hole(g, () -> {
					circle(g, 32, 32, 10);
					circle(g, 12, 32, 3.5);
					circle(g, 52, 32, 3.5);
				});
				circle(g, 32, 32, 5);
			}
			case 7 -> { // rain
				cloud(g, 2, 0, 1);
				line(g, 5, 18, 42, 13, 56);
				line(g, 5, 32, 42, 27, 56);
				line(g, 5, 46, 42, 41, 56);
			}
			case 8 -> { // night: a moon among stars
				circle(g, 24, 36, 21);
				hole(g, () -> circle(g, 34, 29, 18));
				star(g, 46, 14, 10, 3.6, 4);
				star(g, 52, 42, 7, 2.6, 4);
			}
			case 9 -> { // city
				box(g, 4, 28, 16, 32, 1);
				box(g, 23, 6, 18, 54, 1);
				box(g, 44, 20, 16, 40, 1);
				hole(g, () -> {
					for (int y = 12; y <= 44; y += 10) {
						box(g, 27, y, 4, 5, 0.5);
						box(g, 34, y, 4, 5, 0.5);
					}
					for (int y = 34; y <= 46; y += 10) {
						box(g, 8, y, 3.5, 5, 0.5);
						box(g, 13.5, y, 3.5, 5, 0.5);
					}
					for (int y = 26; y <= 46; y += 10) {
						box(g, 48, y, 3.5, 5, 0.5);
						box(g, 53.5, y, 3.5, 5, 0.5);
					}
				});
			}
			case 10 -> { // eye
				Path2D eye = new Path2D.Double();
				eye.moveTo(2, 32);
				eye.quadTo(32, 2, 62, 32);
				eye.quadTo(32, 62, 2, 32);
				g.fill(eye);
				hole(g, () -> circle(g, 32, 32, 13));
				circle(g, 32, 32, 7.5);
			}
			case 11 -> { // phone
				box(g, 16, 2, 32, 60, 7);
				hole(g, () -> {
					box(g, 21, 9, 22, 38, 2);
					circle(g, 32, 54, 3);
				});
			}
			case 12 -> { // clock
				circle(g, 32, 32, 28);
				hole(g, () -> circle(g, 32, 32, 22));
				line(g, 5, 32, 32, 32, 17);
				line(g, 5, 32, 32, 43, 38);
			}
			case 13 -> { // crown
				shape(g, 8, 48, 4, 16, 19, 30, 32, 8, 45, 30, 60, 16, 56, 48);
				box(g, 8, 51, 48, 7, 2);
				circle(g, 4, 15, 4);
				circle(g, 32, 8, 4.5);
				circle(g, 60, 15, 4);
			}
			case 14 -> { // road
				shape(g, 23, 4, 41, 4, 62, 60, 2, 60);
				hole(g, () -> {
					shape(g, 31, 8, 33, 8, 33.5, 16, 30.5, 16);
					shape(g, 30, 24, 34, 24, 35, 36, 29, 36);
					shape(g, 28.5, 44, 35.5, 44, 37, 60, 27, 60);
				});
			}
			case 15 -> { // home
				shape(g, 32, 4, 62, 32, 2, 32);
				box(g, 10, 29, 44, 31, 2);
				hole(g, () -> box(g, 26, 40, 12, 20, 1));
			}
			case 16 -> { // music: two notes
				line(g, 6, 24, 46, 24, 14, 50, 8, 50, 40);
				shape(g, 24, 11, 50, 5, 50, 16, 24, 22);
				oval(g, 16, 47, 10, 8);
				oval(g, 42, 41, 10, 8);
			}
			case 17 -> { // drink: a glass on a stem
				shape(g, 6, 6, 58, 6, 32, 34);
				line(g, 5, 32, 32, 32, 55);
				line(g, 5, 19, 57, 45, 57);
				hole(g, () -> shape(g, 16, 10, 48, 10, 43, 15.5, 21, 15.5));
			}
			case 18 -> cloud(g, -3, 10, 1.2);
			case 19 -> { // snow: a flake
				for (int i = 0; i < 3; i++) {
					double angle = Math.PI * i / 3;
					double cos = Math.cos(angle);
					double sin = Math.sin(angle);
					line(g, 5, 32 - cos * 28, 32 - sin * 28, 32 + cos * 28, 32 + sin * 28);
					for (int side = -1; side <= 1; side += 2) {
						double bx = 32 + side * cos * 18;
						double by = 32 + side * sin * 18;
						line(g, 4, bx + Math.cos(angle + side * 0.9) * 8, by + Math.sin(angle + side * 0.9) * 8, bx, by, bx + Math.cos(angle - side * 0.9) * 8,
							by + Math.sin(angle - side * 0.9) * 8);
					}
				}
			}
			case 20 -> shape(g, 38, 2, 12, 36, 28, 36, 22, 62, 52, 24, 35, 24); // lightning
			case 21 -> { // tree
				shape(g, 32, 2, 50, 26, 14, 26);
				shape(g, 32, 14, 56, 44, 8, 44);
				box(g, 27, 42, 10, 20, 1.5);
			}
			case 22 -> { // flower
				for (int i = 0; i < 5; i++) {
					double angle = Math.PI * 2 * i / 5 - Math.PI / 2;
					circle(g, 32 + Math.cos(angle) * 13, 24 + Math.sin(angle) * 13, 9.5);
				}
				line(g, 5, 32, 38, 32, 62);
				shape(g, 32, 54, 46, 44, 46, 52, 34, 60);
				hole(g, () -> circle(g, 32, 24, 6));
			}
			case 23 -> { // wave
				for (int row = 0; row < 3; row++) {
					Path2D wave = new Path2D.Double();
					double y = 16 + row * 16;
					wave.moveTo(4, y);
					wave.curveTo(12, y - 12, 20, y - 12, 26, y);
					wave.curveTo(32, y + 10, 38, y + 10, 44, y);
					wave.curveTo(48, y - 8, 54, y - 9, 60, y - 4);
					g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(wave);
				}
			}
			case 24 -> { // mountain
				shape(g, 2, 56, 24, 10, 36, 32, 44, 22, 62, 56);
				hole(g, () -> shape(g, 24, 10, 31, 23, 26.5, 20, 23, 25, 19, 20.5));
			}
			case 25 -> { // bird
				Path2D wings = new Path2D.Double();
				wings.moveTo(3, 26);
				wings.quadTo(20, 12, 32, 34);
				wings.quadTo(44, 12, 61, 26);
				wings.quadTo(44, 24, 34, 48);
				wings.lineTo(30, 48);
				wings.quadTo(20, 24, 3, 26);
				g.fill(wings);
			}
			case 26 -> shape(g, 32, 2, 37, 10, 37, 24, 61, 38, 61, 44, 37, 38, 36, 52, 44, 58, 44, 62, 32, 59, 20, 62, 20, 58, 28, 52, 27, 38, 3, 44, 3, 38, 27, 24, 27, 10); // plane
			case 27 -> { // train
				box(g, 10, 4, 44, 44, 9);
				line(g, 5, 20, 50, 12, 60);
				line(g, 5, 44, 50, 52, 60);
				hole(g, () -> {
					box(g, 16, 11, 14, 14, 2);
					box(g, 34, 11, 14, 14, 2);
					circle(g, 21, 37, 3.5);
					circle(g, 43, 37, 3.5);
				});
			}
			case 28 -> { // boat
				shape(g, 4, 42, 60, 42, 50, 58, 14, 58);
				line(g, 4, 30, 42, 30, 4);
				shape(g, 34, 6, 56, 36, 34, 36);
				shape(g, 26, 14, 26, 36, 10, 36);
			}
			case 29 -> { // bike
				circle(g, 15, 44, 13);
				circle(g, 49, 44, 13);
				hole(g, () -> {
					circle(g, 15, 44, 8.5);
					circle(g, 49, 44, 8.5);
				});
				line(g, 4, 15, 44, 26, 22, 42, 22, 49, 44);
				line(g, 4, 26, 22, 33, 44, 15, 44);
				line(g, 4, 33, 44, 42, 22);
				line(g, 4, 21, 16, 30, 16);
				line(g, 4, 42, 22, 40, 12, 47, 12);
			}
			case 30 -> { // key
				circle(g, 17, 32, 15);
				box(g, 28, 28, 34, 8, 2);
				box(g, 44, 34, 6, 11, 1.5);
				box(g, 54, 34, 6, 8, 1.5);
				hole(g, () -> circle(g, 15, 32, 6));
			}
			case 31 -> { // lock
				arc(g, 7, 32, 24, 13, 0, 180);
				line(g, 7, 19, 24, 19, 30);
				line(g, 7, 45, 24, 45, 30);
				box(g, 10, 28, 44, 32, 6);
				hole(g, () -> {
					circle(g, 32, 41, 5);
					box(g, 30, 42, 4, 10, 1.5);
				});
			}
			case 32 -> { // door
				box(g, 12, 2, 40, 60, 3);
				hole(g, () -> {
					circle(g, 43, 34, 3.5);
					box(g, 18, 8, 28, 20, 1.5);
					box(g, 18, 40, 18, 16, 1.5);
				});
				box(g, 20, 10, 24, 16, 1);
				box(g, 20, 42, 14, 12, 1);
			}
			case 33 -> { // bed
				box(g, 2, 14, 8, 44, 2.5);
				box(g, 2, 34, 60, 14, 3);
				box(g, 54, 26, 8, 32, 2.5);
				box(g, 13, 23, 18, 10, 4);
			}
			case 34 -> { // book
				shape(g, 30, 14, 4, 8, 4, 50, 30, 56);
				shape(g, 34, 14, 60, 8, 60, 50, 34, 56);
				hole(g, () -> {
					line(g, 3, 10, 20, 24, 23);
					line(g, 3, 10, 30, 24, 33);
					line(g, 3, 10, 40, 24, 43);
					line(g, 3, 54, 20, 40, 23);
					line(g, 3, 54, 30, 40, 33);
					line(g, 3, 54, 40, 40, 43);
				});
			}
			case 35 -> { // letter
				box(g, 2, 12, 60, 40, 5);
				hole(g, () -> line(g, 4.5, 6, 17, 32, 37, 58, 17));
			}
			case 36 -> { // camera
				box(g, 20, 8, 24, 14, 4);
				box(g, 2, 16, 60, 40, 7);
				hole(g, () -> {
					circle(g, 32, 36, 14);
					box(g, 48, 22, 7, 4, 1.5);
				});
				circle(g, 32, 36, 8.5);
			}
			case 37 -> { // ring with a stone
				circle(g, 32, 42, 20);
				hole(g, () -> circle(g, 32, 42, 13));
				shape(g, 32, 2, 43, 11, 32, 25, 21, 11);
			}
			case 38 -> { // diamond
				shape(g, 16, 8, 48, 8, 62, 25, 32, 60, 2, 25);
				hole(g, () -> {
					line(g, 3.5, 2, 25, 62, 25);
					line(g, 3.5, 16, 8, 24, 25, 32, 60);
					line(g, 3.5, 48, 8, 40, 25, 32, 60);
					line(g, 3.5, 24, 25, 32, 8, 40, 25);
				});
			}
			case 39 -> { // gift
				circle(g, 22, 10, 8);
				circle(g, 42, 10, 8);
				box(g, 2, 16, 60, 13, 3);
				box(g, 7, 31, 50, 30, 3);
				hole(g, () -> {
					circle(g, 22, 10, 3.5);
					circle(g, 42, 10, 3.5);
					box(g, 28.5, 16, 7, 46, 0);
				});
			}
			case 40 -> { // coffee
				arc(g, 6, 46, 40, 10, -90, 180);
				box(g, 6, 24, 40, 34, 8);
				g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				for (int x = 16; x <= 36; x += 10) {
					Path2D steam = new Path2D.Double();
					steam.moveTo(x, 18);
					steam.curveTo(x - 5, 13, x + 5, 9, x, 4);
					g.draw(steam);
				}
			}
			case 41 -> { // tear
				circle(g, 32, 40, 20);
				shape(g, 32, 2, 49.5, 30.5, 14.5, 30.5);
				hole(g, () -> arc(g, 4.5, 32, 40, 11.5, 195, 60));
			}
			case 42 -> { // lips
				Path2D upper = new Path2D.Double();
				upper.moveTo(2, 31);
				upper.quadTo(14, 8, 26, 17);
				upper.quadTo(32, 21, 38, 17);
				upper.quadTo(50, 8, 62, 31);
				upper.quadTo(32, 37, 2, 31);
				g.fill(upper);
				Path2D lower = new Path2D.Double();
				lower.moveTo(5, 35);
				lower.quadTo(32, 42, 59, 35);
				lower.quadTo(32, 66, 5, 35);
				g.fill(lower);
			}
			case 43 -> { // smile
				circle(g, 32, 32, 29);
				hole(g, () -> {
					circle(g, 22, 25, 4.5);
					circle(g, 42, 25, 4.5);
					arc(g, 5, 32, 32, 15, 200, 140);
				});
			}
			case 44 -> { // hand
				box(g, 15, 26, 32, 36, 10);
				box(g, 15, 10, 7, 28, 3.5);
				box(g, 23.5, 3, 7, 30, 3.5);
				box(g, 32, 5, 7, 30, 3.5);
				box(g, 40.5, 10, 7, 26, 3.5);
				shape(g, 44, 36, 56, 24, 61, 29, 50, 50, 44, 50);
			}
			case 45 -> { // shoe
				Path2D shoe = new Path2D.Double();
				shoe.moveTo(4, 14);
				shoe.lineTo(20, 14);
				shoe.curveTo(22, 26, 34, 30, 46, 33);
				shoe.curveTo(56, 35, 61, 40, 61, 48);
				shoe.lineTo(4, 48);
				shoe.closePath();
				g.fill(shoe);
				box(g, 3, 51, 59, 7, 3);
				hole(g, () -> {
					line(g, 3, 25, 23, 20, 28);
					line(g, 3, 32, 27, 27, 32);
				});
			}
			case 46 -> { // wings
				Path2D wing = new Path2D.Double();
				wing.moveTo(4, 34);
				wing.curveTo(10, 14, 34, 4, 61, 8);
				wing.curveTo(58, 16, 52, 20, 46, 22);
				wing.curveTo(52, 22, 56, 23, 58, 24);
				wing.curveTo(54, 31, 47, 34, 40, 35);
				wing.curveTo(45, 36, 48, 37, 50, 39);
				wing.curveTo(44, 46, 36, 47, 28, 46);
				wing.curveTo(26, 54, 16, 58, 8, 56);
				wing.curveTo(12, 50, 6, 44, 4, 34);
				g.fill(wing);
			}
			case 47 -> { // ghost
				circle(g, 32, 26, 22);
				shape(g, 10, 26, 54, 26, 54, 60, 46.7, 53, 39.3, 60, 32, 53, 24.7, 60, 17.3, 53, 10, 60);
				hole(g, () -> {
					oval(g, 24, 26, 4.5, 6);
					oval(g, 40, 26, 4.5, 6);
				});
			}
			case 48 -> { // world
				circle(g, 32, 32, 29);
				hole(g, () -> {
					g.setStroke(new BasicStroke(3.5f));
					g.draw(new Ellipse2D.Double(19, 3, 26, 58));
					line(g, 3.5, 3, 32, 61, 32);
					line(g, 3.5, 32, 3, 32, 61);
					line(g, 3.5, 8, 18, 56, 18);
					line(g, 3.5, 8, 46, 56, 46);
				});
			}
			case 49 -> { // flag
				line(g, 6, 10, 5, 10, 60);
				Path2D flag = new Path2D.Double();
				flag.moveTo(13, 8);
				flag.curveTo(26, 0, 36, 16, 58, 8);
				flag.lineTo(58, 36);
				flag.curveTo(36, 44, 26, 28, 13, 36);
				flag.closePath();
				g.fill(flag);
			}
			case 50 -> { // radio
				line(g, 4, 14, 20, 44, 4);
				box(g, 2, 18, 60, 40, 7);
				hole(g, () -> {
					circle(g, 20, 38, 11);
					box(g, 37, 27, 19, 5, 2);
					box(g, 37, 36, 19, 5, 2);
					box(g, 37, 45, 19, 5, 2);
				});
				circle(g, 20, 38, 5.5);
			}
			case 51 -> { // tv
				line(g, 4, 22, 2, 32, 12, 42, 2);
				box(g, 2, 12, 60, 42, 7);
				box(g, 20, 56, 24, 5, 2);
				hole(g, () -> box(g, 8, 18, 48, 30, 3));
				shape(g, 27, 25, 41, 33, 27, 41);
			}
			case 52 -> { // guitar
				circle(g, 21, 44, 17);
				circle(g, 33, 31, 11);
				shape(g, 36, 24, 53, 5, 60, 11, 42, 30);
				shape(g, 51, 2, 62, 2, 62, 13);
				hole(g, () -> {
					circle(g, 23, 42, 5.5);
					line(g, 3, 11, 50, 17, 56);
				});
			}
			case 53 -> { // microphone
				box(g, 21, 2, 22, 36, 11);
				arc(g, 5, 32, 26, 18, 180, 180);
				line(g, 5, 32, 44, 32, 56);
				line(g, 5, 20, 58, 44, 58);
			}
			case 54 -> { // ball
				circle(g, 32, 32, 29);
				hole(g, () -> {
					line(g, 3.5, 3, 32, 61, 32);
					line(g, 3.5, 32, 3, 32, 61);
					arc(g, 3.5, 0, 32, 20, -68, 136);
					arc(g, 3.5, 64, 32, 20, 112, 136);
				});
			}
			case 55 -> { // dice
				box(g, 5, 5, 54, 54, 11);
				hole(g, () -> {
					circle(g, 19, 19, 5);
					circle(g, 45, 19, 5);
					circle(g, 32, 32, 5);
					circle(g, 19, 45, 5);
					circle(g, 45, 45, 5);
				});
			}
			case 56 -> { // rocket
				Path2D body = new Path2D.Double();
				body.moveTo(32, 1);
				body.curveTo(44, 10, 46, 26, 43, 44);
				body.lineTo(21, 44);
				body.curveTo(18, 26, 20, 10, 32, 1);
				g.fill(body);
				shape(g, 21, 30, 8, 46, 8, 52, 22, 46);
				shape(g, 43, 30, 56, 46, 56, 52, 42, 46);
				shape(g, 25, 47, 39, 47, 32, 63);
				hole(g, () -> circle(g, 32, 22, 6));
			}
			case 57 -> { // umbrella
				g.fill(new Arc2D.Double(2, 6, 60, 56, 0, 180, Arc2D.PIE));
				hole(g, () -> {
					circle(g, 12, 35, 10);
					circle(g, 32, 35, 10);
					circle(g, 52, 35, 10);
				});
				line(g, 5, 32, 28, 32, 53);
				arc(g, 5, 26, 53, 6, 180, 180);
			}
			case 58 -> { // candle
				Path2D flame = new Path2D.Double();
				flame.moveTo(32, 2);
				flame.curveTo(40, 12, 41, 18, 38, 23);
				flame.curveTo(35, 27, 29, 27, 26, 23);
				flame.curveTo(23, 18, 24, 12, 32, 2);
				g.fill(flame);
				box(g, 20, 30, 24, 32, 3);
				hole(g, () -> line(g, 3, 20, 38, 27, 42, 20, 46));
			}
			case 59 -> { // light: a bulb
				circle(g, 32, 24, 20);
				shape(g, 20, 36, 44, 36, 40, 48, 24, 48);
				box(g, 24, 50, 16, 5, 2);
				box(g, 27, 57, 10, 5, 2);
				hole(g, () -> line(g, 3.5, 26, 24, 30, 30, 34, 24, 38, 30));
			}
			case 60 -> { // heartbreak: a heart broken in two
				circle(g, 20, 23, 14);
				circle(g, 44, 23, 14);
				shape(g, 7.5, 29.5, 56.5, 29.5, 32, 58);
				hole(g, () -> line(g, 4.5, 33, 8, 26, 22, 37, 32, 29, 44, 32, 60));
			}
			case 61 -> { // bottle
				box(g, 26, 2, 12, 8, 2);
				shape(g, 27, 8, 37, 8, 37, 20, 46, 30, 46, 60, 18, 60, 18, 30, 27, 20);
				hole(g, () -> box(g, 22, 36, 20, 14, 2));
			}
			case 62 -> { // sunglasses
				line(g, 5, 4, 24, 60, 24);
				box(g, 5, 24, 24, 20, 8);
				box(g, 35, 24, 24, 20, 8);
				box(g, 26, 24, 12, 6, 2);
			}
			case 63 -> { // headphones
				arc(g, 7, 32, 34, 23, 0, 180);
				box(g, 4, 32, 14, 26, 6);
				box(g, 46, 32, 14, 26, 6);
			}
			case 64 -> { // cat: its head
				shape(g, 8, 6, 26, 20, 10, 30);
				shape(g, 56, 6, 38, 20, 54, 30);
				oval(g, 32, 36, 24, 21);
				hole(g, () -> {
					oval(g, 22, 33, 4, 5.5);
					oval(g, 42, 33, 4, 5.5);
					shape(g, 29, 42, 35, 42, 32, 46);
				});
			}
			case 65 -> { // dog: its head
				oval(g, 32, 34, 20, 23);
				shape(g, 14, 12, 26, 14, 14, 42, 4, 34);
				shape(g, 50, 12, 38, 14, 50, 42, 60, 34);
				hole(g, () -> {
					circle(g, 24, 30, 3.5);
					circle(g, 40, 30, 3.5);
					oval(g, 32, 43, 6, 4.5);
				});
			}
			case 66 -> { // fish
				Path2D fish = new Path2D.Double();
				fish.moveTo(2, 32);
				fish.quadTo(22, 8, 44, 30);
				fish.lineTo(62, 14);
				fish.lineTo(62, 50);
				fish.lineTo(44, 34);
				fish.quadTo(22, 56, 2, 32);
				g.fill(fish);
				hole(g, () -> circle(g, 16, 29, 3.5));
			}
			case 67 -> { // butterfly
				oval(g, 17, 20, 15, 14);
				oval(g, 47, 20, 15, 14);
				oval(g, 20, 44, 11, 12);
				oval(g, 44, 44, 11, 12);
				box(g, 29, 12, 6, 44, 3);
				line(g, 3, 31, 12, 25, 3);
				line(g, 3, 33, 12, 39, 3);
			}
			case 68 -> { // leaf
				Path2D leaf = new Path2D.Double();
				leaf.moveTo(56, 6);
				leaf.curveTo(20, 6, 4, 26, 12, 52);
				leaf.curveTo(40, 58, 58, 40, 56, 6);
				g.fill(leaf);
				line(g, 4.5, 12, 52, 4, 60);
				hole(g, () -> line(g, 3.5, 14, 50, 46, 16));
			}
			case 69 -> { // apple
				circle(g, 22, 38, 18);
				circle(g, 42, 38, 18);
				oval(g, 32, 44, 20, 16);
				line(g, 4.5, 32, 22, 34, 8);
				Path2D leaf = new Path2D.Double();
				leaf.moveTo(35, 14);
				leaf.quadTo(42, 2, 54, 6);
				leaf.quadTo(48, 18, 35, 14);
				g.fill(leaf);
			}
			case 70 -> { // icecream
				circle(g, 32, 20, 17);
				shape(g, 15, 30, 49, 30, 32, 62);
				hole(g, () -> {
					line(g, 3, 22, 38, 36, 52);
					line(g, 3, 42, 38, 28, 52);
				});
			}
			case 71 -> { // cake: with a candle
				box(g, 30, 14, 4, 12, 1);
				Path2D flame = new Path2D.Double();
				flame.moveTo(32, 2);
				flame.curveTo(37, 8, 36, 13, 32, 13);
				flame.curveTo(28, 13, 27, 8, 32, 2);
				g.fill(flame);
				box(g, 6, 28, 52, 30, 5);
				hole(g, () -> {
					g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					Path2D icing = new Path2D.Double();
					icing.moveTo(6, 40);
					icing.curveTo(12, 48, 18, 48, 24, 40);
					icing.curveTo(30, 48, 36, 48, 42, 40);
					icing.curveTo(48, 48, 54, 48, 58, 40);
					g.draw(icing);
				});
			}
			case 72 -> { // balloon
				oval(g, 32, 24, 19, 22);
				shape(g, 28, 44, 36, 44, 38, 50, 26, 50);
				g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				Path2D string = new Path2D.Double();
				string.moveTo(32, 50);
				string.curveTo(26, 54, 38, 58, 32, 62);
				g.draw(string);
				hole(g, () -> arc(g, 3.5, 32, 24, 11, 100, 60));
			}
			case 73 -> { // trophy
				shape(g, 16, 6, 48, 6, 46, 26, 38, 36, 26, 36, 18, 26);
				arc(g, 5, 16, 16, 9, 90, 180);
				arc(g, 5, 48, 16, 9, -90, 180);
				box(g, 29, 34, 6, 14, 1);
				box(g, 18, 48, 28, 10, 3);
			}
			case 74 -> { // moneybag
				shape(g, 22, 4, 42, 4, 38, 16, 26, 16);
				Path2D bag = new Path2D.Double();
				bag.moveTo(26, 16);
				bag.lineTo(38, 16);
				bag.curveTo(52, 26, 58, 40, 54, 52);
				bag.curveTo(52, 59, 44, 61, 32, 61);
				bag.curveTo(20, 61, 12, 59, 10, 52);
				bag.curveTo(6, 40, 12, 26, 26, 16);
				g.fill(bag);
				hole(g, () -> {
					line(g, 3.5, 22, 16.5, 42, 16.5);
					circle(g, 32, 41, 9);
				});
				circle(g, 32, 41, 5);
			}
			case 75 -> { // hourglass
				box(g, 12, 2, 40, 7, 3);
				box(g, 12, 55, 40, 7, 3);
				shape(g, 16, 8, 48, 8, 34, 32, 48, 56, 16, 56, 30, 32);
				hole(g, () -> shape(g, 22, 13, 42, 13, 32, 27));
			}
			case 76 -> { // place: a pin on the map
				circle(g, 32, 24, 20);
				shape(g, 14, 33, 50, 33, 32, 62);
				hole(g, () -> circle(g, 32, 24, 8));
			}
			case 77 -> { // anchor
				circle(g, 32, 10, 8);
				hole(g, () -> circle(g, 32, 10, 3.5));
				line(g, 6, 32, 18, 32, 56);
				line(g, 6, 20, 26, 44, 26);
				arc(g, 6, 32, 34, 22, 200, 140);
				shape(g, 4, 34, 16, 38, 8, 48);
				shape(g, 60, 34, 48, 38, 56, 48);
			}
			case 78 -> { // bell
				Path2D bell = new Path2D.Double();
				bell.moveTo(32, 6);
				bell.curveTo(46, 6, 50, 18, 50, 30);
				bell.curveTo(50, 40, 54, 44, 58, 50);
				bell.lineTo(6, 50);
				bell.curveTo(10, 44, 14, 40, 14, 30);
				bell.curveTo(14, 18, 18, 6, 32, 6);
				g.fill(bell);
				circle(g, 32, 5, 4);
				circle(g, 32, 56, 6);
			}
			case 79 -> { // gamepad
				box(g, 4, 18, 56, 30, 14);
				circle(g, 14, 44, 10);
				circle(g, 50, 44, 10);
				hole(g, () -> {
					box(g, 12, 30, 14, 4.5, 1);
					box(g, 16.75, 25.25, 4.5, 14, 1);
					circle(g, 42, 28, 3.2);
					circle(g, 49, 35, 3.2);
				});
			}
			case 80 -> { // smoke: a plain cloud of it, rising
				cloud(g, 4, 30, 1.0);
				circle(g, 40, 24, 8);
				circle(g, 48, 12, 5);
			}
			case 81 -> { // wristwatch
				box(g, 23, 2, 18, 15, 3);
				box(g, 23, 47, 18, 15, 3);
				box(g, 49, 28, 5, 8, 1.5);
				circle(g, 32, 32, 18);
				hole(g, () -> circle(g, 32, 32, 13));
				line(g, 3.5, 32, 22, 32, 32, 39, 36);
			}
			case 82 -> { // hanger
				arc(g, 4, 32, 11, 6, -90, 270);
				line(g, 4, 32, 17, 32, 22);
				line(g, 5, 32, 22, 58, 46, 6, 46, 32, 22);
			}
			case 83 -> { // praying hands
				shape(g, 31, 4, 31, 52, 18, 60, 11, 54, 20, 44, 19, 24);
				shape(g, 33, 4, 33, 52, 46, 60, 53, 54, 44, 44, 45, 24);
				box(g, 8, 54, 16, 8, 2);
				box(g, 40, 54, 16, 8, 2);
			}
			case 84 -> { // storm cloud with its bolt
				cloud(g, 4, 4, 1.0);
				shape(g, 35, 36, 24, 50, 31, 50, 26, 62, 41, 45, 34, 45, 39, 36);
			}
			case 85 -> { // record
				circle(g, 32, 32, 29);
				hole(g, () -> {
					arc(g, 1.6, 32, 32, 22, 30, 100);
					arc(g, 1.6, 32, 32, 22, 210, 100);
					arc(g, 1.6, 32, 32, 17, 70, 80);
					arc(g, 1.6, 32, 32, 17, 250, 80);
					circle(g, 32, 32, 11);
				});
				circle(g, 32, 32, 8);
				hole(g, () -> circle(g, 32, 32, 2.5));
			}
			case 86 -> { // siren: a beacon with its light
				box(g, 12, 48, 40, 11, 3);
				g.fill(new Arc2D.Double(17, 20, 30, 56, 0, 180, Arc2D.CHORD));
				hole(g, () -> box(g, 24, 28, 4, 14, 2));
				line(g, 4, 32, 4, 32, 11);
				line(g, 4, 9, 14, 15, 20);
				line(g, 4, 55, 14, 49, 20);
			}
			case 87 -> { // palm tree
				line(g, 5, 30, 59, 33, 44, 34, 27);
				shape(g, 34, 26, 22, 16, 8, 19, 3, 30, 16, 24);
				shape(g, 34, 26, 46, 16, 60, 19, 62, 30, 50, 24);
				shape(g, 34, 26, 25, 11, 28, 2, 37, 13);
				shape(g, 34, 26, 43, 8, 54, 7, 40, 20);
				box(g, 14, 57, 36, 5, 2.5);
			}
			case 88 -> { // spotlight on a stage
				// the lamp, turned toward the stage, and its beam
				shape(g, 2, 14, 14, 2, 30, 18, 18, 30);
				shape(g, 25, 27, 31, 21, 63, 42, 47, 61);
				box(g, 2, 58, 60, 4, 2);
			}
			case 89 -> { // dumbbell
				box(g, 14, 29, 36, 6, 3);
				box(g, 8, 17, 9, 30, 3);
				box(g, 47, 17, 9, 30, 3);
				box(g, 2, 23, 7, 18, 2);
				box(g, 55, 23, 7, 18, 2);
			}
			default -> circle(g, 32, 32, 20);
		}
	}
}
