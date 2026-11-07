#version 330

// the wet ground's reflections, made at a smaller size
uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

// A soft filter of four taps, so the smaller picture does not show.
void main() {
    vec2 texel = 0.5 / vec2(textureSize(Sampler0, 0));
    fragColor = (texture(Sampler0, texCoord0 + texel) + texture(Sampler0, texCoord0 - texel)
        + texture(Sampler0, texCoord0 + vec2(texel.x, -texel.y)) + texture(Sampler0, texCoord0 + vec2(-texel.x, texel.y))) * 0.25;
}
