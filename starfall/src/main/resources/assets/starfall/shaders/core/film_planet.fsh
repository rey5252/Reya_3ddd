#version 150

// One planet, ray-traced per pixel in view space (the camera at the origin, looking down -Z): Earth with
// continents, oceans, ice, clouds, city lights and a scattering atmosphere; Jupiter with turbulent belts and
// the Great Red Spot; Saturn with its rings, their shadow on the planet and the planet's shadow on them.
// Output is premultiplied alpha; depth is written so geometry drawn later sits in front of or behind it.

uniform mat4 ProjMat;
uniform vec3 Center;
uniform float Radius;
uniform vec3 AxisX;
uniform vec3 AxisY;
uniform vec3 AxisZ;
uniform vec3 SunDir;
uniform float Kind;
uniform float Time;
uniform vec4 Atmo;
uniform vec4 Ring;
uniform float Fade;
uniform vec4 Spot;
uniform vec3 SpotColor;
uniform float Night;

in vec2 ndc;
in vec4 tint;

out vec4 fragColor;

// ---------------------------------------------------------------- noise

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx) * 2.0 - 1.0;
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
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

// fractal noise; lod is how many octaves are worth adding at this distance (fractional, so nothing pops)
float fbm(vec3 p, float lod) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 11; i++) {
        float w = clamp(lod - float(i), 0.0, 1.0);
        if (w <= 0.0) break;
        s += a * w * gnoise(p);
        p = p * 2.03 + vec3(1.31, -0.73, 0.57);
        a *= 0.5;
    }
    return s;
}

// like fbm, but the small scales keep more of their strength: turbulence rather than hills
float rough(vec3 p, float lod) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 11; i++) {
        float w = clamp(lod - float(i), 0.0, 1.0);
        if (w <= 0.0) break;
        s += a * w * gnoise(p);
        p = p * 2.03 + vec3(1.31, -0.73, 0.57);
        a *= 0.62;
    }
    return s;
}

float ridged(vec3 p, float lod) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 8; i++) {
        float w = clamp(lod - float(i), 0.0, 1.0);
        if (w <= 0.0) break;
        float n = 1.0 - abs(gnoise(p) * 2.0);
        s += a * w * n * n;
        p = p * 2.07 + vec3(0.7, 1.9, -1.3);
        a *= 0.5;
    }
    return s;
}

float band(float x, float a, float b, float fw) {
    return smoothstep(a - fw, a + fw, x) * (1.0 - smoothstep(b - fw, b + fw, x));
}

float depthOf(vec3 v) {
    vec4 clip = ProjMat * vec4(v, 1.0);
    return clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
}

// ---------------------------------------------------------------- Earth

float earthHeight(vec3 p, float lod, out vec3 warp) {
    vec3 q = p * 1.55;
    warp = vec3(gnoise(q + vec3(2.1, 7.7, 1.3)), gnoise(q + vec3(9.4, 3.3, 6.6)), gnoise(q + vec3(4.8, 1.2, 8.9)));
    float base = fbm(p * 2.0 + warp * 1.1, lod) + 0.3 * fbm(p * 5.5 + warp * 2.0 + vec3(3.0, 1.0, 7.0), lod - 1.5);
    return base + 0.07 * ridged(p * 7.0 + warp * 1.5, lod - 2.5) * smoothstep(0.0, 0.1, base);
}

