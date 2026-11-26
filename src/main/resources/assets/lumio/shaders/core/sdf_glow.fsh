#version 330

#moj_import <minecraft:dynamictransforms.glsl>
//26.3 #include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

//26.3 #ifndef OIT_ALPHA_ONLY
out vec4 fragColor;
//26.3 #endif

// The soft glow around letters, from the true distance in the field's fourth channel: strongest on the outline, fading out over the field's
// whole range outside it. It is smooth at any size, as it comes from a distance and not from a blurred picture.
void main() {
    float distance = texture(Sampler0, texCoord0).a - 0.5;
    float glow = smoothstep(-0.5, 0.05, distance);
    glow *= glow;
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * glow) * ColorModulator;
    if (color.a < 0.004) {
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
