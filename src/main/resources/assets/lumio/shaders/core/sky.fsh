#version 330

uniform sampler2D Sampler0;
#if defined(SPLIT) || defined(VOLUME_PASS) || defined(CLOUD_COMPOSITE)
// the light made by the pass before, at 1 / LOWRES_DIVISOR of the size of the screen, or the volumetric clouds, at 1 / VOLUME_DIVISOR of it;
// in the pass of the volumetric clouds, the clouds of the frame before
uniform sampler2D Sampler1;
#endif
#ifdef CLOUD_COMPOSITE
// the depth of the finished world
uniform sampler2D Sampler2;
#endif

#if defined(VOLUME_PASS) || defined(CLOUD_COMPOSITE)
#moj_import <minecraft:projection.glsl>
#endif

in vec3 direction;

out vec4 fragColor;

// A sky that is made for every pixel: nebulae, stars, aurora. Only plain float math is used, so it works with every
// graphics backend.
//
// What the shader needs to know is in the first texels of the texture, from 0 to 255 per channel:
//   texel 0: how far the animation is (red and green, 16 bits), the theme (blue), the opacity (alpha)
//   texel 1: the rotation of the stars (red and green, 16 bits), the brightness times 64 (blue), the star density (alpha)
//   texel 2: the color (red, green, blue), how much of the mood of an island of the Nether (alpha)
//   texel 3 and 4: the color of the background at the horizon and straight above
//   texel 5 and 6: in alpha how cold the biome is, and how clear the air is (height)
//   texel 7: how high the camera is (red and green, 16 bits, a thirty-second of a block, from -512), and in alpha how far
//            north it is, as 0 to 1
//   texel 8: the color of the lightning (red, green, blue), and how bright its flash is, times 127 (alpha)
//   texel 9: how much the storm clouds take the color (red), whether there are bolts (green) and branches on them (blue), and in alpha, as bits:
//            1 the falling stars in every sky, 2 the volumetric storm clouds
//   texel 11: the time in seconds, kept for the things that move fast: whole seconds (red and green, 16 bits), and the fraction (blue)
//   texel 12: the time of the falling stars, as texel 11, which goes at their own speed
//   texel 15: how high the camera is exactly (red and green, 16 bits, a thirty-second of a block, from -512)
//   texel 10: the sky follows the music: how much a hit of the music lights up, times 255 / 1.4 (red), how loud it is (green), which hit it is,
//             so that a bolt is at another place for every hit (blue), and whether it is on (alpha)
//   texels 39 to 46: places in the world, each a signed 32-bit block (red the lowest byte) and a fraction in 1/256 of a block, see worldCoordinate:
//            39 and 40 where the camera is, east and south, followed slowly (for the lights of the sky); 41 and 42 where it is exactly (for the
//            clouds, which stand in the world); 43 and 44 how far the wind has carried the clouds; 45 the fractions of 39 to 42, 46 of 43 and 44

const float PI = 3.14159265;
const float TAU = 6.2831853;
// how much the middle and the near stars turn per block the player moves, against the far ones: the depth of the sky
const float MIDDLE_PARALLAX = 0.00012;
const float NEAR_PARALLAX = 0.0003;
// how many steps the lights of the sky are made of: 0 is fast, 1 is balanced, 2 is detailed; it is set from the parameters
float quality = 1.0;
// how many seconds the sky has been going, with the speed of the animation, for what moves in seconds and not in the minutes of a cycle
float gTime = 0.0;
// the aurora's own clock, in seconds at its speed (texel 47): its curtains drift on by it
float gAuroraTime = 0.0;
// how the curtains drift one way (in units of their pattern a second), and how gently and how fast they wave while they drift
const vec2 AURORA_DRIFT = vec2(0.006, 0.0025);
const float AURORA_WAVE = 0.12;
const float AURORA_WAVE_SPEED = 0.22;
// how far the start of the steps of the volumetric clouds is moved in this frame, a part of a step: another one in every frame
float gFrameShift = 0.0;
// the same, for the falling stars, which have a speed of their own that is set in the settings
float gStar = 0.0;

vec4 parameter(float index) {
    return floor(texture(Sampler0, vec2((index + 0.5) / 48.0, 0.5)) * 255.0 + 0.5);
}

// a number from -1 to 1 kept in 16 bits: the low byte, then the high one
float signed16(vec2 bytes) {
    return (bytes.x + bytes.y * 256.0) / 65535.0 * 2.0 - 1.0;
}

float unpack16(vec4 texel) {
    return (texel.r + texel.g * 256.0) / 65535.0;
}

// A place in the world from its texel and its fraction. Nothing wraps, so x = 0 is an ordinary place. The highest byte with the sign offset is a whole
// multiple of 2^24 and is added first: so the sum is exact in 32-bit floats within 2^24 blocks of the origin.
float worldCoordinate(vec4 whole, float fraction) {
    float block = (whole.a * 16777216.0 - 2147483648.0) + (whole.b * 65536.0 + whole.g * 256.0 + whole.r);
    return block + fraction / 256.0;
}

// where the camera is exactly, for the clouds
vec3 exactEye() {
    vec4 fractions = parameter(45.0);
    vec4 eyeUp = parameter(15.0);
    return vec3(worldCoordinate(parameter(41.0), fractions.b), (eyeUp.r + eyeUp.g * 256.0) / 32.0 - 512.0, worldCoordinate(parameter(42.0), fractions.a));
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float valueNoise2(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = p - cell;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(cell), hash12(cell + vec2(1.0, 0.0)), f.x),
               mix(hash12(cell + vec2(0.0, 1.0)), hash12(cell + vec2(1.0, 1.0)), f.x), f.y);
}

float valueNoise3(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = p - cell;
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash13(cell), hash13(cell + vec3(1.0, 0.0, 0.0)), f.x),
                  mix(hash13(cell + vec3(0.0, 1.0, 0.0)), hash13(cell + vec3(1.0, 1.0, 0.0)), f.x), f.y);
    float b = mix(mix(hash13(cell + vec3(0.0, 0.0, 1.0)), hash13(cell + vec3(1.0, 0.0, 1.0)), f.x),
                  mix(hash13(cell + vec3(0.0, 1.0, 1.0)), hash13(cell + vec3(1.0, 1.0, 1.0)), f.x), f.y);
    return mix(a, b, f.z);
}

// noise with detail at several scales, between 0 and 1
float fractal(vec3 p, int octaves) {
    float sum = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amplitude * valueNoise3(p);
        total += amplitude;
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        amplitude *= 0.5;
    }
    return sum / total;
}

vec3 rotateX(vec3 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec3(v.x, c * v.y - s * v.z, s * v.y + c * v.z);
}

