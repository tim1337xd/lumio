#version 330

#moj_import <lumio:corners.glsl>

// A pane of glass over the screen. See GlassPanes for what it is made of. The quad gives the number of the pane (in the texture
// coordinate) and how much of it there is (the opacity of its color); everything else is read from the texture of the parameters:
//   texels 0 to 63: the colors of the glow along the edge, from the start of the top edge, clockwise, with how bright it is as their alpha
//   texels 64 to 127: how bright the glow of the Colored theme is at those places, as their alpha
//   texel 128: not used
//   11 texels per pane, after them:
//   0: where the pane is, in pixels of the screen from the bottom left corner, two numbers of 16 bits, each moved up by 32768
//   1: its width and height, 16 bits each
//   2: the radius of its corners times 8 (16 bits), how blurred what is behind it is, how much of the pane there is
//   3: the color of the glass, and how much of it (255 is a solid pane)
//   4: the shine on the edge, the bending at the edge, the coloring at the edge, the darkness of the shadow
//   5: how far the shadow reaches, how far the glow reaches, how strong the glow is, the flags
//   6: the color of the line on the edge of a solid pane, how deep the edge is
//   7: the accent color, which lights a button, and how much it is lit
//   8: the brightness of what is behind the pane, measured on the CPU, whether the shader measures it itself instead (255), how much of the
//      colors of the Colored theme lie over the world, and how much film grain lies over them (in any other pane, how saturated what is behind it is)
//   9: the width and height of the screen, 16 bits each
//   10: up to four panes whose glows reach into this one's, 255 for none
// The quad gives which part it draws in its second texture coordinate: all of the pane, what lies outside of its edge (in the pass behind all elements), or the pane itself.

uniform sampler2D Sampler0;  // the screen as it was before the panes, sharp; for a pane of the Colored theme, its colors over the whole screen
uniform sampler2D Sampler1;  // the same, blurred and small
uniform sampler2D Sampler2;  // the parameters

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const int FLAG_OPAQUE = 1;
const int FLAG_WINDOW = 2;
const int FLAG_RIM_ONLY = 4;
const int FLAG_INNER_GLOW = 8;
const int FLAG_LIGHT = 16;
const int FLAG_FLAT = 32;
const int FLAG_COLORED = 64;
const int FLAG_READABLE = 128;

const float PART_OUTSIDE = 1.0;
const float PART_INSIDE = 2.0;
const int PANE_TEXELS = 13;
const int COLORED_BRIGHTNESS_TEXEL = 64;
const int FIRST_PANE_TEXEL = 129;
const int NO_NEIGHBOR = 255;

// the backdrop is a little more colorful and brighter before it is tinted, so the glass looks alive instead of grey
const float VIBRANCY_SATURATION = 1.18;
// the saturation setting of a pane of glass, from 0 to 1, as a factor: at its default of 0.6 it is the same as VIBRANCY_SATURATION
const float SATURATION_RANGE = 1.18 / 0.6;
const float VIBRANCY_BRIGHTNESS = 1.05;
// how much denser the tint gets over a bright backdrop (and lighter over a dark one), per unit of brightness away from the middle, and its limits
const float NEUTRAL_BRIGHTNESS = 0.45;
const float ADAPT = 0.35;
const float ADAPT_LIGHTER = 0.10;
const float ADAPT_DENSER = 0.16;
// how bright the glass may be where text lies on it: white text has 4.5:1 up to 0.47, the second line of text up to 0.38, and the cards and the gloss on the glass add a little
const float READABLE_BRIGHTNESS = 0.30;
// the glow's core reaches this share of its spread, and how strong the core and the bloom are
const float GLOW_CORE = 0.22;
const float GLOW_CORE_STRENGTH = 0.7;
const float GLOW_BLOOM_STRENGTH = 0.42;
// the refraction and color fringes stay in this share of the edge's depth
const float BEND_BAND = 0.6;

// a fixed noise per screen pixel, from 0 to 1
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// triangular noise of about one step of an 8-bit screen, against bands in soft gradients
float dither() {
    return (hash(gl_FragCoord.xy) + hash(gl_FragCoord.xy + vec2(17.31, 5.17)) - 1.0) / 255.0;
}

