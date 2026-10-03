#version 150

// One step down the bloom chain: the picture at half the size, each pixel a 13-tap average (a box in the middle
// and four overlapping ones around it) so small bright points don't flicker. On the first step only what is
// bright enough to bloom is kept, with a soft knee.

uniform sampler2D Sampler0;
uniform vec2 Texel;
uniform vec2 Threshold;

in vec2 uv;

out vec4 fragColor;

vec3 tap(vec2 o) {
    return texture(Sampler0, uv + o * Texel).rgb;
}

vec3 bright(vec3 c) {
    if (Threshold.x < 0.0) return c;
    float br = max(c.r, max(c.g, c.b));
    float knee = Threshold.x * Threshold.y + 1.0e-4;
    float soft = clamp(br - Threshold.x + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    return c * max(soft, br - Threshold.x) / max(br, 1.0e-4);
}

void main() {
    vec3 a = tap(vec2(-2.0, -2.0)), b = tap(vec2(0.0, -2.0)), c = tap(vec2(2.0, -2.0));
    vec3 d = tap(vec2(-1.0, -1.0)), e = tap(vec2(1.0, -1.0));
    vec3 f = tap(vec2(-2.0, 0.0)), g = tap(vec2(0.0, 0.0)), h = tap(vec2(2.0, 0.0));
    vec3 i = tap(vec2(-1.0, 1.0)), j = tap(vec2(1.0, 1.0));
    vec3 k = tap(vec2(-2.0, 2.0)), l = tap(vec2(0.0, 2.0)), m = tap(vec2(2.0, 2.0));
    vec3 col = (d + e + i + j) * 0.125
             + (a + b + f + g) * 0.03125 + (b + c + g + h) * 0.03125
             + (f + g + k + l) * 0.03125 + (g + h + l + m) * 0.03125;
    fragColor = vec4(bright(col), 1.0);
}
