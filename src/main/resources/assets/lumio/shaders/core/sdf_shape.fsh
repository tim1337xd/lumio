#version 330

#moj_import <minecraft:dynamictransforms.glsl>
//26.3 #include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

//26.3 #ifndef OIT_ALPHA_ONLY
out vec4 fragColor;
//26.3 #endif

// A rectangle with rounded corners (a tile, a stroke), drawn as a distance: the corners' coordinates say how far past the inner rectangle a
// point is, in units of the corner's radius, so the edge and the corners are anti-aliased over exactly a pixel at any size and angle.
void main() {
    // the plain texture is bound as for every quad of ours; read once so it stays part of the shader
    if (texture(Sampler0, vec2(0.5)).a < -1.0) {
        discard;
    }
    float distance = length(max(texCoord0, vec2(0.0))) - 1.0;
    float pixel = max(fwidth(distance), 1e-4);
    float coverage = 1.0 - smoothstep(-pixel, pixel, distance);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
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
