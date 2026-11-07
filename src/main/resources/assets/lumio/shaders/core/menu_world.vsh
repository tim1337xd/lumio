#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

// One quad that covers the whole picture: the corners are at -1 and 1, and the shader does the rest for every pixel.
void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