vec4 param(int texel) {
    return texelFetch(Sampler2, ivec2(texel, 0), 0) * 255.0;
}

float u16(float low, float high) {
    return low + high * 256.0;
}

// The screen's colors are sRGB; light adds up in linear color, so colors are mixed there and turned back, which keeps mixed colors clean.
vec3 toLinear(vec3 c) {
    return pow(c, vec3(2.2));
}

vec3 toScreen(vec3 c) {
    return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
}

// The color of the glow at a place of the edge: blended (in linear color) between the two of the 64 places it is between, and round the loop
// at the end, so a long edge has no steps of color along it.
vec4 glowAt(float place) {
    float at = fract(place) * 64.0 - 0.5;
    float low = floor(at);
    float f = at - low;
    int a = int(mod(low, 64.0));
    int b = int(mod(low + 1.0, 64.0));
    vec4 ca = texelFetch(Sampler2, ivec2(a, 0), 0);
    vec4 cb = texelFetch(Sampler2, ivec2(b, 0), 0);
    return vec4(toScreen(mix(toLinear(ca.rgb), toLinear(cb.rgb), f)), mix(ca.a, cb.a, f));
}

// How bright the glow of the Colored theme is at a place of the edge.
float coloredBrightnessAt(float place) {
    float at = fract(place) * 64.0 - 0.5;
    float low = floor(at);
    int a = int(mod(low, 64.0));
    int b = int(mod(low + 1.0, 64.0));
    return mix(texelFetch(Sampler2, ivec2(COLORED_BRIGHTNESS_TEXEL + a, 0), 0).a, texelFetch(Sampler2, ivec2(COLORED_BRIGHTNESS_TEXEL + b, 0), 0).a, at - low);
}

// How far along the edge the nearest point of the edge is, from 0 to 1: the edge starts at the left end of the top edge and goes
// clockwise, as the one of the menu does. The point and the box are given with the top left corner as the origin, and y going down.
float edgePlace(vec2 q, vec2 size, float r) {
    float x0 = r;
    float x1 = size.x - r;
    float y0 = r;
    float y1 = size.y - r;
    float cx = clamp(q.x, x0, x1);
    float cy = clamp(q.y, y0, y1);
    float dx = q.x - cx;
    float dy = q.y - cy;
    float across = max(0.0, x1 - x0);
    float down = max(0.0, y1 - y0);
    float arc = 3.14159265 * r * 0.5;
    float total = 2.0 * (across + down) + 4.0 * arc;
    if (total <= 0.0) {
        return 0.0;
    }
    float s;
    if (abs(dx) < 1e-4 || abs(dy) < 1e-4) {
        if (abs(dy) >= abs(dx)) {
            s = dy < 0.0 ? cx - x0 : across + arc + down + arc + (x1 - cx);
        } else {
            s = dx > 0.0 ? across + arc + (cy - y0) : across + arc + down + arc + across + arc + (y1 - cy);
        }
    } else {
        float angle = atan(dy, dx);
        if (dx > 0.0 && dy < 0.0) {
            s = across + (angle + 1.57079633) * r;
        } else if (dx > 0.0) {
            s = across + arc + down + angle * r;
        } else if (dy > 0.0) {
            s = across + arc + down + arc + across + (angle - 1.57079633) * r;
        } else {
            s = across + arc + down + arc + across + arc + down + (angle + 3.14159265) * r;
        }
    }
    return clamp(s / total, 0.0, 0.9999);
}

// Where a point of the screen lies in the Colored theme's field, as this pane has it: the whole screen for the HUD, the window for a menu. Beyond the field, as a dropdown can be, it is mirrored: never a streak.
vec2 flowPlace(vec2 point, int base) {
    vec4 place = param(base + 11);
    vec4 extent = param(base + 12);
    vec2 uv = (point - (vec2(u16(place.r, place.g), u16(place.b, place.a)) - 32768.0)) / vec2(u16(extent.r, extent.g), u16(extent.b, extent.a));
    return 1.0 - abs(1.0 - mod(uv, 2.0));
}

// The glow of the Colored theme at a point of an edge: the color of the field right there, in this frame and in this pane's place of the field. A dark part of the field gives a dark, faint glow, a bright part a bright one.
vec3 flowForGlow(vec2 edgePoint, int base) {
    return texture(Sampler0, flowPlace(edgePoint, base)).rgb;
}

