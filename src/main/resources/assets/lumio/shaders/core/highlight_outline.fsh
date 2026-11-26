#version 330

#moj_import <minecraft:projection.glsl>

// Lumio's outline of highlighted entities (see HighlightOutlines), from the silhouettes the game draws into its outline target. Four passes, by define:
//   COVER     one texel per 8 by 8 pixels: whether any silhouette is in them
//   GROW      that grown by the reach, so a pixel far from every silhouette is known with one look
//   ACROSS    per pixel, how far across the nearest silhouette pixel of its row is (signed, in pixels), or NONE
//   COMPOSITE from those rows, the nearest silhouette pixel in the reach (the exact distance); the line and the glow over the screen, only where
//             the entity is seen there and nothing of the world stands in front of the line
// The quad's color: red the line width in tenths of a pixel, green the glow in fifths, blue the reach in pixels, alpha the grown tiles.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
#ifdef COMPOSITE
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
#endif

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const int TILE = 8;
const int MOST_REACH = 33;
const int MOST_GROW = 5;
const float NONE = 1000.0;
// the code of a slot: its mark, and the distance in 32nds of a block, as HighlightOutlines writes them
const int MARK_GREEN = 0x13;
const int MARK_BLUE = 0x7B;
const float DISTANCE_STEPS = 32.0;
// how much nearer than an entity's nearest point the world may be and still not hide it: a little, and more far away
const float DEPTH_SLACK_BLOCKS = 0.3;
const float DEPTH_SLACK_SHARE = 0.01;

#ifdef COMPOSITE
// how far the world is along the view at a pixel, from its depth (reversed: the sky is 0)
float worldDistance(ivec2 at) {
    float depth = texelFetch(Sampler3, at, 0).r;
    return abs(ProjMat[3][2] / max(depth + ProjMat[2][2], 1e-6));
}
#endif

int reach() {
    return int(vertexColor.b * 255.0 + 0.5);
}

bool silhouette(ivec2 at) {
    return texelFetch(Sampler0, at, 0).a > 0.5;
}

void main() {
    ivec2 at = ivec2(gl_FragCoord.xy);
#if defined(COVER)
    ivec2 size = textureSize(Sampler0, 0);
    float any = 0.0;
    for (int y = 0; y < TILE; y++) {
        for (int x = 0; x < TILE; x++) {
            ivec2 p = at * TILE + ivec2(x, y);
            if (p.x < size.x && p.y < size.y && texelFetch(Sampler0, p, 0).a > 0.5) {
                any = 1.0;
            }
        }
    }
    fragColor = vec4(any, 0.0, 0.0, 1.0);
#elif defined(GROW)
    ivec2 size = textureSize(Sampler0, 0);
    int grow = int(vertexColor.a * 255.0 + 0.5);
    float any = 0.0;
    for (int y = -MOST_GROW; y <= MOST_GROW; y++) {
        for (int x = -MOST_GROW; x <= MOST_GROW; x++) {
            if (abs(x) > grow || abs(y) > grow) {
                continue;
            }
            ivec2 p = clamp(at + ivec2(x, y), ivec2(0), size - 1);
            any = max(any, texelFetch(Sampler0, p, 0).r);
        }
    }
    fragColor = vec4(any, 0.0, 0.0, 1.0);
#elif defined(ACROSS)
    if (texelFetch(Sampler1, at / TILE, 0).r < 0.5) {
        fragColor = vec4(NONE, 0.0, 0.0, 1.0);
        return;
    }
    ivec2 size = textureSize(Sampler0, 0);
    int far = reach();
    float best = NONE;
    for (int d = 0; d <= MOST_REACH; d++) {
        if (d > far || abs(best) < NONE) {
            break;
        }
        if (at.x + d < size.x && silhouette(ivec2(at.x + d, at.y))) {
            best = float(d);
        } else if (at.x - d >= 0 && silhouette(ivec2(at.x - d, at.y))) {
            best = -float(d);
        }
    }
    fragColor = vec4(best, 0.0, 0.0, 1.0);
#elif defined(COMPOSITE)
    if (texelFetch(Sampler4, at / TILE, 0).r < 0.5) {
        fragColor = vec4(0.0);
        return;
    }
    ivec2 size = textureSize(Sampler0, 0);
    int far = reach();
    float width = vertexColor.r * 255.0 / 10.0;
    float glow = vertexColor.g * 255.0 / 5.0;
    // the nearest silhouette pixel: the rows above and below, each with its nearest one across
    float bestSquared = NONE * NONE;
    ivec2 nearest = at;
    for (int d = -MOST_REACH; d <= MOST_REACH; d++) {
        if (abs(d) > far) {
            continue;
        }
        int y = at.y + d;
        if (y < 0 || y >= size.y) {
            continue;
        }
        float across = texelFetch(Sampler0, ivec2(at.x, y), 0).r;
        if (abs(across) >= NONE) {
            continue;
        }
        float squared = across * across + float(d * d);
        if (squared < bestSquared) {
            bestSquared = squared;
            nearest = ivec2(at.x + int(across), y);
        }
    }
    float distance = sqrt(bestSquared);
    // only around the entity, not over it; and within the line and the glow
    if (distance < 0.5 || distance > width + glow + 0.5) {
        fragColor = vec4(0.0);
        return;
    }
    // The silhouette's color is the code of the entity's slot (red, from 1, with a mark in green and blue): its color and its distance are in the table.
    // Seen: the world is not nearer than the entity where its silhouette pixel is (no wall in front of it there), nor where the line is.
    // A silhouette without the code (the game's own glow in the same frame) keeps its color and is drawn as the game draws it, without that test.
    ivec3 code = ivec3(texelFetch(Sampler1, nearest, 0).rgb * 255.0 + 0.5);
    vec3 color = vec3(code) / 255.0;
    if (code.g == MARK_GREEN && code.b == MARK_BLUE && code.r > 0) {
        int slot = code.r - 1;
        color = texelFetch(Sampler2, ivec2(2 * slot, 0), 0).rgb;
        vec4 nearTexel = texelFetch(Sampler2, ivec2(2 * slot + 1, 0), 0);
        float near = (nearTexel.r * 255.0 + nearTexel.g * 255.0 * 256.0) / DISTANCE_STEPS;
        float slack = DEPTH_SLACK_BLOCKS + near * DEPTH_SLACK_SHARE;
        if (worldDistance(nearest) < near - slack || worldDistance(at) < near - slack) {
            fragColor = vec4(0.0);
            return;
        }
    }
    // the line, with a soft half pixel at its edge, then the glow fading away beyond it
    float line = 1.0 - smoothstep(width - 0.5, width + 0.5, distance);
    float beyond = max(distance - width, 0.0);
    float soft = glow > 0.0 ? exp(-3.0 * beyond / glow) * (1.0 - smoothstep(glow * 0.8, glow, beyond)) * 0.6 : 0.0;
    float alpha = max(line, soft);
    fragColor = vec4(color * alpha, alpha);
#endif
}
