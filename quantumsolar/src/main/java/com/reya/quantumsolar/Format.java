package com.reya.quantumsolar;

/** Energy in short: 950, 12.5k, 3.40M, 1.25G. */
public final class Format {
    public static String energy(long fe) {
        if (fe < 10_000L) return Long.toString(fe);
        String[] units = {"k", "M", "G", "T"};
        double v = fe;
        int u = -1;
        while (v >= 1000.0D && u < units.length - 1) {
            v /= 1000.0D;
            u++;
        }
        return (v >= 100.0D ? String.format("%.0f", v) : v >= 10.0D ? String.format("%.1f", v) : String.format("%.2f", v)) + units[u];
    }

    private Format() {
    }
}
