#version 330

// Radiance's Echo, one pass of a Gaussian blur of the glow's source (see EchoGlow): across (the quad's green 0) or down (green 1), as wide as the
// quad's red says (a quarter texel per step). Proper Gaussian weights, a wide blur looking at every second or third texel; the picture is read
// with bilinear filtering, so nothing of it shows steps.

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const float REACH_SIGMAS = 2.5;
const int MOST_TAPS = 20;

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 direction = vertexColor.g < 0.5 ? vec2(1.0 / size.x, 0.0) : vec2(0.0, 1.0 / size.y);
    float sigma = max(0.5, floor(vertexColor.r * 255.0 + 0.5) / 4.0);
    float stride = max(1.0, sigma * REACH_SIGMAS / float(MOST_TAPS));
    int taps = min(MOST_TAPS, int(ceil(sigma * REACH_SIGMAS / stride)));
    vec2 sum = texture(Sampler0, texCoord0).rg;
    float weights = 1.0;
    for (int i = 1; i <= MOST_TAPS; i++) {
        if (i > taps) {
            break;
        }
        float offset = float(i) * stride;
        float weight = exp(-offset * offset / (2.0 * sigma * sigma));
        sum += (texture(Sampler0, texCoord0 + direction * offset).rg + texture(Sampler0, texCoord0 - direction * offset).rg) * weight;
        weights += 2.0 * weight;
    }
    fragColor = vec4(sum / weights, 0.0, 1.0);
}
