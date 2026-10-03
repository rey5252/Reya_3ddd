package com.reya.starfall.client;

import java.util.HashSet;
import java.util.Set;

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
 * A halo around the strikes in the world. While the world is drawn, their light is drawn a second time into a
 * half-size buffer that shares the world's depth (so hills still hide it); later, before the HUD, that buffer is
 * blurred down and up and added over the picture.
 */
final class WorldGlow {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BloomChain CHAIN = new BloomChain(5);
    private static final Set<String> REPORTED = new HashSet<>();
    private static TextureTarget target;
    private static boolean broken, pending;
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
            check("copying the depth");
            target.bindWrite(true);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Starfall: no buffer for the strikes' halo, leaving it out", e);
            broken = true;
            restore(main);
            return false;
        }
    }

    /** Back to drawing the world; the halo is laid over it before the HUD. */
    static void finish() {
        check("drawing the halo's light");
        restore(Minecraft.getInstance().getMainRenderTarget());
        pending = true;
    }

    private static void restore(RenderTarget main) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        RenderSystem.viewport(0, 0, main.width, main.height);
    }

    /** Blurs this frame's halo and adds it over the picture (called before the HUD is drawn). */
    static void compose() {
        if (!pending) return;
        pending = false;
        if (!ready() || target == null) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int glow = CHAIN.run(target.getColorTextureId(), target.width, target.height, -1.0F);
        check("blurring the halo");
        ShaderInstance s = FilmGfx.strikeGlow;
        s.safeGetUniform("Strength").set(1.0F / CHAIN.levels() * 2.0F);
        main.bindWrite(true);
        // let the shader settle its own blend first, then add: the halo only ever brightens the picture
        RenderSystem.setShaderTexture(0, glow);
        s.apply();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        BloomChain.draw(s, glow, 0);
        check("adding the halo");
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    /** Logs a GL error once per place, so a driver that dislikes something says what. */
    private static void check(String where) {
        int e = GL11.glGetError();
        if (e != GL11.GL_NO_ERROR && REPORTED.add(where)) LOGGER.warn("Starfall: GL error 0x{} while {}", Integer.toHexString(e), where);
    }

    private WorldGlow() {
    }
}
