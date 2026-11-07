#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 local;
// the width and the height of the shape, its radius, and how thick its outline is (0 for a filled one)
flat out vec4 shape;
flat out int flags;

// A rounded shape of the menu, see RoundedShapes: the place of the corner from the middle of the shape, and what the shape is.
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    local = UV0;
    shape = vec4(float(UV1.x), float(UV1.y), float(UV2.x), float(UV2.y & 1023));
    flags = UV2.y >> 10;
}
