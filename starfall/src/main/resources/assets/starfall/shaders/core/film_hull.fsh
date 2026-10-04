#version 150

// Lit metal for the film's machines. The vertex colour's alpha picks the material:
//   0.90-1.00 painted armour plates (panel seams, vents, uneven paint)
//   0.75-0.90 bare dark metal (rails, girders, pipes)
//   0.60-0.75 radiator panels (ribbed, in frames)
//   0.45-0.60 rows of lit windows
//   0.25-0.45 lights that are always on (neon trims, lamps)
//   0.00-0.25 lights that come on with the charge, each at its own point (alpha / 0.25 of the way)
// UV0 is the surface position in world units, for the patterns. Light: a sun, a fill from the other side, the
// charge's glow as a point light, and one big bright thing nearby (the galaxy ahead, Jupiter under the ring) that the
// metal mirrors, sharp on smooth parts and spread out on rough ones. Highlights are GGX, with Fresnel and the
// split-sum approximation for what the surroundings reflect.

uniform vec3 SunDir;
uniform vec3 SunColor;
uniform vec3 FillColor;
uniform vec3 GlowPos;
uniform vec3 GlowColor;
uniform float GlowRange;
uniform float Charge;
uniform float Time;
uniform vec3 EnvPos;
uniform float EnvRadius;
uniform vec3 EnvColor;
uniform float EnvLit;

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

const float PI = 3.14159265;

// GGX: the spread of the microfacets, and the shadowing between them (Smith, Schlick's form)
float ggx(float nh, float a) {
    float a2 = a * a;
    float d = nh * nh * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d);
}

float smith(float nv, float nl, float rough) {
    float k = (rough + 1.0) * (rough + 1.0) / 8.0;
    return nv / (nv * (1.0 - k) + k) * nl / (nl * (1.0 - k) + k);
}

vec3 schlick(vec3 f0, float vh) {
    return f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
}

// what a light does to the surface: a diffuse part and a GGX highlight
vec3 shade(vec3 N, vec3 V, vec3 L, vec3 radiance, vec3 diffuse, vec3 f0, float rough) {
    float nl = max(dot(N, L), 0.0);
    if (nl <= 0.0) return vec3(0.0);
    vec3 H = normalize(L + V);
    float nv = max(dot(N, V), 1.0e-3);
    float a = rough * rough;
    vec3 F = schlick(f0, max(dot(V, H), 0.0));
    vec3 spec = ggx(max(dot(N, H), 0.0), a) * smith(nv, nl, rough) * F / (4.0 * nv * nl + 1.0e-4);
    return (diffuse * (1.0 - F) + spec) * radiance * nl;
}

// the reflected light of the surroundings, already averaged over the highlight (Karis' fit for mobile)
vec2 envBRDF(float rough, float nv) {
    vec4 r = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
    float a004 = min(r.x * r.x, exp2(-9.28 * nv)) * r.x + r.y;
    return vec2(-1.04, 1.04) * a004 + r.zw;
}

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(-vPos);
    if (dot(N, V) < 0.0) N = -N;
    float m = vColor.a;
    vec3 albedo = vColor.rgb;
    vec3 emit = vec3(0.0);
    float rough = 0.5;
    float metal = 0.0;

    if (m >= 0.9) {
        // gunmetal paint, darker than it looks in the vertex colours
        albedo *= 0.7;
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
        // some plates newer and glossier than others; seams and vents dull
        rough = mix(0.5, 0.3 + 0.3 * hash12(cell + 29.0), vis) + 0.25 * seam * vis;
        metal = 0.15;
    } else if (m >= 0.75) {
        float brush = sin(vUV.x * 55.0 + sin(vUV.y * 3.0));
        albedo *= 0.92 + 0.08 * brush;
        rough = 0.3 + 0.04 * brush;
        metal = 0.6;
    } else if (m >= 0.6) {
        vec2 g = vUV / vec2(1.6, 1.6);
        vec2 fw = fwidth(g);
        vec2 f = fract(g);
        vec2 ed = min(f, 1.0 - f) / max(fw, vec2(1.0e-5));
        float line = 1.0 - smoothstep(0.5, 1.5, min(ed.x, ed.y));
        float vis = 1.0 - smoothstep(0.15, 0.5, max(fw.x, fw.y));
        float cellTint = 0.85 + 0.3 * hash12(floor(g) + 41.0);
        albedo *= mix(1.0, cellTint, vis) * (1.0 - 0.65 * line * vis);
        rough = 0.2 + 0.3 * line * vis;
        metal = 0.45;
    } else if (m >= 0.45) {
        vec2 g = vUV / vec2(0.9, 0.7);
        vec2 cell = floor(g);
        vec2 f = fract(g);
        float win = step(abs(f.x - 0.5), 0.3) * step(abs(f.y - 0.5), 0.24);
        float on = step(0.35, hash12(cell + 3.0));
        emit = vec3(1.0, 0.93, 0.78) * win * on * 1.7;
        albedo = vec3(0.10, 0.10, 0.11) * (1.0 - win);
        rough = mix(0.45, 0.06, win);
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

    vec3 f0 = mix(vec3(0.04), max(albedo * 1.6, vec3(0.04)), metal);
    vec3 diffuse = albedo * (1.0 - metal);
    float nv = max(dot(N, V), 1.0e-3);
    vec3 col = shade(N, V, SunDir, SunColor, diffuse, f0, rough);
    col += diffuse * FillColor * (0.3 + 0.7 * max(dot(N, -SunDir), 0.0));
    vec3 toGlow = GlowPos - vPos;
    float dg = length(toGlow);
    float fall = 1.0 / (1.0 + dg * dg / max(GlowRange * GlowRange, 1.0e-4));
    col += shade(N, V, toGlow / max(dg, 1.0e-4), GlowColor * fall * fall, diffuse, f0, max(rough, 0.12));

    // the surroundings: the space around is dark, but the one big bright thing in it is mirrored
    vec3 R = reflect(-V, N);
    vec2 ab = envBRDF(rough, nv);
    vec3 specWeight = f0 * ab.x + ab.y;
    // the faint light all round (nebulae, distant stars), brighter towards the sun's side
    col += FillColor * (1.1 + 0.9 * max(dot(R, SunDir), 0.0)) * specWeight;
    if (EnvRadius > 0.0) {
        vec3 toEnv = EnvPos - vPos;
        float de = length(toEnv);
        vec3 Le = toEnv / max(de, 1.0e-4);
        float sinT = clamp(EnvRadius / max(de, 1.0e-4), 0.0, 0.999);
        float cosT = sqrt(1.0 - sinT * sinT);
        // a planet only shines on its sunlit side: how much of the face turned to us is lit
        float lit = mix(1.0, clamp(0.5 + 0.6 * dot(-Le, SunDir), 0.0, 1.0), EnvLit);
        vec3 radiance = EnvColor * lit;
        // the disc's own size, widened by the roughness
        float s0 = 2.0 / max(1.0 - cosT, 1.0e-4);
        float a = rough * rough;
        float sr = 2.0 / max(a * a, 1.0e-4);
        float sharp = 1.0 / (1.0 / s0 + 1.0 / sr);
        col += radiance * exp((dot(R, Le) - 1.0) * sharp) * (sharp / s0) * specWeight;
        col += diffuse * radiance * sinT * sinT * max(dot(N, Le), 0.0);
    }
    // a soft shoulder, so sunlit plates keep their seams instead of burning out
    col = 1.0 - exp(-col * 1.2);
    col += emit * 0.6;
    fragColor = vec4(col, 1.0);
}