vec3 earthLand(vec3 p, float e, vec3 warp, float lod, float sea) {
    float lat = abs(p.y);
    float alt = clamp((e - sea) / 0.25, 0.0, 1.0);
    float moist = fbm(p * 3.4 + warp * 0.7 + vec3(5.0, 1.0, 3.0), lod - 1.0) * 1.6 + 0.5;
    float dry = exp(-pow((lat - 0.40) / 0.12, 2.0));
    float wet = clamp(moist + 0.3 * (1.0 - smoothstep(0.0, 0.25, lat)) - dry * 0.6 - alt * 0.25 + 0.12, 0.0, 1.0);
    float hue = fbm(p * 7.0 + warp * 1.3 + vec3(2.0, 9.0, 4.0), lod - 2.0);
    vec3 desert = mix(vec3(0.78, 0.62, 0.40), vec3(0.66, 0.42, 0.26), smoothstep(-0.2, 0.25, hue));
    vec3 col = mix(desert, vec3(0.50, 0.46, 0.27), smoothstep(0.12, 0.3, wet));
    col = mix(col, mix(vec3(0.20, 0.34, 0.10), vec3(0.30, 0.38, 0.14), smoothstep(-0.2, 0.2, hue)), smoothstep(0.3, 0.5, wet));
    col = mix(col, vec3(0.06, 0.18, 0.06), smoothstep(0.5, 0.78, wet));
    col = mix(col, vec3(0.10, 0.17, 0.11), smoothstep(0.55, 0.7, lat) * smoothstep(0.3, 0.5, wet));
    col = mix(col, vec3(0.42, 0.40, 0.35), smoothstep(0.72, 0.84, lat));
    col = mix(col, vec3(0.34, 0.30, 0.27), smoothstep(0.35, 0.7, alt));
    col = mix(col, vec3(0.92, 0.94, 0.97), smoothstep(0.75, 0.92, alt + lat * 0.25));
    // fields, valleys and ridges up close
    float detail = fbm(p * 38.0 + warp, lod - 4.0);
    return col * (0.84 + 0.34 * (detail + 0.5));
}

float earthClouds(vec3 p, float lod) {
    vec3 q = p * 2.6;
    vec3 w = vec3(gnoise(q + vec3(0.0, Time * 0.05, 0.0)), gnoise(q + vec3(3.1, 1.7, 2.2 - Time * 0.04)),
                  gnoise(q + vec3(6.3, 4.4 + Time * 0.03, 0.9)));
    float c = fbm(p * 4.2 + w * 0.8, lod);
    float wisp = fbm(vec3(p.x * 9.0, p.y * 22.0, p.z * 9.0) + w * 1.5, lod - 1.0);
    float lat = abs(p.y);
    float belt = 0.08 * exp(-lat * lat * 50.0) + 0.1 * exp(-pow((lat - 0.75) / 0.16, 2.0))
               - 0.08 * exp(-pow((lat - 0.40) / 0.12, 2.0));
    float big = smoothstep(-0.02, 0.26, c + belt);
    float fine = smoothstep(0.05, 0.3, wisp + c * 0.6 + belt) * 0.55;
    return clamp(max(big, fine), 0.0, 1.0);
}

vec3 shadeEarth(vec3 pb, vec3 n, vec3 v, float lod, float fp) {
    vec3 warp;
    float sea = 0.03;
    float e = earthHeight(pb, lod, warp);
    float fw = max(fp * 3.0, 0.0008);
    float land = smoothstep(sea - fw, sea + fw, e);
    float lat = abs(pb.y);
    vec3 ocean = mix(vec3(0.003, 0.02, 0.07), vec3(0.02, 0.15, 0.27), smoothstep(sea - 0.14, sea, e));
    vec3 ground = mix(ocean, earthLand(pb, e, warp, lod, sea), land);
    float ice = smoothstep(0.87, 0.9, lat + 0.035 * gnoise(pb * 9.0));
    ground = mix(ground, vec3(0.90, 0.93, 0.97), ice);
    float water = (1.0 - land) * (1.0 - ice);

    float ndl = dot(n, SunDir);
    float diff = clamp(ndl * 1.05 + 0.02, 0.0, 1.0);
    float cloud = earthClouds(pb, lod);
    vec3 sunBody = vec3(dot(SunDir, AxisX), dot(SunDir, AxisY), dot(SunDir, AxisZ));
    float shadow = earthClouds(normalize(pb + sunBody * 0.01), lod - 2.0);
    vec3 lit = ground * diff * (1.0 - 0.55 * shadow) * vec3(1.0, 0.97, 0.93);
    // the sun's glint on open water
    vec3 h = normalize(SunDir + v);
    float nh = max(dot(n, h), 0.0);
    lit += water * (1.0 - cloud) * (pow(nh, 220.0) * 3.0 + pow(nh, 24.0) * 0.08) * smoothstep(0.0, 0.1, ndl)
         * vec3(1.0, 0.9, 0.75);
    // clouds, warm where the sun is low
    float low = smoothstep(0.35, 0.0, ndl) * smoothstep(-0.1, 0.05, ndl);
    vec3 cloudCol = vec3(1.0) * diff * 0.97 + vec3(1.0, 0.5, 0.25) * low * 0.35;
    vec3 col = mix(lit, cloudCol, cloud);
    // cities on the night side, along the coasts most of all
    float cities = smoothstep(0.62, 0.88, fbm(pb * 26.0 + warp * 2.0, lod - 3.0) + 0.5
                 + 0.2 * exp(-pow((e - sea) / 0.04, 2.0)));
    cities *= land * (1.0 - ice) * smoothstep(0.1, -0.12, ndl) * (1.0 - cloud * 0.85);
    col += cities * vec3(1.0, 0.70, 0.36) * 1.4 * Night;
    return col;
}