vec3 rotateY(vec3 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

// turns the hue of a color, by a fraction of a full turn
vec3 hueShift(vec3 color, float turns) {
    float angle = turns * TAU;
    vec3 axis = vec3(0.57735);
    return color * cos(angle) + cross(axis, color) * sin(angle) + axis * dot(axis, color) * (1.0 - cos(angle));
}

// ---------------------------------------------------------------------------------------------------------------------
// Stars: the sky is split into cells, on the six sides of a cube, and some cells have a star somewhere in them.

// Where a direction is on the cube around the sky: the side, and the place on it. It is the same for every layer of stars, so it
// is worked out once.
vec2 cubeUv(vec3 dir, out float face) {
    vec3 a = abs(dir);
    vec2 uv;
    if (a.x >= a.y && a.x >= a.z) {
        uv = dir.yz / a.x;
        face = dir.x > 0.0 ? 0.0 : 1.0;
    } else if (a.y >= a.z) {
        uv = dir.xz / a.y;
        face = dir.y > 0.0 ? 2.0 : 3.0;
    } else {
        uv = dir.xy / a.z;
        face = dir.z > 0.0 ? 4.0 : 5.0;
    }
    // Cells of equal size on the cube are smaller and stretched towards its corners. Measuring them in angles evens that out.
    return atan(uv) * 1.2732395;
}

// The direction of a place on the cube around the sky: the side, and the place on it as cubeUv gives it.
vec3 cubeDirection(vec2 uv, float face) {
    vec2 c = tan(uv / 1.2732395);
    if (face < 1.5) {
        return normalize(vec3(face < 0.5 ? 1.0 : -1.0, c.x, c.y));
    }
    if (face < 3.5) {
        return normalize(vec3(c.x, face < 2.5 ? 1.0 : -1.0, c.y));
    }
    return normalize(vec3(c.x, c.y, face < 4.5 ? 1.0 : -1.0));
}

// Stars: some cells of the cube have one. Each is drawn round on the screen: how far the pixel is from the star is measured in pixels, through how
// the direction changes from one pixel to the next, and turned back into an angle with the size of a pixel there. So a star is never stretched,
// neither by the cube nor towards the edges of a wide view, where the view itself stretches the sky. A near layer gives its stars a soft glow, and
// the brightest of them four fine glints.
vec3 stars(vec3 dir, vec2 uv, float face, float scale, float chance, float seed, float size, float brightness, float near) {
    // how the direction changes per pixel; worked out before any pixel leaves, as it needs its neighbours
    vec3 acrossPixel = dFdx(dir);
    vec3 downPixel = dFdy(dir);
    vec2 grid = uv * scale;
    vec2 cell = floor(grid);
    vec2 id = cell + vec2(face * 131.7 + seed, face * 71.3 + seed * 3.0);
    if (hash12(id) > chance) {
        return vec3(0.0);
    }
    vec2 position = vec2(0.2) + 0.6 * vec2(hash12(id + 7.1), hash12(id + 13.7));
    vec3 star = cubeDirection((cell + position) / scale, face);
    // a cell is a quarter turn of the cube divided by the scale, as an angle
    float cellAngle = 0.7853982 / scale;
    float starSize = size * (0.5 + hash12(id + 3.3)) * cellAngle;
    vec3 off = dir - star;
    float away = length(off);
    // where the star is from this pixel, in pixels on the screen, and the size of a pixel as an angle
    mat2 metric = mat2(dot(acrossPixel, acrossPixel), dot(acrossPixel, downPixel), dot(acrossPixel, downPixel), dot(downPixel, downPixel));
    vec2 pixels = inverse(metric) * vec2(dot(acrossPixel, off), dot(downPixel, off));
    float pixel = sqrt(sqrt(max(determinant(metric), 1e-24)));
    float apart = length(pixels) * pixel;
    // a star is never smaller than a pixel, so it does not flicker as the view moves; it is dimmer then
    float radius = max(starSize, pixel * 1.3);
    float spot = 1.0 - smoothstep(radius * 0.2, radius, apart);
    spot *= min(1.0, starSize * starSize / (radius * radius) + 0.2);
    float bright = 0.4 + 0.6 * hash12(id + 2.2);
    if (near > 0.5) {
        // the near stars: a soft glow around them, and the brightest a cross of fine glints, across and up the screen. Both fade out before
        // the edge of the star's cell, which is all that is drawn of it, so they never end in a straight line.
        vec2 edges = min(position, 1.0 - position);
        float edge = min(edges.x, edges.y) * cellAngle;
        float within = 1.0 - smoothstep(edge * 0.45, edge * 0.9, away);
        float glow = exp(-apart / (radius * 1.5)) * 0.22;
        if (bright > 0.85) {
            float a = abs(pixels.x) * pixel / radius;
            float b = abs(pixels.y) * pixel / radius;
            glow += (exp(-a * 0.6) * exp(-b * 4.0) + exp(-b * 0.6) * exp(-a * 4.0)) * 0.35;
        }
        spot += glow * within;
    }
    float twinkle = 0.7 + 0.3 * sin(gTime * (1.1 + hash12(id + 5.1) * 3.0) + hash12(id + 1.9) * TAU);
    // white, blue-white or warm
    float hue = hash12(id + 9.9);
    vec3 color = hue < 0.45 ? vec3(1.0, 0.98, 0.95) : hue < 0.8 ? vec3(0.72, 0.82, 1.0) : vec3(1.0, 0.84, 0.64);
    return color * spot * twinkle * brightness * bright;
}

// ---------------------------------------------------------------------------------------------------------------------


float milkyWay(vec3 dir) {
    float offset = dot(dir, normalize(vec3(0.35, 0.8, 0.45)));
    return exp(-offset * offset * 8.0);
}


// The northern lights: curtains of light that stand in the world, high above it. Every one of them is a place in the
// world, not a direction, so as the player walks, they are seen from another side, and they come closer when the player
// goes north (towards -Z). The sky is marched through: the ray goes up through a stack of layers, and every layer has
// ridges, which are the curtains, and fine streaks. The streaks of all layers add up to the rays that stand upright.
float ridge(vec2 q, float flow) {
    float warp = valueNoise2(q * 0.55 + 3.7) * 2.2;
    float f = valueNoise2(q + vec2(warp, flow));
    float f2 = valueNoise2(q * 1.9 + vec2(flow * 0.6, warp + 9.1));
    float line = 1.0 - abs(f * 2.0 - 1.0);
    float line2 = line * line;
    return line2 * line2 * line * (0.55 + 0.9 * f2);
}

// The pattern that moves the start of the march a little from pixel to pixel, so that the layers do not show as bands. It is ordered (a 4 by 4
// pattern that repeats) and not random: random starts were seen as grain in the curtains, as neighbouring pixels got very different values.
// With the ordered pattern neighbours differ by a known small step, which the filter of the light pass and the eye take as smooth.
const float BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

float orderedJitter() {
    ivec2 p = ivec2(gl_FragCoord.xy) & 3;
    return (BAYER[p.y * 4 + p.x] + 0.5) / 16.0;
}

// The aurora's colors: its natural ones, one shifting into another going up; or those picked, which blend into each other across the sky.
float gAuroraCount = 0.0;
vec3 auroraColor(vec2 world, float v, vec3 tint) {
    if (gAuroraCount < 0.5) {
        return mix(tint, hueShift(tint, 0.22), smoothstep(0.0, 0.8, v));
    }
    if (gAuroraCount < 1.5) {
        return tint;
    }
    float place = clamp(valueNoise2(world / 1500.0 + vec2(gTime * 0.008, 0.0)) * 1.8 - 0.4 + v * 0.25, 0.0, 1.0) * (gAuroraCount - 1.0);
    float first = floor(place);
    vec3 from = parameter(29.0 + first).rgb / 255.0;
    vec3 to = parameter(29.0 + min(first + 1.0, gAuroraCount - 1.0)).rgb / 255.0;
    return mix(from, to, place - first);
}

vec3 aurora(vec3 dir, vec2 camera, float camHeight, vec3 tint, float phase, float latitude, float cold) {
    // no light is made from the ground up, and the layers are not reached close to the horizon
    if (dir.y <= 0.012) {
        return vec3(0.0);
    }
#ifdef LIGHT_PASS
    // a quarter of the pixels, so there are more steps for each, which is less grain, for less than the full size needed
    int STEPS = quality < 0.5 ? 16 : 28;
#else
    int STEPS = quality < 0.5 ? 10 : quality < 1.5 ? 16 : 24;
#endif
    // The curtains drift steadily one way on the aurora's own clock, and wave gently while they do; the waving never turns the flow back.
    float wave = sin(gAuroraTime * AURORA_WAVE_SPEED);
    float wave2 = sin(gAuroraTime * AURORA_WAVE_SPEED * 1.7 + 1.3);
    vec2 drift = AURORA_DRIFT * gAuroraTime;
    vec3 total = vec3(0.0);
    // every pixel starts at another height, a fraction of a step, in an ordered pattern, so the layers do not show as bands and not as grain
    float jitter = orderedJitter();
    for (int i = 0; i < 32; i++) {
        if (i >= STEPS) {
            break;
        }
        // The light is in the lower layers, and fades out going up, so the steps are close together low and far apart high.
        // The weight keeps the brightness the same, whatever the number of steps.
        float u = (float(i) + jitter) / float(STEPS);
        float v = u * sqrt(u);
        float weight = 1.5 * sqrt(u) * (16.0 / float(STEPS));
        float vertical = smoothstep(0.0, 0.06, v) * exp(-v * 3.2);
        float height = 90.0 + 380.0 * v;
        float t = height / dir.y;
        float distanceFade = exp(-t / 5200.0);
        // a layer that is too dark to be seen is not worked out
        if (vertical * distanceFade * weight < 0.001) {
            continue;
        }
        vec2 world = camera + dir.xz * t;

        // how far the curtains reach: a band across the world, north of the origin, in which they are strongest
        float bandX = (world.y + 2400.0) / 3600.0;
    float band = exp(-bandX * bandX);
        float strength = (0.3 + 0.7 * band) * (0.35 + 0.95 * latitude) * (0.45 + 0.9 * cold);

        // The curtains lean: every height is a little to the side of the one below it, so the sheets are seen as sheets, with depth, and
        // not as a flat pattern. Wide ones are far away; the narrow ones are near, so there are lines over the player too.
        vec2 lean = vec2(0.3, -0.2) * v;
        vec2 q = world / 760.0 + drift + vec2(AURORA_WAVE * wave, AURORA_WAVE * 0.75 * wave2) + lean * 0.5;
        float wide = ridge(q, gAuroraTime * 0.07 + 0.2 * wave);
        float curtain = wide * 0.85;
        // the narrow lines are only seen when they are near: past this, they are fainter than 1 in 100
        if (t < 12000.0) {
            float nearLine = ridge(world / 230.0 + drift * (760.0 / 230.0) + vec2(0.25 * wave2, 0.15 * wave) + lean * 0.8, gAuroraTime * 0.09 + 0.25 * wave2);
            float near = nearLine * nearLine * 1.6;
            curtain += near * 1.6 * exp(-t / 2600.0);
        }
        float streaks = valueNoise2(world / 34.0 + vec2(0.0, v * 3.0));
        float patches = smoothstep(0.2, 0.75, valueNoise2(q * 0.45 + vec2(3.3, wave * 0.25)));

        vec3 color = auroraColor(world, v, tint);
        color = mix(color, mix(tint, vec3(1.0), 0.45), (1.0 - smoothstep(0.0, 0.1, v)) * 0.6);
        float fade = vertical * distanceFade * weight;
        total += color * curtain * (0.35 + 0.9 * streaks) * (0.25 + 0.95 * patches) * fade * strength;
        // a wide, soft glow between the curtains, so that the sky straight above is never empty
        total += color * (0.25 + 0.75 * patches) * fade * (0.3 + 0.7 * strength) * 0.07;
    }
    // looking nearly flat, there is more of the layers in every pixel
    total *= 0.62 * (1.0 + 0.6 * (1.0 - smoothstep(0.0, 0.35, dir.y)));
    total += tint * 0.05 * (1.0 - smoothstep(0.0, 0.5, dir.y)) * (0.5 + latitude);
    return total * smoothstep(0.012, 0.09, dir.y) * 3.0;
}

// Large soft lights that float about in the sky and swell and fade, as the dots of light round the falling stars do.
vec3 glowDots(vec3 dir, vec2 uv, float face, float scale, float chance, float seed, float size, vec3 tint) {
    vec2 grid = uv * scale;
    vec2 cell = floor(grid);
    vec2 id = cell + vec2(face * 131.7 + seed, face * 71.3 + seed * 3.0);
    if (hash12(id) > chance) {
        return vec3(0.0);
    }
    float h1 = hash12(id + 7.1);
    float h2 = hash12(id + 13.7);
    vec2 drift = 0.08 * vec2(sin(gTime * (0.15 + 0.2 * h1) + h2 * TAU), cos(gTime * (0.12 + 0.2 * h2) + h1 * TAU));
    vec2 position = vec2(0.5) + drift + 0.12 * (vec2(h1, h2) - 0.5);
    // measured as an angle, in cells, so the light is round wherever it is on the cube
    float d = length(dir - cubeDirection((cell + position) / scale, face)) * scale / 0.7853982;
    float pulse = 0.5 + 0.5 * sin(gTime * (0.5 + h1) + h2 * TAU);
    float core = exp(-d * d / (size * size * 0.07));
    float halo = exp(-d * d / (size * size));
    return mix(tint, vec3(1.0, 0.9, 0.95), 0.5 * core) * (core * 1.3 + halo * 0.4) * (0.35 + 0.65 * pulse);
}

// ---------------------------------------------------------------------------------------------------------------------
// Storm clouds: heavy clouds in two layers high above the world, in places of the world, so they pass over as the player walks.
// Now and then there is lightning: a flash that lights the clouds, and a bolt that goes down from them.





float fractal2(vec2 p, int octaves) {
    float sum = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amplitude * valueNoise2(p);
        total += amplitude;
        p = mat2(1.6, 1.2, -1.2, 1.6) * p + vec2(3.7, 1.3);
        amplitude *= 0.5;
    }
    return sum / total;
}

