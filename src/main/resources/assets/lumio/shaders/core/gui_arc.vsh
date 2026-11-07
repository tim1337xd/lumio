#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in float LineWidth;

out vec4 vertexColor;
out vec2 local;
// the outer radius and the thickness, and where the arc starts and how far it goes, in turns
flat out vec4 arc;

// A part of a ring, see Arcs.
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    local = UV0;
    float angles = LineWidth;
    float start = floor(angles / 4096.0);
    float span = angles - start * 4096.0;
    arc = vec4(float(UV1.x) / 100.0, float(UV1.y) / 100.0, start / 2048.0, span / 2048.0);
}
