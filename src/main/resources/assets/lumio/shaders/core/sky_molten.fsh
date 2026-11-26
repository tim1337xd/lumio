#version 330

// The Molten sky: glowing lines of light over a dark sky in one color. See MoltenSky.
//   texel 0: the clock in seconds (low byte, high byte, fraction)
//   texel 1: the lines' color, and their brightness (in hundredths)
//   texel 2: the horizon's color behind them, and how much of the sky is shown
//   texel 3: the top of the sky's color, and how many lines (0 few, 1 normal, 2 many)
//   texel 4: how much a flash of lightning lights the lines, how thick the lines are and how far their glow reaches (in hundredths), whether pulses run
// Each of three layers is one contour line (the middle level) of its own smooth noise on the view's direction, bent by a second noise (domain
// warping): on a sphere it is seamless, and it winds across the whole sky and down towards the horizon. One level per layer, so there are no rings
// of levels as on a map; the three layers cross and merge. Along each line a slow noise sets how hot it glows: some stretches glow nearly white with
// a wide soft halo, others fade out completely, so lines start and end softly. The fields drift slowly through their third dimension, so the lines
// flow without ever repeating, and now and then a pulse of light runs along one of them.
// It is drawn at the screen's full size; the lines are anti-aliased by their distance in pixels, over about one pixel. On the lower qualities
// (HALF_SIZE) it is drawn at half the size and made larger by sky_molten_up.fsh: distances stay in pixels of the screen, so the lines keep their
// width, and the edge of a line is soft over one pixel of the smaller picture.

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec3 direction;

out vec4 fragColor;

// how far the noise is stretched across the sky (more lines for a larger number): few, normal, many
const vec3 SCALE = vec3(2.4, 3.2, 4.4);
const float DRIFT = 0.035;
const float WARP_SCALE = 1.3;
const float WARP = 1.4;
// the level of the noise the line follows
const float LEVEL = 0.5;
// the core of a line and its halo, in pixels, at a hot stretch; a cool one is thinner
const float CORE_PIXELS = 1.1;
const float HALO_PIXELS = 3.5;
const float WIDE_PIXELS = 34.0;
const float WIDE_PIXELS_COOL = 10.0;
const float WIDE_FIELD = 28.0;
// how steep the field must be (per radian of the view, for its scale) for its line to show: gentler slopes are tiny rings and saddles
const float STEEP_LOW = 0.18;
const float STEEP_HIGH = 0.45;
const float THIN = 0.6;
// how much wider than its usual width a hot stretch is
const float HOT_WIDTH = 1.6;
// how much brighter the core of the hottest stretches is
const float HOT_BOOST = 1.3;
// the sky darkens toward the edges of the view (the angle from its middle, as cosines: full light inside, darkest outside) by this much
const float VIGNETTE_INNER = 0.94;
const float VIGNETTE_OUTER = 0.62;
const float VIGNETTE = 0.45;
// how bright the halo and the wide glow are next to the core
const float HALO = 0.85;
const float WIDE = 0.45;
// the pale core of a hot stretch (#fff0c8)
const vec3 PALE = vec3(1.0, 0.941, 0.784);
// how hot a line glows along its length: the slow noise's scale and drift, and where its stretches fade out and glow fully
const float HEAT_SCALE = 1.1;
const float HEAT_DRIFT = 0.02;
const float COLD = 0.36;
const float HOT = 0.74;
// a pulse: how often a stretch has one (a share in a window of seconds), how long the window is, how fast and how long the pulse is, and over which
// part of its window and of its stretch it comes and goes, so it never starts or ends at once
const float PULSE_SHARE = 0.15;
const float PULSE_WINDOW = 9.0;
const float PULSE_SPEED = 0.22;
const float PULSE_SHARPNESS = 70.0;
const float PULSE_LIGHT = 1.4;
const float PULSE_FADE = 0.14;
// how far below the horizon the lines go before they fade into it
const float BELOW = 0.22;
// how bright the second and third layers of lines are next to the first
const float SECOND_LAYER = 0.85;
const float THIRD_LAYER = 0.7;
// the most the width wavers to, the most light a stretch has with a pulse on it, and what is too little to be seen (with the sky's roll-off)
const float WAVER_MOST = 1.15;
const float LIT_MOST = 2.96;
const float UNSEEN = 0.0007;
#ifdef HALF_SIZE
// how many pixels of the screen one pixel of this picture covers across
const float PIXEL = 2.0;
#else
const float PIXEL = 1.0;
#endif

