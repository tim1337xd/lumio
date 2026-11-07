#version 330

in vec4 vertexColor;
in vec2 local;

out vec4 fragColor;

// a fixed noise per screen pixel, from 0 to 1
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    float d2 = dot(local, local);
    if (d2 >= 1.0) {
        discard;
    }
    // the same falloff as Ui.washFalloff: smooth, and exactly zero at the edge
    float rest = 1.0 - d2;
    float alpha = vertexColor.a * rest * rest * rest;
    // A faint light over a large area changes the 8-bit screen color by one step every few dozen pixels, which shows as bands. Triangular
    // noise of about one screen step per pixel hides them. A step of the screen is one 255th of the difference between the light and what is
    // under it; that is not known here, so the largest it can be for this color is used. The noise fades out towards the edge, so the light
    // does not end in a ring of grain.
    vec3 c = vertexColor.rgb;
    float contrast = max(max(max(c.r, c.g), c.b), 1.0 - min(min(c.r, c.g), c.b));
    float noise = hash(gl_FragCoord.xy) + hash(gl_FragCoord.xy + vec2(17.31, 5.17)) - 1.0;
    alpha += noise * clamp(rest * 6.0, 0.0, 1.0) / (255.0 * max(contrast, 0.25));
    fragColor = vec4(c, clamp(alpha, 0.0, 1.0));
}
