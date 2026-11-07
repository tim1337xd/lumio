#version 330

// Radiance's Echo, the trail of the first person item and hands, one frame of it in a picture as large as the screen: red holds the thin copies of
// the outline and the streaks of its outer edge, green the faint sheet between them.
// The silhouette is where the hand pass left depth (the game clears it before that pass); while the item moves it is stamped into the sheet, so the
// swept area reads as a ribbon. What was there fades by the time; the sheet is smeared along the item's motion of this frame, so the frames melt into
// one, while the lines stay sharp. The larger of the faded old value and the new stamp stays, so a sheet keeps the same light at every frame rate.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const float PARAMETERS = 10.0;
// the smear of the sheet: how many places along the motion are looked at on each side, and how far it reaches at most, in pixels of a 1080 line screen
const int SMEAR_STEPS = 3;
const float SMEAR_MOST = 6.0;
const float REFERENCE_LINES = 1080.0;

vec4 parameter(float index) {
    return floor(texture(Sampler0, vec2((index + 0.5) / PARAMETERS, 0.5)) * 255.0 + 0.5);
}

float inside(vec2 uv) {
    return texture(Sampler2, uv).r > 1e-6 ? 1.0 : 0.0;
}

// the item's motion since the frame before, in pixels of the screen
vec2 motion() {
    vec4 m = parameter(4.0);
    return (vec2(m.r + m.g * 256.0, m.b + m.a * 256.0) - 32768.0) / 4.0;
}

void main() {
    vec4 decays = parameter(0.0);
    float decayLines = (decays.r + decays.g * 256.0) / 65535.0;
    float decaySheet = (decays.b + decays.a * 256.0) / 65535.0;
    bool clear = parameter(1.0).g > 127.5;
    bool moving = parameter(7.0).g > 127.5;
    vec2 pixel = 1.0 / vec2(textureSize(Sampler2, 0));

    float lines = 0.0;
    float sheet = 0.0;
    if (!clear) {
        vec4 middle = texture(Sampler1, texCoord0);
        lines = middle.r;
        // the old sheet, blurred a little along the motion both ways, so the frames before melt into one ribbon; an average, so it softens without
        // spreading further out every frame
        vec2 moved = motion();
        float reach = min(length(moved), SMEAR_MOST * float(textureSize(Sampler2, 0).y) / REFERENCE_LINES);
        vec2 move = length(moved) > 0.001 ? normalize(moved) * reach * pixel : vec2(0.0);
        float sum = 0.0;
        for (int i = -SMEAR_STEPS; i <= SMEAR_STEPS; i++) {
            sum += texture(Sampler1, texCoord0 + move * (float(i) / float(SMEAR_STEPS))).g;
        }
        sheet = max(middle.g, sum / float(2 * SMEAR_STEPS + 1));
    }
    lines = lines * decayLines;
    sheet = max(sheet * decaySheet, moving ? inside(texCoord0) : 0.0);
    fragColor = vec4(lines, sheet, 0.0, 1.0);
}
