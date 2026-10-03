package com.reya.starfall.client;

import java.util.Locale;
import java.util.Random;

import com.reya.starfall.BigDipper;
import com.reya.starfall.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The film that plays for whoever pressed the button, while the target is being marked. Everything in it is
 * drawn here from code: the sky falling away, the Earth, the planets, the galaxy and the weapon out past it.
 */
public final class Film {
    private static Skill skill;
    private static long start = -1;
    private static BlockPos target = BlockPos.ZERO;
    private static float radius;
    /** J skips the film; the HUD still reports the impact. */
    private static boolean skipped;

    private static final int STARS = 520;
    private static final float[] SX = new float[STARS], SY = new float[STARS], SZ = new float[STARS], SB = new float[STARS];
    private static final int[] SC = new int[STARS];
    private static final int GALAXY = 1700;
    private static final float[] GR = new float[GALAXY], GT = new float[GALAXY], GB = new float[GALAXY];
    private static final int[] GC = new int[GALAXY];

    static {
        Random r = new Random(1997L);
        for (int i = 0; i < STARS; i++) {
            SX[i] = r.nextFloat() * 2 - 1;
            SY[i] = r.nextFloat() * 2 - 1;
            SZ[i] = r.nextFloat();
            SB[i] = 0.3F + r.nextFloat() * 0.7F;
            float k = r.nextFloat();
            SC[i] = k < 0.15F ? 0xFFD8B0 : k < 0.35F ? 0xB8CCFF : 0xFFFFFF;
        }
        for (int i = 0; i < GALAXY; i++) {
            float k = r.nextFloat();
            if (k < 0.22F) {
                // the bulge
                GR[i] = (float) Math.abs(r.nextGaussian()) * 0.11F;
                GT[i] = r.nextFloat() * 6.2832F;
                GC[i] = 0xFFE6C0;
            } else {
                int arm = r.nextInt(2);
                float rad = 0.08F + (float) Math.pow(r.nextFloat(), 0.8D) * 0.92F;
                GR[i] = rad;
                GT[i] = arm * 3.1416F + rad * 6.4F + (float) r.nextGaussian() * (0.28F + 0.2F * rad);
                GC[i] = r.nextFloat() < 0.06F ? 0xFF9AC8 : r.nextFloat() < 0.5F ? 0xC8D8FF : 0xFFF2E0;
            }
            GB[i] = 0.35F + r.nextFloat() * 0.65F;
        }
    }

    static void play(Skill s, long gameTime, BlockPos at, float size) {
        skill = s;
        start = gameTime;
        target = at;
        radius = size;
        skipped = false;
    }

    /** Skips the film that is playing (the impact report still shows). */
    public static void stop() {
        skipped = true;
    }

    static void clear() {
        skill = null;
        start = -1;
        skipped = false;
    }

    /** Game tick (from the start) at which this skill's impact is confirmed, and how long the report stays. */
    private static int impactAt(Skill s) {
        return s == Skill.RAILGUN ? Skill.MARK : s == Skill.GUNGNIR ? Skill.GUNGNIR_IMPACT + Skill.SHOCK_TIME / 2 : Skill.FLARE;
    }

