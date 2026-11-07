#version 330

// The colors of the Colored theme: thirteen soft fields of color over a dark base, for the whole screen, at a small size. See ColoredFlow.
//   three texels per field: its color, its place (two numbers of 16 bits), and its size (a share of the screen's height, up to 2, in 16 bits)
//   then the base's color, and the shape of the screen (width by height, up to 4)
//   then how fast fields fall off, how long they are across, and how clear their edges are

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const int FIELDS = 13;
const float MOST_FALL = 12.0;
const float MOST_STRETCH = 4.0;
// how narrow the step of the clearest edge is
const float STEP = 0.12;
// how much of the color a field has at its middle
const float STRENGTH = 0.95;
const float PLACE_FROM = -1.0;
const float PLACE_RANGE = 3.0;
const float MOST_RADIUS = 2.0;

vec4 parameter(int texel) {
    return texelFetch(Sampler0, ivec2(texel, 0), 0) * 255.0;
}

float placeOf(float low, float high) {
    return (low + high * 256.0) / 65535.0 * PLACE_RANGE + PLACE_FROM;
}

void main() {
    vec4 baseTexel = parameter(FIELDS * 3);
    vec4 mood = parameter(FIELDS * 3 + 1) / 255.0;
    float fall = mood.r * MOST_FALL;
    float stretch = 1.0 + mood.g * (MOST_STRETCH - 1.0);
    float sharp = mood.b;
    float aspect = baseTexel.a / 255.0 * 4.0;
    vec3 color = baseTexel.rgb / 255.0;
    // from the top left of the screen, in shares of its height
    vec2 here = vec2(texCoord0.x * aspect, (1.0 - texCoord0.y));
    for (int i = 0; i < FIELDS; i++) {
        vec4 field = parameter(i * 3);
        vec4 place = parameter(i * 3 + 1);
        vec4 size = parameter(i * 3 + 2);
        vec2 middle = vec2(placeOf(place.r, place.g) * aspect, placeOf(place.b, place.a));
        float radius = (size.r + size.g * 256.0) / 65535.0 * MOST_RADIUS;
        vec2 away = (here - middle) / max(radius, 0.01);
        away.x /= stretch;
        float amount = exp(-dot(away, away) * fall) * STRENGTH;
        if (sharp > 0.0) {
            amount = mix(amount, smoothstep(0.5 - STEP, 0.5 + STEP, amount) * STRENGTH, sharp);
        }
        color = mix(color, field.rgb / 255.0, amount);
    }
    fragColor = vec4(color, 1.0);
}
