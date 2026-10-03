package com.reya.starfall.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/**
 * A halo around the strikes in the world: their light is drawn a second time into a half-size buffer that
 * shares the world's depth (so hills still hide it), blurred down and up, and added back over the world.
 */
final class WorldGlow {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BloomChain CHAIN = new BloomChain(5);
    private static TextureTarget target;
    private static boolean broken;
    /** The framebuffer the world was being drawn into (the main one, or with Fabulous graphics another). */
    private static int previous;

    static boolean ready() {
        return !broken && ClientConfig.glow() && BloomChain.ready() && FilmGfx.strikeGlow != null;
    }

    /** Starts drawing the light into the halo buffer. Returns false (draw nothing more) if it can't. */
    static boolean begin() {
        if (!ready()) return false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int w = Math.max(1, main.width / 2), h = Math.max(1, main.height / 2);
        try {
            if (target == null) {
                target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
                target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                target.setFilterMode(GL11.GL_LINEAR);
            } else if (target.width != w || target.height != h) {
                target.resize(w, h, Minecraft.ON_OSX);
                target.setFilterMode(GL11.GL_LINEAR);
            }
            target.clear(Minecraft.ON_OSX);
            target.copyDepthFrom(main);
            target.bindWrite(true);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Starfall: no buffer for the strikes' halo, leaving it out", e);
            broken = true;
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            RenderSystem.viewport(0, 0, main.width, main.height);
            return false;
        }
    }

    /** Blurs what was drawn and adds it over the world. */
    static void end(float strength) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int glow = CHAIN.run(target.getColorTextureId(), target.width, target.height, -1.0F);
        ShaderInstance s = FilmGfx.strikeGlow;
        s.safeGetUniform("Strength").set(strength / CHAIN.levels() * 2.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        RenderSystem.viewport(0, 0, main.width, main.height);
        BloomChain.draw(s, glow, 0);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private WorldGlow() {
    }
}
