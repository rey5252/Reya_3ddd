package com.reya.starfall.client;

import com.reya.starfall.BigDipper;
import com.reya.starfall.Skill;

/** Small looping previews of each skill, drawn into a box: used by the skill menu's cards and the HUD. */
final class Previews {
    /** Draws the preview of {@code skill} into (x, y, w, h); {@code time} is in seconds. Clip it with a scissor. */
    static void draw(Canvas c, Skill skill, float time, double x, double y, double w, double h) {
        switch (skill) {
            case RAILGUN -> railgun(c, time, x, y, w, h);
            case GUNGNIR -> gungnir(c, time, x, y, w, h);
            case SEVEN_STARS -> sevenStars(c, time, x, y, w, h);
        }
        c.flush();
    }

    private static void stars(Canvas c, float time, double x, double y, double w, double h, int seed, int count) {
        for (int i = 0; i < count; i++) {
            double sx = x + w * frac(Math.sin(i * 12.9898D + seed) * 43758.5453D);
            double sy = y + h * frac(Math.sin(i * 78.233D + seed) * 12345.678D);
            float tw = 0.45F + 0.55F * (float) Math.abs(Math.sin(time * 2.0D + i * 1.7D));
            double s = (i % 5 == 0) ? 1.2D : 0.7D;
            c.rect(sx, sy, sx + s, sy + s, Fx.argb(0xFFFFFF, 0.8F * tw));
        }
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static void railgun(Canvas c, float time, double x, double y, double w, double h) {
        int red = ClientStrike.RED;
        c.mode(false);
        c.gradient(x, y, x + w, y + h, 0xFF06060E, 0xFF180810);
        stars(c, time, x, y, w, h * 0.75D, 3, 26);
        double ground = y + h * 0.78D;
        c.gradient(x, ground, x + w, y + h, 0xFF26301E, 0xFF141A10);
        double cx = x + w * 0.5D;
        float p = (time % 3.2F) / 3.2F;
        c.mode(true);
        if (p < 0.45F) {
            // the red laser marks the ground and the charge builds
            float k = p / 0.45F;
            c.line(cx, y, cx, ground, 1.0D + k, Fx.argb(red, 0.9F), Fx.argb(red, 0.9F));
            c.ellipse(cx, ground + 1, 6 + k * 10, 2 + k * 2, 0, 0.8D, 0, Math.PI * 2, Fx.argb(red, 0.8F));
            c.glow(cx, ground, 4 + k * 6, Fx.argb(red, 0.8F));
        } else {
            float k = p < 0.75F ? (p - 0.45F) / 0.3F : 1.0F;
            float fade = p < 0.75F ? 1.0F : 1.0F - (p - 0.75F) / 0.25F;
            double half = w * 0.18D * Math.min(1.0F, k * 3.0F);
            c.mode(false);
            // the column of the world the beam took
            c.rect(cx - half, ground, cx + half, y + h, 0xFF000000);
            c.mode(true);
            c.rect(cx - half * 1.3D, y, cx + half * 1.3D, y + h, Fx.argb(red, 0.35F * fade));
            c.rect(cx - half, y, cx + half, y + h, Fx.argb(0xFF8080, 0.5F * fade));
            c.rect(cx - half * 0.45D, y, cx + half * 0.45D, y + h, Fx.argb(0xFFFFFF, 0.7F * fade));
            c.glow(cx, ground, half * 3.0D, Fx.argb(red, 0.5F * fade));
            if (fade < 1.0F) c.line(cx - half, ground, cx + half, ground, 1.2D, Fx.argb(red, 0.9F), Fx.argb(red, 0.9F));
        }
    }

    private static void gungnir(Canvas c, float time, double x, double y, double w, double h) {
        int ember = ClientStrike.EMBER;
        c.mode(false);
        c.gradient(x, y, x + w, y + h, 0xFF05060C, 0xFF0C0806);
        stars(c, time, x, y, w, h, 9, 22);
        double r = h * 0.36D;
        double jx = x + w * 0.28D, jy = y + h * 0.56D;
        c.mode(false);
        c.sphere(jx, jy, r, time * 0.2D, 0.3D, -0.55D, -0.35D, 0.75D,
                (lat, lon, sx, sy, sz) -> Math.sin(lat * 11.0D + Math.sin(lon * 3.0D) * 0.4D) > 0.2D ? 0xD9B88F : 0xA8744C, 0xE8C8A0);
        double ex = x + w * 0.62D, ey = y + h * 0.48D, rx = w * 0.3D, ry = h * 0.16D, tilt = -0.15D;
        c.mode(true);
        c.ellipse(ex, ey, rx, ry, tilt, 1.0D, 0, Math.PI * 2, Fx.argb(ember, 0.75F));
        float p = (time % 3.0F) / 3.0F;
        if (p < 0.78F) {
            double laps = 7.0D * Math.pow(p / 0.78D, 1.7D);
            double a = laps * Math.PI * 2;
            for (int k = 0; k < 10; k++) {
                double[] p0 = Canvas.onEllipse(ex, ey, rx, ry, tilt, a - k * 0.12D);
                double[] p1 = Canvas.onEllipse(ex, ey, rx, ry, tilt, a - (k + 1) * 0.12D);
                c.line(p0[0], p0[1], p1[0], p1[1], 2.2D * (1 - k / 10.0D), Fx.argb(ember, 0.9F * (1 - k / 10.0F)), Fx.argb(ember, 0.9F * (1 - k / 10.0F)));
            }
            double[] head = Canvas.onEllipse(ex, ey, rx, ry, tilt, a);
            c.glow(head[0], head[1], 5.0D, Fx.argb(0xFFD080, 0.95F));
        } else {
            // flung at the Earth
            float k = (p - 0.78F) / 0.22F;
            double[] start = Canvas.onEllipse(ex, ey, rx, ry, tilt, 0);
            double hx = start[0] + (x + w - start[0]) * k, hy = start[1] + (y + h - start[1]) * k;
            c.line(start[0], start[1], hx, hy, 2.0D, Fx.argb(ember, 0.0F), Fx.argb(ember, 0.95F));
            c.glow(hx, hy, 6.0D, Fx.argb(0xFFE0A0, 0.95F));
        }
    }

    private static void sevenStars(Canvas c, float time, double x, double y, double w, double h) {
        int violet = ClientStrike.VIOLET;
        c.mode(false);
        c.gradient(x, y, x + w, y + h, 0xFF0A0614, 0xFF140A22);
        stars(c, time, x, y, w, h, 17, 24);
        double scale = w * 0.78D / BigDipper.EXTENT;
        double mx = 0, my = 0;
        for (int i = 0; i < 7; i++) {
            mx += BigDipper.X[i] / 7.0D;
            my += BigDipper.Y[i] / 7.0D;
        }
        double[] px = new double[7], py = new double[7];
        for (int i = 0; i < 7; i++) {
            px[i] = x + w * 0.5D - (BigDipper.X[i] - mx) * scale;
            py[i] = y + h * 0.42D - (BigDipper.Y[i] - my) * scale;
        }
        c.mode(true);
        for (int[] l : BigDipper.LINES) {
            c.line(px[l[0]], py[l[0]], px[l[1]], py[l[1]], 0.8D, Fx.argb(violet, 0.6F), Fx.argb(violet, 0.6F));
        }
        float cycle = time % 4.2F;
        int falling = (int) (cycle / 0.6F);
        for (int i = 0; i < 7; i++) {
            double size = BigDipper.size(i);
            float pulse = 0.8F + 0.2F * (float) Math.sin(time * 5.0D + i);
            c.glow(px[i], py[i], (2.5D + size * 2.5D) * pulse, Fx.argb(0xE8D0FF, 0.95F));
            if (i == falling) {
                // this star's shooting star comes down
                float k = (cycle - i * 0.6F) / 0.6F;
                double fy = py[i] + (y + h - py[i]) * k;
                c.line(px[i], py[i], px[i], fy, 1.5D, Fx.argb(violet, 0.0F), Fx.argb(0xFFFFFF, 0.95F));
                c.glow(px[i], fy, 3.5D, Fx.argb(0xFFFFFF, 0.9F));
                if (k > 0.85F) c.ellipse(px[i], y + h - 2, 8 * size, 2, 0, 1.0D, 0, Math.PI * 2, Fx.argb(violet, 0.9F));
            }
        }
    }

    private Previews() {
    }
}
