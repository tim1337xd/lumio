#version 330

#moj_import <minecraft:dynamictransforms.glsl>
//26.3 #include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

//26.3 #ifndef OIT_ALPHA_ONLY
out vec4 fragColor;
//26.3 #endif

// Same as vanilla's outline shader, but keeps the alpha of the vertex color so silhouettes can be translucent.
void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a == 0.0) {
        discard;
    }
    vec4 tinted = vec4(ColorModulator.rgb * vertexColor.rgb, ColorModulator.a * vertexColor.a);
//26.3 #ifdef OIT_ALPHA_ONLY
//26.3     executeAlphaOnlyPhase(gl_FragCoord.z, tinted.a);
//26.3 #else
//26.3 #ifdef OIT_ACCUMULATE
//26.3     tinted = sampleColorForAccumulation(tinted);
//26.3 #endif
    fragColor = tinted;
//26.3 #endif
}
