#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Lines of Radiance's Echo at places between two frames, when the item moved far: drawn into the after-glow's picture so the fan stays dense. The
// lines lie in a texture (see EchoFeedback): the first texel the size of the picture, then three per line, its two ends on the screen (from -1 to
// 1, kept in 16 bits over -2 to 2) and its light. Every quad of the buffer is one line: its position says which corner and which line.

uniform sampler2D Sampler0;

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec4 vertexColor;
out vec2 texCoord0;

vec4 texel(int index) {
    return floor(texelFetch(Sampler0, ivec2(index, 0), 0) * 255.0 + 0.5);
}

float unpack(float low, float high) {
    return (low + high * 256.0) / 65535.0 * 4.0 - 2.0;
}

void main() {
    vec4 header = texel(0);
    vec2 size = vec2(header.r + header.g * 256.0, header.b + header.a * 256.0);
    int line = int(Position.y + 0.5);
    int corner = int(Position.x + 0.5);
    vec4 first = texel(1 + line * 3);
    vec4 second = texel(2 + line * 3);
    vec4 look = texel(3 + line * 3);
    vec2 a = vec2(unpack(first.r, first.g), unpack(first.b, first.a));
    vec2 b = vec2(unpack(second.r, second.g), unpack(second.b, second.a));
    // across and along the line, a pixel of the picture in screen units
    vec2 along = (b - a) * size;
    along = length(along) > 1e-4 ? normalize(along) : vec2(1.0, 0.0);
    vec2 across = vec2(-along.y, along.x);
    float halfWidth = max(look.g / 16.0, 0.5);
    // a pixel more on every side, where the line fades out, so its edges are smooth and not stair steps
    float reach = halfWidth + 1.0;
    vec2 pixel = 2.0 / size;
    vec2 end = corner == 0 || corner == 3 ? a - along * pixel * reach : b + along * pixel * reach;
    float side = corner < 2 ? -1.0 : 1.0;
    gl_Position = vec4(end + across * pixel * reach * side, 0.0, 1.0);
    vertexColor = vec4(look.r / 255.0, halfWidth, 0.0, 0.0);
    // how far across the line this corner is, in pixels
    texCoord0 = vec2(reach * side, 0.0);
}
