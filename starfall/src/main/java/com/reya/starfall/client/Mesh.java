package com.reya.starfall.client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.reya.starfall.Starfall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * One of the film's machines, made by {@code tools/gen_film_models.py} and kept on the GPU once loaded. It is lit
 * by {@code film_hull} (or, without it, by a fixed light baked into plain colours).
 */
final class Mesh {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Mesh RAILGUN = new Mesh("railgun");
    static final Mesh ACCELERATOR = new Mesh("accelerator");

    private final ResourceLocation location;
    private VertexBuffer buffer;
    private boolean lit, failed;

    private Mesh(String name) {
        this.location = new ResourceLocation(Starfall.MODID, "film/" + name + ".bin");
    }

    private boolean load() {
        if (buffer != null) return true;
        if (failed) return false;
        lit = FilmGfx.hull != null;
        Vector3f light = new Vector3f(-0.4F, 0.6F, -0.5F).normalize();
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location);
            try (InputStream raw = resource.open(); DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw))) {
                if (in.readInt() != 0x53464D31) throw new IOException("not a Starfall mesh");
                int quads = in.readInt();
                BufferBuilder b = Tesselator.getInstance().getBuilder();
                b.begin(VertexFormat.Mode.QUADS, lit ? DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL : DefaultVertexFormat.POSITION_COLOR);
                for (int i = 0; i < quads * 4; i++) {
                    float x = in.readFloat(), y = in.readFloat(), z = in.readFloat(), u = in.readFloat(), v = in.readFloat();
                    int r = in.readUnsignedByte(), g = in.readUnsignedByte(), bl = in.readUnsignedByte(), m = in.readUnsignedByte();
                    float nx = in.readByte() / 127.0F, ny = in.readByte() / 127.0F, nz = in.readByte() / 127.0F;
                    in.readByte();
                    if (lit) {
                        b.vertex(x, y, z).uv(u, v).color(r, g, bl, m).normal(nx, ny, nz).endVertex();
                    } else {
                        // a fixed light, and the lamps simply bright
                        float k = m < 120 ? 1.6F : 0.25F + 0.75F * Math.abs(nx * light.x + ny * light.y + nz * light.z);
                        b.vertex(x, y, z).color(Math.min(255, Math.round(r * k)), Math.min(255, Math.round(g * k)),
                                Math.min(255, Math.round(bl * k)), 255).endVertex();
                    }
                }
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(b.end());
                VertexBuffer.unbind();
            }
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Starfall: could not load the film model {}", location, e);
            failed = true;
            return false;
        }
    }

    void draw(Scene3D s, Matrix4f model, Scene3D.Light light) {
        if (!load()) return;
        Matrix4f modelView = new Matrix4f(s.view).mul(model);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        ShaderInstance sh = lit ? FilmGfx.hull : GameRenderer.getPositionColorShader();
        if (sh == null) return;
        if (lit) {
            Vector3f sun = s.dirToView(light.sun);
            Vector3f glow = s.toView(light.glow);
            float k = light.sunStrength, gk = light.glowStrength;
            sh.safeGetUniform("SunDir").set(sun.x, sun.y, sun.z);
            sh.safeGetUniform("SunColor").set(Scene3D.r(light.sunColor) * k, Scene3D.g(light.sunColor) * k, Scene3D.b(light.sunColor) * k);
            sh.safeGetUniform("FillColor").set(Scene3D.r(light.fill), Scene3D.g(light.fill), Scene3D.b(light.fill));
            sh.safeGetUniform("GlowPos").set(glow.x, glow.y, glow.z);
            sh.safeGetUniform("GlowColor").set(Scene3D.r(light.glowColor) * gk, Scene3D.g(light.glowColor) * gk, Scene3D.b(light.glowColor) * gk);
            sh.safeGetUniform("GlowRange").set(light.glowRange);
            sh.safeGetUniform("Charge").set(light.charge);
            sh.safeGetUniform("Time").set(light.time);
            Vector3f env = s.toView(light.env);
            float ek = light.envStrength;
            sh.safeGetUniform("EnvPos").set(env.x, env.y, env.z);
            sh.safeGetUniform("EnvRadius").set(light.envRadius);
            sh.safeGetUniform("EnvColor").set(Scene3D.r(light.envColor) * ek, Scene3D.g(light.envColor) * ek, Scene3D.b(light.envColor) * ek);
            sh.safeGetUniform("EnvLit").set(light.envLit ? 1.0F : 0.0F);
        }
        buffer.bind();
        buffer.drawWithShader(modelView, s.projection, sh);
        VertexBuffer.unbind();
    }
}