// The soft fall of a light or a shadow from an edge, as the halos of the menu have it.
float falloff(float t) {
    t = clamp(t, 0.0, 1.0);
    float f = 1.0 - t * t * (3.0 - 2.0 * t);
    return pow(f, 1.6);
}

// How much of a pane's glow there is at a point, as in its outside, and its color there.
float glowOfPane(int pane, vec2 p, vec2 screen, out vec3 color) {
    int base = FIRST_PANE_TEXEL + pane * PANE_TEXELS;
    vec4 t0 = param(base);
    vec4 t1 = param(base + 1);
    vec4 t2 = param(base + 2);
    vec4 t5 = param(base + 5);
    vec2 size = vec2(u16(t1.r, t1.g), u16(t1.b, t1.a));
    vec2 origin = vec2(u16(t0.r, t0.g) - 32768.0, u16(t0.b, t0.a) - 32768.0);
    float radius = u16(t2.r, t2.g) / 8.0;
    vec2 normal;
    float dist = continuousBox(p - (origin + size * 0.5), size * 0.5, radius, normal);
    float d = max(dist, 0.0);
    float spread = t5.g;
    float core = falloff(d / max(1.5, spread * GLOW_CORE)) * GLOW_CORE_STRENGTH;
    float bloom = falloff(d / max(spread, 1.0)) * GLOW_BLOOM_STRENGTH;
    bool colored = (int(t5.a + 0.5) & FLAG_COLORED) != 0;
    float place = edgePlace(vec2(p.x - origin.x, (origin.y + size.y) - p.y), size, radius);
    float bright;
    if (colored) {
        color = flowForGlow(p - normal * d, base);
        bright = coloredBrightnessAt(place);
    } else {
        vec4 glow = glowAt(place);
        color = glow.rgb;
        bright = glow.a;
    }
    return spread < 0.5 ? 0.0 : (1.0 - (1.0 - core) * (1.0 - bloom)) * bright * t5.b / 255.0 * t2.a / 255.0;
}

