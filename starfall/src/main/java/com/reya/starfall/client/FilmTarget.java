package com.reya.starfall.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * The film is drawn off screen at twice the window's resolution, then shrunk onto it: every pixel on screen is
 * the average of four, so edges come out smooth instead of stair-stepped.
 */
final class FilmTarget {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static TextureTarget target;
    private static boolean broken;

    /** Starts drawing off screen. Returns false (and the film is drawn straight onto the screen) if it can't. */
    static boolean begin() {
        if (broken) return false;
        int scale = ClientConfig.filmQuality();
        if (scale <= 1) return false;
        Window window = Minecraft.getInstance().getWindow();
        int max = Math.min(8192, RenderSystem.maxSupportedTextureSize());
        int w = window.getWidth() * scale, h = window.getHeight() * scale;
        if (w > max || h > max) {
            float k = Math.min(max / (float) w, max / (float) h);
            w = Math.round(w * k);
            h = Math.round(h * k);
        }
        if (w < 2 || h < 2) return false;
        try {
            if (target == null) {
                target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
                target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            } else if (target.width != w || target.height != h) {
                target.resize(w, h, Minecraft.ON_OSX);
            }
            target.setFilterMode(GL11.GL_LINEAR);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Starfall: no off-screen buffer for the film, drawing it directly", e);
            broken = true;
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            return false;
        }
    }

    /** Back to the screen, and the film onto it, through the lens if it can be, else as a quad over the GUI. */
    static void end(GuiGraphics g, float time) {
        Minecraft mc = Minecraft.getInstance();
        if (FilmPost.apply(target, time)) return;
        mc.getMainRenderTarget().bindWrite(true);
        Window window = mc.getWindow();
        float gw = (float) (window.getWidth() / window.getGuiScale());
        float gh = (float) (window.getHeight() / window.getGuiScale());
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, 0.0F, gh, 0.0F).uv(0.0F, 0.0F).endVertex();
        b.vertex(m, gw, gh, 0.0F).uv(1.0F, 0.0F).endVertex();
        b.vertex(m, gw, 0.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        b.vertex(m, 0.0F, 0.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private FilmTarget() {
    }
}
