package com.reya.starfall.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/**
 * The film's lens: what was drawn off screen goes through a bloom chain (down to a sixty-fourth of its size
 * and back up), then onto the screen with the colours parting towards the edges, a smear outwards when the
 * camera rushes forward, darkened corners, grain and flashes; it shakes on a shot, bright things trail behind
 * them for a moment (the last frame is kept and fades) and the colour can drain out. The scenes set how much of
 * each they want.
 */
final class FilmPost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BloomChain CHAIN = new BloomChain(6);
    private static boolean broken;
    /** The last frame as it went onto the screen, for the trails; valid while the film runs on without a jump. */
    private static TextureTarget history;
    private static boolean historyValid;
    private static float lastTime;
    private static long lastNanos;

    // what the film wants this frame; reset by begin()
    static float bloom, aberration, zoom, vignette, grain;
    /** How hard the picture shakes (as a part of the screen), how long bright things trail (0..1), how grey it goes. */
    static float shake, trail, desaturate;
    /** A flash over the picture: ARGB, the alpha is how much it washes out. */
    static int flash;

    /** Back to a plain lens for a new frame. */
    static void begin() {
        bloom = 0.9F;
        aberration = 0.0025F;
        zoom = 0.0F;
        vignette = 0.45F;
        grain = 0.035F;
        shake = 0.0F;
        trail = 0.0F;
        desaturate = 0.0F;
        flash = 0;
    }

    static boolean ready() {
        return !broken && BloomChain.ready() && FilmGfx.post != null;
    }

    /** Puts the film onto the screen through the lens. Returns false if it couldn't (draw it plainly then). */
    static boolean apply(RenderTarget film, float time) {
        if (!ready()) return false;
        int glow;
        try {
            glow = ClientConfig.glow() ? CHAIN.run(film.getColorTextureId(), film.width, film.height, 0.62F) : 0;
        } catch (RuntimeException e) {
            LOGGER.error("Starfall: no buffers for the film's bloom, drawing it plainly", e);
            broken = true;
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            return false;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        // the trail fades by real time, so it lasts as long at any frame rate; a jump in the film starts it afresh
        long now = System.nanoTime();
        float dt = Mth.clamp((now - lastNanos) / 1.0E9F, 0.001F, 0.25F);
        float step = time - lastTime;
        boolean continuous = historyValid && history != null && history.width == main.width && history.height == main.height
                && now - lastNanos < 250_000_000L && step >= 0.0F && step < 0.25F;
        lastNanos = now;
        lastTime = time;
        float keep = continuous && trail > 0.0F ? (float) Math.exp(-dt / (0.09F * trail)) : 0.0F;
        float sx = 0.0F, sy = 0.0F;
        if (shake > 0.0F) {
            sx = shake * (Mth.sin(time * 61.0F) * 0.6F + Mth.sin(time * 137.0F) * 0.4F);
            sy = shake * (Mth.cos(time * 53.0F) * 0.6F + Mth.sin(time * 151.0F) * 0.4F);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        ShaderInstance s = FilmGfx.post;
        s.safeGetUniform("Texel").set(1.0F / film.width, 1.0F / film.height);
        s.safeGetUniform("Bloom").set(glow != 0 ? bloom / CHAIN.levels() * 1.6F : 0.0F);
        s.safeGetUniform("Aberration").set(aberration);
        s.safeGetUniform("Zoom").set(zoom);
        s.safeGetUniform("Vignette").set(vignette);
        s.safeGetUniform("Grain").set(grain);
        s.safeGetUniform("Time").set(time);
        s.safeGetUniform("Flash").set(((flash >> 16) & 0xFF) / 255.0F, ((flash >> 8) & 0xFF) / 255.0F, (flash & 0xFF) / 255.0F,
                ((flash >>> 24) & 0xFF) / 255.0F);
        s.safeGetUniform("Shake").set(sx, sy);
        s.safeGetUniform("Trail").set(keep);
        s.safeGetUniform("Desaturate").set(desaturate);
        RenderSystem.setShaderTexture(2, keep > 0.0F ? history.getColorTextureId() : 0);
        BloomChain.pass(main, s, film.getColorTextureId(), glow);
        RenderSystem.setShaderTexture(1, 0);
        RenderSystem.setShaderTexture(2, 0);
        remember(main);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return true;
    }

    /** Keeps this frame for the next one's trails (only while the film wants trails, to spare the copy). */
    private static void remember(RenderTarget main) {
        historyValid = false;
        if (trail <= 0.0F || FilmGfx.strikeGlow == null) return;
        try {
            if (history == null) {
                history = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            } else if (history.width != main.width || history.height != main.height) {
                history.resize(main.width, main.height, Minecraft.ON_OSX);
            }
        } catch (RuntimeException e) {
            LOGGER.error("Starfall: no buffer for the film's trails, leaving them out", e);
            trail = 0.0F;
            main.bindWrite(true);
            return;
        }
        ShaderInstance c = FilmGfx.strikeGlow;
        c.safeGetUniform("Strength").set(1.0F);
        BloomChain.pass(history, c, main.getColorTextureId(), 0);
        main.bindWrite(true);
        historyValid = true;
    }

    private FilmPost() {
    }
}
