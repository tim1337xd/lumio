#version 330

// A line of Radiance's Echo between two frames: its light goes into the lines of the after-glow, fading out over its last pixel across, so its edge
// is smooth.

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float cover = clamp(vertexColor.g + 0.5 - abs(texCoord0.x), 0.0, 1.0);
    fragColor = vec4(vertexColor.r * cover, 0.0, 0.0, 0.0);
}
