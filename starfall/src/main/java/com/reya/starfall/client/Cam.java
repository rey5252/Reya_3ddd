package com.reya.starfall.client;

import org.joml.Vector3f;

/** Where a film's camera is, what it looks at, which way is up, and its lens. */
final class Cam {
    final Vector3f eye, target, up;
    float fov = 50.0F, near = 0.1F, far = 1.0E6F;

    Cam(Vector3f eye, Vector3f target, Vector3f up) {
        this.eye = eye;
        this.target = target;
        this.up = up;
    }

    Cam(Vector3f eye, Vector3f target) {
        this(eye, target, new Vector3f(0.0F, 1.0F, 0.0F));
    }

    Cam lens(float fov, float near, float far) {
        this.fov = fov;
        this.near = near;
        this.far = far;
        return this;
    }

    // ------------------------------------------------------------------ easing and paths

    static float clamp01(float x) {
        return x < 0.0F ? 0.0F : Math.min(x, 1.0F);
    }

    /** 0 before a, 1 after b, smooth in between. */
    static float ramp(float x, float a, float b) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3.0F - 2.0F * t);
    }

    /** Smoother still: starts and stops with no jolt at all. */
    static float smoother(float x, float a, float b) {
        float t = clamp01((x - a) / (b - a));
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    /**
     * A smooth path through key points, one every {@code 1 / (n - 1)} of t: a Catmull-Rom spline, so the camera
     * passes through each point without a corner and without stopping.
     */
    static Vector3f path(float t, Vector3f... keys) {
        int n = keys.length;
        if (n == 1) return new Vector3f(keys[0]);
        float f = clamp01(t) * (n - 1);
        int i = Math.min(n - 2, (int) Math.floor(f));
        float u = f - i;
        Vector3f p0 = keys[Math.max(0, i - 1)], p1 = keys[i], p2 = keys[i + 1], p3 = keys[Math.min(n - 1, i + 2)];
        float u2 = u * u, u3 = u2 * u;
        return new Vector3f(
                cr(p0.x, p1.x, p2.x, p3.x, u, u2, u3),
                cr(p0.y, p1.y, p2.y, p3.y, u, u2, u3),
                cr(p0.z, p1.z, p2.z, p3.z, u, u2, u3));
    }

    private static float cr(float p0, float p1, float p2, float p3, float u, float u2, float u3) {
        return 0.5F * (2.0F * p1 + (-p0 + p2) * u + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * u2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * u3);
    }

    /**
     * A smooth path through points reached at the given (ascending) times: each key's tangent comes from its
     * neighbours and the time between them, so the camera's speed changes smoothly however the keys are spaced.
     */
    static Vector3f keyed(float t, float[] times, Vector3f[] points) {
        int n = times.length;
        if (t <= times[0]) return new Vector3f(points[0]);
        if (t >= times[n - 1]) return new Vector3f(points[n - 1]);
        int i = 0;
        while (i < n - 2 && t > times[i + 1]) i++;
        float dt = times[i + 1] - times[i];
        float u = (t - times[i]) / dt;
        Vector3f m0 = tangent(i, times, points).mul(dt), m1 = tangent(i + 1, times, points).mul(dt);
        float u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        return new Vector3f(points[i]).mul(h00).add(m0.mul(h10)).add(new Vector3f(points[i + 1]).mul(h01)).add(m1.mul(h11));
    }

    private static Vector3f tangent(int i, float[] times, Vector3f[] points) {
        int n = times.length;
        if (i == 0 || i == n - 1) return new Vector3f();
        return new Vector3f(points[i + 1]).sub(points[i - 1]).div(times[i + 1] - times[i - 1]);
    }

    /** A number through key values reached at the given times. */
    static float keyed1(float t, float[] times, float... values) {
        Vector3f[] k = new Vector3f[values.length];
        for (int i = 0; i < values.length; i++) k[i] = new Vector3f(values[i], 0.0F, 0.0F);
        return keyed(t, times, k).x;
    }

    /** A number that moves through key values like {@link #path}, e.g. a distance or an angle. */
    static float path1(float t, float... keys) {
        Vector3f[] k = new Vector3f[keys.length];
        for (int i = 0; i < keys.length; i++) k[i] = new Vector3f(keys[i], 0.0F, 0.0F);
        return path(t, k).x;
    }

    /** Exponential interpolation, for distances that grow by orders of magnitude. */
    static float expLerp(float a, float b, float t) {
        return (float) (a * Math.pow(b / a, t));
    }
}
