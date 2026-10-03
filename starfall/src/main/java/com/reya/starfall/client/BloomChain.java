package com.reya.starfall.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;

/**
 * A bloom: a picture halved again and again (keeping only its bright light on the first step), then built back
 * up, each level adding the spread-out light of the one below. What comes out is a soft glow, wide around big
 * lights and tight around small ones, to be added over the picture.
 */
final class BloomChain {
    private final int levels;
    private final TextureTarget[] down, up;

    BloomChain(int levels) {
        this.levels = levels;
        this.down = new TextureTarget[levels];
        this.up = new TextureTarget[levels];
    }

    int levels() {
        return levels;
    }

    static boolean ready() {
        return FilmGfx.postDown != null && FilmGfx.postUp != null;
    }

    /** Runs the chain over a picture; returns the glow's texture, at half the picture's size. */
    int run(int source, int width, int height, float threshold) {
        int w = width, h = height;
        for (int i = 0; i < levels; i++) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            down[i] = sized(down[i], w, h);
            up[i] = sized(up[i], w, h);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        int src = source, sw = width, sh = height;
        for (int i = 0; i < levels; i++) {
            ShaderInstance s = FilmGfx.postDown;
            s.safeGetUniform("Texel").set(1.0F / sw, 1.0F / sh);
            s.safeGetUniform("Threshold").set(i == 0 ? threshold : -1.0F, 0.5F);
            pass(down[i], s, src, 0);
            src = down[i].getColorTextureId();
            sw = down[i].width;
            sh = down[i].height;
        }
        int below = down[levels - 1].getColorTextureId();
        int bw = down[levels - 1].width, bh = down[levels - 1].height;
        for (int i = levels - 2; i >= 0; i--) {
            ShaderInstance s = FilmGfx.postUp;
            s.safeGetUniform("Texel").set(1.0F / bw, 1.0F / bh);
            s.safeGetUniform("Radius").set(1.0F);
            pass(up[i], s, down[i].getColorTextureId(), below);
            below = up[i].getColorTextureId();
            bw = up[i].width;
            bh = up[i].height;
        }
        return below;
    }

    private static TextureTarget sized(TextureTarget t, int w, int h) {
        if (t == null) {
            t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        } else if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        }
        return t;
    }

    /** One pass of {@code shader} over the whole of {@code into}, reading tex0 (and tex1). */
    static void pass(RenderTarget into, ShaderInstance shader, int tex0, int tex1) {
        into.bindWrite(true);
        draw(shader, tex0, tex1);
    }

    /** One pass of {@code shader} over whatever is bound, reading tex0 (and tex1). */
    static void draw(ShaderInstance shader, int tex0, int tex1) {
        RenderSystem.setShaderTexture(0, tex0);
        RenderSystem.setShaderTexture(1, tex1);
        RenderSystem.setShader(() -> shader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        b.vertex(1.0D, -1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        b.vertex(1.0D, 1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        b.vertex(-1.0D, 1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(b.end());
    }
}
