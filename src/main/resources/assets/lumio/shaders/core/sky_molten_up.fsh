#version 330

// The Molten sky of the lower qualities, made larger: it was drawn at half the screen's size (sky_molten.fsh with HALF_SIZE), and is filtered up
// with a cubic B-spline, from four taps of the linear filter. Smooth, without the blocks of a linear filter or the dark rings a sharper cubic
// draws beside a bright line. See MoltenSky.

// the Molten parameters (texel 2 holds how much of the sky is shown), and the smaller picture
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec3 direction;

out vec4 fragColor;

// how many pixels of the screen one pixel of the smaller picture covers across
const float HALF = 2.0;

void main() {
    vec2 size = vec2(textureSize(Sampler1, 0));
    vec2 at = gl_FragCoord.xy / (size * HALF) * size - 0.5;
    vec2 cell = floor(at);
    vec2 f = at - cell;
    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;
    vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;
    vec2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;
    vec2 w3 = f3 / 6.0;
    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    // where the linear filter reads each pair of texels with the weights of the pair
    vec2 p0 = (cell + 0.5 - 1.0 + w1 / g0) / size;
    vec2 p1 = (cell + 0.5 + 1.0 + w3 / g1) / size;
    vec3 color = (g0.y * (g0.x * texture(Sampler1, vec2(p0.x, p0.y)).rgb + g1.x * texture(Sampler1, vec2(p1.x, p0.y)).rgb)
        + g1.y * (g0.x * texture(Sampler1, vec2(p0.x, p1.y)).rgb + g1.x * texture(Sampler1, vec2(p1.x, p1.y)).rgb));
    fragColor = vec4(color, texelFetch(Sampler0, ivec2(2, 0), 0).a);
}
