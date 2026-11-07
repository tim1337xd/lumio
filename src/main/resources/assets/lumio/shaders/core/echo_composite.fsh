#version 330

// Radiance's Echo laid over the screen, as the reference does it (Lumio-Design/EchoReference.html): the trail (its faint sheet, a soft copy of
// its lines and the lines themselves), the glow around the item and the hands of now (two blurs of a waved band around them, see EchoGlow: the
// narrow one fully and the wide one at 60 %, shifted a little against the motion and drifting slowly), and a soft rim along their edge with a
// lighter core. The glow and the rim are added as light, in the chosen color; a color darker than gold is also laid over as an opaque band, with
// a lighter tint of its hue in the core and the lines, so blue is as strong as gold. The strength scales them. Nothing of it where the item and the hands
// are. The output is premultiplied: the trail laid over the screen, the light added to it.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const float PARAMETERS = 10.0;
const float TAU = 6.2831853;
const float REFERENCE_LINES = 1080.0;
// the shimmer's loop, in seconds (ten pi): every speed below is a whole number of turns of it, so the loop has no seam
const float LOOP_SECONDS = 31.415927;
// the glow: how much of the wide blur is added, how far the wide blur is shifted against the narrow one, and the slow drift (pixels of a 1080 line
// screen and radians a second); its flicker; and the strength at which it is as bright as the reference
const float WIDE_SHARE = 0.6;
const float WIDE_SHIFT = 1.5;
const float DRIFT_PIXELS = 6.0;
const vec2 DRIFT_SPEEDS = vec2(0.6, 0.8);
const vec2 FLICKER_SPEEDS = vec2(4.4, 2.4);
const float FULL_STRENGTH = 0.7;
// the rim: the band's outer radius and its core's, in pixels of a 1080 line screen; how bright the band is, how much white is in the core
const float RIM_PIXELS = 3.0;
const float RIM_CORE_PIXELS = 1.25;
const float RIM_LIGHT = 0.9;
const float RIM_CORE_LIGHT = 0.95;
const float RIM_CORE_WHITE = 0.25;
// how quickly the rim is full near the edge: high enough that it reads as a band of 2 to 3 pixels on its own, where no patch glows under it
const float RIM_SPREAD = 6.0;
// the trail: the sheet's blur (pixels of a 1080 line screen), its opacity where it is full and the share it is laid with; the lines' soft copy's
// blur and share, the lines' share, and how much white is in their color
const float SHEET_BLUR = 3.0;
const float SHEET_OPACITY = 0.9;
const float SOFT_BLUR = 4.0;
const float SOFT_SHARE = 0.45;
const float LINE_SHARE = 0.95;
const float LINE_WHITE = 0.35;
// the lines are read through a blur this wide (pixels of a 1080 line screen), so the steps of a pixel art edge melt into a smooth line
const float LINE_SOFTEN = 0.75;
// a dark or deep color (pure blue) is made as strong as gold: the glow is laid over the screen as an opaque band in the color at its full value, by
// how much darker than gold the color is, and only a part of it is added as light, in a lighter tint of the same hue; gold itself stays added as
// before. Gold's luminance (the game's #FFAA00), how opaque the band gets at most, how much of the added light stays, and how much white the tint takes
const float GOLD_LUMINANCE = 0.68;
const float BAND_OPACITY = 0.85;
const float DARK_LIGHT_SHARE = 0.4;
const float TINT_WHITE = 0.35;
// the wide glow's shift (lean and drift together) at most, in pixels of a 1080 line screen at the width 1: about the band's width, so no lobe ever
// comes off the item
const float MOST_SHIFT = 9.0;

vec4 parameter(float index) {
    return floor(texture(Sampler0, vec2((index + 0.5) / PARAMETERS, 0.5)) * 255.0 + 0.5);
}

float inside(vec2 uv) {
    return texture(Sampler2, uv).r > 1e-6 ? 1.0 : 0.0;
}

// a signed pair from 16 bits each, in quarter pixels around the middle
vec2 pixelsOf(vec4 texel) {
    return (vec2(texel.r + texel.g * 256.0, texel.b + texel.a * 256.0) - 32768.0) / 4.0;
}

// lays a layer of a color and an opacity over what is below, premultiplied
vec4 over(vec4 below, vec3 color, float opacity) {
    float a = clamp(opacity, 0.0, 1.0);
    return vec4(color * a + below.rgb * (1.0 - a), a + below.a * (1.0 - a));
}

// the average of sixteen looks around a point at two radii (pixels of the picture), in the trail's two channels
vec2 blurred(vec2 uv, vec2 texel, float radius) {
    vec2 sum = vec2(0.0);
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * 0.7853982 + 0.3926991;
        vec2 step = vec2(cos(angle), sin(angle)) * texel * radius;
        sum += texture(Sampler1, uv + step).rg + texture(Sampler1, uv + step * 0.5).rg;
    }
    return sum / 16.0;
}

