#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// The letters of the lyrics, as a picture, drawn the way the game draws letters: what the picture covers hides what is behind it, water
// too, and what it does not cover is left out, so it does not hide anything. That is decided by the picture and not by how faint the line
// is, so a line that fades out does so smoothly, and does not go out all at once.
void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.1) {
        discard;
    }
    vec4 color = texel * vertexColor * ColorModulator;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color;
}