// ---------------------------------------------------------------------------------------------------------------------
// The volumetric storm: real clouds in 3D. A layer of air between two heights over the player has heaps of cloud standing in it, and a ray goes
// from the eye through that layer in steps, for every pixel (of a smaller picture, which is filtered up). At every step the cloud there takes some
// of the light that comes from behind it, and gives light of its own: the sun's, which has come to that place through the cloud towards the sun
// (a few steps are taken that way, so a heap shades itself and the ones behind it), and the light of the sky round it. So the heaps have sides,
// depth and shadows because they are bodies, and not because a picture is shaded.

// The layer of the clouds, in blocks of the world: above where islands and builds are, below where a player flies up to.
float gBottom = 192.0;
float gTop = 280.0;
// the longest way through the layer that is marched, how far away the clouds are gone in the haze, how long a step is, how long the first step
// towards the sun is, and how large the billows are up close; all in blocks
const float CLOUD_MAX_PATH = 1400.0;
const float CLOUD_FADE = 4500.0;
const float CLOUD_STEP = 24.0;
const float CLOUD_LIGHT_STEP = 32.0;
float gBillow = 38.0;
// how much a cloud of full density takes of the light, for every unit of the way through it
const float CLOUD_SIGMA = 0.035;
// how large the billows of the clouds are at the step that is worked out now: larger far away, where the steps are long
float gBillowSize = 38.0;
// how soft the base of a heap is, as a part of the height of the layer: at least a little, and as much as a step is long, so a long step far away
// does not either hit the base or miss it, from one pixel to the next (which was grain)
float gEdge = 0.06;
// the same for the sides of the masses: how much softer their outline in the plan is, as a part of the noise of the plan. Towards the horizon the
// ray goes almost level, so it crosses the sides of the far masses, and with long steps a side was either hit or missed from one pixel to the next,
// which showed as upright stripes (the base had the same as flat stripes, which gEdge took away). Nothing up close, where the steps are short.
float gSide = 0.0;

