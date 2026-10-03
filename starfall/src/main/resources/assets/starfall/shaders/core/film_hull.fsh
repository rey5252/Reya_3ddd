#version 150

// Lit metal for the film's machines. The vertex colour's alpha picks the material:
//   0.90-1.00 painted armour plates (panel seams, vents, uneven paint)
//   0.75-0.90 bare dark metal (rails, girders, pipes)
//   0.60-0.75 radiator panels (ribbed, in frames)
//   0.45-0.60 rows of lit windows
//   0.25-0.45 lights that are always on (neon trims, lamps)
//   0.00-0.25 lights that come on with the charge, each at its own point (alpha / 0.25 of the way)
// UV0 is the surface position in world units, for the patterns. Light: a sun, a fill from the other side, and
// the charge's glow as a point light.

uniform vec3 SunDir;
uniform vec3 SunColor;
uniform vec3 FillColor;
uniform vec3 GlowPos;
uniform vec3 GlowColor;
uniform float GlowRange;
uniform float Charge;
uniform float Time;

in vec3 vPos;
in vec3 vNormal;
in vec2 vUV;
in vec4 vColor;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(-vPos);
    if (dot(N, V) < 0.0) N = -N;
    float m = vColor.a;
    vec3 albedo = vColor.rgb;
    vec3 emit = vec3(0.0);
    float shine = 0.35;
    float hard = 36.0;

    if (m >= 0.9) {
        vec2 g = vUV / vec2(2.4, 1.2);
        vec2 fw = fwidth(g);
        vec2 cell = floor(g);
        float h = hash12(cell);
        vec2 f = fract(g);
        float split = step(0.65, h);
        f.x = mix(f.x, fract(g.x * 2.0), split);
        cell.x = mix(cell.x, floor(g.x * 2.0) + 1000.0, split);
        fw.x *= 1.0 + split;
        vec2 ed = min(f, 1.0 - f) / max(fw, vec2(1.0e-5));
        float seam = 1.0 - smoothstep(0.6, 1.6, min(ed.x, ed.y));
        float vis = 1.0 - smoothstep(0.12, 0.4, max(fw.x, fw.y));
        albedo *= mix(1.0, 0.86 + 0.28 * hash12(cell + 17.0), vis);
        float vent = step(0.82, hash12(cell + 5.0));
        vec2 fc = abs(f - 0.5);
        float inset = step(fc.x, 0.32) * step(fc.y, 0.26);
        float ribs = 0.5 + 0.5 * sin(f.y * 42.0);
        albedo *= 1.0 - vent * inset * (0.35 + 0.3 * ribs) * vis;
        albedo *= 1.0 - seam * 0.6 * vis;
        shine = 0.45;
        hard = 42.0;
    } else if (m >= 0.75) {
        albedo *= 0.92 + 0.08 * sin(vUV.x * 55.0 + sin(vUV.y * 3.0));
        shine = 0.7;
        hard = 70.0;
    } else if (m >= 0.6) {
        vec2 g = vUV / vec2(1.6, 1.6);
        vec2 fw = fwidth(g);
        vec2 f = fract(g);
        vec2 ed = min(f, 1.0 - f) / max(fw, vec2(1.0e-5));
        float line = 1.0 - smoothstep(0.5, 1.5, min(ed.x, ed.y));
        float vis = 1.0 - smoothstep(0.15, 0.5, max(fw.x, fw.y));
        float cellTint = 0.85 + 0.3 * hash12(floor(g) + 41.0);
        albedo *= mix(1.0, cellTint, vis) * (1.0 - 0.65 * line * vis);
        shine = 1.1;
        hard = 120.0;
    } else if (m >= 0.45) {
        vec2 g = vUV / vec2(0.9, 0.7);
        vec2 cell = floor(g);
        vec2 f = fract(g);
        float win = step(abs(f.x - 0.5), 0.3) * step(abs(f.y - 0.5), 0.24);
        float on = step(0.35, hash12(cell + 3.0));
        emit = vec3(1.0, 0.93, 0.78) * win * on * 1.7;
        albedo = vec3(0.10, 0.10, 0.11) * (1.0 - win);
    } else if (m >= 0.25) {
        emit = vColor.rgb * 2.2;
        albedo = vColor.rgb * 0.1;
    } else {
        float th = m / 0.25;
        float lit = smoothstep(th - 0.03, th + 0.05, Charge);
        float flick = 0.85 + 0.15 * sin(Time * 37.0 + th * 23.0);
        emit = vColor.rgb * lit * flick * (2.0 + 2.0 * Charge);
        albedo = vec3(0.07, 0.07, 0.08);
    }

    vec3 col = vec3(0.0);
    float ndl = max(dot(N, SunDir), 0.0);
    vec3 H = normalize(SunDir + V);
    col += albedo * SunColor * ndl + SunColor * pow(max(dot(N, H), 0.0), hard) * shine * ndl;
    col += albedo * FillColor * (0.3 + 0.7 * max(dot(N, -SunDir), 0.0));
    vec3 toGlow = GlowPos - vPos;
    float dg = length(toGlow);
    vec3 Lg = toGlow / max(dg, 1.0e-4);
    float fall = 1.0 / (1.0 + dg * dg / max(GlowRange * GlowRange, 1.0e-4));
    fall *= fall;
    float ndg = max(dot(N, Lg), 0.0);
    col += (albedo * ndg + pow(max(dot(N, normalize(Lg + V)), 0.0), hard) * shine * ndg) * GlowColor * fall;
    float fres = pow(1.0 - max(dot(N, V), 0.0), 5.0);
    col += FillColor * fres * 0.6 * shine;
    col += emit;
    fragColor = vec4(col, 1.0);
}
