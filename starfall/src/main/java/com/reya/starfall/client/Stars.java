package com.reya.starfall.client;

import java.util.Random;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * The film's stars: thousands of soft points far away on every side (built once), the brightest with
 * diffraction spikes, and the near ones that streak past when the camera moves fast.
 */
final class Stars {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COUNT = 6500;
    private static final float FAR = 1000.0F;
    /** One pixel of a 720 pixel tall screen with a 50 degree field of view, in radians. */
    private static final float PIXEL = 0.0012F;
    private static VertexBuffer buffer;
    private static boolean failed;

    private static final int NEAR = 900;
    private static final float[] NX = new float[NEAR], NY = new float[NEAR], NZ = new float[NEAR], NB = new float[NEAR];

    static {
        Random r = new Random(77L);
        for (int i = 0; i < NEAR; i++) {
            NX[i] = r.nextFloat();
            NY[i] = r.nextFloat();
            NZ[i] = r.nextFloat();
            NB[i] = 0.35F + 0.65F * r.nextFloat();
        }
    }

    /** The sky's stars, as bright as {@code brightness} (0..1). */
    static void draw(Scene3D s, float brightness) {
        ShaderInstance sh = FilmGfx.soft;
        if (sh == null || failed) return;
        if (buffer == null && !build()) return;
        Matrix4f rotation = new Matrix4f(s.view);
        rotation.m30(0.0F).m31(0.0F).m32(0.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(brightness, brightness, brightness, 1.0F);
        buffer.bind();
        buffer.drawWithShader(rotation, s.projection, sh);
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }

    private static boolean build() {
        try {
            Random r = new Random(4242L);
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < COUNT; i++) {
                Vector3f d = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian());
                if (d.lengthSquared() < 1.0E-6F) continue;
                d.normalize();
                float mag = (float) Math.pow(r.nextFloat(), 7.0D);
                float temp = r.nextFloat();
                int rgb = temp < 0.3F ? 0xC0D0FF : temp > 0.86F ? 0xFFDCAE : 0xFFF6EE;
                int a = Math.min(255, Math.round((0.25F + 0.75F * (float) Math.sqrt(mag)) * 255.0F));
                Vector3f t1 = new Vector3f(Math.abs(d.y) < 0.9F ? 0.0F : 1.0F, Math.abs(d.y) < 0.9F ? 1.0F : 0.0F, 0.0F).cross(d).normalize();
                Vector3f t2 = new Vector3f(d).cross(t1).normalize();
                Vector3f c = new Vector3f(d).mul(FAR);
                float half = (0.55F + 3.2F * mag) * 2.2F * PIXEL * FAR;
                float g = mag > 0.25F ? 4.0F : 0.0F;
                quad(b, c, t1, t2, half, g, rgb, a);
                if (mag > 0.92F) {
                    // diffraction spikes on the brightest
                    float len = (0.55F + 3.2F * mag) * 6.0F * PIXEL * FAR, w = 0.6F * PIXEL * FAR;
                    spike(b, c, t1, t2, len, w, rgb, a / 2);
                    spike(b, c, t2, t1, len, w, rgb, a / 2);
                }
            }
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(b.end());
            VertexBuffer.unbind();
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Starfall: could not build the film's stars", e);
            failed = true;
            return false;
        }
    }

    /** A round soft sprite around c, spanning h along both t1 and t2. */
    private static void quad(BufferBuilder b, Vector3f c, Vector3f t1, Vector3f t2, float h, float g, int rgb, int a) {
        int r8 = (rgb >> 16) & 0xFF, g8 = (rgb >> 8) & 0xFF, b8 = rgb & 0xFF;
        float[][] corners = {{-1.0F, -1.0F}, {1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}};
        for (float[] k : corners) {
            b.vertex(c.x + (t1.x * k[0] + t2.x * k[1]) * h, c.y + (t1.y * k[0] + t2.y * k[1]) * h, c.z + (t1.z * k[0] + t2.z * k[1]) * h)
                    .uv(g + k[0], k[1]).color(r8, g8, b8, a).endVertex();
        }
    }

    /** A diffraction spike through c along {@code along}: bright at the star, gone at both tips. */
    private static void spike(BufferBuilder b, Vector3f c, Vector3f along, Vector3f across, float len, float w, int rgb, int a) {
        int r8 = (rgb >> 16) & 0xFF, g8 = (rgb >> 8) & 0xFF, b8 = rgb & 0xFF;
        for (int end = -1; end <= 1; end += 2) {
            float tx = along.x * len * end, ty = along.y * len * end, tz = along.z * len * end;
            b.vertex(c.x - across.x * w, c.y - across.y * w, c.z - across.z * w).uv(0.0F, -1.0F).color(r8, g8, b8, a).endVertex();
            b.vertex(c.x + tx - across.x * w, c.y + ty - across.y * w, c.z + tz - across.z * w).uv(0.0F, -1.0F).color(r8, g8, b8, 0).endVertex();
            b.vertex(c.x + tx + across.x * w, c.y + ty + across.y * w, c.z + tz + across.z * w).uv(0.0F, 1.0F).color(r8, g8, b8, 0).endVertex();
            b.vertex(c.x + across.x * w, c.y + across.y * w, c.z + across.z * w).uv(0.0F, 1.0F).color(r8, g8, b8, a).endVertex();
        }
    }

    /**
     * Stars close by, streaking past a camera moving at {@code velocity} (world units a second): each is drawn
     * from where it was {@code shutter} seconds ago to where it is. They live in a box of side {@code box} that
     * travels with the camera.
     */
    static void warp(Scene3D s, Soft light, Vector3f velocity, float box, float shutter, float brightness, int tint) {
        if (brightness <= 0.0F) return;
        Vector3f eye = s.eye;
        Vector3f trail = new Vector3f(velocity).mul(shutter);
        for (int i = 0; i < NEAR; i++) {
            float x = wrap(NX[i] - eye.x / box) * box, y = wrap(NY[i] - eye.y / box) * box, z = wrap(NZ[i] - eye.z / box) * box;
            float d = (float) Math.sqrt(x * x + y * y + z * z) / (box * 0.5F);
            if (d >= 1.0F) continue;
            float k = brightness * NB[i] * (1.0F - d * d * d * d);
            Vector3f head = new Vector3f(eye).add(x, y, z);
            Vector3f tail = new Vector3f(head).add(trail);
            int color = Fx.argb(tint, Math.min(1.0F, k));
            light.line(tail, head, 0.0012F, 0.0026F, color & 0x00FFFFFF, color);
            light.glowScreen(head, 0.004F, Fx.argb(0xFFFFFF, Math.min(1.0F, k * 0.8F)), false);
        }
    }

    private static float wrap(float v) {
        return v - (float) Math.floor(v) - 0.5F;
    }

    private Stars() {
    }
}
