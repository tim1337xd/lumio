#version 330

#moj_import <lumio:corners.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord;
in vec2 local;
flat in vec3 shape;

out vec4 fragColor;

void main() {
    // how large a pixel of the screen is here, so the edge is soft by one pixel at any size the image is drawn at
    float pixel = max(max(fwidth(local.x), fwidth(local.y)), 1e-3);
    float coverage = clamp(0.5 - continuousBox(local, shape.xy * 0.5, shape.z) / pixel, 0.0, 1.0);
    if (coverage <= 0.002) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord) * vertexColor;
    fragColor = vec4(color.rgb, color.a * coverage);
}