// ---------------------------------------------------------------- the gas giants

float gasLight(vec3 n, vec3 v) {
    float diff = clamp((dot(n, SunDir) + 0.05) / 1.05, 0.0, 1.0);
    float mu = clamp(dot(n, v), 0.0, 1.0);
    return diff * (0.6 + 0.4 * sqrt(mu));
}

vec3 shadeJupiter(vec3 p, float lod) {
    float lat = p.y;
    // coordinates stretched along the bands, warped twice over so the flow rolls up into eddies
    vec3 q = vec3(p.x * 2.6, p.y * 15.0, p.z * 2.6);
    vec3 w1 = vec3(fbm(q + vec3(0.0, 0.0, Time * 0.02), lod - 2.0),
                   fbm(q + vec3(5.2, 1.3, 2.8), lod - 2.0),
                   fbm(q + vec3(1.7, 9.2, 4.4), lod - 2.0));
    vec3 q2 = q * 1.9 + w1 * 3.2;
    vec3 w2 = vec3(fbm(q2 + vec3(8.3, 2.8, 1.1), lod - 1.0), fbm(q2 + vec3(2.1, 6.6, 3.3), lod - 1.0), 0.0);
    // small swirls riding on the big ones
    vec3 q3 = vec3(p.x * 10.0, p.y * 40.0, p.z * 10.0) + vec3(w2.x, w1.y, w2.y) * 5.0;
    float eddy = rough(q3, lod - 1.0);
    float y = lat + w1.x * 0.05 + w2.x * 0.03 + eddy * 0.01;
    // the band pattern: a value across a palette from dark belts to white zones
    float z = 0.5 + 0.30 * sin(y * 21.0 + 0.5) + 0.16 * sin(y * 9.0 - 0.3) + 0.08 * sin(y * 47.0 + 2.0);
    float shear = 1.0 - abs(sin(y * 21.0 + 0.5));
    z += eddy * 0.6 * (0.3 + 0.7 * shear) + w2.y * 0.3;
    vec3 col = mix(vec3(0.26, 0.13, 0.07), vec3(0.55, 0.28, 0.14), smoothstep(0.1, 0.36, z));
    col = mix(col, vec3(0.76, 0.50, 0.30), smoothstep(0.34, 0.5, z));
    col = mix(col, vec3(0.90, 0.80, 0.64), smoothstep(0.5, 0.66, z));
    col = mix(col, vec3(0.97, 0.93, 0.85), smoothstep(0.78, 0.97, z));
    // the equatorial zone a little ochre, the poles grey-blue and mottled
    col = mix(col, vec3(0.90, 0.72, 0.48), exp(-y * y * 140.0) * 0.4);
    float polar = smoothstep(0.62, 0.92, abs(lat));
    col = mix(col, vec3(0.48, 0.49, 0.54) * (0.8 + 0.4 * (eddy + 0.5)), polar);
    // fine streaks along the flow
    float fil = rough(vec3(p.x * 14.0, p.y * 120.0, p.z * 14.0) + vec3(w2.x, 0.0, w2.y) * 9.0, lod - 0.5);
    float grain = rough(p * 70.0 + vec3(w2.x, eddy, w2.y) * 6.0, lod - 4.0);
    col *= (0.82 + 0.38 * (fil + 0.5)) * (0.9 + 0.25 * grain);
    // blue-grey festoons hanging off the equatorial belt
    float fest = smoothstep(0.2, 0.42, gnoise(vec3(p.x * 11.0, p.y * 36.0, p.z * 11.0) + vec3(w2.x, 0.0, w2.y) * 2.5))
               * exp(-pow((lat - 0.1) / 0.035, 2.0));
    col = mix(col, vec3(0.34, 0.38, 0.50), fest * 0.6);
    // the Great Red Spot: a turning oval with a pale collar
    vec3 gs = normalize(vec3(0.62, -0.37, 0.69));
    vec3 east = normalize(cross(vec3(0.0, 1.0, 0.0), gs));
    vec3 north = cross(gs, east);
    vec3 d = p - gs;
    vec2 o = vec2(dot(d, east) / 0.16, dot(d, north) / 0.085);
    float r = length(o) + (1.0 - smoothstep(0.75, 0.9, dot(p, gs))) * 10.0;
    float ang = 3.0 * exp(-r * 1.2) + Time * 0.4;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 sw = vec2(o.x * ca - o.y * sa, o.x * sa + o.y * ca);
    float swirl = fbm(vec3(sw * 3.0, 3.0), lod - 1.0);
    vec3 spot = mix(vec3(0.62, 0.22, 0.12), vec3(0.88, 0.50, 0.32), clamp(r * 0.7 + swirl * 0.9, 0.0, 1.0));
    col = mix(col, vec3(0.93, 0.86, 0.74), band(r, 1.0, 1.35, 0.12) * 0.35);
    col = mix(col, spot, 1.0 - smoothstep(0.85, 1.05, r));
    // white ovals further south
    for (int k = 0; k < 3; k++) {
        float lon = 1.55 + float(k) * 0.42;
        vec3 c = vec3(cos(lon) * 0.838, -0.545, sin(lon) * 0.838);
        vec3 dd = p - c;
        vec3 e2 = normalize(cross(vec3(0.0, 1.0, 0.0), c));
        float rr = length(vec2(dot(dd, e2) / 0.045, dd.y / 0.028)) + (1.0 - step(0.9, dot(p, c))) * 10.0;
        col = mix(col, vec3(0.97, 0.95, 0.91), (1.0 - smoothstep(0.55, 1.0, rr)) * 0.85);
    }
    // a little more colour and contrast than the raw palette
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, 1.3);
    return pow(max(col, vec3(0.0)), vec3(1.15));
}

