package com.reya.starfall.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.slf4j.Logger;

/**
 * The film's lens: what was drawn off screen goes through a bloom chain (down to a sixty-fourth of its size
 * and back up), then onto the screen with the colours parting towards the edges, a smear outwards when the
 * camera rushes forward, darkened corners, grain and flashes. The scenes set how much of each they want.
 */
final class FilmPost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BloomChain CHAIN = new BloomChain(6);
    private static boolean broken;

    // what the film wants this frame; reset by begin()
    static float bloom, aberration, zoom, vignette, grain;
    /** A flash over the picture: ARGB, the alpha is how much it washes out. */
    static int flash;

    /** Back to a plain lens for a new frame. */
    static void begin() {
        bloom = 0.9F;
        aberration = 0.0025F;
        zoom = 0.0F;
        vignette = 0.45F;
        grain = 0.035F;
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
        BloomChain.pass(Minecraft.getInstance().getMainRenderTarget(), s, film.getColorTextureId(), glow);
        RenderSystem.setShaderTexture(1, 0);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return true;
    }

    private FilmPost() {
    }
}
