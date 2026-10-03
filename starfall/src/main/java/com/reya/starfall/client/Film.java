package com.reya.starfall.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.reya.starfall.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/**
 * The film that plays for whoever pressed the button while the target is being marked. Everything in it is drawn
 * here from code: the sky falling away, the planets, the galaxy and the weapon out past it. The shots live in
 * {@link FilmRailgun}, {@link FilmGungnir} and {@link FilmSevenStars}; this class keeps the state, the HUD over
 * them and the opening they share.
 */
public final class Film {
    private static Skill skill;
    private static long start = -1;
    private static BlockPos target = BlockPos.ZERO;
    private static float radius;
    /** J skips the film; the HUD still reports the impact. */
    private static boolean skipped;
    /** The showcase recorder can hold the film at a moment of its choosing. */
    private static Skill heldSkill;
    private static float heldAt = -1.0F;

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

    /** Shows one moment of a film (seconds from its start) until {@link #release()}; for the showcase recorder. */
    public static void hold(Skill s, float seconds) {
        heldSkill = s;
        heldAt = seconds;
    }

    public static void release() {
        heldSkill = null;
        heldAt = -1.0F;
    }

    /** Game tick (from the start) at which this skill's impact is confirmed. */
    private static int impactAt(Skill s) {
        return s == Skill.RAILGUN ? Skill.MARK : s == Skill.GUNGNIR ? Skill.GUNGNIR_IMPACT + Skill.SHOCK_TIME / 2 : Skill.FLARE;
    }