    static float age(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || skill == null) return -1;
        return mc.level.getGameTime() - start + partial;
    }

    public static boolean active() {
        float t = age(0.0F);
        return !skipped && t >= 0 && t < Skill.MARK;
    }

    // ------------------------------------------------------------------ the overlay

    static void render(GuiGraphics g, float partial, int width, int height) {
        float t = age(partial);
        if (t < 0) return;
        int report = impactAt(skill);
        if (t >= report + 80) {
            clear();
            return;
        }
        float s = t / 20.0F;
        if (t < Skill.MARK && !skipped) {
            g.flush();
            Canvas c = new Canvas(g.pose().last().pose(), width, height);
            switch (skill) {
                case RAILGUN -> railgun(c, s);
                case GUNGNIR -> gungnir(c, s);
                case SEVEN_STARS -> sevenStars(c, s, g);
            }
            c.finish();
            hud(g, s, width, height);
            g.flush();
        } else if (t >= report) {
            impact(g, t - report, width, height);
        }
    }

    /** The uplink frame over the film: corner brackets, timers, the lock and the charge. */
    private static void hud(GuiGraphics g, float s, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int color = 0xFF000000 | skill.color;
        frame(g, w, h, color);
        g.drawString(font, Component.translatable("film.starfall.uplink", skill.tag(), skill.title()), 18, 14, color, false);
        g.drawString(font, String.format(Locale.ROOT, "T+%05.2f", s), 18, 25, 0xFFB8B8C4, false);
        float left = Math.max(0.0F, impactAt(skill) / 20.0F - s);
        String impact = Component.translatable("film.starfall.impact_in").getString() + String.format(Locale.ROOT, " T-%05.2f", left);
        g.drawString(font, impact, w - 18 - font.width(impact), 14, color, false);
        Component lock = Component.translatable("film.starfall.lock", target.getX(), target.getY(), target.getZ());
        g.drawString(font, lock, w - 18 - font.width(lock), h - 30, 0xFFB8B8C4, false);
        Component skip = Component.translatable("film.starfall.skip", Keys.FILM.getTranslatedKeyMessage());
        g.drawString(font, skip, w - 18 - font.width(skip), h - 19, 0xFF8A8A96, false);
        Component scene = Component.translatable("film.starfall." + skill.id + "." + scene(s));
        g.drawString(font, scene, 18, h - 19, 0xFFD8D8E0, false);
        float charge = -1;
        if (skill == Skill.RAILGUN && s >= 5.4F && s < 7.0F) charge = Math.min(1.0F, (s - 5.4F) / 1.6F * 1.15F);
        if (skill == Skill.GUNGNIR && s >= 2.2F && s < 5.6F) charge = Math.min(1.0F, (s - 2.2F) / 3.4F);
        if (skill == Skill.SEVEN_STARS && s >= 2.6F && s < 6.5F) charge = Math.min(1.0F, (s - 2.6F) / 3.9F);
        if (charge >= 0) {
            int bw = Math.min(240, w - 80), bx = (w - bw) / 2, by = h - 46;
            Component locked = Component.translatable("film.starfall.locked." + skill.id);
            g.drawString(font, locked, (w - font.width(locked)) / 2, by - 24, 0xFFC8C8D4, false);
            g.drawString(font, Component.translatable("film.starfall.charge_label"), bx, by - 11, color, false);
            String pct = Math.round(charge * 100) + "%";
            g.drawString(font, pct, bx + bw - font.width(pct), by - 11, color, false);
            g.fill(bx, by, bx + bw, by + 1, 0xFF5A5A66);
            g.fill(bx, by - 1, bx + Math.round(bw * charge), by + 2, color);
        }
        if (skill == Skill.GUNGNIR && scene(s) == 2 && lapLabel > 0) {
            Component lap = Component.translatable("film.starfall.lap", lapLabel);
            g.drawString(font, lap, w - 18 - font.width(lap), 25, color, false);
        }
    }

    private static void frame(GuiGraphics g, int w, int h, int color) {
        int m = 10, l = 22;
        g.fill(m, m, m + l, m + 1, color);
        g.fill(m, m, m + 1, m + l, color);
        g.fill(w - m - l, m, w - m, m + 1, color);
        g.fill(w - m - 1, m, w - m, m + l, color);
        g.fill(m, h - m - 1, m + l, h - m, color);
        g.fill(m, h - m - l, m + 1, h - m, color);
        g.fill(w - m - l, h - m - 1, w - m, h - m, color);
        g.fill(w - m - 1, h - m - l, w - m, h - m, color);
    }

    /** After the hit: the frame stays a moment longer and the impact is confirmed. */
    private static void impact(GuiGraphics g, float since, int w, int h) {
        Font font = Minecraft.getInstance().font;
        float in = Math.min(1.0F, since / 6.0F), out = Math.min(1.0F, (80.0F - since) / 12.0F);
        float k = Math.max(0.0F, Math.min(in, out));
        if (k <= 0.02F) return;
        int a = Math.max(4, Math.round(k * 255)) << 24;
        int color = a | skill.color;
        frame(g, w, h, color);
        g.drawString(font, Component.translatable("film.starfall.uplink", skill.tag(), skill.title()), 18, 14, color, false);
        Component done = Component.translatable("film.starfall.confirmed_" + skill.id);
        g.drawString(font, done, w - 18 - font.width(done), 14, color, false);
        Component big = Component.translatable("film.starfall.confirmed_big");
        g.pose().pushPose();
        g.pose().translate(w / 2.0F, h * 0.6F, 0.0F);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font, big, -font.width(big) / 2, 0, color, true);
        g.pose().popPose();
        int height = Minecraft.getInstance().level == null ? 384 : Minecraft.getInstance().level.getHeight();
        Component status = switch (skill) {
            case RAILGUN -> Component.translatable("film.starfall.status.railgun", String.format(Locale.ROOT, "%04d", Math.round(radius * 2)), height);
            case GUNGNIR -> Component.translatable("film.starfall.status.gungnir", String.format(Locale.ROOT, "%04d", Math.round(radius * 2)), height);
            case SEVEN_STARS -> Component.translatable("film.starfall.status.seven_stars");
        };
        g.drawString(font, status, (w - font.width(status)) / 2, Math.round(h * 0.6F) + 24, a | 0xF0F0F4, true);
        Component lock = Component.translatable("film.starfall.lock", target.getX(), target.getY(), target.getZ());
        g.drawString(font, lock, w - 18 - font.width(lock), h - 19, a | 0xB8B8C4, false);
    }

    private static int scene(float s) {
        if (skill == Skill.RAILGUN) return s < 1 ? 0 : s < 2.4F ? 1 : s < 3.8F ? 2 : s < 5.4F ? 3 : s < 7.0F ? 4 : 5;
        if (skill == Skill.GUNGNIR) return s < 1 ? 0 : s < 2.2F ? 1 : s < 5.6F ? 2 : s < 6.6F ? 3 : 4;
        return s < 1 ? 0 : s < 2.4F ? 1 : s < 5.0F ? 2 : s < 6.5F ? 3 : 4;
    }

    // ------------------------------------------------------------------ SS-01

    private static void railgun(Canvas c, float s) {
        if (s < 1.0F) {
            ascent(c, s, ClientStrike.RED);
        } else if (s < 2.4F) {
            float p = (s - 1.0F) / 1.4F;
            space(c, p * 0.5F, 0.0F, 1.0F);
            earthLeaving(c, p, ClientStrike.RED);
        } else if (s < 3.8F) {
            float p = (s - 2.4F) / 1.4F;
            space(c, 0.5F + p * 0.9F, 0.012F, 1.0F);
            c.mode(false);
            planet(c, c.w * 0.5F, c.h * 0.62F, c.h * 0.05F * (1 - p * 0.7F), s, Film::earth, 0x6FA8FF);
            float e = (float) Math.pow(p, 2.2D);
            double r = c.h * (0.04D + 0.9D * e);
            saturn(c, c.w * (0.56D + 0.55D * e), c.h * (0.42D + 0.62D * e), r, s);
            laserUp(c, c.w * 0.5F, c.h * 0.62F, ClientStrike.RED, 0.8F);
        } else if (s < 5.4F) {
            float p = (s - 3.8F) / 1.6F;
            float rush = Math.max(0.0F, 1.0F - p * 2.2F);
            space(c, 1.4F + p * 3.0F, 0.01F + 0.06F * rush, 1.0F + rush * 1.5F);
            double g = c.h * (2.8D - 2.35D * ClientStrike.easeOut(p));
            galaxy(c, c.w * 0.5D, c.h * 0.58D, g, s * 0.12F, Math.min(1.0F, p * 3.0F));
        } else if (s < 7.0F) {
            float p = (s - 5.4F) / 1.6F;
            space(c, 4.4F + p * 0.1F, 0.0F, 1.0F);
            galaxy(c, c.w * 0.2D, c.h * 0.78D, c.h * 0.32D, s * 0.12F, 1.0F);
            railgunCharging(c, s, p);
        } else {
            float p = s - 7.0F;
            tunnel(c, p, ClientStrike.RED, 0xFFD0D0);
            c.mode(false);
            planet(c, c.w * 0.5F, c.h * 0.5F, c.h * 0.015F * Math.exp(p * 3.4F), s, Film::earth, 0x6FA8FF);
            if (p < 0.12F) {
                c.mode(false);
                c.rect(0, 0, c.w, c.h, Fx.argb(0xFFFFFF, 1.0F - p / 0.12F));
            }
            if (p > 0.72F) {
                c.mode(false);
                c.rect(0, 0, c.w, c.h, Fx.argb(0xFFFFFF, (p - 0.72F) / 0.28F));
            }
        }
    }

    private static void railgunCharging(Canvas c, float s, float p) {
        double vx = c.w * 0.58D, vy = c.h * 0.4D;
        double nx = c.w * 0.18D, ny = c.h * 1.35D;
        double spreadNear = c.w * 0.2D, spreadFar = 5.0D;
        double ax = vx - nx, ay = vy - ny;
        double len = Math.sqrt(ax * ax + ay * ay);
        double ux = -ay / len, uy = ax / len;
        float charge = Math.min(1.0F, p * 1.15F);
        c.mode(false);
        for (int side = -1; side <= 1; side += 2) {
            double x0 = nx + ux * spreadNear * side, y0 = ny + uy * spreadNear * side;
            double x1 = vx + ux * spreadFar * side, y1 = vy + uy * spreadFar * side;
            c.taper(x0, y0, c.w * 0.07D, x1, y1, 2.0D, 0xFF262A32, 0xFF181A20);
            c.taper(x0 - ux * c.w * 0.03D * side, y0 - uy * c.w * 0.03D * side, 3.0D, x1, y1, 0.6D, 0xFF8A94A6, 0xFF4A505C);
        }
        // the coils, lighting up from the breech to the muzzle as it charges
        for (int i = 0; i < 10; i++) {
            double f = 1.0D - Math.pow(1.0D - (i + 0.5D) / 10.0D, 1.6D);
            double cx = nx + ax * f, cy = ny + ay * f;
            double rr = spreadNear * 1.35D * (1.0D - f) + spreadFar * 2.5D * f;
            double wid = 9.0D * (1.0D - f) + 1.2D;
            c.mode(false);
            c.ellipse(cx, cy, rr, rr * 0.45D, Math.atan2(uy, ux), wid, 0, Math.PI * 2, 0xFF30343E);
            float lit = Math.max(0.0F, Math.min(1.0F, (charge - i / 10.0F) * 6.0F));
            if (lit > 0) {
                float pulse = 0.75F + 0.25F * (float) Math.sin(s * 30.0F + i);
                c.mode(true);
                c.ellipse(cx, cy, rr, rr * 0.45D, Math.atan2(uy, ux), wid * 0.6D, 0, Math.PI * 2, Fx.argb(0xFF3030, 0.9F * lit * pulse));
                c.ellipse(cx, cy, rr, rr * 0.45D, Math.atan2(uy, ux), wid * 2.2D, 0, Math.PI * 2, Fx.argb(0xFF2020, 0.25F * lit));
            }
        }
        c.mode(true);
        if (p > 0.3F) {
            Random r = new Random((long) (s * 24.0F));
            for (int k = 0; k < 7; k++) {
                double f = 0.1D + r.nextDouble() * 0.8D;
                double cx = nx + ax * f, cy = ny + ay * f;
                double half = spreadNear * (1.0D - f) + spreadFar * f;
                double px = cx - ux * half, py = cy - uy * half;
                for (int seg = 1; seg <= 6; seg++) {
                    double q = seg / 6.0D;
                    double jitter = (r.nextDouble() - 0.5D) * half * 0.35D;
                    double qx = cx + ux * half * (2 * q - 1) + ax / len * jitter, qy = cy + uy * half * (2 * q - 1) + ay / len * jitter;
                    c.line(px, py, qx, qy, 1.4D, Fx.argb(0xFFE0E0, 0.9F), Fx.argb(0xFF6060, 0.8F));
                    px = qx;
                    py = qy;
                }
            }
        }
        double m = 8.0D + 110.0D * charge * charge;
        c.glow(vx, vy, m * 2.0D, Fx.argb(0xFF2020, 0.5F * charge));
        c.glow(vx, vy, m, Fx.argb(0xFFD0D0, 0.9F * charge));
        c.flush();
    }

    // ------------------------------------------------------------------ SS-03

    private static void gungnir(Canvas c, float s) {
        int ember = ClientStrike.EMBER;
        if (s < 1.0F) {
            ascent(c, s, ember);
        } else if (s < 2.2F) {
            float p = (s - 1.0F) / 1.2F;
            space(c, p * 0.6F, 0.0F, 1.0F);
            float pan = (float) Math.max(0.0D, (p - 0.55D) / 0.45D);
            pan = pan * pan * (3 - 2 * pan);
            c.mode(false);
            double ex = c.w * 0.5D - c.w * 1.1D * pan;
            double r = c.h * (1.4D - 1.3D * ClientStrike.easeOut(Math.min(1.0D, p / 0.6D)));
            planet(c, ex, c.h * 0.6D + r * (1 - Math.min(1.0F, p / 0.6F)), r, s, Film::earth, 0x6FA8FF);
            laserUp(c, ex, c.h * 0.6D + r * (1 - Math.min(1.0F, p / 0.6F)) - r, ember, 0.9F);
            c.mode(false);
            planet(c, c.w * (1.25D - 0.95D * pan), c.h * 0.58D, c.h * (0.05D + 0.37D * pan), s * 0.2F, Film::jupiter, 0xE8C8A0);
        } else if (s < 5.6F) {
            float p = (s - 2.2F) / 3.4F;
            space(c, 0.6F, 0.0F, 1.0F);
            c.mode(false);
            planet(c, c.w * 0.3D, c.h * 0.58D, c.h * (0.42D + 0.1D * p), s * 0.2F, Film::jupiter, 0xE8C8A0);
            accelerator(c, s, p);
        } else if (s < 6.6F) {
            float p = (s - 5.6F) / 1.0F;
            space(c, 0.6F + p * 0.6F, 0.02F * p, 1.0F);
            if (p < 0.6F) {
                float q = p / 0.6F;
                c.mode(false);
                planet(c, c.w * (0.3D - 0.2D * q), c.h * 0.58D, c.h * 0.52D * (1 - 0.6D * q), s * 0.2F, Film::jupiter, 0xE8C8A0);
                double[] lp = Canvas.onEllipse(c.w * 0.66D, c.h * 0.47D, c.w * 0.27D, c.h * 0.11D, -0.12D, 7 * Math.PI * 2);
                double tx = c.w * 0.5D, ty = c.h * 0.5D;
                double ex = lp[0] + (tx - lp[0]) * q, ey = lp[1] + (ty - lp[1]) * q;
                double len = 30.0D + q * q * c.w * 1.6D, wid = 3.0D + q * q * q * 110.0D;
                double ang = Math.atan2(ty - lp[1], tx - lp[0]);
                double bx = ex - Math.cos(ang) * len, by = ey - Math.sin(ang) * len;
                c.mode(true);
                c.taper(bx, by, wid * 0.2D, ex, ey, wid * 1.6D, Fx.argb(ember, 0.0F), Fx.argb(ember, 0.7F));
                c.mode(false);
                c.taper(bx, by, wid * 0.1D, ex, ey, wid, 0xFF050506, 0xFF101014);
            } else {
                float q = (p - 0.6F) / 0.4F;
                c.mode(false);
                planet(c, c.w * 0.5D, c.h * 0.5D, c.h * (0.03D + 0.1D * q), s, Film::earth, 0x6FA8FF);
                c.mode(true);
                c.line(c.w * (0.95D - 0.4D * q), c.h * (0.05D + 0.38D * q), c.w * 0.5D, c.h * 0.5D, 2.0D,
                        Fx.argb(ember, 0.9F), Fx.argb(ember, 0.0F));
            }
        } else {
            float p = (s - 6.6F) / 1.4F;
            space(c, 1.2F, 0.0F, 1.0F);
            c.mode(false);
            double r = c.h * 0.13D * Math.exp(p * 3.4D);
            planet(c, c.w * 0.5D, c.h * 0.5D + r * 0.15D, r, s, Film::earth, 0x6FA8FF);
            float sky = Math.max(0.0F, Math.min(1.0F, (p - 0.5F) / 0.25F));
            if (sky > 0) {
                c.mode(false);
                c.gradient(0, 0, c.w, c.h, Fx.argb(0x4A7FD0, sky), Fx.argb(0xA8C8EC, sky));
                c.rect(0, c.h * 0.82D, c.w, c.h, Fx.argb(0x3A5E2A, sky));
                c.mode(true);
                double mx = c.w * 0.5D, my = c.h * 0.86D;
                c.ellipse(mx, my, 40, 8, 0, 2, 0, Math.PI * 2, Fx.argb(ember, sky));
                c.ellipse(mx, my, 70, 14, 0, 1.5, 0, Math.PI * 2, Fx.argb(ember, 0.6F * sky));
                float fall = Math.max(0.0F, Math.min(1.0F, (p - 0.7F) / 0.25F));
                double tip = -c.h * 0.2D + (my + c.h * 0.2D) * fall * fall;
                c.mode(true);
                c.line(mx, tip - c.h * 0.9D, mx, tip, 14.0D, Fx.argb(ember, 0.0F), Fx.argb(ember, 0.6F));
                c.mode(false);
                c.line(mx, tip - c.h * 0.9D, mx, tip, 5.0D, 0xFF08080A, 0xFF0C0C10);
            }
            if (p > 0.9F) {
                c.mode(false);
                c.rect(0, 0, c.w, c.h, Fx.argb(0xFFE6C8, (p - 0.9F) / 0.1F));
            }
        }
    }

    private static void accelerator(Canvas c, float s, float p) {
        int ember = ClientStrike.EMBER;
        double cx = c.w * 0.66D, cy = c.h * 0.47D, rx = c.w * 0.27D, ry = c.h * 0.11D, tilt = -0.12D;
        c.mode(false);
        c.ellipse(cx, cy, rx, ry, tilt, 6.0D, 0, Math.PI * 2, 0xFF2A221E);
        c.mode(true);
        c.ellipse(cx, cy, rx, ry, tilt, 1.6D, 0, Math.PI * 2, Fx.argb(ember, 0.55F + 0.3F * p));
        for (int i = 0; i < 16; i++) {
            double[] n = Canvas.onEllipse(cx, cy, rx, ry, tilt, i * Math.PI / 8);
            c.glow(n[0], n[1], 5.0D + 3.0D * Math.sin(s * 12 + i), Fx.argb(0xFFC080, 0.8F));
        }
        double q = Math.max(0.0D, Math.min(1.0D, (p - 0.08D) / 0.85D));
        double laps = 7.0D * Math.pow(q, 1.7D);
        double a = laps * Math.PI * 2;
        double speed = 7.0D * 1.7D * Math.pow(Math.max(q, 1.0E-3D), 0.7D);
        // the trail of the needle spinning up
        double trail = Math.min(Math.PI * 1.5D, 0.15D + speed * 0.12D);
        int steps = 24;
        for (int k = 0; k < steps; k++) {
            double a0 = a - trail * k / steps, a1 = a - trail * (k + 1) / steps;
            double[] p0 = Canvas.onEllipse(cx, cy, rx, ry, tilt, a0), p1 = Canvas.onEllipse(cx, cy, rx, ry, tilt, a1);
            float fade = 1.0F - (float) k / steps;
            c.line(p0[0], p0[1], p1[0], p1[1], 7.0D * fade + 1.0D, Fx.argb(ember, 0.8F * fade), Fx.argb(ember, 0.8F * fade));
        }
        double[] head = Canvas.onEllipse(cx, cy, rx, ry, tilt, a), back = Canvas.onEllipse(cx, cy, rx, ry, tilt, a - 0.12D);
        c.glow(head[0], head[1], 14.0D, Fx.argb(0xFFD080, 0.9F));
        c.mode(false);
        c.line(back[0], back[1], head[0], head[1], 4.0D, 0xFF060608, 0xFF141418);
        c.flush();
        int lap = Math.min(7, (int) Math.floor(laps) + 1);
        lapLabel = q <= 0 ? -1 : lap;
    }

    /** Lap of the accelerator ring shown in the corner (set while drawing). */
    private static int lapLabel = -1;

    // ------------------------------------------------------------------ SS-04

    private static void sevenStars(Canvas c, float s, GuiGraphics g) {
        int violet = ClientStrike.VIOLET;
        if (s < 1.0F) {
            ascent(c, s, violet);
            return;
        }
        if (s < 2.4F) {
            float p = (s - 1.0F) / 1.4F;
            space(c, p * 1.2F, 0.02F, 1.0F);
            nebula(c, s, 0.6F);
            c.mode(false);
            planet(c, c.w * 0.5D, c.h * (0.7D + 0.4D * p), c.h * 0.12D * (1 - p), s, Film::earth, 0x6FA8FF);
            return;
        }
        space(c, 1.2F, 0.0F, 0.7F);
        nebula(c, s, 1.0F);
        double scale = c.w * 0.62D / BigDipper.EXTENT;
        double zoom = s < 6.5F ? 1.0D + 0.05D * (s - 2.4F) : 1.2D + 1.2D * Math.pow((s - 6.5F) / 1.5F, 2.0D);
        double ox = c.w * 0.5D, oy = c.h * 0.52D;
        double[] px = new double[7], py = new double[7];
        double mx = 0, my = 0;
        for (int i = 0; i < 7; i++) {
            mx += BigDipper.X[i] / 7.0D;
            my += BigDipper.Y[i] / 7.0D;
        }
        for (int i = 0; i < 7; i++) {
            px[i] = ox - (BigDipper.X[i] - mx) * scale * zoom;
            py[i] = oy - (BigDipper.Y[i] - my) * scale * zoom;
        }
        float[] wake = new float[7];
        for (int i = 0; i < 7; i++) {
            float w0 = 2.6F + i * 0.3F;
            wake[i] = Math.max(0.0F, Math.min(1.0F, (s - w0) / 0.25F));
        }
        boolean array = s >= 5.0F;
        float arrayK = array ? Math.min(1.0F, (s - 5.0F) / 0.4F) : 0.0F;
        c.mode(true);
        for (int[] line : BigDipper.LINES) {
            int a = line[0], b = line[1];
            float k = Math.min(wake[a], wake[b]);
            if (k <= 0) continue;
            c.line(px[a], py[a], px[b], py[b], 1.0D + 1.6D * arrayK, Fx.argb(violet, 0.3F * k + 0.5F * arrayK), Fx.argb(violet, 0.3F * k + 0.5F * arrayK));
            if (arrayK > 0) c.line(px[a], py[a], px[b], py[b], 9.0D, Fx.argb(violet, 0.18F * arrayK), Fx.argb(violet, 0.18F * arrayK));
        }
        for (int i = 0; i < 7; i++) {
            float k = wake[i];
            if (k <= 0) {
                c.glow(px[i], py[i], 3.0D, Fx.argb(0xFFFFFF, 0.6F));
                continue;
            }
            double size = BigDipper.size(i);
            double core = 2.0D + size * 1.8D;
            float pulse = 0.85F + 0.15F * (float) Math.sin(s * 9.0F + i);
            c.glow(px[i], py[i], (10.0D + size * 14.0D) * k * pulse, Fx.argb(violet, 0.6F * k));
            c.glow(px[i], py[i], core * 2.2D, Fx.argb(0xFFFFFF, 0.95F * k));
            c.line(px[i] - core * 9 * k, py[i], px[i], py[i], 1.2D, Fx.argb(0xE8D0FF, 0.0F), Fx.argb(0xE8D0FF, 0.8F * k));
            c.line(px[i], py[i], px[i] + core * 9 * k, py[i], 1.2D, Fx.argb(0xE8D0FF, 0.8F * k), Fx.argb(0xE8D0FF, 0.0F));
            c.line(px[i], py[i] - core * 7 * k, px[i], py[i], 1.2D, Fx.argb(0xE8D0FF, 0.0F), Fx.argb(0xE8D0FF, 0.8F * k));
            c.line(px[i], py[i], px[i], py[i] + core * 7 * k, 1.2D, Fx.argb(0xE8D0FF, 0.8F * k), Fx.argb(0xE8D0FF, 0.0F));
            float wakeAge = s - (2.6F + i * 0.3F);
            if (wakeAge >= 0 && wakeAge < 0.8F) {
                double rr = 6.0D + wakeAge * 60.0D;
                c.ellipse(px[i], py[i], rr, rr, 0, 1.5D, 0, Math.PI * 2, Fx.argb(violet, 0.8F * (1 - wakeAge / 0.8F)));
            }
            if (arrayK > 0) {
                // each star becomes a node of the stellar array
                double hr = 14.0D + size * 6.0D + 3.0D * Math.sin(s * 4.0F + i);
                double rot = s * (i % 2 == 0 ? 1.2D : -1.2D);
                for (int e = 0; e < 6; e++) {
                    double a0 = rot + e * Math.PI / 3, a1 = rot + (e + 1) * Math.PI / 3;
                    c.line(px[i] + Math.cos(a0) * hr, py[i] + Math.sin(a0) * hr, px[i] + Math.cos(a1) * hr, py[i] + Math.sin(a1) * hr,
                            1.4D, Fx.argb(0xE8D0FF, 0.85F * arrayK), Fx.argb(0xE8D0FF, 0.85F * arrayK));
                }
            }
        }
        if (s >= 6.5F) {
            // they fire, one after another, down at the Earth
            float p = (s - 6.5F) / 1.5F;
            for (int i = 0; i < 7; i++) {
                float f = (p - i * 0.09F) / 0.25F;
                if (f <= 0) continue;
                float k = Math.min(1.0F, f);
                double tx = c.w * 0.5D, ty = c.h * 1.2D;
                double ex = px[i] + (tx - px[i]) * k, ey = py[i] + (ty - py[i]) * k;
                c.line(px[i], py[i], ex, ey, 3.0D, Fx.argb(0xFFFFFF, 0.9F), Fx.argb(violet, 0.9F));
                c.line(px[i], py[i], ex, ey, 12.0D, Fx.argb(violet, 0.3F), Fx.argb(violet, 0.1F));
                if (f < 1.3F) c.glow(px[i], py[i], 40.0D * (1.3F - f), Fx.argb(0xFFFFFF, 0.6F));
            }
            if (p > 0.8F) {
                c.mode(false);
                c.rect(0, 0, c.w, c.h, Fx.argb(0xF0E4FF, (p - 0.8F) / 0.2F));
            }
        }
        c.flush();
        // names under the stars that have woken
        Font font = Minecraft.getInstance().font;
        c.finish();
        if (s < 6.5F) {
            for (int i = 0; i < 7; i++) {
                if (wake[i] <= 0.3F) continue;
                int alpha = Math.max(5, Math.min(255, (int) (wake[i] * 220)));
                String name = BigDipper.NAMES[i];
                g.drawString(font, name, (int) (px[i] - font.width(name) / 2.0D), (int) (py[i] + 10 + BigDipper.size(i) * 4),
                        alpha << 24 | 0xD8C8FF, false);
            }
        }
    }

    private static void nebula(Canvas c, float s, float k) {
        c.mode(true);
        Random r = new Random(77L);
        for (int i = 0; i < 9; i++) {
            double x = c.w * r.nextDouble(), y = c.h * r.nextDouble();
            double rr = c.h * (0.25D + r.nextDouble() * 0.35D);
            x += Math.sin(s * 0.3D + i) * 20.0D;
            int col = i % 3 == 0 ? 0x6A3AB0 : i % 3 == 1 ? 0x3A2A80 : 0x8A3A90;
            c.glow(x, y, rr, Fx.argb(col, 0.18F * k));
        }
    }

    // ------------------------------------------------------------------ shared pieces

    /** Up the laser from the ground: the sky darkens, the land drops away, the first stars show. */
    private static void ascent(Canvas c, float p, int laser) {
        c.mode(false);
        int top = Fx.mix(0xFF6FA4E0, 0xFF02030A, p * 1.1F);
        int bottom = Fx.mix(0xFFCFE2F4, 0xFF0A1020, p);
        c.gradient(0, 0, c.w, c.h, top, bottom);
        double ground = c.h * (0.72D + p * p * 0.45D);
        c.gradient(0, ground, c.w, c.h + 2, 0xFF4E7A38, 0xFF2A3E20);
        c.rect(0, ground, c.w, ground + 2, 0xFF7FA860);
        c.flush();
        if (p > 0.35F) space(c, p * 0.3F, 0.0F, (p - 0.35F) / 0.65F, false);
        laserUp(c, c.w * 0.5D, ground, laser, 1.0F);
    }

    private static void laserUp(Canvas c, double x, double y, int color, float k) {
        c.mode(true);
        c.line(x, y, x, -10, 16.0D, Fx.argb(color, 0.25F * k), Fx.argb(color, 0.1F * k));
        c.line(x, y, x, -10, 4.0D, Fx.argb(color, 0.9F * k), Fx.argb(color, 0.6F * k));
        c.line(x, y, x, -10, 1.5D, Fx.argb(0xFFFFFF, 0.9F * k), Fx.argb(0xFFFFFF, 0.5F * k));
        c.glow(x, y, 18.0D, Fx.argb(color, 0.9F * k));
        c.flush();
    }

    private static void space(Canvas c, float travel, float streak, float brightness) {
        space(c, travel, streak, brightness, true);
    }

    /** Black space and stars rushing past: {@code travel} is how far we've flown, {@code streak} the blur. */
    private static void space(Canvas c, float travel, float streak, float brightness, boolean background) {
        if (background) {
            c.mode(false);
            c.rect(0, 0, c.w, c.h, 0xFF02030A);
        }
        c.mode(true);
        double cx = c.w * 0.5D, cy = c.h * 0.5D;
        for (int i = 0; i < STARS; i++) {
            float z = SZ[i] - travel;
            z -= (float) Math.floor(z);
            if (z < 0.03F) continue;
            double k = 0.16D / z;
            double x = cx + SX[i] * c.w * 0.6D * k, y = cy + SY[i] * c.h * 0.6D * k;
            if (x < -20 || y < -20 || x > c.w + 20 || y > c.h + 20) continue;
            float a = Math.min(1.0F, SB[i] * Math.min(1.0F, (1.0F - z) * 3.0F) * brightness);
            double size = Math.min(3.5D, 0.7D + 0.25D * k) * (0.6D + SB[i] * 0.6D);
            if (streak > 0) {
                double zp = Math.min(0.999D, z + streak);
                double kp = 0.16D / zp;
                double xp = cx + SX[i] * c.w * 0.6D * kp, yp = cy + SY[i] * c.h * 0.6D * kp;
                c.line(xp, yp, x, y, size, Fx.argb(SC[i], 0.0F), Fx.argb(SC[i], a));
            } else {
                c.rect(x - size * 0.5D, y - size * 0.5D, x + size * 0.5D, y + size * 0.5D, Fx.argb(SC[i], a));
            }
        }
        c.flush();
    }

    /** The beam home: a tunnel of light rushing outward. */
    private static void tunnel(Canvas c, float p, int color, int hot) {
        c.mode(false);
        c.rect(0, 0, c.w, c.h, 0xFF050106);
        space(c, 6.0F + p * 4.0F, 0.12F, 1.0F, false);
        c.mode(true);
        double cx = c.w * 0.5D, cy = c.h * 0.5D;
        for (int k = 0; k < 8; k++) {
            double q = (p * 3.0D + k / 8.0D) % 1.0D;
            double rr = q * q * c.w * 0.9D;
            c.ellipse(cx, cy, rr, rr * 0.85D, 0, 3.0D + q * 10.0D, 0, Math.PI * 2, Fx.argb(color, 0.6F * (float) (1.0D - q)));
        }
        c.glow(cx, cy, c.h * 0.4D, Fx.argb(hot, 0.5F));
        c.flush();
    }

    private static void earthLeaving(Canvas c, float p, int laser) {
        double r = c.h * (1.9D - 1.82D * ClientStrike.easeOut(p));
        double ex = c.w * 0.5D;
        double ey = c.h * 0.55D + r - (c.h * 0.55D + r - c.h * 0.6D) * p;
        c.mode(false);
        planet(c, ex, ey, r, p * 0.6F, Film::earth, 0x6FA8FF);
        laserUp(c, ex, ey - r, laser, 1.0F);
    }

    private static void planet(Canvas c, double x, double y, double r, float spin, Canvas.Surface surface, int atmosphere) {
        if (!c.glowing()) {
            c.sphere(x, y, r, spin, 0.4D, -0.55D, -0.35D, 0.75D, surface, atmosphere);
        }
        c.mode(true);
        c.glow(x, y, r * 1.12D, Fx.argb(atmosphere, 0.18F));
        c.mode(false);
    }

    private static void saturn(Canvas c, double x, double y, double r, float s) {
        double tilt = -0.33D;
        int[] bands = {0x6A5A44, 0xB89A70, 0xD8C090, 0xC8AE80, 0x000000, 0xBFA478, 0xA88E66, 0x8A7658};
        double[] radii = {1.25D, 1.4D, 1.55D, 1.72D, 1.9D, 1.97D, 2.12D, 2.28D};
        // the far half of the rings goes behind the planet
        c.mode(false);
        for (int i = 0; i < bands.length; i++) {
            if (bands[i] == 0) continue;
            c.ellipse(x, y, r * radii[i], r * radii[i] * 0.22D, tilt, r * 0.1D, Math.PI, Math.PI * 2, Fx.argb(bands[i], 0.85F));
        }
        c.sphere(x, y, r, s * 0.3F, tilt, -0.55D, -0.35D, 0.75D, Film::saturnSurface, 0xE8D0A0);
        c.mode(false);
        for (int i = 0; i < bands.length; i++) {
            if (bands[i] == 0) continue;
            c.ellipse(x, y, r * radii[i], r * radii[i] * 0.22D, tilt, r * 0.1D, 0, Math.PI, Fx.argb(bands[i], 0.9F));
        }
        c.flush();
    }

    private static void galaxy(Canvas c, double x, double y, double size, float spin, float alpha) {
        if (alpha <= 0) return;
        c.mode(true);
        c.glow(x, y, size * 0.9D, Fx.argb(0x8090D0, 0.12F * alpha));
        c.glow(x, y, size * 0.32D, Fx.argb(0xFFE6C8, 0.5F * alpha));
        double squash = 0.42D;
        for (int i = 0; i < GALAXY; i++) {
            double a = GT[i] + spin;
            double px = x + Math.cos(a) * GR[i] * size, py = y + Math.sin(a) * GR[i] * size * squash;
            if (px < -4 || py < -4 || px > c.w + 4 || py > c.h + 4) continue;
            double sz = Math.max(0.8D, Math.min(4.0D, size / c.h * 1.4D)) * (0.6D + GB[i] * 0.6D);
            c.rect(px - sz * 0.5D, py - sz * 0.5D, px + sz * 0.5D, py + sz * 0.5D, Fx.argb(GC[i], GB[i] * alpha * 0.85F));
        }
        c.flush();
    }

    // ------------------------------------------------------------------ planet surfaces

    private static int earth(double lat, double lon, double x, double y, double z) {
        if (Math.abs(lat) > 1.22D) return 0xF2F6FA;
        double land = fbm(x * 1.9D + 3.1D, y * 1.9D, z * 1.9D, 4);
        int base;
        if (land > 0.53D) {
            double dry = fbm(x * 3.5D, y * 3.5D + 7.0D, z * 3.5D, 2);
            base = Math.abs(lat) < 0.45D && dry > 0.55D ? 0xC8A86A : dry > 0.5D ? 0x5E8A3A : 0x3C6E2E;
        } else {
            base = land > 0.48D ? 0x2E6EB0 : 0x1A4A8C;
        }
        double cloud = fbm(x * 3.0D + 11.0D, y * 4.5D, z * 3.0D - 5.0D, 4);
        if (cloud > 0.56D) return Fx.mix(0xFF000000 | base, 0xFFFFFFFF, (float) Math.min(1.0D, (cloud - 0.56D) * 5.0D)) & 0xFFFFFF;
        return base;
    }

    private static int jupiter(double lat, double lon, double x, double y, double z) {
        double turb = fbm(x * 4.0D, y * 1.5D, z * 4.0D, 3) - 0.5D;
        double b = Math.sin(lat * 11.0D + turb * 2.4D);
        int col = b > 0.55D ? 0xF0E2CA : b > 0.0D ? 0xD9B88F : b > -0.55D ? 0xB07A50 : 0x9C6B4A;
        // the Great Red Spot
        double dl = (lat + 0.38D) / 0.1D, dn = Math.atan2(Math.sin(lon - 1.2D), Math.cos(lon - 1.2D)) / 0.24D;
        if (dl * dl + dn * dn < 1.0D) col = Fx.mix(0xFFC0583A, 0xFFE0906A, (float) (dl * dl + dn * dn)) & 0xFFFFFF;
        return col;
    }

    private static int saturnSurface(double lat, double lon, double x, double y, double z) {
        double b = Math.sin(lat * 9.0D + (fbm(x * 3, y, z * 3, 2) - 0.5D) * 1.5D);
        return b > 0.4D ? 0xEAD6A8 : b > -0.3D ? 0xD8BE8A : 0xC4A06A;
    }

    private static double fbm(double x, double y, double z, int octaves) {
        double sum = 0, amp = 0.5D, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y, z);
            norm += amp;
            x *= 2.03D;
            y *= 2.03D;
            z *= 2.03D;
            amp *= 0.5D;
        }
        return sum / norm;
    }

    private static double noise(double x, double y, double z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        double c000 = h(x0, y0, z0), c100 = h(x0 + 1, y0, z0), c010 = h(x0, y0 + 1, z0), c110 = h(x0 + 1, y0 + 1, z0);
        double c001 = h(x0, y0, z0 + 1), c101 = h(x0 + 1, y0, z0 + 1), c011 = h(x0, y0 + 1, z0 + 1), c111 = h(x0 + 1, y0 + 1, z0 + 1);
        double x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx;
        double x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
        double y0v = x00 + (x10 - x00) * fy, y1v = x01 + (x11 - x01) * fy;
        return y0v + (y1v - y0v) * fz;
    }

    private static double h(int x, int y, int z) {
        long n = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        n = (n << 13) ^ n;
        n = n * (n * n * 15731L + 789221L) + 1376312589L;
        return ((n >>> 8) & 0xFFFFFFL) / (double) 0x1000000L;
    }

    private Film() {
    }
}
