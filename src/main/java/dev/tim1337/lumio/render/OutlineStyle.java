package dev.tim1337.lumio.render;

/**
 * @param kind            outline kind
 * @param width           how far the outline extends beyond the item, in pixels of a 16 pixel wide item
 * @param strength        for a glow, the opacity right next to the item, from 0 to 1
 * @param rgb             the color
 * @param glowSmoothness  for a glow, how much it is smoothed so it does not follow the item's edges, from 0 to 1
 * @param opacity         how much of it shows, from 0 to 1, for fading between looks
 */
public record OutlineStyle(OutlineKind kind, float width, float strength, int rgb, float glowSmoothness, float opacity) {
}
