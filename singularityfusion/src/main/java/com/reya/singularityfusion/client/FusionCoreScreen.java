package com.reya.singularityfusion.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.FusionGeometry;
import com.reya.singularityfusion.block.FusionStatus;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import com.reya.singularityfusion.gui.Layouts;
import com.reya.singularityfusion.menu.FusionCoreMenu;
import com.reya.singularityfusion.network.Net;
import com.reya.singularityfusion.network.StartFusionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The fusion core's screen: a window onto space in a frame of dark metal and gold. Through the window, among drifting
 * nebulae, twinkling and shooting stars and far galaxies, the singularity as it hangs over the core (as big as the core
 * is full, its disk turning, collapsing as a fusion ends), planets orbiting it lit by its disk, and streams of matter
 * curving into it from the pylons' portholes down the window's sides. To the left a tube of violet plasma (the energy),
 * to the right one of gold (the fusion's progress); under the singularity the catalyst, the start orb and the output
 * in their portholes, and under them a hologram of what the core is doing.
 *
 * <p>As the screen opens the window opens like an iris, the portholes pop in, the gems round the frame light one after
 * another and the hologram types itself out; a gleam runs along the frame now and then. In a fusion the pylons' items
 * fly into the singularity one after another, the catalyst before them; at its end a flash and a shock wave, and the
 * result comes down out of the singularity into the output.
 */
public class FusionCoreScreen extends AbstractContainerScreen<FusionCoreMenu> {
    private static final int TEXT = 0xE6E0F2, TEXT_DIM = 0xA99FC8;
    private static final int TEX = Layouts.WIDGETS_TEX;
    /** The singularity: its shadow's radius when the core is full (pixels), its middle. */
    private static final float HOLE_R = 13.0F, HX = Layouts.HOLE[0], HY = Layouts.HOLE[1];
    /** How far in front of the slots' items the glass over them is drawn (they are drawn 250 in front, and a little deep). */
    private static final float OVER_ITEMS = 300.0F;

    /** A planet on its orbit round the singularity: the orbit's half width and height, its tilt, its period (seconds). */
    private record Orbit(int[] body, float a, float b, float tilt, float period, float size, float phase, float ar, float ag, float ab, boolean moon) {
        float[] at(float angle) {
            float x = a * Mth.cos(angle), y = b * Mth.sin(angle), c = Mth.cos(tilt), s = Mth.sin(tilt);
            return new float[] {HX + x * c - y * s, HY + x * s + y * c};
        }
    }

    private static final Orbit[] ORBITS = {new Orbit(Layouts.P_LAVA, 47.0F, 14.0F, -0.1F, 19.0F, 7.0F, 0.6F, 1.0F, 0.42F, 0.18F, false),
            new Orbit(Layouts.P_ICE, 63.0F, 19.0F, 0.15F, 31.0F, 10.0F, 2.4F, 0.55F, 0.8F, 1.0F, false),
            new Orbit(Layouts.P_OCEAN, 77.0F, 24.0F, -0.2F, 46.0F, 13.0F, 4.3F, 0.4F, 0.7F, 1.0F, true)};

    private long openedAt, lastNanos, orbChangedAt, statusChangedAt, pressedAt, energyRoseAt;
    private float shownCharge, shownEnergy, shownProgress, spin, ringTurn, hover, charging;
    /** Clocks for what moves at speeds that change (so that it never jumps): the plasma's flows (pixels), the gems' wave, the streams. */
    private float flowEnergy, flowProgress, gemPhase, streamClock;
    private long lastEnergy = -1L;
    private int orbState = -1, lastOrbState = -1;
    @Nullable
    private FusionStatus shownStatus, lastStatus;

    public FusionCoreScreen(FusionCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = Layouts.W;
        imageHeight = Layouts.H;
        inventoryLabelX = Layouts.INV_X;
        inventoryLabelY = Layouts.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        if (openedAt == 0L) {
            openedAt = System.nanoTime();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.5F));
        }
    }

    // ------------------------------------------------------------------ time

    /** Seconds since the screen opened. */
    private float sinceOpen() {
        return (System.nanoTime() - openedAt) / 1.0E9F;
    }

    private static float since(long nanos) {
        return (System.nanoTime() - nanos) / 1.0E9F;
    }

    /** Seconds on a clock that runs on round an hour (for what moves for ever). */
    private static float clock() {
        return (Util.getMillis() % 3_600_000L) / 1000.0F;
    }

    /** Ticks since a moment in the core's world (a fusion's end, or its stop), or -1 if it was long ago or never. */
    private static float ticksSince(long at, float partialTick) {
        Level level = Minecraft.getInstance().level;
        if (level == null || at == Long.MIN_VALUE) return -1.0F;
        long ticks = level.getGameTime() - at;
        return ticks < 0L || ticks > 200L ? -1.0F : ticks + partialTick;
    }

    private static float sinceDone(@Nullable FusionCoreBlockEntity core, float partialTick) {
        return core == null ? -1.0F : ticksSince(core.doneAt, partialTick);
    }

    private static float approach(float from, float to, float dt, float rate) {
        return from + (to - from) * (1.0F - (float) Math.exp(-dt * rate));
    }

    /** Eases out with a little overshoot, for things popping in. */
    private static float pop(float t) {
        if (t <= 0.0F) return 0.0F;
        if (t >= 1.0F) return 1.0F;
        float u = t - 1.0F;
        return 1.0F + 2.70158F * u * u * u + 1.70158F * u * u;
    }

    private static float easeOut(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    /** How far a pylon's porthole has popped in as the screen opens: down each column, the right a moment after the left. */
    private float portPop(int frame, float open) {
        return pop((open - 0.22F - 0.035F * (frame % 6) - (frame >= 6 ? 0.015F : 0.0F)) / 0.35F);
    }

    /** What is shown catches up with the core smoothly. */
    private void step(@Nullable FusionCoreBlockEntity core, boolean fusing, float progress, float partialTick, int mouseX, int mouseY) {
        long now = System.nanoTime();
        boolean first = lastNanos == 0L;
        float dt = first ? 0.0F : Mth.clamp((now - lastNanos) / 1.0E9F, 0.0F, 0.25F);
        lastNanos = now;
        float charge = core == null ? 0.0F : core.charge();
        float energy = core == null ? 0.0F : (float) Mth.clamp(core.energy() / (double) Math.max(1L, core.capacity()), 0.0D, 1.0D);
        shownCharge = first ? charge : approach(shownCharge, charge, dt, 2.0F);
        shownEnergy = approach(shownEnergy, sinceOpen() < 0.3F ? 0.0F : energy, dt, 3.5F);
        float done = sinceDone(core, partialTick);
        shownProgress = fusing ? progress : approach(shownProgress, done >= 0.0F && done < 18.0F ? 1.0F : 0.0F, dt, 4.0F);
        spin = (spin + dt * (0.05F + 0.12F * shownCharge + (fusing ? 0.25F + 0.7F * FusionGeometry.collapse(progress) : 0.0F))) % 8.0F;
        // the energy rising (it comes in a few ticks at a time): the plasma flows faster
        long stored = core == null ? 0L : core.energy();
        if (lastEnergy >= 0L && stored > lastEnergy) energyRoseAt = now;
        lastEnergy = stored;
        charging = approach(charging, since(energyRoseAt) < 0.6F ? 1.0F : 0.0F, dt, 3.0F);
        flowEnergy = (flowEnergy + dt * (6.0F + 12.0F * charging)) % 6400.0F;
        flowProgress = (flowProgress + dt * (fusing ? 16.0F : 6.0F)) % 6400.0F;
        gemPhase = (gemPhase + dt * (fusing ? 5.0F : 1.5F)) % Cosmos2D.TWO_PI;
        streamClock = (streamClock + dt * (fusing ? 0.7F : 0.25F)) % 1.0F;
        int state = fusing ? 3 : core == null || core.status() != FusionStatus.READY ? 2 : overStart(mouseX, mouseY) ? 1 : 0;
        if (state != orbState) {
            lastOrbState = orbState;
            orbState = state;
            orbChangedAt = now;
        }
        ringTurn = (ringTurn + dt * (state == 3 ? 3.4F : state == 2 ? 0.08F : 0.4F)) % Cosmos2D.TWO_PI;
        hover = approach(hover, state == 1 ? 1.0F : 0.0F, dt, 14.0F);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        tooltips(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        FusionCoreBlockEntity core = menu.core();
        float time = clock(), open = sinceOpen();
        boolean fusing = core != null && core.fusing();
        float progress = core == null ? 0.0F : core.progress(partialTick);
        step(core, fusing, progress, partialTick, mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0.0F);
        window(g, core, time, open, fusing, progress, partialTick);
        Cosmos2D.paint(g, Cosmos2D.FRAME);
        Cosmos2D.quad(Layouts.W * 0.5F, Layouts.H * 0.5F, Layouts.W * 0.5F, Layouts.H * 0.5F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        Cosmos2D.end();
        gleam(g, time);
        gems(g, core, time, open, fusing, partialTick);
        float cost = -1.0F;
        boolean enough = true;
        if (core != null && core.cost() > 0L) {
            cost = (float) Mth.clamp(core.cost() / (double) Math.max(1L, core.capacity()), 0.0D, 1.0D);
            enough = core.energy() >= core.cost();
        }
        tube(g, Layouts.ENERGY, Layouts.W_PLASMA_VIOLET, shownEnergy, time, flowEnergy, 0.72F, 0.48F, 1.0F, cost, enough, 0);
        tube(g, Layouts.PROGRESS, Layouts.W_PLASMA_GOLD, shownProgress, time, flowProgress, 1.0F, 0.76F, 0.4F, -1.0F, true, 1);
        hologram(g, core, time, open);
        portholes(g, core, time, open, fusing, progress);
        orb(g, time, open);
        flights(g, core, time, fusing, progress);
        preview(g, core, time);
        g.pose().popPose();
    }

    /**
     * Through the window: space, galaxies, stars, the planets on the far halves of their orbits, the singularity, the
     * planets on the near halves, the streams into it; a fusion's flash and shock wave. It opens like an iris.
     */
    private void window(GuiGraphics g, @Nullable FusionCoreBlockEntity core, float time, float open, boolean fusing, float progress, float partialTick) {
        int[] w = Layouts.WINDOW;
        float mid = (w[1] + w[3]) * 0.5F, half = (w[3] - w[1]) * 0.5F * easeOut(open / 0.6F);
        int top = Mth.floor(mid - half), bottom = Mth.ceil(mid + half);
        if (bottom - top < w[3] - w[1]) shutters(g, w, top, bottom);
        if (bottom - top < 1) return;
        g.enableScissor(leftPos + w[0], topPos + top, leftPos + w[2], topPos + bottom);
        float c = shownCharge, appear = Fx.smooth(0.2F, 0.85F, open);
        Cosmos2D.space(g, w[0], w[1], w[2], w[3], 0.0F, 0.0F, time, 0.6F + 0.4F * c);
        Cosmos2D.galaxy(g, 58.0F, 32.0F, 24.0F, time * 0.03F - 0.4F, 0.6F, 0.58F, 0.7F);
        Cosmos2D.galaxy(g, 176.0F, 112.0F, 14.0F, 1.2F - time * 0.04F, 0.42F, 0.38F, 0.55F);
        Cosmos2D.twinkles(g, w[0], w[1], w[2], w[3], 26, 5, time, 1.0F);
        Cosmos2D.shootingStars(g, w[0], w[1], w[2], w[3], time, 0.9F, 3);
        orbitLines(g, time, appear);
        Cosmos2D.ringed(g, 191.0F, 33.0F, 17.0F, -0.28F, HX, HY, 0.15F + 0.85F * c, appear);
        planets(g, time, c, appear, false);
        float collapse = fusing ? FusionGeometry.collapse(progress) : 0.0F;
        Cosmos2D.hole(g, HX, HY, HOLE_R, c * Fx.smooth(0.15F, 0.8F, open), collapse, spin, time * 20.0F, fusing ? 0.4F : 0.0F, 72.0F);
        planets(g, time, c, appear, true);
        streams(g, core, fusing, progress, appear);
        float done = sinceDone(core, partialTick);
        if (done >= 0.0F) {
            Cosmos2D.flash(g, HX, HY, done / FusionGeometry.FLASH_TICKS, 95.0F);
            Cosmos2D.shock(g, HX, HY, done / FusionGeometry.SHOCK_TICKS, 130.0F, 0.42F);
            float white = 1.0F - done / 7.0F;
            if (white > 0.0F) {
                Cosmos2D.glow(g);
                Cosmos2D.rect(w[0], w[1], w[2], w[3], 0.9F, 0.85F, 1.0F, 0.45F * white * white);
                Cosmos2D.end();
            }
        }
        float dark = 1.0F - Fx.smooth(0.25F, 0.95F, open);
        if (dark > 0.0F) {
            Cosmos2D.shade(g);
            Cosmos2D.rect(w[0], w[1], w[2], w[3], 0.02F, 0.01F, 0.06F, dark);
            Cosmos2D.end();
        }
        g.disableScissor();
        // the iris's edges, glowing as it opens
        float edge = 1.0F - Fx.smooth(0.45F, 0.75F, open);
        if (edge > 0.0F) {
            Cosmos2D.light(g, Cosmos2D.WIDGETS);
            Cosmos2D.beam(w[0], top, w[2], top, 6.0F, 0.75F * edge, 0.55F * edge, edge);
            Cosmos2D.beam(w[0], bottom, w[2], bottom, 6.0F, 0.75F * edge, 0.55F * edge, edge);
            Cosmos2D.end();
        }
    }

    /** The window's shutters, drawing back from its middle as it opens: plates of dark metal, seams across them. */
    private static void shutters(GuiGraphics g, int[] w, int top, int bottom) {
        Cosmos2D.shade(g);
        float mid = (w[1] + w[3]) * 0.5F;
        shutter(w[0], w[1], w[2], top, 0.16F, 0.14F, 0.23F, 0.06F, 0.05F, 0.09F);
        shutter(w[0], bottom, w[2], w[3], 0.06F, 0.05F, 0.09F, 0.13F, 0.11F, 0.19F);
        for (int k = 1; k < 12; k++) {
            float up = top - 9.0F * k, down = bottom + 9.0F * k;
            if (up > w[1]) {
                Cosmos2D.rect(w[0], up - 1.0F, w[2], up, 0.02F, 0.015F, 0.04F, 0.9F);
                Cosmos2D.rect(w[0], up, w[2], up + 1.0F, 0.3F, 0.27F, 0.4F, 0.5F);
            }
            if (down < w[3] && down > mid) {
                Cosmos2D.rect(w[0], down - 1.0F, w[2], down, 0.02F, 0.015F, 0.04F, 0.9F);
                Cosmos2D.rect(w[0], down, w[2], down + 1.0F, 0.3F, 0.27F, 0.4F, 0.5F);
            }
        }
        Cosmos2D.end();
    }

    /** A shutter's plate from (x1, y1) to (x2, y2), its colour from (r1, g1, b1) at the top to (r2, g2, b2) at the bottom. */
    private static void shutter(float x1, float y1, float x2, float y2, float r1, float g1, float b1, float r2, float g2, float b2) {
        if (y2 <= y1) return;
        Cosmos2D.point(x1, y1, r1, g1, b1, 1.0F);
        Cosmos2D.point(x1, y2, r2, g2, b2, 1.0F);
        Cosmos2D.point(x2, y2, r2, g2, b2, 1.0F);
        Cosmos2D.point(x2, y1, r1, g1, b1, 1.0F);
    }

    /** The orbits, faint, pulses of light running round them the way the planets go (brighter on their near halves). */
    private void orbitLines(GuiGraphics g, float time, float appear) {
        Cosmos2D.glow(g);
        int segments = 120;
        for (Orbit o : ORBITS) {
            float[] last = o.at(0.0F);
            for (int j = 1; j <= segments; j++) {
                float a0 = Cosmos2D.TWO_PI * (j - 1) / segments, a1 = Cosmos2D.TWO_PI * j / segments;
                float[] at = o.at(a1);
                float wave = 0.5F + 0.5F * Mth.cos(a0 * 20.0F - time * 1.4F);
                float k = appear * (0.05F + 0.06F * wave) * (0.55F + 0.45F * Mth.sin(a0));
                Cosmos2D.line(last[0], last[1], at[0], at[1], 0.8F, 0.55F, 0.5F, 1.0F, k, k);
                last = at;
            }
        }
        Cosmos2D.end();
    }

    /** The planets on the near halves of their orbits (in front of the singularity) or the far (behind it); the ocean's moon. */
    private void planets(GuiGraphics g, float time, float charge, float appear, boolean near) {
        float lit = 0.15F + 0.85F * charge;
        for (Orbit o : ORBITS) {
            float angle = o.phase + Cosmos2D.TWO_PI * time / o.period;
            float depth = Mth.sin(angle);
            if ((depth >= 0.0F) != near) continue;
            float[] at = o.at(angle);
            float size = o.size * (1.0F + 0.12F * depth), dim = 0.8F + 0.2F * depth;
            if (!o.moon) {
                Cosmos2D.planet(g, o.body, at[0], at[1], size, HX, HY, lit, appear, dim, o.ar, o.ag, o.ab);
                continue;
            }
            float m = Cosmos2D.TWO_PI * time / 6.5F;
            float mx = at[0] + 11.0F * Mth.cos(m), my = at[1] + 3.6F * Mth.sin(m);
            boolean front = Mth.sin(m) >= 0.0F;
            if (!front) Cosmos2D.planet(g, Layouts.P_MOON, mx, my, size * 0.36F, HX, HY, lit, appear, dim * 0.9F, 0.7F, 0.7F, 0.8F);
            Cosmos2D.planet(g, o.body, at[0], at[1], size, HX, HY, lit, appear, dim, o.ar, o.ag, o.ab);
            if (front) Cosmos2D.planet(g, Layouts.P_MOON, mx, my, size * 0.36F, HX, HY, lit, appear, dim, 0.7F, 0.7F, 0.8F);
        }
    }

    /**
     * A stream of matter from each pylon's porthole curving into the singularity: a faint trickle while it waits,
     * bright while a fusion pours its item in, none once its item has gone.
     */
    private void streams(GuiGraphics g, @Nullable FusionCoreBlockEntity core, boolean fusing, float progress, float appear) {
        Level level = Minecraft.getInstance().level;
        if (core == null || level == null) return;
        List<BlockPos> at = core.pylons();
        int[] shows = frames(at.size());
        float stop = HOLE_R * shownCharge * 1.1F + 1.0F;
        for (int f = 0; f < shows.length; f++) {
            int i = shows[f];
            if (i < 0 || !(level.getBlockEntity(at.get(i)) instanceof GravitonPylonBlockEntity pylon) || pylon.item().isEmpty()) continue;
            float k;
            if (fusing) {
                float leaves = FusionGeometry.departure(i, at.size());
                if (progress >= leaves + FusionGeometry.FLIGHT) continue;
                k = progress < FusionGeometry.BEAMS_IN ? 0.3F : 1.0F;
            } else {
                k = 0.45F * Fx.smooth(0.05F, 0.6F, shownCharge);
            }
            float[] s = rim(f);
            boolean left = f < Layouts.PYLONS.length / 2;
            float r = fusing ? 1.0F : left ? 0.72F : 0.5F, gr = fusing ? 0.78F : left ? 0.46F : 0.66F, b = fusing ? 0.5F : 1.0F;
            Cosmos2D.stream(g, s[0], s[1], HX, HY, 0.22F, stop, streamClock, 1.0F, f, fusing ? 6 : 3, k * appear, r, gr, b);
        }
    }

    /** Where a pylon's porthole's rim faces the singularity. */
    private static float[] rim(int frame) {
        float[] c = centre(Layouts.PYLONS[frame]);
        float dx = HX - c[0], dy = HY - c[1], len = Mth.sqrt(dx * dx + dy * dy);
        return new float[] {c[0] + dx / len * 11.0F, c[1] + dy / len * 11.0F};
    }

    /** The middle of an item at `at` (its top left). */
    private static float[] centre(int[] at) {
        return new float[] {at[0] + 8.0F, at[1] + 8.0F};
    }

    /** Now and then a gleam of light runs along the frame's metal and across the title's glass. */
    private void gleam(GuiGraphics g, float time) {
        float t = time % 7.0F / 1.5F;
        if (t >= 1.0F) return;
        float x = Mth.lerp(t, -60.0F, Layouts.W + 60.0F), k = Mth.sin(t * Fx.PI) * 0.3F;
        int[] title = Layouts.TITLE;
        int[][] strips = {{4, 0, title[0], 8}, {title[2], 0, Layouts.W - 4, 8}, title, {4, Layouts.MH - 8, Layouts.W - 4, Layouts.MH},
                {0, 8, 8, Layouts.MH - 8}, {Layouts.W - 8, 8, Layouts.W, Layouts.MH - 8}, {Layouts.PANEL_X, Layouts.MH, Layouts.PANEL_X + 6, Layouts.H - 4},
                {Layouts.W - Layouts.PANEL_X - 6, Layouts.MH, Layouts.W - Layouts.PANEL_X, Layouts.H - 4},
                {Layouts.PANEL_X + 4, Layouts.H - 6, Layouts.W - Layouts.PANEL_X - 4, Layouts.H}};
        for (int[] s : strips) {
            g.enableScissor(leftPos + s[0], topPos + s[1], leftPos + s[2], topPos + s[3]);
            Cosmos2D.light(g, Cosmos2D.WIDGETS);
            Cosmos2D.beam(x + 50.0F, -20.0F, x - 50.0F, Layouts.H + 20.0F, 18.0F, k, 0.92F * k, 0.78F * k);
            Cosmos2D.end();
            g.disableScissor();
        }
    }

    /**
     * The gems round the frame glow with the core's charge, a wave of light running round them (fast in a fusion);
     * they light one after another as the screen opens, flash red when a fusion stops and white-gold when one ends.
     */
    private void gems(GuiGraphics g, @Nullable FusionCoreBlockEntity core, float time, float open, boolean fusing, float partialTick) {
        float c = shownCharge;
        float stopped = core == null ? -1.0F : ticksSince(core.abortedAt, partialTick), done = sinceDone(core, partialTick);
        float red = stopped >= 0.0F ? 1.0F - Fx.smooth(0.0F, 18.0F, stopped) : 0.0F;
        float white = done >= 0.0F ? 1.0F - Fx.smooth(0.0F, 26.0F, done) : 0.0F;
        int n = Layouts.GEMS.length;
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        for (int i = 0; i < n; i++) {
            float[] gem = Layouts.GEMS[i];
            float on = Fx.smooth(0.15F + 0.03F * i, 0.3F + 0.03F * i, open);
            float wave = 0.5F + 0.5F * Mth.sin(gemPhase - Cosmos2D.TWO_PI * i / n);
            float k = on * (0.2F + 0.3F * c + (fusing ? 0.3F : 0.0F)) * (0.4F + 0.6F * wave) + 0.8F * white + 0.6F * red;
            float r = Mth.lerp(white, Mth.lerp(red, 0.62F, 1.0F), 1.0F);
            float gr = Mth.lerp(white, Mth.lerp(red, 0.36F, 0.2F), 0.85F);
            float b = Mth.lerp(white, Mth.lerp(red, 1.0F, 0.15F), 0.6F);
            float size = gem[2] * 7.5F;
            Cosmos2D.sprite(TEX, Layouts.W_BLOOM, gem[0], gem[1], size, size, 0.0F, r * k, gr * k, b * k);
            // a glint as each lights, and at each crest of the wave
            float flare = on * (1.0F - Fx.smooth(0.3F + 0.03F * i, 0.55F + 0.03F * i, open)) + Math.max(0.0F, wave - 0.93F) * 8.0F * on * (0.4F + 0.6F * c);
            if (flare > 0.01F) {
                float s = gem[2] * 5.0F;
                Cosmos2D.sprite(TEX, Layouts.W_SPARKLE, gem[0], gem[1], s, s, 0.0F, 0.9F * flare, 0.8F * flare, flare);
            }
        }
        Cosmos2D.end();
    }

    /**
     * A tube of plasma `fill` full: dark glass, the plasma rising in it with light flowing up through it (`flowed` the
     * pixels it has flowed), a glowing surface, bubbles, then the glass's frame and gleam over it all; `mark` (if not
     * negative) a line at what the recipe takes, gold if there is enough and pulsing red if not.
     */
    private void tube(GuiGraphics g, int[] bar, int[] plasma, float fill, float time, float flowed, float fr, float fg, float fb, float mark,
                      boolean enough, int seed) {
        float x = bar[0], y = bar[1], w = bar[2], h = bar[3], bottom = y + h, top = bottom - h * Mth.clamp(fill, 0.0F, 1.0F);
        Cosmos2D.shade(g);
        Cosmos2D.rect(x, y, x + w, bottom, 0.03F, 0.02F, 0.07F, 0.72F);
        if (bottom - top > 0.3F) {
            Cosmos2D.paint(g, Cosmos2D.WIDGETS);
            Cosmos2D.part(TEX, plasma, 0.0F, (top - y) / h, 1.0F, 1.0F, x, top, x + w, bottom, 1.0F, 1.0F, 1.0F);
            Cosmos2D.light(g, Cosmos2D.WIDGETS);
            // the flow's picture repeats up the tube, rising
            int[] f = Layouts.W_FLOW;
            float tile = f[3] * 0.5F, s = flowed, k = 0.38F;
            for (int n = Mth.floor(-s / tile); n <= Mth.floor((bottom - top - s) / tile); n++) {
                float hi = Math.min(bottom, bottom - s - n * tile), lo = Math.max(top, bottom - s - (n + 1) * tile);
                if (hi <= lo) continue;
                Cosmos2D.part(TEX, f, 0.0F, (bottom - lo - s) / tile - n, 1.0F, (bottom - hi - s) / tile - n, x, lo, x + w, hi, fr * k, fg * k, fb * k);
            }
            // bubbles rising to the surface
            for (int i = 0; i < 5 && bottom - top > 5.0F; i++) {
                float life = (flowed * (0.02F + 0.012F * Fx.hash(i, seed, 1)) + Fx.hash(i, seed, 2)) % 1.0F;
                float by = bottom - 2.0F - life * (bottom - top - 3.0F);
                float bx = x + 2.5F + (w - 5.0F) * Fx.hash(i, seed, 3) + Mth.sin(time * 2.0F + i) * 0.8F;
                float fade = Fx.smooth(0.0F, 0.1F, life) * (1.0F - Fx.smooth(0.85F, 1.0F, life)) * 0.8F;
                Cosmos2D.sprite(TEX, Layouts.W_SPARKLE, bx, by, 3.2F, 3.2F, 0.0F, fr * fade, fg * fade, fb * fade);
            }
            // the surface, glowing
            float glow = 0.55F + 0.2F * Mth.sin(time * 3.0F + seed);
            Cosmos2D.beam(x - 1.0F, top, x + w + 1.0F, top, 4.0F, Mth.lerp(0.5F, fr, 1.0F) * glow, Mth.lerp(0.5F, fg, 1.0F) * glow,
                    Mth.lerp(0.5F, fb, 1.0F) * glow);
        }
        Cosmos2D.paint(g, Cosmos2D.WIDGETS);
        Cosmos2D.sprite(TEX, Layouts.W_TUBE, x + w * 0.5F, y + h * 0.5F, w + 4.0F, h + 4.0F, 0.0F, 1.0F);
        if (mark >= 0.0F) {
            float my = bottom - h * mark, k = enough ? 0.75F : 0.6F + 0.4F * Mth.sin(time * 6.0F);
            Cosmos2D.glow(g);
            Cosmos2D.line(x - 3.0F, my, x + w + 3.0F, my, 1.0F, enough ? 1.0F : 1.0F, enough ? 0.82F : 0.36F, enough ? 0.45F : 0.28F, k, k);
        }
        Cosmos2D.end();
    }

    /** The hologram the status shows in: it unfolds as the screen opens, flickers, a scan line runs down it; it flares when the status changes. */
    private void hologram(GuiGraphics g, @Nullable FusionCoreBlockEntity core, float time, float open) {
        int[] s = Layouts.STATUS;
        float on = Fx.smooth(0.3F, 0.6F, open);
        if (on <= 0.0F) return;
        float flicker = 0.9F + 0.1F * Mth.sin(time * 37.0F) * Mth.sin(time * 11.3F);
        float cx = (s[0] + s[2]) * 0.5F, cy = (s[1] + s[3]) * 0.5F, w = s[2] - s[0], h = s[3] - s[1];
        Cosmos2D.paint(g, Cosmos2D.WIDGETS, 1.0F, 1.0F, 1.0F, on * flicker);
        Cosmos2D.sprite(TEX, Layouts.W_HOLOGRAM, cx, cy, w * (0.15F + 0.85F * easeOut(on)), h, 0.0F, 1.0F);
        float change = core != null && core.status() != shownStatus ? 0.0F : Mth.clamp(since(statusChangedAt) / 0.5F, 0.0F, 1.0F);
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        if (change < 1.0F && shownStatus != null) {
            float k = (1.0F - change) * 0.5F;
            Cosmos2D.sprite(TEX, Layouts.W_HOLOGRAM, cx, cy, w, h, 0.0F, 0.5F * k, 0.75F * k, k);
        }
        Cosmos2D.end();
        g.enableScissor(leftPos + s[0] + 1, topPos + s[1] + 1, leftPos + s[2] - 1, topPos + s[3] - 1);
        float t = time / 2.4F % 1.0F, sy = Mth.lerp(t, s[1] - 2.0F, s[3] + 2.0F);
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        Cosmos2D.beam(s[0], sy, s[2], sy, 3.0F, 0.16F * on, 0.28F * on, 0.48F * on);
        Cosmos2D.end();
        g.disableScissor();
    }

    /**
     * The portholes: the pylons' (violet with a glow where a pylon stands, plain steel where none does; flashing gold
     * as a fusion takes its item), the catalyst's and the output's (gold); the pylons' items floating in them.
     */
    private void portholes(GuiGraphics g, @Nullable FusionCoreBlockEntity core, float time, float open, boolean fusing, float progress) {
        Level level = Minecraft.getInstance().level;
        List<BlockPos> at = core == null ? List.of() : core.pylons();
        int[] shows = frames(at.size());
        int count = shows.length;
        ItemStack[] items = new ItemStack[count];
        float[] glow = new float[count], gold = new float[count];
        for (int f = 0; f < count; f++) {
            items[f] = ItemStack.EMPTY;
            int i = shows[f];
            if (i < 0) continue;
            if (level != null && level.getBlockEntity(at.get(i)) instanceof GravitonPylonBlockEntity pylon) items[f] = pylon.item();
            glow[f] = items[f].isEmpty() ? 0.08F : 0.14F + 0.3F * shownCharge;
            if (fusing && !items[f].isEmpty()) {
                float leaves = FusionGeometry.departure(i, at.size());
                if (progress < leaves) {
                    glow[f] = Mth.lerp(Fx.smooth(leaves - 0.15F, leaves, progress), 0.45F, 1.0F);
                } else {
                    gold[f] = 1.0F - Fx.smooth(leaves, leaves + FusionGeometry.FLIGHT, progress);
                    glow[f] = 0.1F + gold[f];
                    items[f] = ItemStack.EMPTY;     // it has flown
                }
            }
        }
        float[] cat = centre(Layouts.CATALYST), out = centre(Layouts.OUTPUT);
        float catPop = pop((open - 0.12F) / 0.35F), outPop = pop((open - 0.17F) / 0.35F);
        boolean hasCatalyst = !menu.getSlot(FusionCoreMenu.CATALYST).getItem().isEmpty(), hasOutput = !menu.getSlot(FusionCoreMenu.OUTPUT).getItem().isEmpty();

        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        for (int f = 0; f < count; f++) {
            if (shows[f] < 0) continue;
            float[] c = centre(Layouts.PYLONS[f]);
            float s = 30.0F * portPop(f, open), k = glow[f] * (0.85F + 0.15F * Mth.sin(time * 2.0F + f));
            Cosmos2D.sprite(TEX, Layouts.W_BLOOM, c[0], c[1], s, s, 0.0F, Mth.lerp(gold[f], 0.55F, 1.0F) * k, Mth.lerp(gold[f], 0.3F, 0.75F) * k,
                    Mth.lerp(gold[f], 1.0F, 0.35F) * k);
        }
        if (hasCatalyst) Cosmos2D.sprite(TEX, Layouts.W_BLOOM, cat[0], cat[1], 30.0F * catPop, 30.0F * catPop, 0.0F, 0.32F, 0.24F, 0.1F);
        float outGlow = hasOutput ? 0.35F + 0.15F * Mth.sin(time * 3.0F) : 0.0F;
        if (outGlow > 0.0F) Cosmos2D.sprite(TEX, Layouts.W_BLOOM, out[0], out[1], 32.0F * outPop, 32.0F * outPop, 0.0F, outGlow, 0.75F * outGlow, 0.35F * outGlow);
        Cosmos2D.paint(g, Cosmos2D.WIDGETS, 0.85F, 0.85F, 0.85F, 1.0F);
        for (int f = 0; f < count; f++) {
            if (shows[f] >= 0) continue;
            float[] c = centre(Layouts.PYLONS[f]);
            float s = Layouts.PORTHOLE * portPop(f, open);
            Cosmos2D.sprite(TEX, Layouts.W_PORT_STEEL, c[0], c[1], s, s, 0.0F, 1.0F);
        }
        Cosmos2D.paint(g, Cosmos2D.WIDGETS);
        for (int f = 0; f < count; f++) {
            if (shows[f] < 0) continue;
            float[] c = centre(Layouts.PYLONS[f]);
            float s = Layouts.PORTHOLE * portPop(f, open);
            Cosmos2D.sprite(TEX, Layouts.W_PORT_VIOLET, c[0], c[1], s, s, 0.0F, 1.0F);
        }
        Cosmos2D.sprite(TEX, Layouts.W_PORT_GOLD, cat[0], cat[1], Layouts.PORTHOLE * catPop, Layouts.PORTHOLE * catPop, 0.0F, 1.0F);
        Cosmos2D.sprite(TEX, Layouts.W_PORT_GOLD, out[0], out[1], Layouts.PORTHOLE * outPop, Layouts.PORTHOLE * outPop, 0.0F, 1.0F);
        for (int f = 0; f < count; f++) {
            if (gold[f] <= 0.01F) continue;
            float[] c = centre(Layouts.PYLONS[f]);
            Cosmos2D.paint(g, Cosmos2D.WIDGETS, 1.0F, 1.0F, 1.0F, gold[f]);
            Cosmos2D.sprite(TEX, Layouts.W_PORT_GOLD, c[0], c[1], Layouts.PORTHOLE, Layouts.PORTHOLE, 0.0F, 1.0F);
        }
        Cosmos2D.end();
        // the items, floating
        for (int f = 0; f < count; f++) {
            float p = portPop(f, open);
            if (items[f].isEmpty() || p < 0.3F) continue;
            float[] c = centre(Layouts.PYLONS[f]);
            g.pose().pushPose();
            g.pose().translate(c[0], c[1] + Mth.sin(time * 1.8F + f * 0.9F) * 0.6F, 0.0F);
            g.pose().scale(p, p, 1.0F);
            g.renderItem(items[f], -8, -8);
            g.pose().popPose();
        }
        if (at.size() > count) {
            float[] last = centre(Layouts.PYLONS[count - 1]);
            Ui.centred(g, font, Component.literal("+" + (at.size() - count)), Math.round(last[0]), Math.round(last[1]) + 12, 0xFF000000 | TEXT_DIM);
        }
    }

    /** The start orb: its ring turning round it (fast in a fusion), its glow breathing; it swells under the mouse and ripples when pressed. */
    private void orb(GuiGraphics g, float time, float open) {
        float cx = Layouts.START[0] + Layouts.ORB * 0.5F, cy = Layouts.START[1] + Layouts.ORB * 0.5F;
        float scale = pop((open - 0.2F) / 0.4F) * (1.0F + 0.07F * hover);
        if (scale <= 0.0F) return;
        int state = Math.max(0, orbState);
        float pulse = 0.5F + 0.5F * Mth.sin(time * 3.0F);
        float k = switch (state) {
            case 0 -> 0.35F + 0.25F * pulse;
            case 1 -> 0.8F;
            case 3 -> 0.6F + 0.3F * Mth.sin(time * 9.0F);
            default -> 0.1F;
        };
        float r = state == 3 ? 1.0F : state == 2 ? 0.5F : 0.62F, gr = state == 3 ? 0.82F : state == 2 ? 0.5F : 0.34F, b = state == 3 ? 0.6F : state == 2 ? 0.55F : 1.0F;
        float pressed = since(pressedAt) / 0.6F;
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        Cosmos2D.sprite(TEX, Layouts.W_BLOOM, cx, cy, 48.0F * scale, 48.0F * scale, 0.0F, r * k, gr * k, b * k);
        if (pressed < 1.0F) {
            float f = (1.0F - pressed) * (1.0F - pressed);
            Cosmos2D.sprite(TEX, Layouts.W_BLOOM, cx, cy, 40.0F + 60.0F * pressed, 40.0F + 60.0F * pressed, 0.0F, f, 0.8F * f, 0.5F * f);
            for (int i = 0; i < 8; i++) {
                float a = Cosmos2D.TWO_PI * i / 8.0F + 0.3F, d = 10.0F + 26.0F * easeOut(pressed);
                Cosmos2D.sprite(TEX, Layouts.W_SPARKLE, cx + d * Mth.cos(a), cy + d * Mth.sin(a), 6.0F, 6.0F, 0.0F, f, 0.85F * f, 0.6F * f);
            }
        }
        if (pressed < 1.0F) {
            Cosmos2D.paint(g, Cosmos2D.WIDGETS, 1.0F, 1.0F, 1.0F, (1.0F - pressed) * (1.0F - pressed));
            float s = 32.0F * (1.0F + 1.1F * easeOut(pressed));
            Cosmos2D.sprite(TEX, Layouts.W_ORB_RING, cx, cy, s, s, -ringTurn, 1.0F);
        }
        float ring = state == 2 ? 0.55F : 1.0F;
        Cosmos2D.paint(g, Cosmos2D.WIDGETS, ring, ring, ring, 1.0F);
        Cosmos2D.sprite(TEX, Layouts.W_ORB_RING, cx, cy, 32.0F * scale, 32.0F * scale, ringTurn, 1.0F);
        float change = Mth.clamp(since(orbChangedAt) / 0.22F, 0.0F, 1.0F), size = Layouts.ORB * scale;
        if (change < 1.0F && lastOrbState >= 0) {
            Cosmos2D.paint(g, Cosmos2D.WIDGETS);
            Cosmos2D.sprite(TEX, orbFace(lastOrbState), cx, cy, size, size, 0.0F, 1.0F);
        }
        Cosmos2D.paint(g, Cosmos2D.WIDGETS, 1.0F, 1.0F, 1.0F, change < 1.0F && lastOrbState >= 0 ? change : 1.0F);
        Cosmos2D.sprite(TEX, orbFace(state), cx, cy, size, size, 0.0F, 1.0F);
        Cosmos2D.paint(g, Cosmos2D.WIDGETS);
        Cosmos2D.sprite(TEX, Layouts.W_PORT_GLOSS, cx, cy, size * 1.08F, size * 1.08F, 0.0F, 1.0F);
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        // a glint on its glass now and then; in a fusion, three motes circling it
        float glint = Math.max(0.0F, Mth.sin(time * 2.0F) - 0.9F) * 10.0F * (state == 2 ? 0.3F : 1.0F);
        if (glint > 0.0F) Cosmos2D.sprite(TEX, Layouts.W_SPARKLE, cx - 4.0F * scale, cy - 5.0F * scale, 7.0F * glint, 7.0F * glint, 0.0F, glint, glint, glint);
        if (state == 3) {
            for (int i = 0; i < 3; i++) {
                float a = time * 4.0F + Cosmos2D.TWO_PI * i / 3.0F;
                Cosmos2D.sprite(TEX, Layouts.W_SPARKLE, cx + 15.0F * Mth.cos(a), cy + 15.0F * Mth.sin(a), 5.0F, 5.0F, 0.0F, 1.0F, 0.85F, 0.55F);
            }
        }
        Cosmos2D.end();
    }

    private static int[] orbFace(int state) {
        int[] o = Layouts.W_ORB;
        return new int[] {o[0] + o[2] * state, o[1], o[2], o[3]};
    }

    /** In a fusion: the catalyst rising out of its porthole into the singularity, then the pylons' items, each in its turn. */
    private void flights(GuiGraphics g, @Nullable FusionCoreBlockEntity core, float time, boolean fusing, float progress) {
        Level level = Minecraft.getInstance().level;
        if (core == null || level == null || !fusing) return;
        ItemStack catalyst = menu.getSlot(FusionCoreMenu.CATALYST).getItem();
        if (!catalyst.isEmpty() && progress >= FusionGeometry.CATALYST_RISES && progress < FusionGeometry.CATALYST_GONE) {
            float[] c = centre(Layouts.CATALYST);
            fly(g, catalyst, c[0], c[1], HX, HY, -0.18F,
                    (progress - FusionGeometry.CATALYST_RISES) / (FusionGeometry.CATALYST_GONE - FusionGeometry.CATALYST_RISES), false);
        }
        List<BlockPos> at = core.pylons();
        int[] shows = frames(at.size());
        for (int f = 0; f < shows.length; f++) {
            int i = shows[f];
            if (i < 0 || !(level.getBlockEntity(at.get(i)) instanceof GravitonPylonBlockEntity pylon) || pylon.item().isEmpty()) continue;
            float t = (progress - FusionGeometry.departure(i, at.size())) / FusionGeometry.FLIGHT;
            if (t < 0.0F || t >= 1.0F) continue;
            float[] c = centre(Layouts.PYLONS[f]);
            fly(g, pylon.item(), c[0], c[1], HX, HY, 0.22F, t, false);
        }
    }

    /**
     * An item `t` of the way along a curve from (sx, sy) to (ex, ey), sparks trailing it: falling into the singularity
     * (faster and smaller as it goes) or coming out of it (growing as it slows).
     */
    private void fly(GuiGraphics g, ItemStack stack, float sx, float sy, float ex, float ey, float bend, float t, boolean out) {
        float e = out ? 1.0F - (1.0F - t) * (1.0F - t) : t * t;
        float[] at = Cosmos2D.along(sx, sy, ex, ey, bend, e);
        float scale = out ? 0.25F + 0.75F * e : 1.0F - 0.85F * e;
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        float halo = 22.0F * scale + 8.0F;
        Cosmos2D.sprite(TEX, Layouts.W_BLOOM, at[0], at[1], halo, halo, 0.0F, 0.42F, 0.3F, 0.7F);
        for (int j = 1; j <= 6; j++) {
            float[] b = Cosmos2D.along(sx, sy, ex, ey, bend, Math.max(0.0F, e - j * 0.035F));
            float k = (1.0F - j / 7.0F) * 0.8F, s = 5.5F - j * 0.6F;
            Cosmos2D.sprite(TEX, Layouts.W_SPARKLE, b[0], b[1], s, s, 0.0F, k, 0.85F * k, 0.6F * k);
        }
        Cosmos2D.end();
        g.pose().pushPose();
        g.pose().translate(at[0], at[1], 0.0F);
        g.pose().mulPose(Axis.ZP.rotation((out ? 1.0F - t : t) * 4.0F));
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    /** What the catalyst and the pylons would make, a ghost breathing in the empty output. */
    private void preview(GuiGraphics g, @Nullable FusionCoreBlockEntity core, float time) {
        if (core == null || core.preview().isEmpty() || !menu.getSlot(FusionCoreMenu.OUTPUT).getItem().isEmpty()) return;
        float k = 0.85F + 0.15F * Mth.sin(time * 2.2F);
        RenderSystem.setShaderColor(0.5F * k, 0.42F * k, 0.78F * k, 1.0F);
        g.pose().pushPose();
        g.pose().translate(Layouts.OUTPUT[0], Layouts.OUTPUT[1] + Mth.sin(time * 1.6F) * 0.6F, 0.0F);
        g.renderItem(core.preview(), 0, 0);
        g.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ over the items: glass, the result, text

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        FusionCoreBlockEntity core = menu.core();
        float time = clock(), open = sinceOpen();
        float partialTick = minecraft == null ? 0.0F : minecraft.getFrameTime();
        // over the slots' items: they leave the depth test on, so this goes in front of them
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, OVER_ITEMS);
        glass(g, core, time, open, partialTick);
        g.pose().popPose();
        title(g, time, open);
        int a = alpha(Fx.smooth(0.3F, 0.6F, open));
        if (a >= 8) g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, a << 24 | TEXT_DIM, false);
        status(g, core, time, open);
    }

    /**
     * The portholes' glass over their items, a glint on it now and then; the catalyst's clouding over once it has risen
     * into the singularity, the output's clouded until the result comes down out of it into the output.
     */
    private void glass(GuiGraphics g, @Nullable FusionCoreBlockEntity core, float time, float open, float partialTick) {
        boolean fusing = core != null && core.fusing();
        float progress = core == null ? 0.0F : core.progress(partialTick), done = sinceDone(core, partialTick);
        float catalystFog = fusing ? Fx.smooth(FusionGeometry.CATALYST_RISES, FusionGeometry.CATALYST_RISES + 0.03F, progress) : 0.0F;
        float landing = done < 0.0F ? 1.0F : Mth.clamp((done - FusionGeometry.RESULT_FROM) / FusionGeometry.RESULT_TICKS, 0.0F, 1.0F);
        float outputFog = done < 0.0F ? 0.0F : 1.0F - Fx.smooth(0.85F, 1.0F, landing);
        float[] cat = centre(Layouts.CATALYST), out = centre(Layouts.OUTPUT);
        if (catalystFog > 0.01F) {
            Cosmos2D.paint(g, Cosmos2D.WIDGETS, 1.0F, 1.0F, 1.0F, 0.94F * catalystFog);
            Cosmos2D.sprite(TEX, Layouts.W_PORT_GOLD, cat[0], cat[1], Layouts.PORTHOLE, Layouts.PORTHOLE, 0.0F, 1.0F);
        }
        if (outputFog > 0.01F) {
            Cosmos2D.paint(g, Cosmos2D.WIDGETS, 1.0F, 1.0F, 1.0F, 0.94F * outputFog);
            Cosmos2D.sprite(TEX, Layouts.W_PORT_GOLD, out[0], out[1], Layouts.PORTHOLE, Layouts.PORTHOLE, 0.0F, 1.0F);
        }
        Cosmos2D.end();
        ItemStack result = menu.getSlot(FusionCoreMenu.OUTPUT).getItem();
        if (done >= FusionGeometry.RESULT_FROM && landing < 1.0F && !result.isEmpty()) fly(g, result, HX, HY, out[0], out[1], -0.12F, landing, true);

        Cosmos2D.paint(g, Cosmos2D.WIDGETS);
        for (int f = 0; f < Layouts.PYLONS.length; f++) {
            float[] c = centre(Layouts.PYLONS[f]);
            float s = Layouts.PORTHOLE * portPop(f, open);
            Cosmos2D.sprite(TEX, Layouts.W_PORT_GLOSS, c[0], c[1], s, s, 0.0F, 1.0F);
        }
        float catPop = pop((open - 0.12F) / 0.35F) * Layouts.PORTHOLE, outPop = pop((open - 0.17F) / 0.35F) * Layouts.PORTHOLE;
        Cosmos2D.sprite(TEX, Layouts.W_PORT_GLOSS, cat[0], cat[1], catPop, catPop, 0.0F, 1.0F);
        Cosmos2D.sprite(TEX, Layouts.W_PORT_GLOSS, out[0], out[1], outPop, outPop, 0.0F, 1.0F);
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        for (int f = 0; f < Layouts.PYLONS.length + 2; f++) {
            float[] c = f < Layouts.PYLONS.length ? centre(Layouts.PYLONS[f]) : f == Layouts.PYLONS.length ? cat : out;
            float k = Math.max(0.0F, Mth.sin(time * 0.9F + f * 1.7F) - 0.97F) * 33.0F;
            if (k > 0.0F) Cosmos2D.sprite(TEX, Layouts.W_SPARKLE, c[0] - 4.5F, c[1] - 5.0F, 7.0F * k, 7.0F * k, 0.0F, k, k, k);
        }
        Cosmos2D.end();
    }

    /** The title on its plaque in gold, a light running along it, glowing softly; it fades in as the screen opens. */
    private void title(GuiGraphics g, float time, float open) {
        int a = alpha(Fx.smooth(0.25F, 0.55F, open));
        if (a < 8) return;
        int[] t = Layouts.TITLE;
        String text = title.getString();
        int width = font.width(text), max = t[2] - t[0] - 16;
        float scale = width > max ? Math.max(0.5F, max / (float) width) : 1.0F;
        float cx = (t[0] + t[2]) * 0.5F, y = t[1] + (t[3] - t[1] - 8) / 2.0F + 1.0F;
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        float half = width * scale * 0.5F + 6.0F, k = a / 255.0F * 0.12F;
        Cosmos2D.beam(cx - half, y + 4.0F, cx + half, y + 4.0F, 14.0F, k, 0.75F * k, 0.4F * k);
        Cosmos2D.end();
        float shine = time / 4.5F % 1.0F * 1.8F - 0.4F;
        g.pose().pushPose();
        g.pose().translate(cx - width * scale * 0.5F, y + (1.0F - scale) * 4.0F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int x = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int cw = font.width(ch);
            float u = (x + cw * 0.5F) / Math.max(1, width), edge = Math.abs(u - 0.5F) * 2.0F;
            float light = (float) Math.exp(-((u - shine) / 0.09F) * ((u - shine) / 0.09F)) * 0.85F;
            int r = Math.round(Mth.lerp(light, Mth.lerp(edge, 255.0F, 236.0F), 255.0F));
            int gr = Math.round(Mth.lerp(light, Mth.lerp(edge, 230.0F, 176.0F), 252.0F));
            int b = Math.round(Mth.lerp(light, Mth.lerp(edge, 172.0F, 92.0F), 236.0F));
            g.drawString(font, ch, x, 0, a << 24 | r << 16 | gr << 8 | b, true);
            x += cw;
            i += Character.charCount(cp);
        }
        g.pose().popPose();
    }

    /**
     * What the core is doing, and what the fusion costs (or what it makes, or what is stored), in the hologram: typed
     * out as the screen opens; a new status fades in over the old with a moment's glitch.
     */
    private void status(GuiGraphics g, @Nullable FusionCoreBlockEntity core, float time, float open) {
        if (core == null) return;
        int[] s = Layouts.STATUS;
        FusionStatus status = core.status();
        if (status != shownStatus) {
            lastStatus = shownStatus;
            shownStatus = status;
            statusChangedAt = System.nanoTime();
        }
        float change = lastStatus == null ? 1.0F : Mth.clamp(since(statusChangedAt) / 0.3F, 0.0F, 1.0F);
        float typed = Fx.smooth(0.45F, 0.95F, open);
        int cx = (s[0] + s[2]) / 2, width = s[2] - s[0] - 10, y1 = s[1] + 3, y2 = s[1] + 12;
        if (change < 1.0F && lastStatus != null) {
            text(g, Component.translatable("gui.singularityfusion.status." + lastStatus.key()), cx, y1, width, colour(lastStatus), 1.0F - change, typed);
        }
        int jitter = change < 0.6F ? Math.round((Fx.hash((int) (time * 30.0F), 7, 1) - 0.5F) * 3.0F) : 0;
        text(g, Component.translatable("gui.singularityfusion.status." + status.key()), cx + jitter, y1, width, colour(status), change, typed);
        Component info;
        int infoColour = TEXT;
        if (status == FusionStatus.FUSING && !core.preview().isEmpty()) {
            info = Component.translatable("gui.singularityfusion.making", core.preview().getHoverName(), Math.round(core.progress(0.0F) * 100.0F));
        } else if (core.cost() > 0L) {
            info = Component.translatable("gui.singularityfusion.cost", number(core.cost()));
            if (core.energy() < core.cost()) infoColour = 0xFFB347;
        } else {
            info = Component.translatable("gui.singularityfusion.stored", number(core.energy()));
            infoColour = TEXT_DIM;
        }
        text(g, info, cx, y2, width, infoColour, 1.0F, typed);
    }

    /** A line of text centred on cx (smaller if it would be wider than maxWidth), faded to `fade`, only the first `typed` of it shown. */
    private void text(GuiGraphics g, Component line, int cx, int y, int maxWidth, int rgb, float fade, float typed) {
        int a = alpha(fade);
        if (a < 8 || typed <= 0.0F) return;
        String full = line.getString();
        int n = Math.round(full.length() * Mth.clamp(typed, 0.0F, 1.0F));
        if (n > 0 && n < full.length() && Character.isHighSurrogate(full.charAt(n - 1))) n--;
        String shown = full.substring(0, n);
        int width = font.width(full);
        float scale = width > maxWidth ? Math.max(0.5F, maxWidth / (float) width) : 1.0F;
        g.pose().pushPose();
        g.pose().translate(cx - width * scale * 0.5F, y + (1.0F - scale) * 4.0F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, shown, 0, 0, a << 24 | rgb, false);
        g.pose().popPose();
    }

    private static int alpha(float fade) {
        return Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255.0F);
    }

    private static int colour(FusionStatus status) {
        return switch (status) {
            case NO_FOUNDATION, BLOCKED, NO_PYLONS -> 0xFF7A6B;
            case NO_RECIPE -> 0xA59CBD;
            case OUTPUT_FULL, NO_ENERGY -> 0xFFB347;
            case READY -> 0x7CF3C8;
            case FUSING -> 0xD7A8FF;
        };
    }

    static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * Which pylon each porthole shows (-1 for none): the first half of them (in their order round the core) down the
     * left column, the rest down the right, each column's run centred, so the two sides balance.
     */
    static int[] frames(int count) {
        int half = Layouts.PYLONS.length / 2;
        int n = Math.min(count, Layouts.PYLONS.length);
        int left = (n + 1) / 2, right = n - left;
        int[] out = new int[Layouts.PYLONS.length];
        Arrays.fill(out, -1);
        for (int i = 0; i < left; i++) out[(half - left) / 2 + i] = i;
        for (int i = 0; i < right; i++) out[half + (half - right) / 2 + i] = left + i;
        return out;
    }

    // ------------------------------------------------------------------ tooltips and clicks

    private void tooltips(GuiGraphics g, int mouseX, int mouseY) {
        FusionCoreBlockEntity core = menu.core();
        if (core == null || !menu.getCarried().isEmpty()) return;
        double mx = mouseX - leftPos, my = mouseY - topPos;
        int[] e = Layouts.ENERGY, p = Layouts.PROGRESS, w = Layouts.WINDOW;
        List<Component> lines = new ArrayList<>();
        int frame = -1;
        for (int f = 0; f < Layouts.PYLONS.length; f++) {
            float[] c = centre(Layouts.PYLONS[f]);
            if ((mx - c[0]) * (mx - c[0]) + (my - c[1]) * (my - c[1]) <= 11.0D * 11.0D) frame = f;
        }
        float reach = Math.max(12.0F, HOLE_R * shownCharge * Cosmos2D.DISK_OUT);
        if (Ui.in(mx, my, e[0] - 2, e[1] - 2, e[2] + 4, e[3] + 4)) {
            lines.add(Component.translatable("gui.singularityfusion.energy", number(core.energy()), number(core.capacity())));
            lines.add(Component.translatable("gui.singularityfusion.charge", Math.round(core.charge() * 100.0F)).withStyle(ChatFormatting.LIGHT_PURPLE));
            if (core.cost() > 0L) lines.add(Component.translatable("gui.singularityfusion.cost", number(core.cost())).withStyle(ChatFormatting.GRAY));
        } else if (Ui.in(mx, my, p[0] - 2, p[1] - 2, p[2] + 4, p[3] + 4)) {
            lines.add(core.fusing() ? Component.translatable("gui.singularityfusion.progress", Math.round(core.progress(0.0F) * 100.0F))
                    : Component.translatable("gui.singularityfusion.idle"));
            if (core.fusing() || core.cost() > 0L) {
                lines.add(Component.translatable("gui.singularityfusion.time", String.format(Locale.ROOT, "%.1f", core.craftTime() / 20.0F))
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (overStart(mouseX, mouseY)) {
            if (core.fusing()) lines.add(Component.translatable("gui.singularityfusion.status.fusing"));
            else if (core.status() == FusionStatus.READY) lines.add(Component.translatable("gui.singularityfusion.start"));
            else lines.add(Component.translatable("gui.singularityfusion.status." + core.status().key()).withStyle(ChatFormatting.RED));
            lines.add(Component.translatable("gui.singularityfusion.start.redstone").withStyle(ChatFormatting.DARK_GRAY));
        } else if (frame >= 0) {
            Level level = Minecraft.getInstance().level;
            int i = frames(core.pylons().size())[frame];
            if (i < 0 || level == null) {
                lines.add(Component.translatable("gui.singularityfusion.pylon.none").withStyle(ChatFormatting.GRAY));
            } else if (level.getBlockEntity(core.pylons().get(i)) instanceof GravitonPylonBlockEntity pylon && !pylon.item().isEmpty()) {
                g.renderTooltip(font, pylon.item(), mouseX, mouseY);
                return;
            } else {
                lines.add(Component.translatable("gui.singularityfusion.pylon.empty"));
            }
        } else if (Ui.in(mx, my, w[0], w[1], w[2] - w[0], w[3] - w[1]) && Math.abs(mx - HX) <= reach && Math.abs(my - HY) <= reach * 0.45F + 6.0F) {
            lines.add(Component.translatable("gui.singularityfusion.singularity", Math.round(core.charge() * 100.0F)));
            if (!core.status().formed()) lines.add(Component.translatable("gui.singularityfusion.unformed").withStyle(ChatFormatting.GRAY));
        }
        if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private boolean overStart(double mouseX, double mouseY) {
        double dx = mouseX - (leftPos + Layouts.START[0] + Layouts.ORB * 0.5D), dy = mouseY - (topPos + Layouts.START[1] + Layouts.ORB * 0.5D);
        return dx * dx + dy * dy <= (Layouts.ORB * 0.5D) * (Layouts.ORB * 0.5D);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        FusionCoreBlockEntity core = menu.core();
        if (button == 0 && overStart(mouseX, mouseY) && core != null && !core.fusing() && core.status() == FusionStatus.READY) {
            Net.CHANNEL.sendToServer(new StartFusionPacket());
            pressedAt = System.nanoTime();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.6F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
