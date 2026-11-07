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
out vec2 texCoord;
out vec2 local;
// the width and the height of the image and its corner radius
flat out vec3 shape;

// An image with rounded corners, see RoundedImage: the corner's side of the image comes in the normal (-1 or 1 on both axes).
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord = UV0;
    shape = vec3(float(UV1.x), float(UV1.y), float(UV2.x) / 16.0);
    local = Normal.xy * shape.xy * 0.5;
}