vec3 shadeSaturn(vec3 p, float lod) {
    float lat = p.y;
    vec3 q = vec3(p.x * 1.2, p.y * 7.0, p.z * 1.2);
    float t = fbm(q + vec3(Time * 0.01, 0.0, 0.0), lod - 2.0);
    float y = lat + t * 0.02;
    float b = sin(y * 26.0) * 0.55 + sin(y * 11.0 + 0.7) * 0.45;
    vec3 col = mix(vec3(0.70, 0.55, 0.34), vec3(0.96, 0.88, 0.68), smoothstep(-0.55, 0.55, b));
    col = mix(col, vec3(0.86, 0.74, 0.52), exp(-y * y * 60.0) * 0.5);
    col = mix(col, vec3(0.60, 0.62, 0.62), smoothstep(0.78, 0.96, abs(lat)));
    col *= 0.92 + 0.16 * (fbm(vec3(p.x * 4.0, p.y * 40.0, p.z * 4.0) + t * 2.0, lod) + 0.5);
    return col;
}

// Saturn's rings at r planet radii: colour and opacity. fw is how much r changes across one pixel, so the
// fine ringlets fade out instead of shimmering once they are smaller than a pixel.
vec4 ringAt(float r, float fw) {
    if (r < 1.1 || r > 2.32) return vec4(0.0);
    float w = max(fw, 0.002);
    float dRing = band(r, 1.11, 1.235, w) * 0.05;
    float cRing = band(r, 1.24, 1.525, w);
    float bRing = band(r, 1.53, 1.945, w);
    float aRing = band(r, 2.03, 2.265, w);
    float encke = band(r, 2.209, 2.219, w);
    float alpha = dRing + cRing * 0.22 + bRing * (0.72 + 0.22 * smoothstep(1.55, 1.8, r)) + aRing * 0.58 * (1.0 - encke);
    float k1 = clamp(1.0 - fw * 160.0, 0.0, 1.0);
    float k2 = clamp(1.0 - fw * 650.0, 0.0, 1.0);
    float ringlets = 1.0 + k1 * 0.22 * sin(r * 230.0 + sin(r * 37.0) * 3.0)
                   + k2 * 0.36 * (hash11(floor(r * 1400.0)) - 0.5);
    alpha = clamp(alpha * ringlets, 0.0, 0.96);
    vec3 col = mix(vec3(0.56, 0.50, 0.43), vec3(0.90, 0.83, 0.70), clamp(bRing + aRing * 0.6, 0.0, 1.0));
    col = mix(col, vec3(0.70, 0.62, 0.52), cRing * 0.6);
    return vec4(col, alpha);
}