// how much of the item and the hands lies within a radius (pixels of the screen), softly: the share of looks around the point that fall on them,
// at the radius and halfway, so the band fades out over about a pixel instead of ending on a step
float near(vec2 uv, vec2 pixel, float radius) {
    float sum = 0.0;
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * 0.7853982;
        vec2 step = vec2(cos(angle), sin(angle)) * pixel * radius;
        sum += inside(uv + step) + inside(uv + step * 0.5);
    }
    return sum / 16.0;
}

void main() {
    vec2 uv = texCoord0;
    if (inside(uv) > 0.5) {
        fragColor = vec4(0.0);
        return;
    }
    bool stamp = parameter(1.0).r > 127.5;
    float strength = parameter(1.0).a / 255.0;
    vec3 rgb = parameter(2.0).rgb / 255.0;
    vec4 shimmerTexel = parameter(5.0);
    float t = (shimmerTexel.r + shimmerTexel.g * 256.0) / 65535.0 * LOOP_SECONDS;

    vec2 size = vec2(textureSize(Sampler1, 0));
    vec2 texel = 1.0 / size;
    float scale = size.y / REFERENCE_LINES;

    // the color at its full value, how much darker than gold the color is, and a lighter tint of its hue for what has to stand out
    vec3 vivid = rgb / max(max(rgb.r, max(rgb.g, rgb.b)), 1e-3);
    float dark = clamp((GOLD_LUMINANCE - dot(rgb, vec3(0.2126, 0.7152, 0.0722))) / GOLD_LUMINANCE, 0.0, 1.0);
    vec3 tint = mix(vivid, vec3(1.0), TINT_WHITE * dark);

    // the trail: the faint sheet, a soft copy of the lines, and the lines sharp
    vec4 result = vec4(0.0);
    vec3 lineColor = mix(mix(rgb, tint, dark), vec3(1.0), LINE_WHITE);
    // only while there is a trail: once it has faded out, its picture holds nothing that could be seen, and its many looks are spared
    if (parameter(7.0).b > 127.5) {
        float sheet = clamp(blurred(uv, texel, SHEET_BLUR * scale).g, 0.0, 1.0);
        result = over(result, rgb, sheet * SHEET_OPACITY);
        float soft = clamp(blurred(uv, texel, SOFT_BLUR * scale).r, 0.0, 1.0);
        result = over(result, lineColor, soft * SOFT_SHARE);
        float lines = clamp(mix(texture(Sampler1, uv).r, blurred(uv, texel, LINE_SOFTEN * scale).r, 0.5) * 1.6, 0.0, 1.0);
        result = over(result, lineColor, lines * LINE_SHARE);
    }

    // the glow and the rim of now: added as light, and for a dark color also laid over as a band
    vec3 light = vec3(0.0);
    if (stamp) {
        float bright = strength / FULL_STRENGTH;
        float width = parameter(7.0).r / 100.0;
        vec3 added = mix(rgb, tint * DARK_LIGHT_SHARE, dark);
        // shifted a little against the recent motion, and drifting slowly, so one side is thicker; never further than the band is wide
        vec2 shiftPixels = pixelsOf(parameter(6.0)) + vec2(sin(t * DRIFT_SPEEDS.x), cos(t * DRIFT_SPEEDS.y)) * DRIFT_PIXELS * scale;
        float most = MOST_SHIFT * max(width, 0.25) * scale;
        shiftPixels *= min(1.0, most / max(length(shiftPixels) * WIDE_SHIFT, 1e-3));
        vec2 shift = shiftPixels * texel;
        float flicker = 0.8 + 0.2 * sin(t * FLICKER_SPEEDS.x) * sin(t * FLICKER_SPEEDS.y + 1.7);
        float glow = (flicker * texture(Sampler3, uv - shift).r + WIDE_SHARE * texture(Sampler4, uv - shift * WIDE_SHIFT).r) * bright;
        // the rim lies within a few pixels of the item, where the blurred edge strip (green: not in patches) always reaches: where it is nothing, so is the rim, and its looks
        // around are spared
        float rim = 0.0;
        float core = 0.0;
        if (texture(Sampler3, uv).g > 0.0) {
            vec2 pixel = 1.0 / vec2(textureSize(Sampler2, 0));
            rim = clamp(near(uv, pixel, RIM_PIXELS * scale) * RIM_SPREAD, 0.0, 1.0) * RIM_LIGHT * bright;
            core = clamp(near(uv, pixel, RIM_CORE_PIXELS * scale) * 2.5, 0.0, 1.0) * RIM_CORE_LIGHT * bright;
        }
        result = over(result, vivid, (glow + rim) * BAND_OPACITY * dark);
        result = over(result, tint, core * dark);
        light += added * (glow + rim) + mix(added, vec3(1.0), RIM_CORE_WHITE) * core * (1.0 - dark * (1.0 - DARK_LIGHT_SHARE));
    }
    fragColor = vec4(result.rgb + light, result.a);
}
