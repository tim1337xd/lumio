#version 330

uniform sampler2D Sampler0;
// the game's block textures, on a sheet of 8 by 6 tiles (MenuGameTextures)
uniform sampler2D Sampler1;
// the game's snow texture
uniform sampler2D Sampler2;
// the game's crop and particle textures, on a sheet of 8 by 6 tiles (MenuGameTextures)
uniform sampler2D Sampler3;

out vec4 fragColor;

// The world behind the main menu, made for every pixel: fields of wheat, carrots and potatoes on low terraces, a dirt path that runs from the
// camera into the distance on the left, a ruined house on the right of it, old field walls, and a few trees far away in the fog. The camera
// stands low over the path, just above the crops, and looks across the fields at an angle to the blocks. The season changes the crops, the
// grass and the snow, and in winter snow falls as in the game; the hour changes the sky, the sun or moon, the fog and the light, and lights the lantern at night.
// It is a ray marched through the blocks, one block at a time, at a part of the size of the screen. The alpha is how far out of focus a pixel
// is, for the pass that softens what is far.
//
// What the shader needs to know is in the first texels of the texture, from 0 to 255 per channel:
//   texel 0: the time in seconds (red and green: whole seconds, blue: the fraction), and the first season (alpha)
//   texel 1: the mouse across, from 0 to 1 (red and green, 16 bits), the second season (blue), how far the change to it is (alpha)
//   texel 2: the mouse down (red and green, 16 bits), how many steps a ray may take (blue), whether the shadows are made (alpha)
//   texel 3: the size of the picture in pixels: the width (red and green) and the height (blue and alpha)
//   texel 4: the hour of the day, from 0 to 24 (red and green, 16 bits), whether snow falls in winter (blue), whether stars show (alpha)
//   texel 5: the camera coming down at the start of the game, from 1 (high up) to 0 (resting): how high it is (red and green, 16 bits) and
//            how far it looks up (blue and alpha, 16 bits)
//   texel 6: in the alpha, whether the picture is sharp everywhere (the season pictures)
//   texel 7: how many particles there are (red); from texel 8 on, three texels per particle: its position (16 bits for each axis), its roll
//            and its texture, and its size, how visible it is, how much it glows and its kind (1 cherry leaf, 2 leaf, 3 firefly)

const int G = 64;
const float MAX_DISTANCE = 170.0;
const float TOP = 92.0;
const float TAU = 6.2831853;
// the particles: how many there are is in texel 7, each one has three texels from texel 8 on (MenuParticles)
const float PARTICLE_TEXEL = 8.0;
const int MAX_PARTICLES = 24;
// where the game's textures are on their sheet (MenuGameTextures)
const int G_WHEAT = 0;
const int G_CARROTS = 8;
const int G_POTATOES = 12;
// the game's block light: the lantern gives light level 15 at its block, one less per block away
const ivec3 LANTERN_CELL = ivec3(13, 68, 16);
// how bright the block light is on the particles, which are lit like the sky before the picture's exposure
const float BLOCK_LIGHT = 1.35;
// the game's fixed shade of a block face by the way it looks: up, down, north and south, east and west
const float SHADE_UP = 1.0;
const float SHADE_DOWN = 0.5;
const float SHADE_NORTH_SOUTH = 0.8;
const float SHADE_EAST_WEST = 0.6;
// how much light a face that looks to the sun keeps in the shadow of the land: a soft, grey shadow
const float SHADOW_LIGHT = 0.72;
// the weights of the colors in their brightness, used by the picture's saturation
const vec3 LUMA = vec3(0.299, 0.587, 0.114);
// the light of something at full brightness, like a glowing firefly
const float FULL_BRIGHT = 2.2;

// the house: its corner (west, front), and the height of its yard, which is level
const ivec2 HOUSE = ivec2(8, 12);
const int YARD = G;

// the kinds of block
const int B_AIR = 0;
const int B_GRASS = 1;
const int B_DIRT = 2;
const int B_FARMLAND = 3;
const int B_PATH = 4;
const int B_STONE = 5;
const int B_LOG = 6;
const int B_LEAVES = 7;
const int B_BRICK = 8;
const int B_MOSSY_BRICK = 9;
const int B_CRACKED_BRICK = 10;
const int B_COBBLE = 11;
const int B_MOSSY_COBBLE = 12;
const int B_PLANKS = 14;
const int B_HAY = 15;
const int B_WATER = 16;
const int B_ICE = 17;
const int B_LANTERN = 18;
const int B_CHAIN = 19;
const int B_SNOW = 25;
// the roof's boards: spruce planks, darker than the walls' oak
const int B_ROOF = 26;
// plants, which stand in the cell above the ground
const int P_WHEAT = 20;
const int P_CARROTS = 21;
const int P_POTATOES = 22;
const int P_GRASS = 23;
const int P_FLOWER = 24;

// the shapes of a block in its cell
const int S_FULL = 0;
const int S_SLAB = 1;
const int S_STAIR_PZ = 2;
const int S_STAIR_NZ = 3;
const int S_STAIR_PX = 4;
const int S_STAIR_NX = 5;
const int S_WALL_X = 6;
const int S_WALL_Z = 7;
const int S_POST = 8;
const int S_LANTERN = 9;
const int S_CHAIN = 10;
const int S_BEAM_X = 11;
const int S_BEAM_Z = 12;
const int S_PATH = 13;
const int S_WATER = 14;
const int S_LAYER = 15;
// the height of one snow layer, as in the game
const float SNOW_LAYER = 0.125;

// the game's block textures, in the order of their sheet (MenuGameTextures)
const int T_GRASS_TOP = 0;
const int T_GRASS_SIDE = 1;
const int T_DIRT = 2;
const int T_STONE = 3;
const int T_FARMLAND = 4;
const int T_SNOW = 5;
const int T_LOG = 6;
const int T_LOG_TOP = 7;
const int T_LEAVES = 8;
const int T_SHORT_GRASS = 9;
const int T_WHEAT = 10;
const int T_POPPY = 11;
const int T_DANDELION = 12;
const int T_CORNFLOWER = 13;
const int T_WATER = 14;
const int T_GRASS_OVERLAY = 15;
const int T_CARROTS = 16;
const int T_POTATOES = 17;
const int T_PATH_TOP = 18;
const int T_PATH_SIDE = 19;
const int T_LANTERN = 20;
const int T_PLANKS = 21;
const int T_BRICKS = 22;
const int T_MOSSY_BRICKS = 23;
const int T_CRACKED_BRICKS = 24;
const int T_COBBLE = 25;
const int T_MOSSY_COBBLE = 26;
const int T_HAY_SIDE = 27;
const int T_HAY_TOP = 28;
const int T_VINE = 29;
const int T_CHAIN = 30;
const int T_WHEAT_YOUNG = 31;
const int T_POTATO_FLOWERS = 32;
const int T_STALKS = 33;
const int T_ICE = 34;
const int T_SNOW_SIDE = 35;
const int T_ALLIUM = 36;
const int T_CARROTS_YOUNG = 37;
const int T_ROOF = 38;

float gTime;
// the season of most of the picture, the one it changes to, and how far: a block changes when its own moment in the change has come
int gSeasonA = 3;
int gSeasonB = 3;
float gSeasonMix = 0.0;
vec3 gGrass = vec3(0.57, 0.74, 0.35);
vec3 gLeaf = vec3(0.95, 0.95, 0.32);
float gNight = 0.0;
// always 0, but read from the parameters: loop lengths use it, so the driver keeps each loop as a loop instead of copying its body many times,
// which made this shader take seconds to compile
int gZero = 0;

vec4 parameter(float index) {
    return floor(texelFetch(Sampler0, ivec2(int(index), 0), 0) * 255.0 + 0.5);
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

float vnoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = p - cell;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(cell), hash12(cell + vec2(1.0, 0.0)), f.x),
               mix(hash12(cell + vec2(0.0, 1.0)), hash12(cell + vec2(1.0, 1.0)), f.x), f.y);
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// The looks: the light of a season at an hour of the day

struct Look {
    vec3 sun;       // the way to the sun, or the moon
    vec3 sunColor;
    vec3 horizon;
    vec3 zenith;
    vec3 ambient;
    vec3 daylight;  // the sky's light on the blocks, as the game's light map gives it: nearly white by day, never the sky's blue
    vec3 fog;
    float density;
    vec3 water;
    vec3 cloud;
    float stars;
    float saturation;
    float exposure;
    float cover;     // how much of the sky the clouds cover: less is more
};