vec3 shadeMoon(vec3 p, float lod) {
    float maria = smoothstep(0.05, 0.25, fbm(p * 1.8 + vec3(4.0, 2.0, 7.0), lod - 3.0));
    vec3 col = mix(vec3(0.64, 0.63, 0.61), vec3(0.33, 0.33, 0.35), maria);
    float craters = ridged(p * 9.0 + vec3(2.0, 5.0, 1.0), lod - 1.0);
    col *= 0.82 + 0.3 * craters;
    return col * (0.9 + 0.2 * (fbm(p * 30.0, lod - 3.0) + 0.5));
}

// ---------------------------------------------------------------- the atmosphere

// Light scattered towards the eye along the ray through the shell (premultiplied), and how much of the
// ground it hides.
vec4 atmosphere(vec3 rd, float tHit, float hit, out float optical) {
    optical = 0.0;
    if (Atmo.a <= 0.0) return vec4(0.0);
    float H = Radius * Atmo.a;
    float Ra = Radius + H;
    vec3 oc = -Center;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - Ra * Ra;
    float h = b * b - c;
    if (h <= 0.0) return vec4(0.0);
    float sq = sqrt(h);
    float t0 = max(-b - sq, 0.0);
    float t1 = -b + sq;
    if (hit > 0.5) t1 = min(t1, tHit);
    if (t1 <= t0) return vec4(0.0);
    float ds = (t1 - t0) / 10.0;
    float scatter = 0.0;
    float sunset = 0.0;
    for (int i = 0; i < 10; i++) {
        vec3 x = rd * (t0 + ds * (float(i) + 0.5)) - Center;
        float r = length(x);
        float alt = clamp((r - Radius) / H, 0.0, 1.0);
        float dens = exp(-alt * 3.2) * (1.0 - alt * alt * 0.5);
        float mu = dot(x / r, SunDir);
        float dl = dens * ds / H;
        optical += dl;
        scatter += dl * smoothstep(-0.22, 0.3, mu);
        sunset += dl * smoothstep(-0.2, 0.02, mu) * smoothstep(0.35, 0.0, mu);
    }
    float phase = 0.8 + 0.6 * pow(max(dot(rd, SunDir), 0.0), 5.0);
    vec3 col = 1.0 - exp(-(Atmo.rgb * scatter * 2.6 + vec3(1.0, 0.42, 0.18) * sunset * 0.25 * Atmo.b) * phase);
    return vec4(col, clamp(max(col.r, max(col.g, col.b)) * 1.15, 0.0, 1.0));
}

// ---------------------------------------------------------------- main

