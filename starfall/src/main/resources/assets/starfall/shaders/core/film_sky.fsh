#version 150

// Deep space seen along each pixel's ray: drifting nebulae with dark dust, and the band of a galaxy's disc
// with its glow, star clouds and dust lanes. The individual stars are drawn on top as sprites.

uniform mat4 ProjMat;
uniform vec3 ViewX;
uniform vec3 ViewY;
uniform vec3 ViewZ;
uniform vec3 NebulaA;
uniform vec3 NebulaB;
uniform vec4 Band;
uniform float Nebula;
uniform float Seed;
uniform float Fade;

in vec2 ndc;
in vec4 tint;

out vec4 fragColor;

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx) * 2.0 - 1.0;
}

float gnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = p - i;
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float n000 = dot(hash33(i), f);
    float n100 = dot(hash33(i + vec3(1.0, 0.0, 0.0)), f - vec3(1.0, 0.0, 0.0));
    float n010 = dot(hash33(i + vec3(0.0, 1.0, 0.0)), f - vec3(0.0, 1.0, 0.0));
    float n110 = dot(hash33(i + vec3(1.0, 1.0, 0.0)), f - vec3(1.0, 1.0, 0.0));
    float n001 = dot(hash33(i + vec3(0.0, 0.0, 1.0)), f - vec3(0.0, 0.0, 1.0));
    float n101 = dot(hash33(i + vec3(1.0, 0.0, 1.0)), f - vec3(1.0, 0.0, 1.0));
    float n011 = dot(hash33(i + vec3(0.0, 1.0, 1.0)), f - vec3(0.0, 1.0, 1.0));
    float n111 = dot(hash33(i + vec3(1.0, 1.0, 1.0)), f - vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float fbm(vec3 p, int octaves) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        s += a * gnoise(p);
        p = p * 2.03 + vec3(1.31, -0.73, 0.57);
        a *= 0.5;
    }
    return s;
}

void main() {
    vec3 dir = normalize(ViewX * (ndc.x / ProjMat[0][0]) + ViewY * (ndc.y / ProjMat[1][1]) - ViewZ);
    vec3 q = dir * 2.2 + vec3(Seed, Seed * 0.37, -Seed * 0.61);
    vec3 w = vec3(gnoise(q * 1.3), gnoise(q * 1.3 + vec3(5.2, 1.1, 0.4)), gnoise(q * 1.3 + vec3(9.1, 3.7, 6.2)));
    float n1 = fbm(q + w * 1.2, 6);
    float n2 = fbm(q * 2.1 - w * 0.8 + vec3(3.3, 0.0, 1.0), 6);
    float dust = smoothstep(0.0, 0.32, fbm(dir * 6.5 + w * 2.0 + vec3(1.7, Seed, 0.2), 6));

    // the galaxy's disc: a band of unresolved stars across the sky
    float bd = dot(dir, normalize(Band.xyz));
    float band = exp(-bd * bd * 22.0);
    float core = exp(-bd * bd * 90.0);
    float clumps = smoothstep(-0.25, 0.5, fbm(dir * 9.0 + w, 6));
    vec3 milky = vec3(0.55, 0.60, 0.75) * band * (0.12 + 0.88 * clumps * clumps) + vec3(1.0, 0.82, 0.60) * core * 0.5 * clumps;
    milky *= Band.w * 0.45 * (1.0 - 0.85 * dust * smoothstep(0.2, 0.9, band));

    vec3 neb = NebulaA * smoothstep(-0.05, 0.45, n1) * (0.6 + 0.4 * n2) + NebulaB * smoothstep(0.08, 0.5, n2) * 0.8;
    neb *= Nebula * (1.0 - 0.6 * dust) * 0.4;

    vec3 col = vec3(0.004, 0.005, 0.012) + (neb + milky) * Fade;
    fragColor = vec4(col, 1.0) * tint;
}
