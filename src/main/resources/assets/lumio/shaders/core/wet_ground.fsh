#version 330

#moj_import <minecraft:projection.glsl>

// what the frame needs to know: see WetGround
uniform sampler2D Sampler0;
// the finished picture, and its depth
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in vec2 texCoord0;

out vec4 fragColor;

// Reflections in screen space, on what faces up, as far as the reflection distance (fading out softly over its last quarter). A ray goes from the
// ground in the mirrored direction through the picture, in steps that get longer, until it has gone behind what the depth says is there: what is
// seen there is the reflection. Where the ray leaves the picture or meets nothing, the sky in its direction is reflected (the frame's sky color
// from the horizon up), and the picture's reflection fades into it near the picture's edges, so there is no border. As water does, it reflects
// little looking straight down and much at a grazing angle. The rings of drops bend the mirror a little. Only plain math.

// the share of the reflection distance over which it fades out at its end
const float FADE_SHARE = 0.25;
// how much water reflects looking straight down, and how quickly that grows towards a grazing angle
const float FRESNEL_DOWN = 0.06;
const float FRESNEL_POWER = 4.0;
const float DARKER = 0.62;

vec3 gRight;
vec3 gUp;
vec3 gBack;

vec4 parameter(float index) {
    return floor(texture(Sampler0, vec2((index + 0.5) / 16.0, 0.5)) * 255.0 + 0.5);
}

vec3 axis(float texel) {
    vec4 a = parameter(texel);
    vec4 b = parameter(texel + 1.0);
    return vec3(a.r + a.g * 256.0, a.b + a.a * 256.0, b.r + b.g * 256.0) / 65535.0 * 2.0 - 1.0;
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// how far a point of the picture is in front of the camera, along its axis
float viewDepth(vec2 uv) {
    float depth = texture(Sampler2, uv).r;
    return ProjMat[3][2] / max(depth + ProjMat[2][2], 1e-6);
}

// a point of the picture, relative to the camera, with the world's axes
vec3 place(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    float z = abs(viewDepth(uv));
    vec3 view = vec3(ndc.x / ProjMat[0][0], ndc.y / ProjMat[1][1], -1.0) * z;
    return gRight * view.x + gUp * view.y + gBack * view.z;
}

// where a point relative to the camera is in the picture, with its depth in z; z below zero when it is behind the camera
vec3 project(vec3 p) {
    vec3 view = vec3(dot(p, gRight), dot(p, gUp), dot(p, gBack));
    if (view.z > -0.05) {
        return vec3(0.0, 0.0, -1.0);
    }
    vec2 ndc = vec2(ProjMat[0][0] * view.x, ProjMat[1][1] * view.y) / -view.z;
    return vec3(ndc * 0.5 + 0.5, -view.z);
}

// The rings of the drops: each cell of the ground has a drop of its own, every so often. x and y bend the mirror, z is the light of the ring.
vec3 rings(vec2 ground, float time) {
    vec3 sum = vec3(0.0);
    vec2 cell = floor(ground);
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 c = cell + vec2(float(dx), float(dy));
            float own = hash(c);
            vec2 centre = c + vec2(hash(c + 7.1), hash(c + 3.7));
            float age = fract(time * (0.7 + own * 0.5) + own * 9.0);
            float radius = age * 0.6;
            vec2 away = ground - centre;
            float d = length(away);
            float wave = exp(-pow((d - radius) * 16.0, 2.0)) * (1.0 - age);
            if (d > 1e-3) {
                sum.xy += away / d * wave;
            }
            sum.z += wave;
        }
    }
    return sum;
}

vec3 blurred(vec2 uv, float radius) {
    vec2 size = radius / vec2(textureSize(Sampler1, 0));
    vec3 sum = texture(Sampler1, uv).rgb * 2.0;
    sum += texture(Sampler1, uv + size * vec2(1.0, 0.6)).rgb;
    sum += texture(Sampler1, uv + size * vec2(-1.0, 0.6)).rgb;
    sum += texture(Sampler1, uv + size * vec2(0.6, -1.0)).rgb;
    sum += texture(Sampler1, uv + size * vec2(-0.6, -1.0)).rgb;
    return sum / 6.0;
}

