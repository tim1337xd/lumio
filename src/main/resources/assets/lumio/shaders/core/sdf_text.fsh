#version 330

#moj_import <minecraft:dynamictransforms.glsl>
//26.3 #include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

//26.3 #ifndef OIT_ALPHA_ONLY
out vec4 fragColor;
//26.3 #endif

// Letters and symbols from a multi-channel signed distance field: the median of the three channels is the distance to the outline (0.5 on
// it), and how fast it changes on the screen (fwidth) says how wide one pixel is, so the edge is anti-aliased over exactly a pixel at any
// size, distance and angle. Small text is made a little heavier through the edge itself, so thin strokes do not break up.
float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec4 field = texture(Sampler0, texCoord0);
    float distance = median(field.r, field.g, field.b) - 0.5;
    float pixel = max(fwidth(distance), 1e-4);
    // a pixel of the screen over more than one and a half of the atlas: small text, thickened up to about one atlas pixel (1/12 of the range)
    float heavier = clamp((pixel * 12.0 - 1.5) * 0.35, 0.0, 1.0) / 12.0;
    float coverage = smoothstep(-pixel, pixel, distance + heavier);
    if (coverage < 0.1) {
        discard;
    }
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
    if (color.a == 0.0) {
        discard;
    }
//26.3 #ifdef OIT_ALPHA_ONLY
//26.3     executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
//26.3 #else
//26.3 #ifdef OIT_ACCUMULATE
//26.3     color = sampleColorForAccumulation(color);
//26.3 #endif
    fragColor = color;
//26.3 #endif
}
