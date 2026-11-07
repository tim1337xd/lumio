#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec4 vertexColor;
out vec2 texCoord0;

// One quad that covers the whole target: the corners are at -1 and 1, and the texture coordinate goes from 0 to 1 over it.
void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}