Look makeLook(int index) {
    Look l;
    if (index == 0) {
        // a summer day, bright and saturated
        l.sun = normalize(vec3(0.62, 0.55, -0.35));
        l.sunColor = vec3(1.0, 0.93, 0.80) * 1.35;
        l.horizon = vec3(0.74, 0.86, 1.0);
        l.zenith = vec3(0.20, 0.45, 0.92);
        l.ambient = vec3(0.40, 0.54, 0.80) * 0.80;
        l.daylight = vec3(1.0);
        l.fog = vec3(0.72, 0.84, 0.98);
        l.density = 0.0068;
        l.water = vec3(0.07, 0.38, 0.62);
        l.cloud = vec3(1.0);
        l.stars = 0.0;
        l.saturation = 1.16;
        l.exposure = 1.45;
        l.cover = 0.50;
    } else if (index == 1) {
        // a low sun, warm and orange, with long shadows
        l.sun = normalize(vec3(0.85, 0.13, -0.15));
        l.sunColor = vec3(1.6, 0.72, 0.34);
        l.horizon = vec3(1.0, 0.56, 0.36);
        l.zenith = vec3(0.16, 0.18, 0.46);
        l.ambient = vec3(0.55, 0.38, 0.50) * 0.58;
        l.daylight = vec3(0.86, 0.76, 0.68);
        l.fog = vec3(0.94, 0.56, 0.42);
        l.density = 0.0080;
        l.water = vec3(0.42, 0.20, 0.30);
        l.cloud = vec3(1.0, 0.56, 0.44);
        l.stars = 0.15;
        l.saturation = 1.12;
        l.exposure = 1.05;
        l.cover = 0.46;
    } else if (index == 2) {
        // a dark blue night with a moon and stars
        l.sun = normalize(vec3(-0.32, 0.46, 0.83));
        l.sunColor = vec3(0.52, 0.64, 1.0) * 0.55;
        l.horizon = vec3(0.07, 0.10, 0.24);
        l.zenith = vec3(0.008, 0.014, 0.07);
        l.ambient = vec3(0.14, 0.20, 0.42) * 0.70;
        l.daylight = vec3(0.26, 0.29, 0.40);
        l.fog = vec3(0.05, 0.08, 0.18);
        l.density = 0.0080;
        l.water = vec3(0.02, 0.10, 0.20);
        l.cloud = vec3(0.14, 0.19, 0.34);
        l.stars = 1.0;
        l.saturation = 1.05;
        l.exposure = 1.25;
        l.cover = 0.60;
    } else if (index == 5) {
        // a winter day: cold, pale and bright, with a low white sun
        l.sun = normalize(vec3(0.60, 0.34, -0.40));
        l.sunColor = vec3(1.0, 0.97, 0.95) * 1.05;
        l.horizon = vec3(0.80, 0.88, 0.96);
        l.zenith = vec3(0.42, 0.58, 0.82);
        l.ambient = vec3(0.58, 0.66, 0.82) * 0.88;
        l.daylight = vec3(0.96, 0.97, 1.0);
        l.fog = vec3(0.82, 0.88, 0.95);
        l.density = 0.0090;
        l.water = vec3(0.10, 0.28, 0.42);
        l.cloud = vec3(0.95, 0.97, 1.0);
        l.stars = 0.0;
        l.saturation = 0.95;
        l.exposure = 1.25;
        l.cover = 0.42;
    } else if (index == 6) {
        // a spring day: fresh, soft and light, with a little haze
        l.sun = normalize(vec3(0.62, 0.50, -0.34));
        l.sunColor = vec3(1.1, 1.0, 0.86) * 1.2;
        l.horizon = vec3(0.82, 0.90, 0.98);
        l.zenith = vec3(0.32, 0.56, 0.92);
        l.ambient = vec3(0.48, 0.60, 0.78) * 0.82;
        l.daylight = vec3(1.0);
        l.fog = vec3(0.82, 0.90, 0.96);
        l.density = 0.0078;
        l.water = vec3(0.12, 0.44, 0.62);
        l.cloud = vec3(1.0);
        l.stars = 0.0;
        l.saturation = 1.12;
        l.exposure = 1.40;
        l.cover = 0.44;
    } else if (index == 4) {
        // an autumn afternoon: warm golden light and big clouds
        l.sun = normalize(vec3(0.75, 0.30, -0.30));
        l.sunColor = vec3(1.55, 1.18, 0.60);
        l.horizon = vec3(1.0, 0.82, 0.56);
        l.zenith = vec3(0.22, 0.38, 0.64);
        l.ambient = vec3(0.56, 0.50, 0.38) * 0.78;
        l.daylight = vec3(1.0, 0.96, 0.88);
        l.fog = vec3(0.95, 0.80, 0.56);
        l.density = 0.0072;
        l.water = vec3(0.20, 0.42, 0.50);
        l.cloud = vec3(1.0, 0.90, 0.78);
        l.stars = 0.0;
        l.saturation = 1.2;
        l.exposure = 1.40;
        l.cover = 0.38;
    } else {
        // a morning in mist: close, pale and soft
        l.sun = normalize(vec3(0.50, 0.42, -0.40));
        l.sunColor = vec3(0.95, 0.94, 0.98) * 0.80;
        l.horizon = vec3(0.76, 0.80, 0.84);
        l.zenith = vec3(0.52, 0.62, 0.74);
        l.ambient = vec3(0.56, 0.62, 0.70) * 0.70;
        l.daylight = vec3(0.84, 0.85, 0.87);
        l.fog = vec3(0.74, 0.78, 0.82);
        l.density = 0.0150;
        l.water = vec3(0.22, 0.38, 0.44);
        l.cloud = vec3(0.86, 0.89, 0.91);
        l.stars = 0.0;
        l.saturation = 0.85;
        l.exposure = 1.12;
        l.cover = 0.50;
    }
    return l;
}

Look mixLook(Look a, Look b, float m) {
    Look l;
    l.sun = normalize(mix(a.sun, b.sun, m));
    l.sunColor = mix(a.sunColor, b.sunColor, m);
    l.horizon = mix(a.horizon, b.horizon, m);
    l.zenith = mix(a.zenith, b.zenith, m);
    l.ambient = mix(a.ambient, b.ambient, m);
    l.daylight = mix(a.daylight, b.daylight, m);
    l.fog = mix(a.fog, b.fog, m);
    l.density = mix(a.density, b.density, m);
    l.water = mix(a.water, b.water, m);
    l.cloud = mix(a.cloud, b.cloud, m);
    l.stars = mix(a.stars, b.stars, m);
    l.saturation = mix(a.saturation, b.saturation, m);
    l.exposure = mix(a.exposure, b.exposure, m);
    l.cover = mix(a.cover, b.cover, m);
    return l;
}

// The look of a season at an hour of the day: the sun goes over from the left to the right, low in the morning and the evening, misty early in
// the morning, and under the land at night, with the moon.
Look timeLook(int base, float hour) {
    float a = (hour - 6.0) / 12.0 * 3.14159265;
    float e = sin(a) * 0.8;
    vec3 sunDir = normalize(vec3(-cos(a) * 0.85, max(e, 0.035), -0.3));
    Look l = makeLook(base);
    // a pale mist in the early morning, which lifts as the sun rises
    float mist = smoothstep(4.5, 6.5, hour) * (1.0 - smoothstep(7.5, 10.0, hour));
    l = mixLook(l, makeLook(3), mist * 0.75);
    float duskW = (1.0 - smoothstep(0.04, 0.42, e)) * smoothstep(-0.30, -0.02, e) * (hour > 12.0 ? 1.0 : 0.55);
    float nightW = 1.0 - smoothstep(-0.22, 0.0, e);
    l = mixLook(l, makeLook(1), duskW * 0.85);
    Look night = makeLook(2);
    l = mixLook(l, night, nightW);
    l.sun = normalize(mix(sunDir, night.sun, nightW));
    return l;
}

int lookFor(int season) {
    return season == 0 ? 5 : season == 1 ? 6 : season == 2 ? 0 : 4;
}

void seasonColors(int season, out vec3 grass, out vec3 leaf) {
    if (season == 0) {
        grass = vec3(0.62, 0.72, 0.58); leaf = vec3(0.50, 0.58, 0.52);
    } else if (season == 1) {
        grass = vec3(0.55, 0.95, 0.32); leaf = vec3(0.70, 1.05, 0.42);
    } else if (season == 2) {
        grass = vec3(0.48, 0.82, 0.26); leaf = vec3(0.55, 0.95, 0.30);
    } else {
        grass = vec3(0.78, 0.74, 0.32); leaf = vec3(1.10, 0.62, 0.16);
    }
}