// where the sun stands, from the time of the game, and what else the page says of the clouds
vec3 gSun = vec3(0.0, 1.0, 0.0);
float gRain = 0.0;
float gRainDark = 0.7;
float gCover = 0.55;
float gGaps = 0.5;
float gGapSize = 0.4;
float gPlanSize = 300.0;
float gPuff = 0.7;
float gShadow = 0.5;
bool gLit = true;
bool gGlow = true;
vec2 gWind = vec2(0.0);
vec3 gCloudTint = vec3(1.0);
// the strike of lightning now: where it is in the world (x, z), how bright, and its bolt
vec2 gFlashAt = vec2(0.0);
float gFlash = 0.0;
float gFlashAround = 0.0;
float gFlashFar = 0.0;
float gBolt = 0.0;
float gBoltSeed = 0.0;
const vec3 FLASH_COLOR = vec3(0.82, 0.88, 1.0);
// lightning over an island of the Nether: warmer, as through its smoke
const vec3 WARM_FLASH_COLOR = vec3(1.0, 0.66, 0.42);
// how much of an island of the Nether's mood the sky has: its warm light from below, warmer lightning
float gWarm = 0.0;
// the color of its light from below
const vec3 EMBER = vec3(0.55, 0.16, 0.05);

vec3 flashColor() {
    return mix(FLASH_COLOR, WARM_FLASH_COLOR, gWarm);
}
float gCloudTintShare = 0.0;

// Reads what the page says of the clouds.
void readClouds() {
    vec4 sun = parameter(22.0);
    gSun = normalize(vec3(signed16(sun.rg), signed16(sun.ba), 0.14));
    vec4 look = parameter(23.0);
    gRain = look.r / 255.0;
    gRainDark = look.g / 255.0;
    gShadow = look.b / 255.0;
    gLit = mod(look.a, 2.0) > 0.5;
    gGlow = mod(floor(look.a / 2.0), 2.0) > 0.5;
    vec4 shape = parameter(24.0);
    gCover = mix(shape.r / 255.0, 0.95, gRain * 0.85);
    gGaps = shape.g / 254.0 * (1.0 - gRain);
    gGapSize = shape.b / 255.0;
    gPlanSize = mix(110.0, 520.0, shape.a / 255.0);
    gBillow = mix(9.0, 34.0, shape.a / 255.0);
    vec4 layer = parameter(25.0);
    gPuff = layer.r / 255.0;
    gBottom = (layer.b + layer.a * 256.0) / 16.0 - 512.0;
    gTop = gBottom + mix(26.0, 190.0, layer.g / 255.0);
    vec4 windFractions = parameter(46.0);
    gWind = vec2(worldCoordinate(parameter(43.0), windFractions.r), worldCoordinate(parameter(44.0), windFractions.g));
    vec4 cloudTintTexel = parameter(32.0);
    gCloudTint = cloudTintTexel.rgb / 255.0;
    vec4 strike = parameter(37.0);
    gFlashAround = (strike.r + strike.g * 256.0) / 65535.0 * TAU;
    gFlashFar = strike.b * 4.0;
    gFlash = strike.a / 255.0;
    vec4 boltTexel = parameter(38.0);
    gBolt = boltTexel.r / 255.0;
    gBoltSeed = boltTexel.g;
    gCloudTintShare = cloudTintTexel.a / 255.0;
}

// How much cloud there is over a place, from above: bent by a slow noise, so the masses have ragged outlines and not round blobs, and with the wide holes of the gaps in them.
float cloudPlan(vec2 xz, int octaves) {
    vec2 plan = (xz + gWind) / gPlanSize;
    float cover = fractal2(plan + vec2(valueNoise2(plan * 0.7 + 11.0), valueNoise2(plan * 0.7 + 23.0)) * 0.9, octaves);
    float low = mix(0.82, 0.14, gCover);
    float amount = smoothstep(low - gSide, low + 0.2 + gSide, cover);
    if (gGaps > 0.01 && amount > 0.0) {
        float holes = valueNoise2((xz + gWind) / mix(500.0, 2600.0, gGapSize) + 40.0);
        float edge = mix(0.28, 0.55, gGaps);
        amount *= smoothstep(edge - 0.1, edge + 0.1, holes);
    }
    return amount;
}


// How dense the cloud is at a place: the heaps are where the noise of the ground plan says there is cloud, and the more there is, the higher they
// stand, so their tops are round domes. Their bases hang down in lumps and pouches (the bottoms of storm clouds are not flat sheets), and with the
// detail on the billows eat into them, at their tops, their edges and their bases. Without it (for the light, which is soft) only the shape is
// worked out.
float cloudDensity(vec3 p, bool detail, out float aboveHere) {
    aboveHere = 0.0;
    vec2 wind = gWind;
    float amount = cloudPlan(p.xz, detail ? 3 : 2);
    if (amount <= 0.0) {
        return 0.0;
    }
    float h = (p.y - gBottom) / (gTop - gBottom);
    float bottom = 0.03 + 0.10 * (1.0 - amount);
    float top = 0.22 + 0.78 * amount;
    // how much cloud there is above this place, up to the top of the heap, which the light of the sky comes through to it
    aboveHere = max(top - h, 0.0) * amount;
    if (!detail) {
        return amount * smoothstep(bottom, bottom + gEdge, h) * (1.0 - smoothstep(top - 0.22, top, h));
    }
    // The base hangs down in lumps: a large, slow noise in 3D lowers it in places, in round pouches, so the underside seen from below has folds and
    // bulges and not one flat face. Far away, where a lump is smaller than a step, they go flatter.
    float lumps = valueNoise3(vec3(p.x + wind.x, p.y * 1.6, p.z + wind.y) / (gBillow * 2.6));
    bottom -= (lumps - 0.45) * 0.20 * (1.0 - 0.6 * smoothstep(60.0, 130.0, gBillowSize));
    float d = amount * smoothstep(bottom, bottom + gEdge, h) * (1.0 - smoothstep(top - 0.22, top, h));
    if (d <= 0.0) {
        return 0.0;
    }
    // deep inside the upper body of a heap the billows would not change what is seen (it is all cloud there): there they are not worked out. Near
    // the base they always are, as the base is what is seen from below.
    bool nearBase = h < bottom + 0.18;
    if (d > 0.85 && !nearBase) {
        return 1.0;
    }
    // The billows: a noise in 3D at two sizes eats into the heap, so its edge is a row of round bulges (the cauliflower of heaped clouds) and not a
    // soft blur; what is left is made firm again, so the heaps have a surface. Far away they are larger, as the steps are longer there: billows
    // smaller than a step showed as stripes. Where they eat in deep (a crease), the light of the sky reaches less, so the creases are dark.
    float billow = fractal(vec3(p.x + wind.x, p.y + gTime * 0.3, p.z + wind.y) / gBillowSize, 2);
    // Far away a billow is smaller than a pixel of the smaller picture, and the steps are long: it only showed as grain there. So the billows
    // fade out with the distance, and the edge is made less firm, and the far heaps are smooth, soft shapes.
    float far = smoothstep(60.0, 130.0, gBillowSize);
    float bite = (1.0 - smoothstep(0.25, 0.75, billow)) * (1.0 - 0.7 * far);
    d -= bite * (nearBase ? 0.55 : 0.38 + 0.25 * h) * mix(0.35, 1.3, gPuff);
    aboveHere += bite * 0.35;
    return clamp(d * mix(1.8, 1.15, far), 0.0, 1.0);
}

