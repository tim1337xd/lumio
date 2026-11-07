#version 330

#moj_import <lumio:corners.glsl>

in vec4 vertexColor;
in vec2 local;
flat in vec4 shape;
flat in int flags;

out vec4 fragColor;

const int SQUARE_LEFT = 1;
const int SQUARE_RIGHT = 2;
const int DASHED = 4;
const int SQUARE_BOTTOM = 8;
// a dash and the gap after it, in outline thicknesses
const float DASH = 3.5;
const float GAP = 2.5;
const float QUARTER_TURN = 1.5707963;

// How far along the outline the point nearest to p is, in the quarter of the shape p is in: from the middle of the top or bottom edge, round the
// corner, to the middle of the left or right side. The four quarters are mirror images, so their dashes meet without a seam.
float alongOutline(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p);
    vec2 inner = halfSize - r;
    if (q.x > inner.x && q.y > inner.y) {
        return inner.x + atan(q.x - inner.x, q.y - inner.y) * r;
    }
    if (halfSize.y - q.y < halfSize.x - q.x) {
        return q.x;
    }
    return inner.x + QUARTER_TURN * r + inner.y - q.y;
}

void main() {
    vec2 halfSize = shape.xy * 0.5;
    float r = shape.z;
    // a side whose corners are square, as the part of a shape that sits at the edge of a window, or the bottom of a header band
    if ((flags & SQUARE_LEFT) != 0 && local.x < 0.0 || (flags & SQUARE_RIGHT) != 0 && local.x > 0.0 || (flags & SQUARE_BOTTOM) != 0 && local.y > 0.0) {
        r = 0.0;
    }
    // how large a pixel of the screen is here, so the edge is soft by one pixel at any size the shape is drawn at
    float pixel = max(max(fwidth(local.x), fwidth(local.y)), 1e-3);
    float d = continuousBox(local, halfSize, r);
    float coverage = clamp(0.5 - d / pixel, 0.0, 1.0);
    float thickness = shape.w;
    if (thickness > 0.0) {
        // the outline: what is less deep inside than its thickness
        coverage *= clamp(0.5 + (d + thickness) / pixel, 0.0, 1.0);
        if ((flags & DASHED) != 0) {
            // a whole number of dashes per quarter, one centered on the middle of every side
            float quarter = halfSize.x + halfSize.y - 2.0 * r + QUARTER_TURN * r;
            float period = quarter / max(1.0, floor(quarter / (thickness * (DASH + GAP)) + 0.5));
            float fromDash = abs(fract(alongOutline(local, halfSize, r) / period + 0.5) - 0.5) * period;
            coverage *= clamp(0.5 + (thickness * DASH * 0.5 - fromDash) / pixel, 0.0, 1.0);
        }
    }
    if (coverage <= 0.002) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