// The season of a block: during a change, every block changes at its own moment, so the change spreads over the land like a dissolve.
int seasonAt(ivec2 c) {
    return hash12(vec2(c) + 71.3) < gSeasonMix ? gSeasonB : gSeasonA;
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// The sky

vec3 sky(vec3 ro, vec3 rd, Look look) {
    float up = clamp(rd.y, 0.0, 1.0);
    vec3 color = mix(look.horizon, look.zenith, pow(up, 0.5));
    float s = max(dot(rd, look.sun), 0.0);
    // the sun or the moon, and the sky brightening towards it as the game's does; no glow of its own round it
    color += look.sunColor * (pow(s, 900.0) * 9.0 + pow(s, 7.0) * 0.14);
    if (look.stars > 0.01 && rd.y > 0.0) {
        vec3 g = rd * 110.0;
        vec3 cell = floor(g);
        float h = hash13(cell);
        if (h > 0.9935) {
            vec3 local = g - cell - 0.5;
            float twinkle = 0.65 + 0.35 * sin(gTime * (1.2 + h * 40.0) + h * 99.0);
            color += vec3(0.85, 0.9, 1.0) * (1.0 - smoothstep(0.0, 0.42, length(local))) * twinkle * look.stars * smoothstep(0.0, 0.2, rd.y);
        }
    }
    if (rd.y > 0.015) {
        // clouds: a flat layer high above, that goes by, and is lit from the sun's side
        float t = (130.0 - ro.y) / rd.y;
        vec2 q = (ro.xz + rd.xz * t) * 0.0085 + vec2(gTime * 0.010, gTime * 0.004);
        float c = 0.55 * vnoise(q) + 0.30 * vnoise(q * 2.3 + 7.0) + 0.15 * vnoise(q * 5.1 + 3.0);
        float density = smoothstep(look.cover, look.cover + 0.28, c);
        float edge = smoothstep(look.cover, look.cover + 0.28, 0.55 * vnoise(q + look.sun.xz * 0.06) + 0.30 * vnoise(q * 2.3 + 7.0) + 0.15 * vnoise(q * 5.1 + 3.0));
        vec3 lit = look.cloud * (0.62 + 0.55 * clamp((density - edge) * 6.0 + 0.5, 0.0, 1.0)) * (0.55 + 0.45 * length(look.sunColor) * 0.5);
        color = mix(color, lit, density * smoothstep(0.015, 0.14, rd.y) * 0.92);
    }
    return color;
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// The land

int encode(int block, int shape) {
    return block | (shape << 8);
}

// The path: two blocks wide, along the blocks, from behind the camera far into the distance.
bool isPath(ivec2 c) {
    return (c.x == 0 || c.x == 1) && c.y > -14 && c.y < 150;
}

bool inYard(ivec2 c) {
    return c.x >= HOUSE.x - 3 && c.x <= HOUSE.x + 11 && c.y >= HOUSE.y - 3 && c.y <= HOUSE.y + 8;
}

// Low terraces, one block at a time, rising diagonally across the fields away from the camera, so their dirt edges are seen.
int terrace(ivec2 c) {
    float u = 0.30 * float(c.y) - 0.21 * float(c.x) + 1.0;
    return int(clamp(floor(u / 9.0), 0.0, 3.0));
}

int columnHeight(ivec2 c) {
    if (inYard(c)) {
        return YARD;
    }
    return G + terrace(c);
}

// The water channels of the fields, one block wide, across the fields on both sides of the path.
bool isChannel(ivec2 c) {
    return (c.y == 31 || c.y == 63) && abs(c.x) >= 2 && abs(c.x) <= 44 && (c.x < 3 || c.x > 5) && !inYard(c);
}

bool inFields(ivec2 c) {
    return c.x >= -46 && c.x <= 46 && c.y >= -14 && c.y <= 142 && !inYard(c);
}

// The old field walls, only on the right of the path: along it, behind the house, and further back, so they enclose the fields there.
// 1 runs along z, 2 along x, 0 is no wall.
int wallLine(ivec2 c) {
    if ((c.x == 4 && c.y >= 19 && c.y <= 140) || (c.x == 26 && c.y >= 19 && c.y <= 44)) {
        return 1;
    }
    if ((c.y == 19 && c.x >= 4 && c.x <= 26) || (c.y == 44 && c.x >= 4 && c.x <= 40)) {
        return 2;
    }
    return 0;
}

bool besideWall(ivec2 c) {
    return wallLine(c + ivec2(1, 0)) != 0 || wallLine(c - ivec2(1, 0)) != 0 || wallLine(c + ivec2(0, 1)) != 0 || wallLine(c - ivec2(0, 1)) != 0;
}

// A few loose boulders in the fields, a block or two high.
const int BOULDERS = 4;
const ivec2 BOULDER[BOULDERS] = ivec2[](ivec2(-9, 23), ivec2(15, 33), ivec2(-17, 49), ivec2(31, 58));

int boulderBlock(ivec3 cell, int h) {
    for (int i = 0; i < BOULDERS + gZero; i++) {
        ivec2 d = cell.xz - BOULDER[i];
        if (abs(d.x) > 1 || abs(d.y) > 1) {
            continue;
        }
        int dy = cell.y - h;
        float r = hash12(vec2(cell.xz) + float(i) * 3.1);
        bool part = abs(d.x) + abs(d.y) <= 1 || r < 0.35;
        if ((dy == 1 && part) || (dy == 2 && d == ivec2(0) && i % 2 == 0)) {
            return encode(r < 0.4 ? B_STONE : r < 0.7 ? B_COBBLE : B_MOSSY_COBBLE, S_FULL);
        }
    }
    return 0;
}

bool nearBoulder(ivec2 c) {
    for (int i = 0; i < BOULDERS + gZero; i++) {
        ivec2 d = c - BOULDER[i];
        if (abs(d.x) <= 2 && abs(d.y) <= 2) {
            return true;
        }
    }
    return false;
}

// The top block of a column: path, water, farmland in the fields, grass elsewhere.
int groundTop(ivec2 c) {
    if (isPath(c)) {
        return B_PATH;
    }
    if (isChannel(c)) {
        return seasonAt(c) == 0 ? B_ICE : B_WATER;
    }
    if (inFields(c) && wallLine(c) == 0 && !besideWall(c) && !nearBoulder(c)) {
        return B_FARMLAND;
    }
    return B_GRASS;
}

int groundBlock(ivec2 c, int depth) {
    if (depth == 0) {
        return groundTop(c);
    }
    return depth < 4 ? B_DIRT : B_STONE;
}

int wallBlock(ivec3 cell, int h) {
    int line = wallLine(cell.xz);
    if (line == 0) {
        return 0;
    }
    int along = line == 1 ? cell.z : cell.x;
    int dy = cell.y - h;
    // where two walls meet or a wall ends, there is a pillar
    bool corner = (cell.x == 4 || cell.x == 26 || cell.x == 40) && (cell.z == 19 || cell.z == 44);
    bool pillar = corner || along % 5 == 0;
    float group = hash12(vec2(float(along / 3), float(line) * 7.0 + float(cell.x == 4 ? 0 : cell.x) * 0.1 + float(cell.z == 19 ? 0 : 1)));
    bool fallen = group < 0.24 && !corner;
    bool gone = group < 0.07 && !corner;
    float r = hash12(vec2(cell.xz) + float(dy) * 3.7);
    if (dy == 1) {
        if (gone) {
            return r < 0.5 ? encode(B_COBBLE, S_SLAB) : 0;
        }
        return encode(r < 0.4 ? B_COBBLE : r < 0.72 ? B_MOSSY_COBBLE : B_STONE, S_FULL);
    }
    if (dy == 2 && !fallen) {
        if (pillar) {
            return encode(B_STONE, S_FULL);
        }
        return encode(r < 0.3 ? B_MOSSY_COBBLE : B_COBBLE, line == 1 ? S_WALL_Z : S_WALL_X);
    }
    if (dy == 3 && pillar && !fallen) {
        return r < 0.5 ? encode(B_STONE, S_SLAB) : encode(B_COBBLE, S_POST);
    }
    return 0;
}

// Bushes and rubble along the walls, a block high.
int wallSideBlock(ivec3 cell, int h) {
    if (cell.y != h + 1 || !besideWall(cell.xz) || isPath(cell.xz)) {
        return 0;
    }
    float r = hash12(vec2(cell.xz) + 13.7);
    if (r < 0.07) {
        return encode(B_LEAVES, S_FULL);
    }
    if (r > 0.965) {
        return encode(r > 0.985 ? B_MOSSY_COBBLE : B_COBBLE, S_SLAB);
    }
    return 0;
}

int stoneVariant(ivec3 p) {
    float r = hash13(vec3(p) + 5.5);
    return r < 0.55 ? B_BRICK : r < 0.8 ? B_MOSSY_BRICK : B_CRACKED_BRICK;
}

// The ruined house, in its own coordinates: x from the west wall (0) to the east wall (7), z from the front (0) to the back (6), y from the
// floor up. The two walls that face the camera fell almost to the ground; the back and east walls still stand, with a little of the roof.
int houseBlock(ivec3 p) {
    int x = p.x;
    int y = p.y;
    int z = p.z;
    if (x < -3 || x > 10 || z < -2 || z > 7 || y < 0 || y > 7) {
        return 0;
    }
    // a stone chimney outside the east wall, taller than the house, with a stone brick slab on top
    if (x == 8 && z == 3) {
        return y <= 6 ? encode(y % 3 == 0 ? B_COBBLE : B_BRICK, S_FULL) : encode(B_BRICK, S_SLAB);
    }
    // hay bales beside the front east corner
    if ((y == 0 && ((x == 8 && z == 0) || (x == 9 && z == 0) || (x == 8 && z == -1))) || (y == 1 && x == 8 && z == 0)) {
        return encode(B_HAY, S_FULL);
    }
    // rubble outside the fallen walls
    if (y == 0) {
        if (x == -1 && z == 2) return encode(B_COBBLE, S_SLAB);
        if (x == -1 && z == 4) return encode(B_MOSSY_BRICK, S_FULL);
        if (x == -2 && z == 5) return encode(B_MOSSY_COBBLE, S_SLAB);
        if (x == 2 && z == -1) return encode(B_BRICK, S_SLAB);
        if (x == 6 && z == -1) return encode(B_COBBLE, S_SLAB);
        if (x == 5 && z == -2) return encode(B_CRACKED_BRICK, S_SLAB);
    }
    if (x < 0 || x > 7 || z < 0 || z > 6) {
        return 0;
    }
    bool back = z == 6;
    bool east = x == 7;
    bool west = x == 0;
    bool front = z == 0;
    if (y > 5) {
        return 0;
    }
    // what is left of the roof: a lean-to of dark boards on the top plates and two rafters, sloping down to the front. Towards the front it fell
    // in: the boards end raggedly, only where something still carries them, and the rafters stick out bare
    if (y == 5) {
        if (back) {
            return x != 3 ? encode(B_ROOF, S_STAIR_PZ) : 0;
        }
        if ((z == 5 && (x == 1 || x == 2 || x == 5 || x == 6)) || (z == 4 && (x == 5 || x == 6)) || (z == 3 && x == 5)) {
            return encode(B_ROOF, S_SLAB);
        }
        return 0;
    }
    if (y == 4) {
        // the top plates: a log along the back wall, and one along the east wall that broke off towards the front
        if (back) {
            return encode(B_LOG, S_BEAM_X);
        }
        if (east && z >= 3) {
            return encode(B_LOG, S_BEAM_Z);
        }
        // two rafters from the back wall towards the front, one broken short
        if ((x == 2 && z >= 4) || (x == 5 && z >= 2)) {
            return encode(B_LOG, S_BEAM_Z);
        }
        return 0;
    }
    // the log posts at the corners: the two at the back stand up to the top plate, the two at the front broke off
    if ((back || front) && (east || west)) {
        int tall = back ? 3 : (east ? 2 : 1);
        return y <= tall ? encode(B_LOG, S_FULL) : 0;
    }
    if (back || east) {
        if (y <= 1) {
            return encode(stoneVariant(p), S_FULL);
        }
        if (y == 2) {
            return encode(B_LOG, back ? S_BEAM_X : S_BEAM_Z);
        }
        if (y == 3 && ((back && (x == 3 || x == 4)) || (east && (z == 2 || z == 3)))) {
            return 0; // the windows
        }
        return encode(B_PLANKS, S_FULL);
    }
    if (west || front) {
        if (front && (x == 3 || x == 4)) {
            return 0; // where the door was
        }
        if (y == 0) {
            return encode(stoneVariant(p), S_FULL);
        }
        if (y == 1) {
            if (west && z == 4) return encode(B_CRACKED_BRICK, S_STAIR_PZ);
            if (west && z == 5) return encode(B_BRICK, S_SLAB);
            if (front && x == 6) return encode(B_MOSSY_BRICK, S_STAIR_NX);
            if (front && x == 1) return encode(B_BRICK, S_SLAB);
        }
        return 0;
    }
    // inside
    if (y == 0) {
        if (z == 2 && x >= 2 && x <= 4) return encode(B_LOG, S_BEAM_X);     // the fallen roof beam
        if (x == 5 && z == 4) return encode(B_PLANKS, S_FULL);              // the broken table
        if (x == 6 && z == 4) return encode(B_PLANKS, S_STAIR_NX);
        if (x == 2 && z == 4) return encode(B_COBBLE, S_SLAB);             // rubble
        if (x == 6 && z == 1) return encode(B_BRICK, S_SLAB);
        if (x == 1 && z == 5) return encode(B_LEAVES, S_FULL);              // the bush in the corner
    }
    if (y == 1 && x == 1 && z == 5) {
        return encode(B_LEAVES, S_FULL);
    }
    // the lantern, hanging from a rafter
    if (x == 5 && z == 4 && y == 3) {
        return encode(B_LANTERN, S_LANTERN);
    }
    return 0;
}

// Whether the floor of the house is planks there; elsewhere grass comes through.
bool housePlanks(ivec2 c) {
    ivec2 p = c - HOUSE;
    return p.x >= 1 && p.x <= 6 && p.y >= 1 && p.y <= 5 && hash12(vec2(p) + 2.0) > 0.35;
}

// A few trees far away, blurred by the fog: never in the fields or near the camera.
int treeBlock(ivec3 cell) {
    ivec2 g = ivec2(floor(vec2(cell.xz) / 7.0));
    if (hash12(vec2(g) + 3.7) > 0.33) {
        return 0;
    }
    ivec2 origin = g * 7 + ivec2(2 + int(floor(hash12(vec2(g) + 9.1) * 3.0)), 2 + int(floor(hash12(vec2(g) + 5.3) * 3.0)));
    if (inFields(origin) || isPath(origin) || origin.y < 60) {
        return 0;
    }
    int ground = columnHeight(origin);
    int trunk = 4 + int(floor(hash12(vec2(g) + 1.9) * 3.0));
    ivec3 local = cell - ivec3(origin.x, ground, origin.y);
    if (local.x == 0 && local.y == 0 && local.z == 0) {
        return 0;
    }
    if (local.x == 0 && local.z == 0 && local.y >= 1 && local.y <= trunk) {
        return encode(B_LOG, S_FULL);
    }
    int ax = abs(local.x);
    int az = abs(local.z);
    if (local.y >= trunk - 1 && local.y <= trunk && ax <= 2 && az <= 2 && ax + az <= 3) {
        return encode(B_LEAVES, S_FULL);
    }
    if (local.y == trunk + 1 && ax + az <= 2) {
        return encode(B_LEAVES, S_FULL);
    }
    return 0;
}

// how many snow layers lie on a column in winter, made further down
int snowLayers(ivec2 c, int top);

// What grows in the cell above a column: crops on farmland, grass and flowers on grass.
int plantAt(ivec2 c, int top) {
    int season = seasonAt(c);
    if (top == B_FARMLAND) {
        // every block of a field has its crop: blocks differ in growth (cropTexture), never by being empty
        // in winter the bare stalks stand out of the snow and its soft drifts; only the deep snow the wind piles against walls and boulders covers them
        if (season == 0 && snowLayers(c, top) >= 3) {
            return 0;
        }
        // patches of carrots and potatoes further back, wheat everywhere else
        if (c.y > 26) {
            float patch = hash12(floor(vec2(c) / vec2(7.0, 5.0)) + 2.2);
            if (patch < 0.16) {
                return P_CARROTS;
            }
            if (patch < 0.30) {
                return P_POTATOES;
            }
        }
        return P_WHEAT;
    }
    if (top != B_GRASS || season == 0) {
        return 0;
    }
    if (wallLine(c) != 0) {
        return 0;
    }
    float r = hash12(vec2(c) + 7.3);
    float flowers = season == 1 ? 0.10 : season == 2 ? 0.05 : 0.02;
    if (r > 1.0 - flowers) {
        return P_FLOWER;
    }
    return r < 0.45 ? P_GRASS : 0;
}

// In winter, the game's snow layers lie on the land: one layer over the fields and the meadow, like a snowy meadow in the game, with only the
// bare stalks standing out of it, a second one in soft drifts here and there, and two or three where the wind piles the snow up against the walls
// and boulders. None on the trodden path and the water. The snow is opaque: nothing of the ground under it shows.
int snowLayers(ivec2 c, int top) {
    if (top == B_PATH || top == B_WATER || top == B_ICE || top == B_PLANKS || seasonAt(c) != 0) {
        return 0;
    }
    if (besideWall(c) || nearBoulder(c)) {
        return 2 + int(hash12(vec2(c) + 19.1) * 1.99);
    }
    // soft drifts where the wind left a second layer: smooth patches over several blocks, not single blocks, so no grid shows
    return vnoise(vec2(c) * 0.16 + 5.1) > 0.66 ? 2 : 1;
}

// The block in a cell, with its shape: the ground, the house, the walls, the boulders, the far trees. 0 for air.
int blockAt(ivec3 cell, int h) {
    if (cell.y <= h) {
        if (cell.y == h) {
            if (inYard(cell.xz) && housePlanks(cell.xz)) {
                return encode(B_PLANKS, S_FULL);
            }
            int top = groundTop(cell.xz);
            bool lowFarmland = top == B_FARMLAND && seasonAt(cell.xz) != 0;
            int shape = top == B_PATH || lowFarmland ? S_PATH : top == B_WATER ? S_WATER : S_FULL;
            return encode(top, shape);
        }
        return encode(groundBlock(cell.xz, h - cell.y), S_FULL);
    }
    if (inYard(cell.xz)) {
        int house = houseBlock(ivec3(cell.x - HOUSE.x, cell.y - YARD - 1, cell.z - HOUSE.y));
        if (house != 0) {
            return house;
        }
    }
    int wall = wallBlock(cell, h);
    if (wall != 0) {
        return wall;
    }
    if (cell.y <= h + 2) {
        int boulder = boulderBlock(cell, h);
        if (boulder != 0) {
            return boulder;
        }
        int side = wallSideBlock(cell, h);
        if (side != 0) {
            return side;
        }
    }
    if (cell.y <= h + 10 && cell.z > 50) {
        return treeBlock(cell);
    }
    return 0;
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// The shapes of blocks: one or two boxes in their cell

void shapeBoxes(int shape, out vec3 a0, out vec3 a1, out vec3 b0, out vec3 b1, out bool two) {
    two = false;
    b0 = vec3(0.0);
    b1 = vec3(0.0);
    a0 = vec3(0.0);
    a1 = vec3(1.0);
    if (shape == S_SLAB) {
        a1 = vec3(1.0, 0.5, 1.0);
    } else if (shape >= S_STAIR_PZ && shape <= S_STAIR_NX) {
        a1 = vec3(1.0, 0.5, 1.0);
        two = true;
        b0 = vec3(shape == S_STAIR_PX ? 0.5 : 0.0, 0.5, shape == S_STAIR_PZ ? 0.5 : 0.0);
        b1 = vec3(shape == S_STAIR_NX ? 0.5 : 1.0, 1.0, shape == S_STAIR_NZ ? 0.5 : 1.0);
    } else if (shape == S_WALL_X || shape == S_WALL_Z || shape == S_POST) {
        a0 = vec3(0.25, 0.0, 0.25);
        a1 = vec3(0.75, 1.0, 0.75);
        if (shape != S_POST) {
            two = true;
            b0 = shape == S_WALL_X ? vec3(0.0, 0.0, 0.3125) : vec3(0.3125, 0.0, 0.0);
            b1 = shape == S_WALL_X ? vec3(1.0, 0.875, 0.6875) : vec3(0.6875, 0.875, 1.0);
        }
    } else if (shape == S_LANTERN) {
        a0 = vec3(0.3125, 0.0625, 0.3125);
        a1 = vec3(0.6875, 0.5625, 0.6875);
        two = true;
        b0 = vec3(0.4375, 0.5625, 0.4375);
        b1 = vec3(0.5625, 1.0, 0.5625);
    } else if (shape == S_CHAIN) {
        a0 = vec3(0.4375, 0.0, 0.4375);
        a1 = vec3(0.5625, 1.0, 0.5625);
    } else if (shape == S_PATH) {
        a1 = vec3(1.0, 0.9375, 1.0);
    } else if (shape == S_WATER) {
        a1 = vec3(1.0, 0.875, 1.0);
    }
}

// Where a ray enters a box, if it does between two distances; the normal of the face it enters through.
bool hitBox(vec3 ro, vec3 srd, vec3 lo, vec3 hi, float tFrom, float tTo, out float tHit, out vec3 normal) {
    vec3 q0 = (lo - ro) / srd;
    vec3 q1 = (hi - ro) / srd;
    vec3 near = min(q0, q1);
    vec3 far = max(q0, q1);
    float tin = max(max(near.x, near.y), near.z);
    float tout = min(min(far.x, far.y), far.z);
    if (tin > tout || tout < tFrom || tin > tTo) {
        tHit = 0.0;
        normal = vec3(0.0, 1.0, 0.0);
        return false;
    }
    tHit = max(tin, tFrom);
    normal = (near.x >= near.y && near.x >= near.z) ? vec3(-sign(srd.x), 0.0, 0.0) : (near.y >= near.z ? vec3(0.0, -sign(srd.y), 0.0) : vec3(0.0, 0.0, -sign(srd.z)));
    return true;
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// The plants

vec2 faceUv(vec3 normal, vec3 p) {
    if (abs(normal.y) > 0.5) {
        return fract(p.xz);
    }
    if (abs(normal.x) > 0.5) {
        return vec2(fract(p.z), fract(p.y));
    }
    return vec2(fract(p.x), fract(p.y));
}

// One of the 16 by 16 textures of the sheet: 8 by 6 of them. The top of a texture is its top in the picture.
vec4 tile(int index, vec2 uv) {
    vec2 inside = clamp(vec2(uv.x, 1.0 - uv.y), 0.001, 0.999);
    return textureLod(Sampler1, (vec2(float(index % 8), float(index / 8)) + inside) / vec2(8.0, 6.0), 0.0);
}

// One of the game's crop and particle textures on their sheet, like tile() for the blocks.
vec4 gameTile(int index, vec2 uv) {
    vec2 inside = clamp(vec2(uv.x, 1.0 - uv.y), 0.001, 0.999);
    return textureLod(Sampler3, (vec2(float(index % 8), float(index / 8)) + inside) / vec2(8.0, 6.0), 0.0);
}

// The texture of a crop by its growth stage in the season of its block, as the game's crops grow: young in spring, ripe in summer and autumn
// (a few wheat blocks one stage behind in summer), and a little different from block to block, like a real field. In winter only bare stalks
// are left, which the game has no texture of: its short dry grass stands in for them. -1 means those stalks.
int cropTexture(int type, ivec3 cell) {
    int season = seasonAt(cell.xz);
    float r = hash12(vec2(cell.xz) + 3.1);
    if (season == 0) {
        return -1;
    }
    if (type == P_WHEAT) {
        int stage = season == 1 ? 1 + int(r * 3.0) : (season == 2 && r < 0.12 ? 6 : 7);
        return G_WHEAT + stage;
    }
    // carrots and potatoes have four textures for their eight ages: young ones in spring, grown ones (flowering potatoes) later
    int stage = season == 1 ? int(r * 2.0) : (r < 0.1 ? 2 : 3);
    return (type == P_CARROTS ? G_CARROTS : G_POTATOES) + stage;
}

vec4 cropTexel(int type, ivec3 cell, vec2 uv) {
    int index = cropTexture(type, cell);
    return index < 0 ? tile(T_STALKS, uv) : gameTile(index, uv);
}

// Crops are built as the game builds them: four planes per block in the shape of a "#", two across each way, 4/16 of a block in from the edges,
// from 1/16 below the block (the farmland is 15/16 high) up to 15/16. They are seen from both sides, and what is clear in their texture is cut
// out, so the rows behind and the farmland below show through.
bool cropPlanes(int type, ivec3 cell, vec3 ro, vec3 rd, float tIn, float tOut, out float tHit, out vec3 nHit) {
    vec3 base = vec3(cell);
    float best = tOut;
    bool found = false;
    nHit = vec3(1.0, 0.0, 0.0);
    for (int k = 0; k < 4 + gZero; k++) {
        bool alongZ = k < 2;
        float offset = (k % 2 == 0) ? 0.25 : 0.75;
        float d = alongZ ? rd.x : rd.z;
        if (abs(d) < 1e-5) {
            continue;
        }
        float th = ((alongZ ? base.x : base.z) + offset - (alongZ ? ro.x : ro.z)) / d;
        if (th < tIn || th >= best) {
            continue;
        }
        vec3 p = ro + rd * th;
        float across = alongZ ? p.z - base.z : p.x - base.x;
        float up = p.y - base.y + 1.0 / 16.0;
        if (across < 0.0 || across > 1.0 || up < 0.0 || up > 1.0) {
            continue;
        }
        if (cropTexel(type, cell, vec2(across, up)).a > 0.5) {
            best = th;
            nHit = alongZ ? vec3(-sign(rd.x), 0.0, 0.0) : vec3(0.0, 0.0, -sign(rd.z));
            found = true;
        }
    }
    tHit = best;
    return found;
}

int flowerTile(ivec3 cell) {
    float kind = hash12(vec2(cell.xz) + 4.4);
    int season = seasonAt(cell.xz);
    if (season == 1 && kind < 0.35) {
        return T_ALLIUM;
    }
    return kind < 0.55 ? T_POPPY : kind < 0.8 ? T_DANDELION : T_CORNFLOWER;
}

vec3 plantCentre(ivec3 cell) {
    vec2 jitter = vec2(hash12(vec2(cell.xz) + 1.7), hash12(vec2(cell.xz) + 9.1)) - 0.5;
    return vec3(float(cell.x) + 0.5 + jitter.x * 0.3, float(cell.y), float(cell.z) + 0.5 + jitter.y * 0.3);
}

float plantHeight(int type, ivec3 cell) {
    return type == P_GRASS ? 0.72 + 0.28 * hash12(vec2(cell.xz) + 3.3) : 1.0;
}

vec2 plantUv(int type, ivec3 cell, vec3 p, bool firstPlane) {
    vec3 tangent = firstPlane ? vec3(0.70710678, 0.0, 0.70710678) : vec3(0.70710678, 0.0, -0.70710678);
    float up = (p.y - float(cell.y)) / plantHeight(type, cell);
    float across = dot(p - plantCentre(cell), tangent) / 1.41421356 + 0.5;
    return vec2(across, up);
}

int plantTile(int type, ivec3 cell) {
    return type == P_GRASS ? T_SHORT_GRASS : flowerTile(cell);
}

// Grass and flowers are two planes that cross in the middle of their cell, as the game draws them.
bool plantCross(int type, ivec3 cell, vec3 ro, vec3 rd, float tIn, float tOut, out float tHit, out vec3 nHit) {
    vec3 c = plantCentre(cell);
    float best = 1e9;
    bool found = false;
    nHit = vec3(0.0, 1.0, 0.0);
    for (int k = 0; k < 2 + gZero; k++) {
        vec3 n = k == 0 ? vec3(0.70710678, 0.0, -0.70710678) : vec3(0.70710678, 0.0, 0.70710678);
        float d = dot(rd, n);
        if (abs(d) < 1e-5) {
            continue;
        }
        float th = dot(c - ro, n) / d;
        if (th < tIn || th > tOut || th >= best) {
            continue;
        }
        vec3 p = ro + rd * th;
        vec2 uv = plantUv(type, cell, p, k == 0);
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            continue;
        }
        if (tile(plantTile(type, cell), clamp(uv, 0.001, 0.999)).a > 0.5) {
            best = th;
            nHit = d < 0.0 ? n : -n;
            found = true;
        }
    }
    tHit = best;
    return found;
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// The ray

// Goes through the blocks along a ray, one cell after the other, and finds the first block, shape or plant it meets.
bool march(vec3 ro, vec3 rd, int steps, float maxT, out float tHit, out vec3 normal, out ivec3 hitCell, out int block) {
    ivec3 cell = ivec3(floor(ro));
    vec3 srd = vec3(abs(rd.x) < 1e-5 ? 1e-5 : rd.x, abs(rd.y) < 1e-5 ? 1e-5 : rd.y, abs(rd.z) < 1e-5 ? 1e-5 : rd.z);
    ivec3 dir = ivec3(sign(srd));
    vec3 tDelta = abs(1.0 / srd);
    vec3 tMax = (vec3(cell) + step(0.0, srd) - ro) / srd;
    int h = columnHeight(cell.xz);
    float t = 0.0;
    vec3 n = vec3(0.0, 1.0, 0.0);
    for (int i = 0; i < steps; i++) {
        float tOut = min(min(tMax.x, tMax.y), tMax.z);
        int found = 0;
        float foundT = t;
        vec3 foundN = n;
        int coded = cell.y <= h + 10 ? blockAt(cell, h) : 0;
        if (coded != 0) {
            int shape = coded >> 8;
            if (shape == S_FULL || shape == S_BEAM_X || shape == S_BEAM_Z) {
                found = coded;
            } else {
                vec3 a0; vec3 a1; vec3 b0; vec3 b1; bool two;
                shapeBoxes(shape, a0, a1, b0, b1, two);
                vec3 base = vec3(cell);
                float ta; vec3 na;
                if (hitBox(ro, srd, base + a0, base + a1, t, tOut, ta, na)) {
                    found = coded;
                    foundT = ta;
                    foundN = na;
                }
                float tb; vec3 nb;
                if (two && hitBox(ro, srd, base + b0, base + b1, t, tOut, tb, nb) && (found == 0 || tb < foundT)) {
                    found = coded;
                    foundT = tb;
                    foundN = nb;
                }
            }
        } else if (cell.y == h + 1 && t < 90.0) {
            int top = groundTop(cell.xz);
            if (inYard(cell.xz) && housePlanks(cell.xz)) {
                top = B_PLANKS;
            }
            // the snow layers at the foot of the plants: what enters through the snow is snow, the stalks stand out above it
            int layers = snowLayers(cell.xz, top);
            float tSnow = 0.0;
            vec3 nSnow = n;
            bool snow = layers > 0 && hitBox(ro, srd, vec3(cell), vec3(cell) + vec3(1.0, float(layers) * SNOW_LAYER, 1.0), t, tOut, tSnow, nSnow);
            int type = plantAt(cell.xz, top);
            if (type == P_WHEAT || type == P_CARROTS || type == P_POTATOES) {
                float tCrop;
                vec3 nCrop;
                if (cropPlanes(type, cell, ro, rd, t, snow ? tSnow : tOut, tCrop, nCrop)) {
                    found = encode(type, S_FULL);
                    foundT = tCrop;
                    foundN = nCrop;
                }
            } else if (type != 0) {
                float tPlant;
                vec3 nPlant;
                if (plantCross(type, cell, ro, rd, t, tOut, tPlant, nPlant)) {
                    found = encode(type, S_FULL);
                    foundT = tPlant;
                    foundN = nPlant;
                }
            }
            if (found == 0 && snow) {
                found = encode(B_SNOW, S_LAYER);
                foundT = tSnow;
                foundN = nSnow;
            }
        }
        if (found != 0) {
            tHit = foundT;
            normal = foundN;
            hitCell = cell;
            block = found;
            return true;
        }
        if (tMax.x < tMax.y && tMax.x < tMax.z) {
            t = tMax.x;
            tMax.x += tDelta.x;
            cell.x += dir.x;
            n = vec3(-float(dir.x), 0.0, 0.0);
            h = columnHeight(cell.xz);
        } else if (tMax.y < tMax.z) {
            t = tMax.y;
            tMax.y += tDelta.y;
            cell.y += dir.y;
            n = vec3(0.0, -float(dir.y), 0.0);
        } else {
            t = tMax.z;
            tMax.z += tDelta.z;
            cell.z += dir.z;
            n = vec3(0.0, 0.0, -float(dir.z));
            h = columnHeight(cell.xz);
        }
        if (t > maxT || (cell.y > int(TOP) && srd.y > 0.0)) {
            break;
        }
    }
    return false;
}

// ---------------------------------------------------------------------------------------------------------------------------------------------
// The color of things

int stoneTile(int block) {
    return block == B_BRICK ? T_BRICKS : block == B_MOSSY_BRICK ? T_MOSSY_BRICKS : block == B_CRACKED_BRICK ? T_CRACKED_BRICKS
        : block == B_COBBLE ? T_COBBLE : block == B_MOSSY_COBBLE ? T_MOSSY_COBBLE : T_STONE;
}

// Whether vines hang on a face: on the inside of the standing walls of the house, and here and there on the field walls.
bool vineOn(int block, int shape, vec3 normal, ivec3 cell) {
    if (abs(normal.y) > 0.5) {
        return false;
    }
    ivec3 local = cell - ivec3(HOUSE.x, YARD + 1, HOUSE.y);
    if (local.x >= 0 && local.x <= 7 && local.z >= 0 && local.z <= 6 && local.y >= 0 && local.y <= 4) {
        bool inside = (local.z == 6 && normal.z < -0.5) || (local.x == 7 && normal.x < -0.5);
        return inside && hash13(vec3(cell) + 8.8) < 0.55;
    }
    return (block == B_COBBLE || block == B_MOSSY_COBBLE || block == B_STONE) && wallLine(cell.xz) != 0 && hash13(vec3(cell) + 2.4) < 0.18;
}

vec3 blockColor(int block, int shape, vec3 normal, vec3 p, ivec3 cell) {
    vec2 fuv = faceUv(normal, p);
    bool top = normal.y > 0.5;
    int season = seasonAt(cell.xz);
    bool snowy = season == 0;
    if (block == P_GRASS || block == P_FLOWER) {
        bool firstPlane = normal.x * normal.z < 0.0;
        vec2 uv = clamp(plantUv(block, cell, p, firstPlane), 0.001, 0.999);
        vec3 color = tile(plantTile(block, cell), uv).rgb;
        if (block == P_GRASS) {
            float own = hash12(vec2(cell.xz) + 5.9);
            color *= gGrass * vec3(0.94 + 0.12 * own, 0.96 + 0.08 * (1.0 - own), 0.92 + 0.1 * own) * mix(0.62, 1.08, smoothstep(0.0, 0.85, uv.y));
        }
        return color;
    }
    if (block == P_WHEAT || block == P_CARROTS || block == P_POTATOES) {
        vec2 uv = vec2(fuv.x, p.y - float(cell.y) + 1.0 / 16.0);
        return cropTexel(block, cell, uv).rgb;
    }
    if (block == B_SNOW) {
        return tile(T_SNOW, fuv).rgb;
    }
    vec3 color;
    if (block == B_GRASS) {
        if (top) {
            return snowy ? tile(T_SNOW, fuv).rgb : tile(T_GRASS_TOP, fuv).rgb * gGrass;
        }
        if (snowy) {
            return tile(T_SNOW_SIDE, fuv).rgb;
        }
        vec4 overlay = tile(T_GRASS_OVERLAY, fuv);
        return mix(tile(T_GRASS_SIDE, fuv).rgb, overlay.rgb * gGrass, overlay.a);
    }
    if (block == B_FARMLAND) {
        if (top) {
            // under snow only snow shows, never the furrows
            return snowy ? tile(T_SNOW, fuv).rgb : tile(T_FARMLAND, fuv).rgb;
        }
        return tile(T_DIRT, fuv).rgb;
    }
    if (block == B_PATH) {
        vec4 path = tile(top ? T_PATH_TOP : T_PATH_SIDE, fuv);
        color = path.a < 0.5 ? tile(T_DIRT, fuv).rgb : path.rgb;
        // trodden: the path is only lightly covered, with patches of snow on it; where there is snow it is opaque
        if (snowy && top && hash12(floor(p.xz * 4.0)) > 0.55) {
            color = tile(T_SNOW, fuv).rgb;
        }
        return color;
    }
    if (block == B_WATER) {
        return tile(T_WATER, vec2(fuv.x, fract(fuv.y + gTime * 0.05))).rgb * vec3(0.30, 0.50, 0.95);
    }
    if (block == B_ICE) {
        return tile(T_ICE, fuv).rgb;
    }
    if (block == B_DIRT) {
        return tile(T_DIRT, fuv).rgb;
    }
    if (block == B_LOG) {
        // a lying log shows its rings at its ends, a standing one at its top and bottom
        bool ring = shape == S_BEAM_X ? abs(normal.x) > 0.5 : shape == S_BEAM_Z ? abs(normal.z) > 0.5 : abs(normal.y) > 0.5;
        vec2 uv = fuv;
        if (!ring && (shape == S_BEAM_X || shape == S_BEAM_Z)) {
            uv = shape == S_BEAM_X && abs(normal.y) > 0.5 ? vec2(fuv.y, fuv.x) : (shape == S_BEAM_X ? vec2(fuv.y, fuv.x) : fuv.yx);
        }
        color = tile(ring ? T_LOG_TOP : T_LOG, uv).rgb;
    } else if (block == B_LEAVES) {
        vec4 leaf = tile(T_LEAVES, fuv);
        color = leaf.a < 0.5 ? vec3(0.04, 0.10, 0.03) : leaf.rgb * gLeaf;
    } else if (block == B_PLANKS) {
        color = tile(T_PLANKS, fuv).rgb;
    } else if (block == B_ROOF) {
        color = tile(T_ROOF, fuv).rgb;
    } else if (block == B_HAY) {
        color = tile(top || normal.y < -0.5 ? T_HAY_TOP : T_HAY_SIDE, fuv).rgb;
    } else if (block == B_LANTERN) {
        // the game's lantern texture holds its parts side by side: the body's sides at 0 to 6 by 2 to 9 pixels, its top at 0 to 6 by 9 to 15
        vec3 local = clamp((p - vec3(cell) - vec3(0.3125, 0.0625, 0.3125)) / vec3(0.375, 0.5, 0.375), 0.0, 1.0);
        vec2 pixel = top || normal.y < -0.5 ? vec2(local.x * 6.0, 9.0 + local.z * 6.0)
            : vec2((abs(normal.x) > 0.5 ? local.z : local.x) * 6.0, 9.0 - local.y * 7.0);
        color = tile(T_LANTERN, vec2(pixel.x / 16.0, 1.0 - pixel.y / 16.0)).rgb;
    } else if (block == B_CHAIN) {
        color = tile(T_CHAIN, fuv).rgb;
    } else {
        color = tile(stoneTile(block), fuv).rgb;
    }
    if (vineOn(block, shape, normal, cell)) {
        vec4 vine = tile(T_VINE, fuv);
        // the game's vine is gray and takes the color of the foliage, as leaves do
        color = mix(color, vine.rgb * gLeaf, vine.a);
    }
    // snow on every top face in winter
    if (snowy && top && block != B_LANTERN && block != B_CHAIN) {
        color = tile(T_SNOW, fuv).rgb;
    }
    return color;
}

// How much of the sun gets to a point: a few steps towards the sun over the columns of the land.
float sunShadow(vec3 p, vec3 normal, Look look) {
    vec3 start = p + normal * 0.06;
    for (int k = 1; k <= 7 + gZero; k++) {
        float along = float(k * k) * 0.55 + 0.4;
        vec3 q = start + look.sun * along;
        ivec3 c = ivec3(floor(q));
        int h = columnHeight(c.xz);
        if (float(h) + 1.0 > q.y) {
            return 0.0;
        }
        if (c.y <= h + 8 && (inYard(c.xz) || wallLine(c.xz) != 0)) {
            int b = blockAt(c, h);
            if (b != 0 && (b >> 8) == S_FULL) {
                return 0.0;
            }
        }
    }
    return 1.0;
}

// The color of a block light level in the game's light map: warm, and dimmer towards the lower levels on the game's curve.
vec3 blockLightColor(int level) {
    float f = clamp(float(level), 0.0, 15.0) / 15.0;
    float b = f / (4.0 - 3.0 * f);
    return vec3(b, b * ((b * 0.6 + 0.4) * 0.6 + 0.4), b * (b * b * 0.6 + 0.4));
}

// The block light of the lantern at a face, in the air the face looks into (a plant is in the air already), as the game's light map gives it:
// 15 at its block and one less per block away along the blocks, steady (a lantern does not flicker). Walls do not stop it here, which is close
// enough for the open ruin and the open field.
vec3 blockLight(vec3 p, vec3 normal, bool plant) {
    vec3 at = plant ? p : p + normal * 0.02;
    ivec3 d = abs(ivec3(floor(at)) - LANTERN_CELL);
    int level = 15 - (d.x + d.y + d.z);
    return level > 0 ? blockLightColor(level) : vec3(0.0);
}

// Undoes the picture's exposure and saturation (see the end of main), so a block comes out in the colors it was shaded in.
vec3 ungrade(vec3 color, Look look) {
    float grey = dot(color, LUMA);
    color = clamp(mix(vec3(grey), color, 1.0 / look.saturation), 0.0, 0.996);
    return -log(1.0 - color) / look.exposure;
}

// A block lit as the game lights it, so its texture looks as in the game: the sky's light times the game's fixed shade of the face, or the
// lantern's light where it is stronger. The sky's blue and the picture's grading are kept off the blocks; only the fog lies over them.
vec3 shade(int coded, vec3 normal, vec3 p, ivec3 cell, Look look, bool shadows) {
    int block = coded & 255;
    int shape = coded >> 8;
    vec3 albedo = blockColor(block, shape, normal, p, cell);
    if (block == B_LANTERN) {
        // in the light of its own block, level 15, like the game's lantern
        return ungrade(albedo * max(look.daylight, blockLightColor(15)), look);
    }
    // plants are not shaded by the way they face, as in the game
    bool plant = block >= P_WHEAT && block <= P_FLOWER;
    float face = plant || normal.y > 0.5 ? SHADE_UP : normal.y < -0.5 ? SHADE_DOWN
        : abs(normal.z) > 0.5 ? SHADE_NORTH_SOUTH : SHADE_EAST_WEST;
    vec3 light = look.daylight;
    float ndl = dot(normal, look.sun);
    if (shadows && ndl > 0.0 && !plant) {
        light *= mix(SHADOW_LIGHT, 1.0, sunShadow(p, normal, look));
    }
    // the game takes the stronger of sky and block light
    light = max(light, blockLight(p, normal, plant)) * face;
    return ungrade(albedo * light, look);
}

vec3 applyFog(vec3 color, float t, vec3 rd, Look look) {
    float f = 1.0 - exp(-pow(t * look.density, 1.35) * 1.5);
    vec3 fogColor = look.fog + look.sunColor * pow(max(dot(rd, look.sun), 0.0), 8.0) * 0.22;
    return mix(color, fogColor, clamp(f, 0.0, 1.0));
}

// The game's snowfall in winter, made the way the game makes it: every block column within SNOW_RADIUS of the camera has a sheet of the
// game's snow texture, one block wide and turned towards the camera, on which the snow falls one texture height (4 blocks) every 25.6
// seconds and drifts sideways by a random amount per column. It is a little more see-through towards the edge of the radius. The sheets are
// found along the ray: one column after the other on its main axis, and the columns beside the ray on the other. Returns the snow's color
// (already weighted) and how much it covers; first is the distance of the first sheet that covers much.
const float SNOW_RADIUS = 10.0;
const float SNOW_TICKS = 20.0;
const float SNOW_NEAREST = 1.25;
// near the camera every column has snow, so it also falls where the camera looks down onto the path and the fields close by
const float SNOW_EVERY_COLUMN = 4.0;
// the share of the columns that have snow: fewer than in the game, so the menu stays calm
const float SNOW_COLUMNS = 0.45;
// the most a column's own speed slows its fall, in the game's units: the game's gaussian can nearly stop a column, which looks like snow that
// hangs in the air; at this most a column falls at about 60% of the base speed
const float SNOW_MOST_SLOWING = 0.8;

vec4 snowfall(vec3 ro, vec3 rd, float far, Look look, out float first) {
    first = far;
    vec2 level = rd.xz;
    if (dot(level, level) < 1e-6) {
        return vec4(0.0);
    }
    bool alongX = abs(rd.x) >= abs(rd.z);
    float major = alongX ? rd.x : rd.z;
    float stepSign = major > 0.0 ? 1.0 : -1.0;
    vec2 camCell = floor(ro.xz);
    // vertical sheets are lit by the sky like the game's weather: bright by day, dark at night
    vec3 light = look.sunColor * 0.5 + look.ambient * 1.1;
    float ticks = gTime * SNOW_TICKS;
    vec3 sum = vec3(0.0);
    float covered = 0.0;
    for (int i = 0; i <= int(SNOW_RADIUS) + gZero; i++) {
        float m = (alongX ? camCell.x : camCell.y) + float(i) * stepSign;
        float tCenter = max(0.0, (m + 0.5 - (alongX ? ro.x : ro.z)) / major);
        float otherLine = floor(alongX ? ro.z + rd.z * tCenter : ro.x + rd.x * tCenter);
        for (int j = -1; j <= 1 + gZero; j++) {
            vec2 cell = alongX ? vec2(m, otherLine + float(j)) : vec2(otherLine + float(j), m);
            vec2 offset = cell - camCell;
            if (abs(offset.x) > SNOW_RADIUS || abs(offset.y) > SNOW_RADIUS) {
                continue;
            }
            if (dot(offset, offset) > SNOW_EVERY_COLUMN * SNOW_EVERY_COLUMN && hash12(cell + 5.3) > SNOW_COLUMNS) {
                continue;
            }
            vec2 center = cell + 0.5;
            vec2 toCamera = ro.xz - center;
            if (dot(toCamera, toCamera) < 0.04) {
                continue;
            }
            vec2 n = normalize(toCamera);
            float denom = dot(level, n);
            if (denom > -1e-4) {
                continue;
            }
            float t = dot(center - ro.xz, n) / denom;
            // the sheets right in front of the camera would only be a few huge, soft flakes at the size the world is drawn
            if (t < SNOW_NEAREST || t > far) {
                continue;
            }
            vec3 p = ro + rd * t;
            float across = dot(p.xz - center, vec2(-n.y, n.x));
            if (abs(across) > 0.5 || abs(p.y - ro.y) > SNOW_RADIUS) {
                continue;
            }
            // the column's own drift, from the game's: a random start, and a random speed across (gaussian-like) and down
            float r1 = hash12(cell);
            float r2 = hash12(cell + 37.1);
            float g1 = (hash12(cell + 71.7) + hash12(cell + 13.3) - 1.0) * 1.7;
            float g2 = min((hash12(cell + 91.9) + hash12(cell + 53.5) - 1.0) * 1.7, SNOW_MOST_SLOWING);
            float u = across + 0.5 + r1 + ticks * 0.01 * g1;
            float v = -p.y * 0.25 + r2 + ticks * 0.001 * g2 - fract(ticks / 512.0);
            vec4 flake = texture(Sampler2, vec2(u, v));
            float fade = mix(1.0, 0.5, min(dot(offset, offset) / (SNOW_RADIUS * SNOW_RADIUS), 1.0));
            float a = flake.a * fade;
            if (a < 0.01) {
                continue;
            }
            sum += (1.0 - covered) * a * flake.rgb * light;
            covered += (1.0 - covered) * a;
            if (a > 0.5) {
                first = min(first, t);
            }
        }
    }
    return vec4(sum, covered);
}

// A direction of the camera's own (right, up, forward) turned the way the camera looks, like the rays.
vec3 cameraAxis(vec3 v, float cp, float sp, float cy, float sy) {
    v = vec3(v.x, cp * v.y + sp * v.z, -sp * v.y + cp * v.z);
    return vec3(cy * v.x + sy * v.z, v.y, -sy * v.x + cy * v.z);
}

// The game's particles (MenuParticles): flat squares facing the camera with the game's particle textures, turned by their roll, in front of
// what the ray hit. Leaves are cut out like the game's opaque particles and lit by the sky and the lantern; fireflies are see-through and glow
// as their light fades in and out. Only the nearest one on a pixel is drawn, and it is fogged like everything else. The distance comes back,
// so a near leaf is as sharp as it should be.
vec3 particles(vec3 color, vec3 ro, vec3 rd, inout float far, Look look, float cp, float sp, float cy, float sy) {
    int count = int(parameter(7.0).r);
    if (count == 0) {
        return color;
    }
    vec3 forward = cameraAxis(vec3(0.0, 0.0, 1.0), cp, sp, cy, sy);
    vec3 right = cameraAxis(vec3(1.0, 0.0, 0.0), cp, sp, cy, sy);
    vec3 up = cameraAxis(vec3(0.0, 1.0, 0.0), cp, sp, cy, sy);
    float facing = dot(rd, forward);
    float nearest = far;
    vec4 hit = vec4(0.0);
    for (int i = 0; i < min(count, MAX_PARTICLES); i++) {
        float texel = PARTICLE_TEXEL + float(i) * 3.0;
        vec4 a = parameter(texel);
        vec4 b = parameter(texel + 1.0);
        vec4 c = parameter(texel + 2.0);
        vec3 pos = vec3(a.r + a.g * 256.0, a.b + a.a * 256.0, b.r + b.g * 256.0) / 65535.0 * 256.0 - 128.0;
        float t = dot(pos - ro, forward) / facing;
        if (t < 0.3 || t >= nearest) {
            continue;
        }
        vec3 q = ro + rd * t - pos;
        vec2 local = vec2(dot(q, right), dot(q, up));
        float roll = b.b / 256.0 * TAU;
        float cr = cos(roll);
        float sr = sin(roll);
        local = vec2(cr * local.x + sr * local.y, -sr * local.x + cr * local.y);
        float size = c.r / 1000.0;
        if (abs(local.x) > size || abs(local.y) > size) {
            continue;
        }
        vec4 picture = gameTile(int(b.a), local / (2.0 * size) + 0.5);
        int kind = int(c.a);
        bool firefly = kind == 3;
        float alpha = firefly ? picture.a * c.g / 255.0 : step(0.1, picture.a);
        if (alpha < 0.01) {
            continue;
        }
        vec3 light = max(look.sunColor * max(look.sun.y, 0.0) + look.ambient, blockLight(pos, vec3(0.0, 1.0, 0.0), true) * BLOCK_LIGHT);
        if (firefly) {
            light = mix(light, vec3(FULL_BRIGHT), c.b / 255.0);
        }
        vec3 tint = kind == 2 ? gLeaf : vec3(1.0);
        nearest = t;
        hit = vec4(applyFog(picture.rgb * tint * light, t, rd, look), alpha);
    }
    if (hit.a > 0.0) {
        color = mix(color, hit.rgb, hit.a);
        if (hit.a > 0.5) {
            far = nearest;
        }
    }
    return color;
}

void main() {
    vec4 clock = parameter(0.0);
    vec4 mouse = parameter(1.0);
    vec4 mouseDown = parameter(2.0);
    vec4 size = parameter(3.0);
    vec4 timeParameter = parameter(4.0);
    vec4 camera = parameter(5.0);
    gTime = clock.r + clock.g * 256.0 + clock.b / 255.0;
    gZero = int(parameter(7.0).g);
    gSeasonA = int(clock.a + 0.5);
    gSeasonB = int(mouse.b + 0.5);
    float lookMix = mouse.a / 255.0;
    gSeasonMix = lookMix;
    lookMix = lookMix * lookMix * (3.0 - 2.0 * lookMix);
    float hour = (timeParameter.r + timeParameter.g * 256.0) / 65535.0 * 24.0;
    Look look = timeLook(lookFor(gSeasonA), hour);
    seasonColors(gSeasonA, gGrass, gLeaf);
    if (lookMix > 0.001) {
        look = mixLook(look, timeLook(lookFor(gSeasonB), hour), lookMix);
        vec3 grass; vec3 leaf;
        seasonColors(gSeasonB, grass, leaf);
        gGrass = mix(gGrass, grass, lookMix);
        gLeaf = mix(gLeaf, leaf, lookMix);
    }
    if (timeParameter.a < 0.5) {
        look.stars = 0.0;
    }
    // snow in winter, coming and going with the season
    float winter = (gSeasonA == 0 ? 1.0 - lookMix : 0.0) + (gSeasonB == 0 ? lookMix : 0.0);
    bool snowing = timeParameter.b > 0.5 && winter > 0.01;
    // the game hides the stars while it snows; the softened sky made them look like flakes that hang still
    if (snowing) {
        look.stars *= 1.0 - winter;
    }
    float mx = (mouse.r + mouse.g * 256.0) / 65535.0;
    float my = (mouseDown.r + mouseDown.g * 256.0) / 65535.0;
    int steps = int(mouseDown.b + 0.5);
    bool shadows = mouseDown.a > 0.5;
    vec2 resolution = vec2(size.r + size.g * 256.0, size.b + size.a * 256.0);

    gNight = 1.0 - smoothstep(-0.22, 0.0, sin((hour - 6.0) / 12.0 * 3.14159265) * 0.8);

    // The camera stands low over the path, just above the crops, and looks across the fields at about 27 degrees to the blocks, so the path and
    // the rows run diagonally through the picture. It follows the mouse by a hair and drifts very slowly. At the start of the game it comes
    // down from high up and behind, looking up into the sky, and tilts down as it lands.
    float descent = (camera.r + camera.g * 256.0) / 65535.0;
    float tilt = (camera.b + camera.a * 256.0) / 65535.0;
    float drift = sin(gTime * 0.05) * 0.12;
    vec3 ro = vec3(-3.3 + (mx - 0.5) * 0.15 + drift, float(G) + 3.35 + descent * 13.5, -1.0 + (0.5 - my) * 0.06 - descent * 4.5);
    float yaw = 0.30 + (0.5 - mx) * 0.004 + drift * 0.02;
    float pitch = -0.12 + (0.5 - my) * 0.003 + tilt * 1.0;
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution) / resolution.y;
    // a field of view of about 60 degrees from top to bottom, like the game's, so the land is wide and the house is at its distance
    vec3 rd = normalize(vec3(uv.x, uv.y, 0.9));
    float cp = cos(pitch);
    float sp = sin(pitch);
    rd = vec3(rd.x, cp * rd.y + sp * rd.z, -sp * rd.y + cp * rd.z);
    float cy = cos(yaw);
    float sy = sin(yaw);
    rd = vec3(cy * rd.x + sy * rd.z, rd.y, -sy * rd.x + cy * rd.z);

    float tHit = MAX_DISTANCE;
    vec3 normal = vec3(0.0, 1.0, 0.0);
    ivec3 hitCell = ivec3(0);
    int block = 0;
    bool hit = march(ro, rd, steps, MAX_DISTANCE, tHit, normal, hitCell, block);

    vec3 color;
    float far = tHit;
    if (hit && (block & 255) == B_WATER && normal.y > 0.5) {
        // the water of the channels: the sky mirrored in small waves, and the bottom seen through it
        vec3 p = ro + rd * tHit;
        vec2 w = p.xz * 0.85;
        float tw = gTime * 0.45;
        vec3 n = normalize(vec3(0.03 * (sin(w.x * 1.7 + tw * 1.3) + sin(w.y * 2.3 - tw * 1.1) * 0.7), 1.0,
            0.03 * (sin(w.y * 1.9 + tw) + sin(w.x * 2.9 + tw * 1.4) * 0.6)));
        vec3 reflected = reflect(rd, n);
        reflected.y = abs(reflected.y);
        float fresnel = 0.03 + 0.97 * pow(1.0 - clamp(dot(-rd, n), 0.0, 1.0), 5.0);
        vec3 depthColor = look.water * (look.ambient * 1.4 + look.sunColor * 0.12);
        color = mix(depthColor, sky(p, reflected, look), clamp(fresnel, 0.0, 1.0));
        float glint = max(dot(reflected, look.sun), 0.0);
        color += look.sunColor * (pow(glint, 500.0) * 2.4 + pow(glint, 40.0) * 0.10);
        color = applyFog(color, tHit, rd, look);
    } else if (hit) {
        vec3 p = ro + rd * tHit;
        color = shade(block, normal, p, hitCell, look, shadows);
        color = applyFog(color, tHit, rd, look);
    } else {
        color = sky(ro, rd, look);
        far = MAX_DISTANCE;
    }

    // the falling snow
    if (snowing) {
        float snowAt;
        vec4 snow = snowfall(ro, rd, far, look, snowAt);
        color = color * (1.0 - snow.a * winter) + snow.rgb * winter;
        // what is covered by a near flake is as sharp as the flake
        far = mix(far, snowAt, step(0.5, winter));
    }

    color = particles(color, ro, rd, far, look, cp, sp, cy, sy);

    // the light is made into a picture: never above white, the colors as strong as the look says, the edges a little darker
    color = 1.0 - exp(-color * look.exposure);
    float grey = dot(color, LUMA);
    color = clamp(mix(vec3(grey), color, look.saturation), 0.0, 1.0);
    vec2 centered = gl_FragCoord.xy / resolution - 0.5;
    color *= 1.0 - 0.45 * dot(centered, centered);
    // a little noise, so that the slow change of the sky is not seen as bands
    color += (hash12(gl_FragCoord.xy + fract(gTime)) - 0.5) / 255.0;
    // how far out of focus the pixel is: sharp near, soft beyond about 25 blocks, unless the picture is to be sharp everywhere
    // (the season pictures are sharp everywhere and opaque)
    bool sharp = parameter(6.0).a > 127.0;
    fragColor = vec4(color, sharp ? 1.0 : smoothstep(22.0, 55.0, far));
}