void main() {
    vec3 rd = normalize(vec3(ndc.x / ProjMat[0][0], ndc.y / ProjMat[1][1], -1.0));
    vec3 oc = -Center;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - Radius * Radius);
    float dPerp = sqrt(max(dot(oc, oc) - b * b, 0.0));
    // the hit, or for a ray that misses, the point where it passes closest (keeps the derivatives smooth)
    float tS = h > 0.0 ? -b - sqrt(h) : -b;
    vec3 P = rd * max(tS, 0.0);
    vec3 N = normalize(P - Center);
    vec3 pb = vec3(dot(N, AxisX), dot(N, AxisY), dot(N, AxisZ));
    float fp = length(fwidth(pb));
    float lod = clamp(log2(1.0 / max(fp * 2.4, 1.0e-6)) - 0.5, 1.0, 11.0);
    float cover = clamp((Radius - dPerp) / max(fwidth(dPerp), 1.0e-5) + 0.5, 0.0, 1.0) * step(0.0, tS);
    vec3 V = -rd;

    // the surface is only worked out where the planet is (nothing in here takes derivatives)
    vec3 surf = vec3(0.0);
    if (cover > 0.0) {
        if (Kind < 0.5) {
            surf = shadeEarth(pb, N, V, lod, fp);
        } else if (Kind < 1.5) {
            surf = shadeJupiter(pb, lod) * gasLight(N, V) * 0.9;
        } else if (Kind < 2.5) {
            surf = shadeSaturn(pb, lod) * gasLight(N, V);
        } else {
            surf = shadeMoon(pb, lod) * clamp(dot(N, SunDir) * 1.1, 0.0, 1.0);
        }
    }
    // the mark of a weapon's laser on the ground
    float spotK = SpotColor.r + SpotColor.g + SpotColor.b;
    if (spotK > 0.0) {
        float ang = acos(clamp(dot(pb, Spot.xyz), -1.0, 1.0));
        float s = max(Spot.w, 1.0e-4);
        surf += SpotColor * (exp(-ang * ang / (s * s)) * 1.8 + exp(-ang / (s * 6.0)) * 0.25);
    }
    // the rings' shadow on the planet
    if (Ring.w > 0.5) {
        float sd = dot(SunDir, AxisY);
        float tr = abs(sd) > 1.0e-4 ? dot(Center - P, AxisY) / sd : -1.0;
        if (tr > 0.0) {
            float rr = length(P + SunDir * tr - Center) / Radius;
            surf *= 1.0 - 0.8 * ringAt(rr, 0.004).a;
        }
    }

    float optical;
    vec4 atmo = atmosphere(rd, tS, step(0.5, cover), optical);
    vec3 col = surf * cover * exp(-optical * 0.25) + atmo.rgb;
    float alpha = max(cover, atmo.a);
    // a soft halo past the limb on the lit side, the way a bright planet blooms against space
    if (Atmo.a > 0.0) {
        vec3 rim = normalize(rd * (-b) - Center);
        float lit = smoothstep(-0.3, 0.5, dot(rim, SunDir));
        float beyond = max(dPerp - Radius, 0.0) / (Radius * Atmo.a * 2.2);
        col += Atmo.rgb * exp(-beyond * beyond) * lit * 0.18 * (1.0 - cover);
    }
    float depth = cover > 0.5 ? depthOf(P) : 1.0;

    if (Ring.w > 0.5) {
        float denom = dot(rd, AxisY);
        float tr = abs(denom) > 1.0e-6 ? dot(Center, AxisY) / denom : -1.0;
        vec3 Q = rd * tr;
        float rr = length(Q - Center) / Radius;
        vec4 ring = ringAt(rr, fwidth(rr));
        if (tr > 0.0 && ring.a > 0.0) {
            float sideSun = dot(SunDir, AxisY);
            float sideEye = dot(V, AxisY);
            float lit = sideSun * sideEye > 0.0 ? 1.0 : 0.22 + 0.55 * (1.0 - ring.a);
            vec3 oq = Q - Center;
            float bq = dot(oq, SunDir);
            float dq = sqrt(max(dot(oq, oq) - bq * bq, 0.0));
            float shade = bq < 0.0 ? smoothstep(Radius * 0.97, Radius * 1.02, dq) : 1.0;
            vec3 rc = ring.rgb * (0.03 + lit * shade * clamp(abs(sideSun) * 2.2 + 0.2, 0.0, 1.0));
            if (cover <= 0.0 || tr < tS) {
                col = rc * ring.a + col * (1.0 - ring.a);
                alpha = ring.a + alpha * (1.0 - ring.a);
                if (ring.a > 0.4) depth = depthOf(Q);
            } else {
                col += rc * ring.a * (1.0 - alpha);
                alpha += ring.a * (1.0 - alpha);
            }
        }
    }

    if (alpha < 0.002) {
        discard;
    }
    gl_FragDepth = depth;
    fragColor = vec4(col, alpha) * Fade * tint.a;
}