// how much light goes on in a direction from where it came, normalized so that light that goes on evenly in every direction is 1
float hgPhase(float cosine, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * cosine, 1e-4), 1.5);
}

// Where a ray from the eye goes through the layer of the clouds: from t0 to t1 along it, as far as the clouds are marched. @return false where it
// never meets the layer, or only behind the eye or in the haze far off
bool cloudSpan(vec3 dir, vec3 eye, out float t0, out float t1) {
    if (abs(dir.y) < 1e-4) {
        // level: in the layer it goes on, else it never meets it
        t0 = 0.0;
        t1 = CLOUD_MAX_PATH;
        return eye.y >= gBottom && eye.y <= gTop;
    }
    float a = (gBottom - eye.y) / dir.y;
    float b = (gTop - eye.y) / dir.y;
    t0 = max(min(a, b), 0.0);
    t1 = max(a, b);
    // what is behind the eye, or so far that it is all haze, is not marched through
    if (t1 <= 0.0 || t0 > CLOUD_FADE * 2.0) {
        return false;
    }
    t1 = min(t1, t0 + CLOUD_MAX_PATH);
    return true;
}

// The clouds in a direction, seen from where the camera is in the world: the light they give (rgb) and how much of the sky behind them is still seen
// (alpha), for the sky to be made over. The layer of the clouds is at heights of the world, so the camera can be under it, in it (in the cloud) or
// over it, looking down on its tops; the ray goes through the part of the layer that it crosses.
vec4 volumeClouds(vec3 dir, vec3 eye, float phase) {
    gFlashAt = eye.xz + vec2(sin(gFlashAround), cos(gFlashAround)) * gFlashFar;
    float t0;
    float t1;
    if (!cloudSpan(dir, eye, t0, t1)) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    // by night the moon stands where the sun is not, and lights them more weakly and coldly
    float dayShare = smoothstep(-0.08, 0.16, gSun.y);
    vec3 sun = gSun.y > -0.02 ? gSun : -gSun;
    // As many steps as the way through the layer needs: a short way gets a few steps, a long one (towards the horizon, or along the layer from in
    // it) the most that the quality allows. So the work goes where the clouds need it.
    int most = quality < 0.5 ? 16 : quality < 1.5 ? 24 : 32;
    int steps = int(clamp((t1 - t0) / CLOUD_STEP, 8.0, float(most)));
    int lightSteps = quality < 0.5 ? 1 : quality < 1.5 ? 2 : 3;
    float stepLength = (t1 - t0) / float(steps);
    // up close (short steps) the base stays as firm as it was; only the long steps far away soften it
    gEdge = max(0.06, stepLength * 1.5 / (gTop - gBottom) - 0.08);
    // the outline in the plan goes as soft as the steps are longer than the usual one: about two steps wide at the longest
    gSide = clamp((stepLength - CLOUD_STEP) / CLOUD_STEP, 0.0, 1.0) * 0.16;
    // every pixel starts a part of a step later than its neighbors, so the steps do not show as layers (an even pattern, which the filtering smooths)
    // The start is one of four places in a step, in a square of two by two pixels: the filter that makes the picture larger takes the mean of every
    // such square, so the four together are as four times as many steps, smooth, and leave no grain. The noise that was used before had a
    // longer pattern, which the filter did not take out: it showed as fine dots and lines in the far clouds.
    ivec2 cell = ivec2(gl_FragCoord.xy) & 1;
    // and in every frame all four are moved on by another part of a step (the golden ratio, so they never line up again): the frames that are
    // mixed together have the steps at many places, which takes out the layers that four places left far away, as lines along the horizon
    float offset = fract((float(cell.x * 2 + (cell.x ^ cell.y)) + 0.5) / 4.0 + gFrameShift);

    // the clouds are grey, or in the color as much as is set; the sun is warm, the shade is cool
    vec3 cloudTint = mix(mix(vec3(1.0, 1.0, 1.02), vec3(0.62, 0.64, 0.70), gRain), gCloudTint, gCloudTintShare);
    float dark = 1.0 - gRain * gRainDark * 0.75;
    // low over the horizon the sun is warm: gold, and red at the very last
    float low = gGlow ? (1.0 - smoothstep(0.02, 0.32, abs(gSun.y))) * dayShare : 0.0;
    vec3 sunsetColor = parameter(36.0).rgb / 255.0;
    vec3 sunColor = mix(vec3(1.0, 0.96, 0.88), sunsetColor, low) * 2.3;
    vec3 moonColor = vec3(0.50, 0.60, 0.85) * 0.42;
    // in the rain the sun does not come through as a light of its own: it is all the even grey of the sky
    vec3 sunLight = mix(moonColor, sunColor, dayShare) * dark * (1.0 - gRain * 0.88);
    // the light of the sky is the sky as it is shown: the clouds and the sky behind them are always of one time, weather and color
    vec3 skyColor = max(parameter(35.0).rgb / 255.0 * 1.25, vec3(0.022, 0.030, 0.055));
    skyColor = mix(skyColor, sunsetColor * 0.75, low * 0.35);
    vec3 skyAbove = cloudTint * skyColor * dark;
    vec3 skyBelow = skyColor * vec3(0.40, 0.42, 0.46) * dark;
    // Much of the light goes on forward through a cloud: towards the sun the edges shine (silver linings), and some comes back, which lights the
    // sides. The forward part is kept from being too strong, so the clouds in front of the sun glow and are not blown out to one bright color.
    float cosine = dot(dir, sun);
    float lightPhase = min((0.7 * hgPhase(cosine, 0.45) + 0.3 * hgPhase(cosine, -0.25)) * 0.35, 1.4);

    vec3 light = vec3(0.0);
    float through = 1.0;
    for (int i = 0; i < 32; i++) {
        if (i >= steps) {
            break;
        }
        float t = t0 + (float(i) + offset) * stepLength;
        vec3 p = eye + dir * t;
        // the billows grow smoothly with the distance, as the steps do (billows smaller than a step showed as stripes and blocks)
        gBillowSize = max(gBillow, t / 30.0);
        float above;
        float d = cloudDensity(p, true, above);
        // far away the clouds go into the haze: they are thinner the farther they are, so they fade and are not cut off
        d *= exp(-t / CLOUD_FADE);
        if (d <= 0.002) {
            continue;
        }
        // the light of the sun at this place: it has come through the cloud between here and the sun, in steps that get longer
        float depth = 0.0;
        float reach = 0.0;
        for (int j = 0; j < 3; j++) {
            if (j >= lightSteps) {
                break;
            }
            float segment = CLOUD_LIGHT_STEP * float(j + 1) * float(j + 1);
            reach += segment;
            float unused;
            depth += cloudDensity(p + sun * (reach - segment * 0.5), false, unused) * segment;
        }
        // the light is dimmed less than what is seen through the cloud: it is also scattered into the shadow, which is not black
        float sunReach = gLit ? exp(-depth * CLOUD_SIGMA * 0.45) : 0.75;
        // a thick cloud is dark on the side that is turned to the eye, as the light that comes in is spent in it (the dark edges of heaped clouds)
        float powder = 1.0 - exp(-d * 2.4);
        float h = clamp((p.y - gBottom) / (gTop - gBottom), 0.0, 1.0);
        // The light of the sky comes from above, through the cloud over this place: where the heap is thin it is bright, where it is thick it is dark.
        // That is what gives the underside of a storm its dark and light patches, seen from below.
        vec3 ambient = gLit ? skyAbove * exp(-above * 3.2) + skyBelow : (skyAbove + skyBelow) * 0.7;
        vec3 given = sunLight * sunReach * lightPhase * (0.45 + 0.55 * powder) * cloudTint + ambient;
        if (gFlash > 0.0) {
            // the light of a strike comes from inside the cloud round it, brightest at its heart and fading over some hundred blocks
            float fromStrike = distance(p.xz, gFlashAt);
            given += flashColor() * gFlash * 3.2 * exp(-fromStrike / 170.0) * (0.5 + 0.5 * powder);
        }
        // what this step gives, as much of it as is not taken back by the cloud of this step itself, and how much is left of what is behind
        float stepThrough = exp(-d * CLOUD_SIGMA * stepLength);
        light += through * given * (1.0 - stepThrough);
        through *= stepThrough;
        if (through < 0.02) {
            through = 0.0;
            break;
        }
    }
    // Far away, towards the horizon, the clouds go into the haze of the sky: the further the layer begins, the less of them is seen. Before, the long
    // way along the layer kept them thick right up to where they were cut off, which was a hard edge over the horizon.
    // A color of their own tints the light they give as a whole, the lit sides as much as the shade, so bright clouds are not just white: the
    // color is taken at its brightest, so they stay as bright and as 3D as before.
    if (gCloudTintShare > 0.0) {
        vec3 own = gCloudTint / max(max(gCloudTint.r, gCloudTint.g), max(gCloudTint.b, 1e-3));
        light = mix(light, light * own, gCloudTintShare);
    }
    float haze = smoothstep(CLOUD_FADE * 2.0, CLOUD_FADE * 0.55, t0);
    return vec4(light * haze, mix(1.0, through, haze));
}

