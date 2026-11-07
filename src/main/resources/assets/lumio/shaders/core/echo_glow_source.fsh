#version 330

// Radiance's Echo, the source of the glow (see EchoGlow), in a picture half as large as the screen: the item and the hands grown by about 20
// pixels of a 1080 line screen (times the width), less themselves, so a band around them; looked at through two flowing waves, one across and
// one down, as the reference shifts strips of it, so the band's outer edge flows. The two blurs of it make the glow.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const float PARAMETERS = 10.0;
const float REFERENCE_LINES = 1080.0;
const float TAU = 6.2831853;
// the shimmer's loop in seconds (ten pi): the waves' speeds below are whole turns of it, so the loop has no seam
const float LOOP_SECONDS = 31.415927;
// how far the band reaches out, at the width 1: the rings it is grown in, in pixels of a 1080 line screen
const vec3 GROW_RINGS = vec3(6.0, 13.0, 20.0);
const int GROW_ANGLES = 12;
// the waves: how far they shift at the width 1 and the usual shimmer, in pixels of a 1080 line screen
const float WAVE_PIXELS = 16.0;
// the patches the glow shows in (see EchoPatches, whose numbers these must match): the noise's scale per pixel of a 1080 line screen, its cells
// in time, the soft threshold, the bias away from the motion and to the bottom, and how far the lean goes at most (pixels of a 1080 line screen)
const float PATCH_SCALE = 0.006;
const float TIME_CELLS = 32.0;
const float PATCH_LOW = 0.45;
const float PATCH_HIGH = 0.70;
const float AWAY_BIAS = 0.15;
const float BOTTOM_BIAS = 0.05;
const float LEAN_MOST = 6.0;

vec4 parameter(float index) {
    return floor(texture(Sampler0, vec2((index + 0.5) / PARAMETERS, 0.5)) * 255.0 + 0.5);
}

float inside(vec2 uv) {
    return texture(Sampler1, uv).r > 1e-6 ? 1.0 : 0.0;
}

// a number from 0 to 1 for a cell, from plain arithmetic (the same in EchoPatches)
float hash13(vec3 p) {
    vec3 p3 = fract(p * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

// smooth value noise, coming round in time after TIME_CELLS cells
float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = p - i;
    vec3 u = f * f * (3.0 - 2.0 * f);
    float z0 = i.z - TIME_CELLS * floor(i.z / TIME_CELLS);
    float z1 = (i.z + 1.0) - TIME_CELLS * floor((i.z + 1.0) / TIME_CELLS);
    float a = mix(mix(hash13(vec3(i.x, i.y, z0)), hash13(vec3(i.x + 1.0, i.y, z0)), u.x), mix(hash13(vec3(i.x, i.y + 1.0, z0)), hash13(vec3(i.x + 1.0, i.y + 1.0, z0)), u.x), u.y);
    float b = mix(mix(hash13(vec3(i.x, i.y, z1)), hash13(vec3(i.x + 1.0, i.y, z1)), u.x), mix(hash13(vec3(i.x, i.y + 1.0, z1)), hash13(vec3(i.x + 1.0, i.y + 1.0, z1)), u.x), u.y);
    return mix(a, b, u.z);
}

// a signed pair from 16 bits each, in quarter pixels around the middle
vec2 pairOf(vec4 texel) {
    return (vec2(texel.r + texel.g * 256.0, texel.b + texel.a * 256.0) - 32768.0) / 4.0;
}

// how much the glow shows at a place (pixels of a 1080 line screen): in soft patches anchored to the item, leaning away from the motion
float patch(vec2 p, float width, float scale) {
    vec2 relative = p - pairOf(parameter(8.0));
    vec4 time = parameter(9.0);
    float phase = (time.r + time.g * 256.0) / 65535.0;
    float lowered = time.b / 255.0 - 0.5;
    float cells = PATCH_SCALE / max(0.25, width);
    float n = noise3(vec3(relative * cells, phase * TIME_CELLS));
    vec2 lean = pairOf(parameter(6.0)) / (LEAN_MOST * scale);
    float away = length(relative);
    if (away > 1e-3) {
        vec2 direction = relative / away;
        n += AWAY_BIAS * max(0.0, dot(direction, lean)) + BOTTOM_BIAS * max(0.0, -direction.y);
    }
    return smoothstep(PATCH_LOW - lowered, PATCH_HIGH - lowered, n);
}

void main() {
    vec2 screen = vec2(textureSize(Sampler1, 0));
    // places in pixels of a 1080 line screen, so the waves and the band are as large at every size
    vec2 world = screen * REFERENCE_LINES / screen.y;
    vec2 p = texCoord0 * world;
    vec4 shimmerTexel = parameter(5.0);
    float t = (shimmerTexel.r + shimmerTexel.g * 256.0) / 65535.0 * LOOP_SECONDS;
    float shimmer = min(1.0, shimmerTexel.b / 255.0 * 2.0);
    float width = parameter(7.0).r / 100.0;
    float a = WAVE_PIXELS * width * shimmer;
    // as the reference: first rows shifted across by a wave down the screen, then columns shifted down by a wave across it
    float y = p.y - (sin(p.x * 0.025 - t * 1.2) * 0.65 + sin(p.x * 0.06 + t * 1.8) * 0.35) * a;
    float x = p.x - (sin(y * 0.022 + t * 1.2) * 0.65 + sin(y * 0.055 - t * 2.0) * 0.35) * a;
    vec2 q = vec2(x, y);
    float grown = 0.0;
    for (int i = 0; i < GROW_ANGLES; i++) {
        float angle = float(i) * (TAU / float(GROW_ANGLES));
        vec2 direction = vec2(cos(angle), sin(angle)) * width;
        grown = max(grown, inside((q + direction * GROW_RINGS.x) / world));
        grown = max(grown, inside((q + direction * GROW_RINGS.y) / world));
        grown = max(grown, inside((q + direction * GROW_RINGS.z) / world));
    }
    grown = max(grown, inside(q / world));
    float band = grown * (1.0 - inside(q / world));
    // right at the edge the band is not waved, so the glow always starts at the item, however far the waves shift its outer edge
    float hugging = 0.0;
    for (int i = 0; i < GROW_ANGLES; i++) {
        float angle = float(i) * (TAU / float(GROW_ANGLES));
        hugging = max(hugging, inside((p + vec2(cos(angle), sin(angle)) * GROW_RINGS.x * width) / world));
    }
    float edge = hugging * (1.0 - inside(p / world));
    band = max(band, edge);
    // the glow only in its patches; the strip at the edge, unmasked, goes along in green, for the rim that stays all around
    fragColor = vec4(band * patch(p, width, screen.y / REFERENCE_LINES), edge, 0.0, 1.0);
}
