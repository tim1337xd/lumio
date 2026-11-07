#version 330

in vec4 vertexColor;
in vec2 local;
flat in vec4 arc;

out vec4 fragColor;

const float TAU = 6.28318530718;
// the room between two segments with flat ends, in units of the panel: the same width from the inner edge to the outer one
const float SEGMENT_GAP = 1.1;

// the point on the middle line of the ring at an angle, in turns from the top, clockwise (y goes down)
vec2 onRing(float turns, float radius) {
    float angle = turns * TAU;
    return vec2(sin(angle), -cos(angle)) * radius;
}

void main() {
    float outer = arc.x;
    // a thickness below 0 is a segment of a chart, with flat ends and a gap of the same width all along them
    bool flatEnds = arc.y < 0.0;
    float thickness = abs(arc.y);
    float start = arc.z;
    float span = arc.w;
    float middle = outer - thickness * 0.5;
    float halfSize = thickness * 0.5;
    // the size of a pixel of the screen in the units of the panel, the same in every direction, so the edge is as soft along a slant as across
    float pixel = max(length(fwidth(local)) * 0.7071, 1e-3);
    float len = length(local);
    float radial = abs(len - middle) - halfSize;

    float d;
    // at the very middle there is no angle (atan of 0 and 0 is not defined, and some cards give NaN): it is far from the ring anyway
    if (span >= 0.9995 || len < 1e-4) {
        // the whole ring
        d = radial;
    } else if (flatEnds) {
        // the distance to the two straight ends, which go out from the middle: how far round the point is from the middle of the segment
        float turns = atan(local.x, -local.y) / TAU;
        float delta = fract(turns - start - span * 0.5 + 0.5) - 0.5;
        float past = (abs(delta) - span * 0.5) * TAU;
        // across to the line of the end, which is the distance of a gap that is as wide at the inside as at the outside
        float across = past >= 1.5707 ? len : len * sin(max(past, -1.5707));
        d = max(radial, across + SEGMENT_GAP * 0.5);
    } else {
        // how far round the point is from the start, in turns, from 0 to 1
        float turns = atan(local.x, -local.y) / TAU;
        float along = fract(turns - start);
        if (along <= span) {
            d = radial;
        } else {
            // past the ends: the distance to the round end that is nearer
            float toStart = length(local - onRing(start, middle));
            float toEnd = length(local - onRing(start + span, middle));
            d = min(toStart, toEnd) - halfSize;
        }
    }
    float coverage = clamp(0.5 - d / pixel, 0.0, 1.0);
    if (coverage <= 0.002) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