// A bolt of lightning seen from far off: down from the base of the clouds (top, as the height of the direction there) to the horizon, crooked, with a
// thin bright core, a soft glow and one branch.
float boltAt(vec3 dir, float centre, float top, float seed) {
    float y = dir.y;
    float azimuth = atan(dir.x, dir.z);
    float bend = (valueNoise2(vec2(y * 140.0 / max(top, 0.02) * 0.1, seed)) - 0.5) * 0.05 + (valueNoise2(vec2(y * 460.0 / max(top, 0.02) * 0.1, seed + 3.0)) - 0.5) * 0.012;
    float dx = mod(azimuth - centre - bend + PI, TAU) - PI;
    float along = smoothstep(-0.03, 0.0, y) * (1.0 - smoothstep(top - 0.01, top, y));
    float core = exp(-dx * dx / (2.0 * 0.0016 * 0.0016)) * 1.6 + exp(-dx * dx / (2.0 * 0.014 * 0.014)) * 0.22;
    float dx2 = mod(azimuth - centre - bend - (y - top * 0.6) * 0.7 + PI, TAU) - PI;
    float branch = exp(-dx2 * dx2 / (2.0 * 0.0014 * 0.0014)) * step(top * 0.3, y) * step(y, top * 0.6) * 0.8;
    return (core + branch) * along;
}

// A soft mist of the color that goes all the way round the sky, thickest low, over the horizon, and thinner higher, drifting slowly round.
vec3 mistRing(vec3 dir, vec3 tint) {
    // a band above the horizon and a fainter one below it, which is seen from an island's edge
    float aboveX = (dir.y - 0.20) / 0.46;
    float belowX = (dir.y + 0.40) / 0.42;
    float ring = exp(-aboveX * aboveX) + 0.7 * exp(-belowX * belowX);
    if (ring < 0.02) {
        return vec3(0.0);
    }
    int fine = quality < 0.5 ? 1 : 0;
    vec3 p = rotateY(dir, gTime * 0.006) * vec3(2.4, 3.2, 2.4);
    float warp = fractal(p * 0.7 + 5.0 + vec3(gTime * 0.010, 0.0, -gTime * 0.008), 2);
    float cloud = fractal(p + warp * 1.4 + vec3(0.0, gTime * 0.012, 0.0), 4 - fine);
    float body = smoothstep(0.40, 0.78, cloud);
    float core = smoothstep(0.64, 0.95, cloud);
    vec3 color = mix(tint, vec3(0.78, 0.62, 1.0), 0.35);
    // a second, larger and softer layer behind it, in a slightly other hue, so the mist has depth
    float far = fractal(p * 0.5 + warp * 0.8 + 17.0, 3 - fine);
    float back = smoothstep(0.42, 0.85, far);
    vec3 backColor = mix(tint, vec3(0.55, 0.7, 1.0), 0.45);
    return (color * body * 0.85 + color * core * 0.65 + backColor * back * 0.4) * ring;
}




// The light of a theme that is smooth: the lights of the sky, and a faint glow of the air. Not the stars.
vec3 skyLight(vec3 dir, vec3 sphere, vec2 camera, float camHeight, vec3 tint, float phase, float theme, float latitude, float cold) {
    vec3 light = vec3(0.0);
    if (theme < 2.5) {
        light += aurora(dir, camera, camHeight, tint, phase, latitude, cold) * 0.55;
    } else {
        // the falling stars: a dark sky with a mist all round it, which the stars cross
        light += mistRing(dir, tint) * 0.3;
    }
    // A faint glow of the air, over the whole sky and strongest straight up, so that nothing is ever black above the player.
    // It covers the whole sky, also below the horizon, which is seen from an island's edge. It is faint and smooth, so it has fewer octaves.
    {
        float airNoise = fractal(rotateY(sphere, phase * TAU) * 2.2 + 3.0, quality < 1.5 ? 2 : 3);
        float upwards = 0.35 + 0.65 * smoothstep(0.0, 1.0, abs(dir.y));
        light += mix(tint, vec3(0.7, 0.8, 1.0), 0.3) * 0.1 * (0.4 + 0.9 * airNoise) * upwards;
    }
    return light;
}

