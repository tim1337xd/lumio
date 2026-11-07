#version 330

uniform sampler2D Sampler0;

out vec4 fragColor;

// The depth of field of the main menu's world: the near scene stays sharp and what is far is soft. The scene comes with how far out of focus
// every pixel is in its alpha (0 sharp, 1 the farthest); a pixel takes the mean of the pixels on a small disk around it, as large as it is out
// of focus. Sharp pixels on the disk count little, so a near edge does not bleed into the soft land behind it.
const int TAPS = 12;
const vec2 DISK[TAPS] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
    vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
    vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598));
// the largest disk, in pixels of the scene's picture
const float RADIUS = 3.2;

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / size;
    vec4 centre = texture(Sampler0, uv);
    float radius = centre.a * RADIUS;
    if (radius < 0.15) {
        fragColor = vec4(centre.rgb, 1.0);
        return;
    }
    vec3 sum = centre.rgb;
    float total = 1.0;
    for (int i = 0; i < TAPS; i++) {
        vec4 tap = texture(Sampler0, uv + DISK[i] * radius / size);
        float weight = smoothstep(0.0, 0.35, tap.a);
        sum += tap.rgb * weight;
        total += weight;
    }
    fragColor = vec4(sum / total, 1.0);
}
