package com.reya.starfall;

import net.minecraft.util.Mth;

/**
 * The seven stars of the Big Dipper (J2000 positions), projected gnomonically around Megrez, the star
 * where the handle meets the bowl. x grows to the east and y to the north, in radians on the tangent plane.
 */
public final class BigDipper {
    public static final String[] NAMES = {"Dubhe", "Merak", "Phecda", "Megrez", "Alioth", "Mizar", "Alkaid"};
    private static final double[] RA = {165.932, 165.460, 178.458, 183.856, 193.507, 200.981, 206.885};
    private static final double[] DEC = {61.751, 56.382, 53.695, 57.033, 55.960, 54.925, 49.313};
    /** Apparent magnitudes: a lower number is a brighter star and a wider crater. */
    public static final double[] MAG = {1.79, 2.37, 2.44, 3.31, 1.77, 2.23, 1.86};
    public static final int MEGREZ = 3;
    /** The figure's lines: the bowl, then the handle. */
    public static final int[][] LINES = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {3, 4}, {4, 5}, {5, 6}};

    public static final double[] X = new double[7];
    public static final double[] Y = new double[7];
    /** Largest distance between two stars, so the figure can be scaled to a span. */
    public static final double EXTENT;

    static {
        double a0 = Math.toRadians(RA[MEGREZ]);
        double d0 = Math.toRadians(DEC[MEGREZ]);
        for (int i = 0; i < 7; i++) {
            double a = Math.toRadians(RA[i]);
            double d = Math.toRadians(DEC[i]);
            double c = Math.sin(d0) * Math.sin(d) + Math.cos(d0) * Math.cos(d) * Math.cos(a - a0);
            X[i] = Math.cos(d) * Math.sin(a - a0) / c;
            Y[i] = (Math.cos(d0) * Math.sin(d) - Math.sin(d0) * Math.cos(d) * Math.cos(a - a0)) / c;
        }
        double max = 0;
        for (int i = 0; i < 7; i++) {
            for (int j = i + 1; j < 7; j++) {
                max = Math.max(max, Math.hypot(X[i] - X[j], Y[i] - Y[j]));
            }
        }
        EXTENT = max;
    }

    /** Brightness relative to Megrez (flux ratio), square-rooted: Megrez is 1, the brightest are about 2. */
    public static double size(int star) {
        return Math.pow(10.0D, -0.2D * (MAG[star] - MAG[MEGREZ]));
    }

    /**
     * Where star {@code i} lands, as a block offset from Megrez. The constellation is laid out as it looks
     * in the sky above a viewer facing along {@code yaw}: north away from them, east to their left.
     */
    public static double[] offset(int star, float yaw, double span) {
        double scale = span / EXTENT;
        double east = X[star] * scale;
        double north = Y[star] * scale;
        float rad = yaw * Mth.DEG_TO_RAD;
        // Minecraft's look vector for a yaw: (-sin, 0, cos); its right hand is (-cos, 0, -sin)
        double fx = -Mth.sin(rad), fz = Mth.cos(rad);
        double rx = -fz, rz = fx;
        return new double[]{fx * north - rx * east, fz * north - rz * east};
    }

    private BigDipper() {
    }
}
