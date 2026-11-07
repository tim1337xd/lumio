#version 330

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// One pass of a dual Kawase blur, which the glass sees through. The quad's color says which: red for a pass up (to a larger picture), none for
// a pass down (to a smaller one), and in its blue channel how far apart the samples are, in half texels of the picture that is read, as a part
// of 4. Every pass down halves the picture and blurs it a little; the passes up double it again and smooth what the passes down left. A few of
// each blur as wide as a large Gaussian with far fewer samples, since most of the work is done on small pictures.
void main() {
    vec2 halfTexel = 0.5 / vec2(textureSize(Sampler0, 0)) * max(vertexColor.b * 4.0, 0.25);
    vec2 uv = texCoord0;
    vec3 sum;
    if (vertexColor.r > 0.5) {
        // up: eight samples around the point, the diagonal ones weighted double
        sum = texture(Sampler0, uv + vec2(-halfTexel.x * 2.0, 0.0)).rgb;
        sum += texture(Sampler0, uv + vec2(-halfTexel.x, halfTexel.y)).rgb * 2.0;
        sum += texture(Sampler0, uv + vec2(0.0, halfTexel.y * 2.0)).rgb;
        sum += texture(Sampler0, uv + vec2(halfTexel.x, halfTexel.y)).rgb * 2.0;
        sum += texture(Sampler0, uv + vec2(halfTexel.x * 2.0, 0.0)).rgb;
        sum += texture(Sampler0, uv + vec2(halfTexel.x, -halfTexel.y)).rgb * 2.0;
        sum += texture(Sampler0, uv + vec2(0.0, -halfTexel.y * 2.0)).rgb;
        sum += texture(Sampler0, uv + vec2(-halfTexel.x, -halfTexel.y)).rgb * 2.0;
        sum /= 12.0;
    } else {
        // down: the point itself and its four diagonal neighbours, each of them already the mean of four texels through the filter
        sum = texture(Sampler0, uv).rgb * 4.0;
        sum += texture(Sampler0, uv - halfTexel).rgb;
        sum += texture(Sampler0, uv + halfTexel).rgb;
        sum += texture(Sampler0, uv + vec2(halfTexel.x, -halfTexel.y)).rgb;
        sum += texture(Sampler0, uv - vec2(halfTexel.x, -halfTexel.y)).rgb;
        sum /= 8.0;
    }
    fragColor = vec4(sum, 1.0);
}