void main() {
    fragColor = vec4(0.0);
    vec2 size = vec2(textureSize(Sampler2, 0));
    vec2 uv = texCoord0;
    if (texture(Sampler2, uv).r <= 1e-6) {
        return;
    }
    gRight = axis(2.0);
    gUp = axis(4.0);
    gBack = axis(6.0);
    vec3 here = place(uv);
    float dist = length(here);
    float reach = parameter(10.0).r;
    if (dist > reach) {
        return;
    }
    // what faces up: the direction it faces, from the points next to it
    vec2 texel = 3.0 / size;
    vec3 across = place(uv + vec2(texel.x, 0.0)) - here;
    vec3 along = place(uv + vec2(0.0, texel.y)) - here;
    vec3 normal = normalize(cross(across, along));
    if (normal.y < 0.0) {
        normal = -normal;
    }
    if (normal.y < 0.9 || length(across) > dist * 0.2 || length(along) > dist * 0.2) {
        return;
    }

    vec4 first = parameter(0.0);
    float strength = first.r / 255.0;
    vec4 east = parameter(8.0);
    vec4 south = parameter(9.0);
    vec2 ground = vec2(east.r + east.g * 256.0 + east.b / 255.0, south.r + south.g * 256.0 + south.b / 255.0) + here.xz;
    float wet = 1.0;
    if (first.g < 127.5) {
        // puddles: shiny patches here and there
        float patches = noise(ground * 0.22) * 0.65 + noise(ground * 0.61 + 9.0) * 0.35;
        wet = smoothstep(0.52, 0.6, patches);
        if (wet <= 0.0) {
            return;
        }
    }
    vec4 clock = parameter(1.0);
    float time = clock.r + clock.g * 256.0 + clock.b / 255.0;
    vec3 ring = rings(ground * 1.4, time);
    vec3 bent = normalize(vec3(ring.x * 0.05 + (noise(ground * 7.0 + time) - 0.5) * 0.012, 1.0, ring.y * 0.05 + (noise(ground * 7.0 - time) - 0.5) * 0.012));

    vec3 dir = here / max(dist, 1e-3);
    vec3 mirrored = reflect(dir, bent);
    float steps = first.b;
    float t = 0.25;
    float stepLength = 0.35;
    vec3 seen = vec3(0.0);
    float found = 0.0;
    vec3 start = here + vec3(0.0, 0.02, 0.0);
    for (int i = 0; i < 32; i++) {
        if (float(i) >= steps) {
            break;
        }
        vec3 at = project(start + mirrored * t);
        if (at.z < 0.0 || at.x < 0.0 || at.x > 1.0 || at.y < 0.0 || at.y > 1.0) {
            break;
        }
        float scene = abs(viewDepth(at.xy));
        float behind = at.z - scene;
        if (behind > 0.0 && behind < 0.8 + t * 0.15) {
            // back and forth between the last two steps, for where exactly it met
            float low = t - stepLength;
            float high = t;
            for (int j = 0; j < 3; j++) {
                float middle = (low + high) * 0.5;
                vec3 point = project(start + mirrored * middle);
                if (point.z > abs(viewDepth(point.xy))) {
                    high = middle;
                } else {
                    low = middle;
                }
            }
            at = project(start + mirrored * high);
            vec2 edge = min(at.xy, 1.0 - at.xy);
            found = smoothstep(0.0, 0.1, edge.x) * smoothstep(0.0, 0.14, edge.y);
            seen = blurred(at.xy, 1.5 + high * 0.25);
            break;
        }
        t += stepLength;
        stepLength *= 1.22;
    }
    // the sky in the ray's direction, from the horizon's color up to the color straight up: what is reflected where the picture has nothing
    vec3 horizon = parameter(11.0).rgb / 255.0;
    vec3 zenith = parameter(12.0).rgb / 255.0;
    vec3 sky = mix(horizon, zenith, smoothstep(0.0, 0.6, mirrored.y));
    if (found <= 0.0) {
        // it met nothing: what it sees is the sky, taken from the picture where it is there
        vec3 far = project(mirrored * 400.0);
        if (far.z > 0.0 && far.x > 0.0 && far.x < 1.0 && far.y > 0.0 && far.y < 1.0 && texture(Sampler2, far.xy).r <= 1e-6) {
            vec2 edge = min(far.xy, 1.0 - far.xy);
            found = smoothstep(0.0, 0.1, edge.x) * smoothstep(0.0, 0.14, edge.y);
            seen = blurred(far.xy, 3.0);
        }
    }
    // what the picture shows of the reflection, fading into the sky near the picture's edges and where the ray met nothing
    seen = mix(sky, seen, found);
    float nearness = 1.0 - smoothstep(reach * (1.0 - FADE_SHARE), reach, dist);
    // looking flat along the ground it shines most, as water does; straight down hardly
    float facing = max(dot(-dir, normal), 0.0);
    float fresnel = mix(FRESNEL_DOWN, 1.0, pow(1.0 - facing, FRESNEL_POWER));
    float alpha = strength * wet * nearness * fresnel * 0.9;
    // the rings have a faint light of their own, reflection or not
    float ringLight = min(ring.z, 1.0) * 0.1 * strength * wet * nearness;
    vec3 color = seen * DARKER * alpha + vec3(ringLight);
    fragColor = vec4(color, min(1.0, alpha + ringLight));
}