vec4 parameter(int texel) {
    return texelFetch(Sampler0, ivec2(texel, 0), 0);
}

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

// one layer's line at a point: the light of its glow (in the lines' color), of its core, and how hot it is there
struct Light {
    float glow;
    float core;
    float heat;
};

Light lineLight(vec3 dir, float pixelAngle, float seconds, vec3 seed, float scale, vec3 drift, float thickness, float glowSize, float pulses, float most) {
    vec3 p = dir * scale + seed + drift * seconds;
    vec3 q = p * WARP_SCALE;
    vec3 warp = vec3(noise(q + vec3(1.7, 9.2, 3.1)), noise(q + vec3(8.3, 2.8, 5.4)), noise(q + vec3(4.1, 6.6, 7.7))) - 0.5;
    vec3 r = p + warp * WARP;
    float field = noise(r) * 0.65 + noise(r * 2.03 + 11.0) * 0.35;
    // the distance to the line, in pixels, by the true length of the field's slope on screen (fwidth adds its two parts, which is not round and drew
    // straight streaks), held up to a least slope: where the slope vanishes, at a saddle or a peak, the distance would collapse and the halo smear
    float perPixel = length(vec2(dFdx(field), dFdy(field))) / PIXEL;
    // how steep the field is per radian of the view, for its scale: low where the line closes into a tiny ring around a peak or meets a saddle
    float steep = perPixel / (pixelAngle * scale);
    float open = smoothstep(STEEP_LOW, STEEP_HIGH, steep);
    float apart = abs(field - LEVEL) / max(perPixel, STEEP_HIGH * pixelAngle * scale);
    // Far from its line a layer gives less than a step of the screen's colors, however hot it would be there: what it would need for that is not worked out.
    float widest = HOT_WIDTH * WAVER_MOST;
    if (apart > CORE_PIXELS * widest * thickness * 0.5 + 0.5 * PIXEL) {
        float reach = exp(-apart / (HALO_PIXELS * widest * glowSize)) * HALO
            + exp(-apart / (WIDE_PIXELS * glowSize)) * exp(-abs(field - LEVEL) * WIDE_FIELD) * WIDE;
        if (reach * LIT_MOST * most < UNSEEN) {
            return Light(0.0, 0.0, 0.0);
        }
    }

    // how hot this stretch glows: from nothing to fully, slowly along the line and slowly in time
    float heat = smoothstep(COLD, HOT, noise(r * HEAT_SCALE + seed * 3.0 + vec3(0.0, seconds * HEAT_DRIFT, 0.0)));
    // now and then a pulse runs along a stretch, lighting even a cool one a little
    float along = noise(r * 0.7 + 31.0) * 6.0;
    float cycle = seconds / PULSE_WINDOW + hash(seed);
    float window = floor(cycle);
    float inWindow = fract(cycle);
    float inStretch = fract(along);
    float fade = smoothstep(0.0, PULSE_FADE, inWindow) * (1.0 - smoothstep(1.0 - PULSE_FADE, 1.0, inWindow))
        * smoothstep(0.0, PULSE_FADE, inStretch) * (1.0 - smoothstep(1.0 - PULSE_FADE, 1.0, inStretch));
    float chosen = step(1.0 - PULSE_SHARE, hash(vec3(floor(along), seed.y, window)));
    float travel = fract(along - seconds * PULSE_SPEED + hash(vec3(seed.x, 5.0, window))) - 0.5;
    float pulse = pulses * chosen * fade * exp(-travel * travel * PULSE_SHARPNESS) * PULSE_LIGHT;
    float lit = heat + pulse * (0.4 + heat);

    // a hot stretch is wider, and the width wavers a little along the line
    float width = mix(THIN, HOT_WIDTH, heat) * (0.85 + 0.3 * noise(r * 2.7 + 17.0));
    // the core's coverage of the pixel: full within its half width, falling off over one pixel at its edge, so it stays smooth at any width
    float halfWidth = CORE_PIXELS * width * thickness * 0.5;
    float core = (1.0 - smoothstep(halfWidth - 0.5 * PIXEL, halfWidth + 0.5 * PIXEL, apart)) * (1.0 + HOT_BOOST * heat * heat);
    float halo = exp(-apart / (HALO_PIXELS * width * glowSize));
    // the wide glow is also held to the line in the field itself: where the field is nearly flat it would otherwise smear into a haze
    float wide = exp(-apart / (mix(WIDE_PIXELS_COOL, WIDE_PIXELS, heat) * glowSize)) * exp(-abs(field - LEVEL) * WIDE_FIELD) * heat;
    Light light;
    light.glow = (halo * HALO + wide * WIDE) * lit;
    // the thin core fades out where the line curls too tight (tiny rings) or runs into a saddle; the halo stays whole there, soft and round,
    // since cutting it left dark holes in the glow
    light.core = core * lit * open;
    light.heat = heat;
    return light;
}

