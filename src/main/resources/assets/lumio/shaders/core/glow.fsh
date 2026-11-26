#version 330

#moj_import <minecraft:dynamictransforms.glsl>
//26.3 #include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

//26.3 #ifndef OIT_ALPHA_ONLY
out vec4 fragColor;
//26.3 #endif

// Colors and alpha come from the texture, which the mod draws itself. The vertex color tints it.
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
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
