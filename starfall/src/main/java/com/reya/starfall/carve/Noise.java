package com.reya.starfall.carve;

/** Small deterministic noise, so carving a column twice gives the same blocks. */
public final class Noise {
    public static double hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Value noise with a cell size of {@code cell} blocks, smoothly blended. */
    public static double smooth(double x, double z, double cell, int salt) {
        double fx = x / cell, fz = z / cell;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
        double tx = fade(fx - x0), tz = fade(fz - z0);
        double a = hash(x0, z0, salt), b = hash(x0 + 1, z0, salt);
        double c = hash(x0, z0 + 1, salt), d = hash(x0 + 1, z0 + 1, salt);
        return lerp(tz, lerp(tx, a, b), lerp(tx, c, d));
    }

    /** Patchy noise for block palettes: big patches with a grainy edge. */
    public static double patch(int x, int z, int salt) {
        return smooth(x, z, 7.0D, salt) * 0.75D + hash(x, z, salt + 1) * 0.25D;
    }

    private static double fade(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    private Noise() {
    }
}
