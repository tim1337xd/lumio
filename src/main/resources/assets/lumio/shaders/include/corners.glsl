// The one corner shape of the mod: continuous corners, as a superellipse instead of a quarter circle, so the side flows into the corner without
// the kink a circle has. The same rule is in CornerShape on the CPU, for what is drawn from textures.
//   - With room on the sides, the corner reaches a little further than a circle of its radius (by CORNER_REACH of it) and is a superellipse of
//     exponent 4: it covers about the same area as the circle, so it looks the same size.
//   - With no room (the radius is half the shape, as on a pill, a dot or a knob) it is exactly the circle, so round shapes stay perfectly round.
//   - In between it blends, by how much room the sides have.

const float CORNER_REACH = 0.305;

// how continuous a corner of this radius is in a shape whose shorter half is halfMin: 0 a circle, 1 a full superellipse
float cornerContinuity(float r, float halfMin) {
    return r <= 0.0 ? 0.0 : clamp((halfMin - r) / (CORNER_REACH * r), 0.0, 1.0);
}

// The signed distance to the edge of a box with continuous corners, with its middle at the origin, and the way out of it: less than 0 inside.
// Inside a corner it is the superellipse's value divided by the length of its gradient, which is the distance near the edge, where it counts.
float continuousBox(vec2 p, vec2 halfSize, float r, out vec2 normal) {
    float halfMin = min(halfSize.x, halfSize.y);
    r = clamp(r, 0.0, halfMin);
    float c = cornerContinuity(r, halfMin);
    float e = r * (1.0 + CORNER_REACH * c);
    float n = 2.0 + 2.0 * c;
    vec2 s = vec2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    vec2 q = abs(p) - (halfSize - e);
    if (e > 0.0 && q.x > 0.0 && q.y > 0.0) {
        vec2 u = q / e;
        float f = pow(pow(u.x, n) + pow(u.y, n), 1.0 / n);
        vec2 g = vec2(pow(u.x / f, n - 1.0), pow(u.y / f, n - 1.0));
        float len = max(length(g), 1e-4);
        normal = s * g / len;
        return (f - 1.0) * e / len;
    }
    normal = q.x > q.y ? vec2(s.x, 0.0) : vec2(0.0, s.y);
    return max(q.x, q.y) - e;
}

float continuousBox(vec2 p, vec2 halfSize, float r) {
    vec2 normal;
    return continuousBox(p, halfSize, r, normal);
}
