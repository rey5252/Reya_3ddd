#version 150

// A barred spiral galaxy, face on in its own plane: an old yellow bulge and bar, two main arms on a
// logarithmic spiral with spurs between them, clumped star clouds, pink star-forming knots, dark dust lanes on
// the inner edges of the arms, and a speckle of bright stars. Drawn additively (premultiplied light).

uniform float Spin;
uniform float Fade;

in vec2 uv;
in vec4 tint;

out vec4 fragColor;

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx) * 2.0 - 1.0;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
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

float fbm(vec3 p, float lod) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 8; i++) {
        float w = clamp(lod - float(i), 0.0, 1.0);
        if (w <= 0.0) break;
        s += a * w * gnoise(p);
        p = p * 2.03 + vec3(1.31, -0.73, 0.57);
        a *= 0.5;
    }
    return s;
}

void main() {
    vec2 p = uv;
    float r = length(p);
    // how many octaves are still bigger than a pixel at this distance
    float fp = length(fwidth(p));
    float lod = clamp(log2(1.0 / max(fp * 9.0, 1.0e-6)), 1.0, 8.0);
    float theta = atan(p.y, p.x) + Spin;
    float lr = log(max(r, 0.003));
    float psi = theta - lr * 4.2;
    vec3 q = vec3(p * 5.0, 0.0);
    vec3 w = vec3(gnoise(q), gnoise(q + vec3(4.1, 2.3, 0.0)), 0.0);
    float wob = gnoise(vec3(p * 3.0, 1.7)) * 0.6;
    float arms = pow(0.5 + 0.5 * cos(2.0 * psi + wob), 3.0);
    float spurs = pow(0.5 + 0.5 * cos(4.0 * psi + 1.3 + wob * 3.0), 6.0) * 0.45;
    float clouds = clamp(fbm(vec3(p * 9.0, 0.5) + w * 0.6, lod) + 0.55, 0.0, 1.4);
    float disc = exp(-r * 2.3) * (1.0 - smoothstep(0.78, 1.0, r));
    float armLight = (arms + spurs) * clouds * disc * smoothstep(0.03, 0.16, r);
    float interarm = disc * clouds * 0.32;

    // dust lanes along the inner edges of the arms
    float lane = pow(0.5 + 0.5 * cos(2.0 * (psi + 0.42) + wob), 8.0) * smoothstep(0.08, 0.25, r) * (1.0 - smoothstep(0.5, 1.0, r));
    float dustNoise = smoothstep(-0.1, 0.4, fbm(vec3(p * 18.0, 2.0) + w, lod - 1.0));
    float dust = lane * (0.45 + 0.55 * dustNoise);

    // the bulge and the bar
    float ca = cos(Spin + 0.6);
    float sa = sin(Spin + 0.6);
    vec2 pr = vec2(p.x * ca - p.y * sa, p.x * sa + p.y * ca);
    float bar = exp(-(pr.x * pr.x * 30.0 + pr.y * pr.y * 220.0));
    float bulge = exp(-r * r * 70.0) * 1.6 + exp(-r * 9.0) * 0.5 + bar * 0.5;

    float knots = smoothstep(0.6, 0.84, fbm(vec3(p * 48.0, 4.0), lod - 2.5) + 0.5) * arms * arms * clouds;
    vec3 col = vec3(0.62, 0.74, 1.0) * armLight * 2.1
             + vec3(0.62, 0.66, 0.80) * interarm * 1.2
             + vec3(1.0, 0.80, 0.52) * bulge * 0.55
             + vec3(1.0, 0.32, 0.50) * knots * disc * 4.5;
    col *= 1.0 - dust * 0.85;

    // a speckle of bright stars, kept at least a pixel wide so they don't shimmer
    vec2 g = p * 220.0;
    vec2 cell = floor(g);
    vec2 f = fract(g) - 0.5;
    vec2 off = vec2(hash12(cell + 3.1), hash12(cell + 7.7)) - 0.5;
    float d = length(f - off * 0.6);
    float px = length(fwidth(g));
    float sr = max(px * 0.6, 0.08);
    float star = step(0.8, hash12(cell)) * exp(-d * d / (sr * sr)) * (0.0064 / (sr * sr));
    col += vec3(0.9, 0.93, 1.0) * star * (arms * 1.5 + 0.3) * (disc * 3.0 + 0.15);

    float edge = 1.0 - smoothstep(0.92, 1.0, r);
    col = 1.0 - exp(-col * 1.3);
    fragColor = vec4(col * edge * tint.rgb * tint.a * Fade, 0.0);
}