void main() {
    vec3 dir = normalize(direction);
    vec4 clock = parameter(11.0);
    gTime = clock.r + clock.g * 256.0 + clock.b / 255.0;
    vec4 auroraClock = parameter(47.0);
    gAuroraTime = auroraClock.r + auroraClock.g * 256.0 + auroraClock.b / 255.0;
    vec4 starClock = parameter(12.0);
    gStar = starClock.r + starClock.g * 256.0 + starClock.b / 255.0;
    vec4 first = parameter(0.0);
    vec4 second = parameter(1.0);
    vec4 tintTexel = parameter(2.0);
    gWarm = tintTexel.a / 255.0;
    vec4 horizonTexel = parameter(3.0);
    vec4 zenithTexel = parameter(4.0);
    vec3 tint = tintTexel.rgb / 255.0;
    float day = horizonTexel.a / 255.0;
    float twilight = zenithTexel.a / 255.0;
    vec3 horizonColor = horizonTexel.rgb / 255.0;
    vec3 zenithColor = zenithTexel.rgb / 255.0;

    float phase = unpack16(first);
    float theme = first.b;
    float opacity = first.a / 255.0;
    float starAngle = unpack16(second) * TAU;
    float brightness = second.b / 64.0;
    float density = second.a / 255.0;

    vec4 east = parameter(5.0);
    vec4 south = parameter(6.0);
    vec4 up = parameter(7.0);
    quality = up.b;
    gAuroraCount = parameter(28.0).b;
    vec4 placeFractions = parameter(45.0);
    vec2 camera = vec2(worldCoordinate(parameter(39.0), placeFractions.r), worldCoordinate(parameter(40.0), placeFractions.g));
    float cold = east.a / 255.0;
    float clear = south.a / 255.0;
    float camHeight = (up.r + up.g * 256.0) / 32.0 - 512.0;
    float latitude = up.a / 255.0;

    // The stars of the game are drawn with this rotation, so what is drawn here moves with them.
    vec3 sphere = rotateX(rotateY(dir, PI * 0.5), -starAngle);
    sphere = rotateY(sphere, phase * TAU);
    // Travelling changes the stars, as it does on a planet: north and south tilt the sky, east and west turn it.
    sphere = rotateX(sphere, (latitude - 0.5) * 1.4);
    sphere = rotateY(sphere, camera.x / 65536.0 * TAU);

    // The background is a color that changes from the horizon to straight above, instead of the sky of the game.
    vec3 color = mix(horizonColor, zenithColor, pow(clamp(dir.y, 0.0, 1.0), 0.55));
    // over an island of the Nether a warm red light comes from below, from the lava far down, and a smoky haze lies low over it
    color += EMBER * gWarm * (1.0 - smoothstep(-0.45, 0.35, dir.y)) * 0.5;

    // Below the horizon the land covers the sky, and the fog hides what is far, so the lights are only made where they can be seen.
    // Only an empty world, with nothing under the player, shows the sky down there, and it fades out well before straight down.
    // By day the lights are 5 in 100 of what they are at night, which is not seen, so they are not made at all.
    float lit = day < 0.999 ? 1.0 : 0.0;
    // by day, the stars and the lights of the sky are not seen as much
    float strength = (1.0 - 0.95 * day) * lit;

#ifdef LIGHT_PASS
    // Only the smooth light, at a lower resolution: it is soft, so nothing is lost by it, and it is a quarter of the pixels. The stars are
    // not in it, they are sharp. The light is kept as it is, with no limit, in a texture that can hold that.
    vec3 smoothLight = vec3(0.0);
    if (theme < 3.5 && lit > 0.0) {
        smoothLight = skyLight(dir, sphere, camera, camHeight, tint, phase, theme, latitude, cold) * strength;
    }
    fragColor = vec4(smoothLight, 1.0);
#elif defined(VOLUME_PASS)
    readClouds();
    // Only the volumetric clouds, at a smaller size: the light they give, and how much of the sky behind them is seen, which the next pass puts
    // over the sky. They are lit by the flashes of the lightning from inside.
    // the clouds stand in the world: they are seen from where the camera is exactly, not from the place that the lights of the sky follow
    vec3 eye = exactEye();
    vec4 frameTexel = parameter(21.0);
    gFrameShift = fract(frameTexel.r * 0.6180340);
    vec4 clouds = volumeClouds(dir, eye, phase);
    // The frames before are mixed in: where this direction was on the screen in the frame before (the view has turned since) is found with how the
    // view was turned then, and what was made there is kept for the most part. So every pixel has the steps of many frames, each started at
    // another place, which is as many more steps: smooth, without grain or the layers of the steps. The clouds are far away and drift slowly, so
    // a direction shows about the same cloud from frame to frame.
    if (frameTexel.g > 127.5) {
        vec4 r0 = parameter(16.0);
        vec4 r1 = parameter(17.0);
        vec4 r2 = parameter(18.0);
        vec4 r3 = parameter(19.0);
        vec4 r4 = parameter(20.0);
        mat3 before = mat3(signed16(r0.rg), signed16(r0.ba), signed16(r1.rg), signed16(r1.ba), signed16(r2.rg), signed16(r2.ba),
            signed16(r3.rg), signed16(r3.ba), signed16(r4.rg));
        // The camera may have moved, too: the cloud this pixel shows was seen from the place before in another direction. It is found where the ray
        // meets the layer (or a little ahead, from inside it), the depth that most of what is seen has. Where that is near and the move large, the
        // old picture is less sure, and counts less.
        vec4 m0 = parameter(33.0);
        vec4 m1 = parameter(34.0);
        vec3 moved = vec3(signed16(m0.rg), signed16(m0.ba), signed16(m1.rg)) * 32.0;
        float a = abs(dir.y) < 1e-4 ? 1e6 : (gBottom - eye.y) / dir.y;
        float b = abs(dir.y) < 1e-4 ? 1e6 : (gTop - eye.y) / dir.y;
        float reach = eye.y > gBottom && eye.y < gTop ? 60.0 : max(min(a, b), 1.0);
        reach = min(reach, CLOUD_FADE);
        vec3 seenBefore = normalize(dir * reach + moved);
        float sure = 1.0 - smoothstep(0.002, 0.04, length(moved) / reach);
        vec4 clip = ProjMat * vec4(before * seenBefore, 0.0);
        if (clip.w > 1e-4) {
            vec2 then = clip.xy / clip.w * 0.5 + 0.5;
            if (all(greaterThan(then, vec2(0.0))) && all(lessThan(then, vec2(1.0)))) {
                clouds = mix(clouds, texture(Sampler1, then), 0.85 * mix(0.35, 1.0, sure));
            }
        }
    }
    fragColor = clouds;
#elif defined(CLOUD_COMPOSITE)
    readClouds();
    vec3 eye = exactEye();
    float level = parameter(26.0).a / 255.0;
    // the clouds were marched at a smaller size, and are filtered up: a smooth bell of four by four taps between its pixels, so the even pattern of the steps does not show
    vec2 cloudSize = vec2(textureSize(Sampler1, 0));
    vec2 screen = vec2(textureSize(Sampler2, 0));
    vec2 cloudAt = gl_FragCoord.xy / screen;
    vec2 texel = 1.0 / cloudSize;
    vec4 clouds = vec4(0.0);
    for (int fx = 0; fx < 4; fx++) {
        for (int fy = 0; fy < 4; fy++) {
            float weight = (fx == 0 || fx == 3 ? 1.0 : 3.0) * (fy == 0 || fy == 3 ? 1.0 : 3.0);
            clouds += texture(Sampler1, cloudAt + (vec2(float(fx), float(fy)) - 1.5) * texel) * weight;
        }
    }
    clouds /= 64.0;
    // very bright light is softened above a knee, so it glows and does not burn out to one flat color
    vec3 knee = max(clouds.rgb - 0.9, 0.0);
    clouds.rgb = min(clouds.rgb, vec3(0.9)) + knee / (1.0 + knee * 1.5);
    // How far the world is along this ray, from its depth. It is in front of the clouds only if it is nearer than where the ray enters their layer: so a hill that reaches into them is wrapped in them.
#ifdef CLOUD_OVER_SKY
    // laid over the sky before the world: all of it is sky now, and the world that comes later covers it where it is nearer
    float depth = 0.0;
#else
    float depth = texelFetch(Sampler2, ivec2(gl_FragCoord.xy), 0).r;
    if (depth <= 1e-6) {
        // the sky has had its clouds already, under all that was drawn over it since
        fragColor = vec4(0.0);
        return;
    }
#endif
    float share = 1.0;
    float shade = 1.0;
    if (depth > 1e-6) {
        vec2 ndc = gl_FragCoord.xy / screen * 2.0 - 1.0;
        float viewDepth = ProjMat[3][2] / max(depth + ProjMat[2][2], 1e-6);
        float world = abs(viewDepth) * length(vec3(ndc.x / ProjMat[0][0], ndc.y / ProjMat[1][1], 1.0));
        // The clouds were marched before the world was drawn, all the way through the layer. Over land only the part of that way in front of it is
        // shown: none where the land is nearer than where the ray enters the layer, all of it where the land is behind where it leaves. So no cloud
        // shows through a wall or a ceiling, also from inside the layer (a cave or the mines at the height of the clouds).
        float t0;
        float t1;
        share = cloudSpan(dir, eye, t0, t1) ? clamp((world - t0) / max(t1 - t0, 1.0), 0.0, 1.0) : 0.0;
        // From inside the layer the march does not know what of the way to the land is cloud and what is a hall, a cave or the mines at the clouds'
        // height: no cloud over the land then, only over the sky.
        if (eye.y > gBottom && eye.y < gTop) {
            share = 0.0;
        }
        // The shadow of the clouds on the land: from the place of this pixel towards the sun, the cloud over it in the layer. By night the moon casts a weak one. It is gone before the fog is: on land
        // that is lost in the fog it would draw the edges of the far chunks on the sky.
        if (gShadow > 0.0) {
            vec3 at = eye + dir * world;
            vec3 towards = gSun.y > -0.02 ? gSun : -gSun;
            if (towards.y > 0.05 && at.y < gBottom) {
                vec3 inLayer = at + towards * ((gBottom + 20.0 - at.y) / towards.y);
                float over = cloudPlan(inLayer.xz, 2);
                float strength = mix(0.25, 0.62, smoothstep(-0.08, 0.16, gSun.y));
                vec4 fogTexel = parameter(28.0);
                float fogEnd = fogTexel.r + fogTexel.g * 256.0;
                shade = 1.0 - over * gShadow * strength * (1.0 - smoothstep(fogEnd * 0.5, fogEnd * 0.85, world * length(dir.xz)));
            }
        }
    }
    float seen = level * share;
    vec3 given = clouds.rgb * seen;
    // a faint grain: the smooth colors would show steps between the shades of the screen without it
    given += (hash12(gl_FragCoord.xy + fract(gTime) * 61.0) - 0.5) / 255.0 * seen;
    float behind = mix(1.0, clouds.a, seen) * mix(1.0, shade, level);
    if (gBolt > 0.5 && gFlash > 0.02) {
        // the bolt goes down from the base of the clouds to the horizon, where it struck, far off: hidden by all that is nearer than it
        float worldAt = depth > 1e-6 ? 0.0 : 1e9;
        if (depth > 1e-6) {
            vec2 ndc = gl_FragCoord.xy / screen * 2.0 - 1.0;
            float viewDepth = ProjMat[3][2] / max(depth + ProjMat[2][2], 1e-6);
            worldAt = abs(viewDepth) * length(vec3(ndc.x / ProjMat[0][0], ndc.y / ProjMat[1][1], 1.0));
        }
        if (worldAt > gFlashFar) {
            float top = atan(max(gBottom - eye.y, 4.0), gFlashFar);
            float boltLight = boltAt(dir, gFlashAround, sin(top), gBoltSeed) * gFlash * level;
            given += flashColor() * boltLight * 1.6;
        }
    }
    fragColor = vec4(given, 1.0 - behind);
#else
    // What is added to it is light: nebulae, stars, aurora.
    vec3 light = vec3(0.0);
    if (lit > 0.0) {
#ifdef SPLIT
        // the smooth light was made at a lower resolution, by the pass before: this pixel is between its pixels, and is filtered
        light = texture(Sampler1, gl_FragCoord.xy / (vec2(textureSize(Sampler1, 0)) * float(LOWRES_DIVISOR))).rgb;
#else
        light = skyLight(dir, sphere, camera, camHeight, tint, phase, theme, latitude, cold) * strength;
#endif
        // The stars cover the whole sky, also below the horizon, which is seen from an island's edge; the land covers them where it is. They lie in
        // layers at different depths: far ones small and dim, a middle layer, and a few near ones, larger and brighter. The nearer layers turn a
        // little with where the player is, against the far one, so the sky has depth; the place followed is smooth, so they never jump.
        {
            float clarity = 0.75 + 0.35 * clear + 0.2 * cold;
            float crowd = 0.6 + 1.4 * milkyWay(sphere);
            vec3 middleSphere = rotateX(rotateY(sphere, camera.x * MIDDLE_PARALLAX), camera.y * MIDDLE_PARALLAX);
            vec3 nearSphere = rotateX(rotateY(sphere, camera.x * NEAR_PARALLAX), camera.y * NEAR_PARALLAX);
            float face;
            vec2 uv = cubeUv(middleSphere, face);
            float nearFace;
            vec2 nearUv = cubeUv(nearSphere, nearFace);
            vec3 starLight = stars(middleSphere, uv, face, 28.0, 0.06 * density * crowd * clarity, 1.0, 0.07, 1.0, 0.0)
                + stars(nearSphere, nearUv, nearFace, 9.0, 0.10 * density * crowd * clarity, 5.0, 0.10, 1.8, 1.0);
            if (theme > 2.5) {
                // many small far stars
                float farFace;
                vec2 farUv = cubeUv(sphere, farFace);
                starLight += stars(sphere, farUv, farFace, 55.0, 0.2 * density * clarity, 11.0, 0.06, 0.8, 0.0);
            }
            if (theme > 4.5) {
                starLight += glowDots(middleSphere, uv, face, 8.0, 0.14 * density * clarity, 21.0, 0.07, mix(tint, vec3(1.0), 0.75));
                starLight += glowDots(middleSphere, uv, face, 15.0, 0.16 * density * clarity, 33.0, 0.06, mix(tint, vec3(1.0), 0.75));
            }
            light += starLight * strength;
        }
    }
    // The light is added to the background without ever going above white.
    color += (1.0 - exp(-light * brightness * 1.3)) * (1.0 - color);
    // at dawn and dusk, the horizon is warm
    color += vec3(0.95, 0.5, 0.3) * twilight * 0.45 * exp(-abs(dir.y) * 4.5);

    // The whole sky is covered, down to and below the horizon, so none of the sky of the game is left where the land is low.
    fragColor = vec4(color, opacity);
#endif
}