    static float age(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || skill == null) return -1;
        return mc.level.getGameTime() - start + partial;
    }

    public static boolean active() {
        if (heldSkill != null) return true;
        float t = age(0.0F);
        return !skipped && t >= 0 && t < Skill.MARK;
    }

    // ------------------------------------------------------------------ the overlay

    static void render(GuiGraphics g, float partial, int width, int height) {
        if (heldSkill != null) {
            film(g, heldSkill, heldAt, width, height);
            return;
        }
        float t = age(partial);
        if (t < 0) return;
        int report = impactAt(skill);
        if (t >= report + 80) {
            clear();
            return;
        }
        if (t < Skill.MARK && !skipped) {
            film(g, skill, t / 20.0F, width, height);
        } else if (t >= report) {
            impact(g, t - report, width, height);
        }
    }

    private static void film(GuiGraphics g, Skill which, float s, int width, int height) {
        g.flush();
        LABELS.clear();
        title = null;
        lapLabel = -1;
        charge = -1.0F;
        boolean offscreen = FilmTarget.begin();
        Canvas c = new Canvas(g.pose().last().pose(), width, height);
        switch (which) {
            case RAILGUN -> FilmRailgun.draw(c, s);
            case GUNGNIR -> FilmGungnir.draw(c, s);
            case SEVEN_STARS -> FilmSevenStars.draw(c, s);
        }
        c.finish();
        if (offscreen) FilmTarget.end(g);
        hud(g, which, s, width, height);
        g.flush();
    }

    // ------------------------------------------------------------------ labels, set while drawing

    private record Label(float x, float y, float box, Component title, Component sub, int color, float alpha) {
    }

    private static final List<Label> LABELS = new ArrayList<>();
    private static Component title, subtitle;
    private static float titleAlpha;
    /** Lap of the accelerator ring shown in the corner, and the weapon's charge (0..1), set while drawing. */
    static int lapLabel = -1;
    static float charge = -1.0F;

    /**
     * Brackets around a thing in the shot with its name and a line under it, the way a tracking HUD tags things.
     * {@code box} is the bracket's half-size in GUI units.
     */
    static void label(Scene3D scene, Vector3f world, float box, String key, int color, float alpha) {
        if (alpha <= 0.02F) return;
        float[] p = scene.project(world);
        if (p == null || p[0] < -40 || p[1] < -40 || p[0] > scene.width + 40 || p[1] > scene.height + 40) return;
        LABELS.add(new Label(p[0], p[1], box, Component.translatable("film.starfall.label." + key),
                Component.translatable("film.starfall.label." + key + ".sub"), color, alpha));
    }

    /** The same with the name given as it is (a star's name). */
    static void labelText(Scene3D scene, Vector3f world, float box, String name, Component sub, int color, float alpha) {
        if (alpha <= 0.02F) return;
        float[] p = scene.project(world);
        if (p == null || p[0] < -40 || p[1] < -40 || p[0] > scene.width + 40 || p[1] > scene.height + 40) return;
        LABELS.add(new Label(p[0], p[1], box, Component.literal(name), sub, color, alpha));
    }

    /** The big title over a shot. */
    static void title(String key, float alpha) {
        if (alpha <= 0.02F) return;
        title = Component.translatable("film.starfall.title." + key);
        subtitle = Component.translatable("film.starfall.title." + key + ".sub");
        titleAlpha = alpha;
    }

    // ------------------------------------------------------------------ the HUD

    /** The uplink frame over the film: corner brackets, timers, the lock, labels and the charge. */
    private static void hud(GuiGraphics g, Skill which, float s, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int color = 0xFF000000 | which.color;
        for (Label l : LABELS) {
            int a = Math.max(4, Math.round(l.alpha * 255)) << 24;
            int x = Math.round(l.x), y = Math.round(l.y), b = Math.round(l.box);
            corners(g, x - b, y - b, x + b, y + b, Math.max(3, b / 2), a | (l.color & 0xFFFFFF));
            g.drawString(font, l.title, x + b + 4, y - b, a | (l.color & 0xFFFFFF), false);
            g.drawString(font, l.sub, x + b + 4, y - b + 10, a | 0xB8C0C8, false);
        }
        if (title != null) {
            int a = Math.max(4, Math.round(titleAlpha * 255)) << 24;
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h * 0.42F, 0.0F);
            g.pose().scale(3.0F, 3.0F, 1.0F);
            g.drawString(font, title, -font.width(title) / 2, 0, a | (which.color & 0xFFFFFF), true);
            g.pose().popPose();
            g.drawString(font, subtitle, (w - font.width(subtitle)) / 2, Math.round(h * 0.42F) + 32, a | 0xE0E0E8, true);
        }
        frame(g, w, h, color);
        g.drawString(font, Component.translatable("film.starfall.uplink", which.tag(), which.title()), 18, 14, color, false);
        g.drawString(font, String.format(Locale.ROOT, "T+%05.2f", s), 18, 25, 0xFFB8B8C4, false);
        float left = Math.max(0.0F, impactAt(which) / 20.0F - s);
        String impact = Component.translatable("film.starfall.impact_in").getString() + String.format(Locale.ROOT, " T-%05.2f", left);
        g.drawString(font, impact, w - 18 - font.width(impact), 14, color, false);
        Component lock = Component.translatable("film.starfall.lock", target.getX(), target.getY(), target.getZ());
        g.drawString(font, lock, w - 18 - font.width(lock), h - 30, 0xFFB8B8C4, false);
        Component skip = Component.translatable("film.starfall.skip", Keys.FILM.getTranslatedKeyMessage());
        g.drawString(font, skip, w - 18 - font.width(skip), h - 19, 0xFF8A8A96, false);
        Component scene = Component.translatable("film.starfall." + which.id + "." + scene(which, s));
        g.drawString(font, scene, 18, h - 19, 0xFFD8D8E0, false);
        if (charge >= 0) {
            int bw = Math.min(240, w - 80), bx = (w - bw) / 2, by = h - 46;
            Component locked = Component.translatable("film.starfall.locked." + which.id);
            g.drawString(font, locked, (w - font.width(locked)) / 2, by - 24, 0xFFC8C8D4, false);
            g.drawString(font, Component.translatable("film.starfall.charge_label"), bx, by - 11, color, false);
            String pct = Math.round(charge * 100) + "%";
            g.drawString(font, pct, bx + bw - font.width(pct), by - 11, color, false);
            g.fill(bx, by, bx + bw, by + 1, 0xFF5A5A66);
            g.fill(bx, by - 1, bx + Math.round(bw * charge), by + 2, color);
        }
        if (lapLabel > 0) {
            Component lap = Component.translatable("film.starfall.lap", lapLabel);
            g.drawString(font, lap, w - 18 - font.width(lap), 25, color, false);
        }
    }

    private static void corners(GuiGraphics g, int x0, int y0, int x1, int y1, int l, int color) {
        g.fill(x0, y0, x0 + l, y0 + 1, color);
        g.fill(x0, y0, x0 + 1, y0 + l, color);
        g.fill(x1 - l, y0, x1, y0 + 1, color);
        g.fill(x1 - 1, y0, x1, y0 + l, color);
        g.fill(x0, y1 - 1, x0 + l, y1, color);
        g.fill(x0, y1 - l, x0 + 1, y1, color);
        g.fill(x1 - l, y1 - 1, x1, y1, color);
        g.fill(x1 - 1, y1 - l, x1, y1, color);
    }

    private static void frame(GuiGraphics g, int w, int h, int color) {
        int m = 10, l = 22;
        corners(g, m, m, w - m, h - m, l, color);
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

    /** Which caption is up at s seconds. */
    private static int scene(Skill which, float s) {
        if (which == Skill.RAILGUN) {
            return s < 1.2F ? 0 : s < 2.6F ? 1 : s < 4.2F ? 2 : s < 5.7F ? 3 : s < 7.4F ? 4 : s < 9.0F ? 5 : s < 11.0F ? 6 : 7;
        }
        if (which == Skill.GUNGNIR) return s < 1.2F ? 0 : s < 3.3F ? 1 : s < 8.0F ? 2 : s < 9.2F ? 3 : 4;
        return s < 1.2F ? 0 : s < 2.6F ? 1 : s < 6.6F ? 2 : s < 9.2F ? 3 : 4;
    }

    // ------------------------------------------------------------------ the opening: up from the target

    static final float EARTH = 100.0F;
    /** The target's direction from the Earth's centre, and the sun by day and by night. */
    static final Vector3f TARGET = new Vector3f(0.15F, 0.55F, 0.82F).normalize();
    static final Vector3f SUN_DAY = new Vector3f(-0.35F, 0.45F, 0.82F).normalize();
    static final Vector3f SUN_NIGHT = new Vector3f(-0.30F, -0.55F, -0.78F).normalize();
    static final Vector3f EAST = new Vector3f(0.0F, 1.0F, 0.0F).cross(TARGET).normalize();
    static final Vector3f NORTH = new Vector3f(TARGET).cross(EAST).normalize();

    static Vector3f onEarth(float up) {
        return new Vector3f(TARGET).mul(EARTH + up);
    }

    /** A camera above the target: {@code alt} up, hanging off to the side of the laser; {@code look} turns it from up the laser (0) to back at the Earth (1). */
    static Cam above(float alt, float look, float side) {
        Vector3f eye = onEarth(alt).add(new Vector3f(EAST).mul(side * (0.35F * alt + 0.25F))).sub(new Vector3f(NORTH).mul(0.45F + 0.15F * alt));
        Vector3f up0 = new Vector3f(NORTH).mul(0.82F).add(new Vector3f(TARGET).mul(0.57F));
        Vector3f lookUp = new Vector3f(eye).add(new Vector3f(up0).mul(10.0F + alt));
        Vector3f target = Cam.lerp(lookUp, new Vector3f(), look);
        Vector3f up = Cam.lerp(TARGET, NORTH, look).normalize();
        float fov = Cam.lerp(58.0F, 44.0F, look);
        return new Cam(eye, target, up).lens(fov, Math.max(0.005F, alt * 0.02F), 1.0E6F);
    }

    /** The Earth for the opening, with the laser's spot on the target. */
    static Scene3D.Planet earth(boolean night, int laser, float laserK, float time) {
        Scene3D.Planet p = new Scene3D.Planet();
        p.kind = Scene3D.Planet.EARTH;
        p.radius = EARTH;
        p.tilt = 0.41F;
        p.spin = 1.1F;
        p.sun.set(night ? SUN_NIGHT : SUN_DAY);
        p.atmosphere = 0x4D8CFF;
        p.atmosphereHeight = 0.035F;
        p.time = time;
        p.night = night ? 1.0F : 0.6F;
        p.spot = new Vector3f(TARGET);
        p.spotSize = 0.006F;
        p.spotColor = laser;
        p.spotStrength = laserK;
        return p;
    }

    /** The laser from the target straight up into space, a hot core in a soft glow. */
    static void laser(Soft light, Vector3f from, Vector3f dir, int color, float k, float glowWidth) {
        if (k <= 0.0F) return;
        Vector3f to = new Vector3f(dir).normalize().mul(1.0E5F).add(from);
        light.beam(from, to, glowWidth, glowWidth * 0.5F, Fx.argb(color, 0.35F * k), Fx.argb(color, 0.0F));
        light.line(from, to, 0.006F, 0.004F, Fx.argb(color, 0.9F * k), Fx.argb(color, 0.2F * k));
        light.line(from, to, 0.0022F, 0.0016F, Fx.argb(0xFFFFFF, 0.9F * k), Fx.argb(0xFFE0E0, 0.3F * k));
    }

    /** The whole opening shot: up from the ground until the Earth hangs below, {@code look} as in {@link #above}. */
    static Scene3D opening(Canvas c, float alt, float look, boolean night, int laser, float time, float fade, Scene3D.Sky sky) {
        Cam cam = above(alt, look, 1.0F);
        Scene3D s = new Scene3D(c.w, c.h, cam);
        s.fade = fade;
        sky.stars = Math.min(1.0F, 0.15F + alt / 6.0F) * (night ? 1.0F : 0.9F);
        s.sky(sky);
        s.planet(earth(night, laser, 1.0F, time));
        Soft light = s.soft(true);
        laser(light, onEarth(0.0F), TARGET, laser, 1.0F, 0.02F + alt * 0.012F);
        light.glow(onEarth(0.02F), 0.15F + alt * 0.02F, Fx.argb(laser, 0.9F), true);
        light.end();
        return s;
    }

    private Film() {
    }
}