void main() {
    int index = int(texCoord0.x + 0.5);
    int base = FIRST_PANE_TEXEL + index * PANE_TEXELS;
    bool outsidePart = texCoord0.y > 0.5 && texCoord0.y < 1.5;
    bool insidePart = texCoord0.y > 1.5;
    vec4 t0 = param(base);
    vec4 t1 = param(base + 1);
    vec4 t2 = param(base + 2);
    vec4 t3 = param(base + 3);
    vec4 t4 = param(base + 4);
    vec4 t5 = param(base + 5);
    vec4 t6 = param(base + 6);
    vec4 t7 = param(base + 7);
    vec4 t8 = param(base + 8);
    vec4 t9 = param(base + 9);

    vec2 origin = vec2(u16(t0.r, t0.g) - 32768.0, u16(t0.b, t0.a) - 32768.0);
    vec2 size = vec2(u16(t1.r, t1.g), u16(t1.b, t1.a));
    float radius = u16(t2.r, t2.g) / 8.0;
    float blur = t2.b / 255.0;
    float fade = t2.a / 255.0 * vertexColor.a;
    vec3 tint = t3.rgb / 255.0;
    float tintAmount = t3.a / 255.0;
    float edgeLight = t4.r / 255.0;
    float refraction = t4.g / 255.0;
    float dispersion = t4.b / 255.0;
    float shadowStrength = t4.a / 255.0;
    float shadowSpread = t5.r;
    float glowSpread = t5.g;
    float glowStrength = t5.b / 255.0;
    int flags = int(t5.a + 0.5);
    vec3 lineColor = t6.rgb / 255.0;
    float thickness = max(2.0, t6.a);
    vec3 accent = t7.rgb / 255.0;
    float lit = t7.a / 255.0;
    bool opaque = (flags & FLAG_OPAQUE) != 0;
    bool window = (flags & FLAG_WINDOW) != 0;
    bool rimOnly = (flags & FLAG_RIM_ONLY) != 0;
    bool innerGlow = (flags & FLAG_INNER_GLOW) != 0;
    bool light = (flags & FLAG_LIGHT) != 0;
    bool flatPane = (flags & FLAG_FLAT) != 0;
    bool colored = (flags & FLAG_COLORED) != 0;

    if (fade <= 0.001 || size.x <= 0.0 || size.y <= 0.0) {
        discard;
    }

    vec2 screen = vec2(u16(t9.r, t9.g), u16(t9.b, t9.a));
    vec2 p = gl_FragCoord.xy;
    vec2 centre = origin + size * 0.5;
    vec2 normal;
    float dist = continuousBox(p - centre, size * 0.5, radius, normal);
    // where on the edge the pixel is, for the colors of the glow: with the top left corner as the origin, and y going down
    vec2 q = vec2(p.x - origin.x, (origin.y + size.y) - p.y);
    float place = edgePlace(q, size, radius);
    // the colors of the Colored theme at this place of the screen: what fills the pane is what lights its edge and glows around it, at the same moment. Outside of the
    // pane, the glow has the color of the fill at the nearest point of the edge; the other themes have the glow's own colors along the edge
    vec3 flow = colored ? texture(Sampler0, flowPlace(p, base)).rgb : vec3(0.0);
    vec4 glowColor = colored ? vec4(flowForGlow(p - normal * max(dist, 0.0), base), coloredBrightnessAt(place)) : glowAt(place);
    // one light for every pane, from above and a little from the left: it lies along the top edge and fades round the corners, and the bottom
    // edge, turned away from it, is a touch darker
    vec2 lightDirection = normalize(vec2(-0.45, 1.0));
    float lightOnEdge = pow(max(dot(normal, lightDirection), 0.0), 2.0);
    float turnedAway = pow(max(-normal.y, 0.0), 2.0);

    // ------------------------------------------------------------------------------------------------ outside: the shadow and the glow
    vec3 outsideColor = vec3(0.0);
    float outsideAlpha = 0.0;
    if (!rimOnly && !insidePart && dist > -1.0) {
        float d = max(dist, 0.0);
        if (shadowSpread > 0.5 && shadowStrength > 0.001) {
            // two shadows, as the light comes from above: a wide, very soft one around the pane, and a small, tighter one right under it
            float strength = shadowStrength * (light ? 0.5 : 1.0);
            vec2 shadowNormal;
            float wide = continuousBox(p - centre + vec2(0.0, shadowSpread * 0.12), size * 0.5, radius, shadowNormal);
            float near = continuousBox(p - centre + vec2(0.0, max(1.0, shadowSpread * 0.05)), size * 0.5, radius, shadowNormal);
            float ambient = falloff(max(wide, 0.0) / shadowSpread) * strength * 0.55;
            float contact = falloff(max(near, 0.0) / max(2.0, shadowSpread * 0.28)) * strength * 0.55;
            outsideColor = vec3(0.0);
            outsideAlpha = 1.0 - (1.0 - ambient) * (1.0 - contact);
        }
        if (glowSpread > 0.5 && glowStrength > 0.001) {
            // two layers: a narrow bright core right at the edge, and a wide, very soft bloom; both reach exactly nothing at their end
            float core = falloff(d / max(1.5, glowSpread * GLOW_CORE)) * GLOW_CORE_STRENGTH;
            float bloom = falloff(d / glowSpread) * GLOW_BLOOM_STRENGTH;
            float ga = (1.0 - (1.0 - core) * (1.0 - bloom)) * glowColor.a * glowStrength;
            if (outsidePart) {
                // where glows meet they make one soft glow: the strongest here is what is drawn, in the colors of all of them by how strong each is, so they do not add up
                vec4 neighbors = param(base + 10);
                // compared as they are drawn, with each pane's fade, so that both see the same at the line where they are equal
                float mine = ga * t2.a / 255.0;
                vec3 sum = glowColor.rgb * mine;
                float weight = mine;
                for (int n = 0; n < 4; n++) {
                    int other = int(neighbors[n] + 0.5);
                    if (other != NO_NEIGHBOR) {
                        vec3 theirColor;
                        float theirs = glowOfPane(other, p, screen, theirColor);
                        if (theirs > mine || (theirs == mine && theirs > 0.0 && other < index)) {
                            ga = 0.0;
                        }
                        sum += theirColor * theirs;
                        weight += theirs;
                    }
                }
                if (weight > 0.0001) {
                    glowColor.rgb = sum / weight;
                }
            }
            float total = outsideAlpha + ga * (1.0 - outsideAlpha);
            // premultiplied: the glow over the shadow, then back to a color and its amount
            outsideColor = total > 0.0001 ? (outsideColor * outsideAlpha * (1.0 - ga) + glowColor.rgb * ga) / total : outsideColor;
            outsideAlpha = total;
        }
    }

    // ------------------------------------------------------------------------------------------------- inside: the glass itself
    vec3 insideColor = vec3(0.0);
    float insideAlpha = 0.0;
    float coverage = clamp(0.5 - dist, 0.0, 1.0);
    if (coverage > 0.0 && !outsidePart) {
        float depth = max(-dist, 0.0);
        float t = clamp(depth / thickness, 0.0, 1.0);
        vec3 color;
        if (opaque) {
            color = tint;
            insideAlpha = 1.0;
        } else {
            // the bending at the edge: the glass is a lens there, and shows what is a little further in, which is what makes it thick; it stays in a
            // narrow band at the edge, and is gentle
            float band = thickness * BEND_BAND;
            float tb = clamp(depth / band, 0.0, 1.0);
            float xr = 1.0 - tb;
            float thetaI = asin(xr * xr);
            float thetaT = asin(sin(thetaI) / 1.45);
            float bend = tb >= 1.0 ? 0.0 : tan(thetaI - thetaT);
            vec2 offset = -normal * bend * band * 0.9 * refraction;
            vec2 uv = p / screen;
            vec2 unit = 1.0 / screen;
            // the colors come apart at the edge, as they do in thick glass: every one is bent a little differently
            float spread = 0.045 * dispersion;
            vec2 uvR = uv + offset * (1.0 - spread) * unit;
            vec2 uvG = uv + offset * unit;
            vec2 uvB = uv + offset * (1.0 + spread) * unit;
            vec3 soft = vec3(texture(Sampler1, uvR).r, texture(Sampler1, uvG).g, texture(Sampler1, uvB).b);
            vec3 behind = soft;
            if (!colored) {
                vec3 sharp = vec3(texture(Sampler0, uvR).r, texture(Sampler0, uvG).g, texture(Sampler0, uvB).b);
                // the blurred picture is the one that is seen, as soon as there is any blur: how much it is blurred is in the picture itself
                behind = mix(sharp, soft, smoothstep(0.0, 0.1, blur));
            }
            float luma = dot(behind, vec3(0.2126, 0.7152, 0.0722));
            behind = clamp(mix(vec3(luma), behind, colored ? VIBRANCY_SATURATION : t8.a / 255.0 * SATURATION_RANGE) * VIBRANCY_BRIGHTNESS, 0.0, 1.0);
            // the tint adapts to how bright it is behind the pane, measured over the whole pane (by the CPU, or here from a few samples)
            float under = t8.r / 255.0;
            if (t8.g > 127.0) {
                vec2 lowCorner = (origin + size * 0.15) / screen;
                vec2 stepUv = size * 0.35 / screen;
                float sum = 0.0;
                for (int sx = 0; sx < 3; sx++) {
                    for (int sy = 0; sy < 3; sy++) {
                        sum += dot(texture(Sampler1, lowCorner + vec2(float(sx), float(sy)) * stepUv).rgb, vec3(0.2126, 0.7152, 0.0722));
                    }
                }
                under = sum / 9.0;
            }
            float amount = clamp(tintAmount + clamp((under - NEUTRAL_BRIGHTNESS) * ADAPT, -ADAPT_LIGHTER, ADAPT_DENSER), 0.0, 0.97);
            // where the world is too bright for that, the tint gets as dense as readable text needs, there and no further; a light tint carries dark text
            float seen = dot(behind, vec3(0.2126, 0.7152, 0.0722));
            float tintBrightness = dot(tint, vec3(0.2126, 0.7152, 0.0722));
            if ((flags & FLAG_READABLE) != 0 && seen > READABLE_BRIGHTNESS && tintBrightness < READABLE_BRIGHTNESS) {
                amount = max(amount, (seen - READABLE_BRIGHTNESS) / (seen - tintBrightness));
            }
            if (colored) {
                // the colors lie over the world, and the dark layer, which keeps white text readable, over them, the same whatever the world is
                behind = mix(behind, flow, t8.b / 255.0);
                if (t8.a > 0.5) {
                    // a faint film that stays still, so that large areas never look flat or banded
                    behind += (hash(gl_FragCoord.xy) - 0.5) * t8.a / 255.0;
                }
                amount = tintAmount;
            }
            color = mix(behind, tint, amount);
            // the light that comes through the edge: a soft brightening inside of it, strongest where the light falls
            // (a flat pane has neither, nor the gloss below: it is the plain fill)
            if (!flatPane) {
                float fresnel = pow(max(1.0 - t, 0.0), 2.5) * edgeLight * (0.22 + 0.38 * lightOnEdge) * (window ? 0.9 : 0.7);
                vec3 lightTint = mix(vec3(1.0), tint, 0.25);
                color = mix(color, lightTint, fresnel);
                // a gloss over the top of the pane, where the light comes in from above
                float top = clamp((p.y - origin.y) / max(size.y, 1.0), 0.0, 1.0);
                color += vec3(0.035 + 0.045 * edgeLight) * smoothstep(0.55, 1.0, top) * (window ? 0.6 : 1.0);
            }
            insideAlpha = 1.0;
        }
        if (lit > 0.001) {
            color = mix(color, accent, 0.26 * lit);
        }
        if (innerGlow && glowStrength > 0.001) {
            float innerDepth = window ? 7.0 * (thickness / 12.0) : 4.0 * (thickness / 12.0);
            float ti = clamp(depth / max(innerDepth, 1.0), 0.0, 1.0);
            float f = 1.0 - ti * ti * (3.0 - 2.0 * ti);
            color = mix(color, glowColor.rgb, f * f * glowColor.a * glowStrength * 0.42);
        }
        // the line on the edge: bright where the light falls on the glass, and in the color of the glow where the glow is
        float rim = 1.0 - smoothstep(0.6, 1.9, depth);
        if (opaque) {
            color = mix(color, lineColor, rim);
        } else {
            float base = window ? 0.10 : 0.35;
            // a flat pane has the same thin line all around, not one that is brighter where the light falls
            float strength = flatPane ? 0.75 : base + (1.0 - base) * lightOnEdge;
            float lineAlpha = min(1.0, 0.28 + 0.62 * edgeLight) * strength;
            vec3 white = light ? vec3(0.1, 0.1, 0.12) : vec3(1.0);
            vec3 line = white;
            if (lit > 0.001) {
                line = mix(white, accent, 0.6 * lit);
                lineAlpha = min(1.0, lineAlpha + 0.3 * lit);
            }
            color = mix(color, line, rim * lineAlpha);
        }
        if (glowStrength > 0.001) {
            vec3 glowLine = mix(glowColor.rgb, vec3(1.0), 0.28);
            color = mix(color, glowLine, rim * min(1.0, glowColor.a * glowStrength * 1.1));
        }
        if (!flatPane) {
            // a very faint darker line inside the bottom edge, turned away from the light
            float lower = (1.0 - smoothstep(0.8, 2.6, depth)) * turnedAway;
            color = mix(color, vec3(0.0), lower * (light ? 0.08 : 0.14));
            if (opaque) {
                // solid panes catch the same light along their top edge
                color = mix(color, vec3(1.0), rim * lightOnEdge * (light ? 0.35 : 0.12));
            }
        }
        if (rimOnly) {
            // only the line: the pane under it is already there
            insideAlpha = rim;
        }
        insideColor = color;
    }

    // the edge of the pane is smooth: halfSize a pixel of the inside and of the outside
    vec3 color = outsidePart ? outsideColor : insidePart ? insideColor : mix(outsideColor, insideColor, coverage);
    float alpha = (outsidePart ? outsideAlpha * (1.0 - coverage) : insidePart ? insideAlpha * coverage : mix(outsideAlpha, insideAlpha, coverage)) * fade;
    // a faint dither against bands in the soft tint, shadow and glow
    float noise = dither();
    color += noise;
    alpha += noise * (1.0 - coverage) * step(0.004, alpha);
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(clamp(color, 0.0, 1.0), clamp(alpha, 0.0, 1.0));
}