vec3 molten(vec3 dir) {
    vec4 clock = parameter(0) * 255.0;
    float seconds = clock.r + clock.g * 256.0 + clock.b / 255.0;
    vec4 line = parameter(1);
    vec3 lineColor = line.rgb;
    float brightness = line.a * 2.55;
    vec3 horizon = parameter(2).rgb;
    vec4 zenithTexel = parameter(3);
    vec3 zenith = zenithTexel.rgb;
    int lines = int(zenithTexel.a * 255.0 + 0.5);
    vec4 shape = parameter(4);
    float flash = shape.r;
    float thickness = shape.g * 2.55;
    float glowSize = shape.b * 2.55;
    float pulses = shape.a;

    // at the horizon and below it exactly the horizon's color, which is the fog's: the world meets the sky without a seam
    vec3 sky = mix(horizon, zenith, smoothstep(0.0, 0.75, dir.y));
    float scale = lines == 0 ? SCALE.x : lines == 1 ? SCALE.y : SCALE.z;
    // how wide a pixel is, as an angle of the view
    float pixelAngle = max(length(vec2(length(dFdx(dir)), length(dFdy(dir)))) * 0.7071 / PIXEL, 1e-6);

    // under the horizon's fade there is only the horizon's color
    if (dir.y <= -BELOW && flash <= 0.0) {
        return sky;
    }
    float most = brightness * (1.0 + flash);

    // three layers at their own scales, each drifting its own way: their lines cross and merge, and light up where they meet
    Light first = lineLight(dir, pixelAngle, seconds, vec3(0.0, 0.0, 0.0), scale, vec3(0.0, 0.0, DRIFT), thickness, glowSize, pulses, most);
    Light second = lineLight(dir, pixelAngle, seconds, vec3(5.3, 1.9, 8.1), scale * 0.8, vec3(DRIFT * 0.6, 0.0, -DRIFT * 0.5), thickness, glowSize, pulses, most);
    Light third = lineLight(dir, pixelAngle, seconds, vec3(2.6, 7.4, 4.8), scale * 1.25, vec3(-DRIFT * 0.4, DRIFT * 0.3, DRIFT * 0.7), thickness, glowSize, pulses, most);
    float glow = first.glow + second.glow * SECOND_LAYER + third.glow * THIRD_LAYER;
    vec3 core = mix(lineColor, PALE, first.heat) * first.core + mix(lineColor, PALE, second.heat) * second.core * SECOND_LAYER
        + mix(lineColor, PALE, third.heat) * third.core * THIRD_LAYER;

    float shown = smoothstep(-BELOW, 0.05, dir.y) * brightness * (1.0 + flash);
    vec3 light = max(lineColor * glow + core, vec3(0.0)) * shown;
    // the lightning lights the whole sky a little too
    light += lineColor * flash * 0.08;
    // a soft roll-off of the light instead of a hard clip where lines cross; the sky behind stays exactly the fog's color
    vec3 color = sky + (1.0 - sky) * (1.0 - exp(-light * 1.4));
    // never darker than the sky behind: a slope that vanished or a value that went wrong at a line's end left black drops on some lines
    color = any(isnan(color)) ? sky : max(color, sky);
    // a soft vignette toward the edges of the view, held off near the horizon so the fog meets the sky as it is
    vec3 forward = -vec3(ModelViewMat[0][2], ModelViewMat[1][2], ModelViewMat[2][2]);
    float edge = 1.0 - smoothstep(VIGNETTE_OUTER, VIGNETTE_INNER, dot(dir, normalize(forward)));
    return color * (1.0 - VIGNETTE * edge * smoothstep(0.0, 0.25, dir.y));
}

void main() {
    fragColor = vec4(molten(normalize(direction)), parameter(2).a);
}
