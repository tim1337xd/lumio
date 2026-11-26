#version 330

// The game's vignette, as its own pipeline draws it (its texture times its color, multiplied into the picture by the blending), except where lyrics
// stand: Sampler1 holds how much of each pixel is lyrics. The main depth holds only the hand at this point of the frame, cleared to 0 elsewhere; the
// hand lies over the lyrics, so where it is the vignette counts in full.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * texelFetch(Sampler3, ivec2(0, 0), 0);
    if (color.a == 0.0) {
        discard;
    }
    // by the pixel on the screen: the same on every backend, whichever way up a target lies
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float lyrics = texelFetch(Sampler2, pixel, 0).r > 0.0 ? 0.0 : texelFetch(Sampler1, pixel, 0).a;
    fragColor = vec4(color.rgb * (1.0 - lyrics), color.a);
}
