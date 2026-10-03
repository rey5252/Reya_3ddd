package com.reya.starfall.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * A tiny perspective renderer for the films, drawn over the screen: flat-shaded boxes lit by one light, glowing
 * additive shapes, and camera-facing sprites. Its depth is cleared before and after, so the rest of the screen
 * is untouched.
 */
final class Scene3D {
    private final Matrix4f view = new Matrix4f();
    private final Vector3f light = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    private BufferBuilder buf;
    private float ambient = 0.18F;

    /** Sets up a camera at {@code eye} looking at {@code target}, with a vertical field of view in degrees. */
    Scene3D(float width, float height, float fov, Vector3f eye, Vector3f target) {
        RenderSystem.backupProjectionMatrix();
        Matrix4f projection = new Matrix4f().setPerspective((float) Math.toRadians(fov), width / height, 0.1F, 6000.0F);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        view.setLookAt(eye, target, new Vector3f(0.0F, 1.0F, 0.0F));
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        light(-0.35F, 0.6F, -0.7F, 0.18F);
    }

    /** The one light: a direction it shines from, and how bright the unlit sides stay. */
    void light(float x, float y, float z, float ambientLight) {
        light.set(x, y, z).normalize();
        ambient = ambientLight;
    }

    /** Solid, depth-writing shapes. */
    void solid() {
        flush();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        begin();
    }

    /** Glowing shapes: added on top, hidden behind solid ones, but not hiding anything themselves. */
    void glow() {
        flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        begin();
    }

    private void begin() {
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private void flush() {
        if (buf == null) return;
        BufferUploader.drawWithShader(buf.end());
        buf = null;
    }

    void end() {
        flush();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private void vertex(float x, float y, float z, int argb) {
        view.transformPosition(x, y, z, tmp);
        buf.vertex(tmp.x, tmp.y, tmp.z).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    /** A flat quad (a, b, c, d in order) shaded by the light from its normal. */
    void face(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int rgb, boolean lit) {
        int color = 0xFF000000 | rgb;
        if (lit) {
            Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a)).normalize();
            float k = ambient + (1.0F - ambient) * Math.max(0.0F, Math.abs(n.dot(light)));
            color = 0xFF000000 | Fx.mix(0xFF000000, 0xFF000000 | rgb, k) & 0xFFFFFF;
        }
        vertex(a.x, a.y, a.z, color);
        vertex(b.x, b.y, b.z, color);
        vertex(c.x, c.y, c.z, color);
        vertex(d.x, d.y, d.z, color);
    }

    /** A box between two corners, turned by {@code model}, shaded per face. */
    void box(Matrix4f model, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, boolean lit) {
        Vector3f[] p = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            p[i] = model.transformPosition(new Vector3f((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1));
        }
        face(p[0], p[1], p[3], p[2], rgb, lit);
        face(p[4], p[6], p[7], p[5], rgb, lit);
        face(p[0], p[4], p[5], p[1], rgb, lit);
        face(p[2], p[3], p[7], p[6], rgb, lit);
        face(p[0], p[2], p[6], p[4], rgb, lit);
        face(p[1], p[5], p[7], p[3], rgb, lit);
    }

    /** A thin beam (box) from a to b with a square cross-section of half-size {@code r}. */
    void rod(Vector3f a, Vector3f b, float r, int rgb, boolean lit) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1.0E-4F) return;
        Matrix4f m = new Matrix4f().translate(a).rotateTowards(dir.div(len), Math.abs(dir.y) > 0.99F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0));
        box(m, -r, -r, 0.0F, r, r, len, rgb, lit);
    }

    /** A tube along +Z (after {@code model}) from z0 to z1, its colour fading from c0 to c1 (glow pass). */
    void tube(Matrix4f model, float radius, float z0, float z1, int c0, int c1, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            Vector3f p0 = model.transformPosition(new Vector3f((float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius, z0));
            Vector3f p1 = model.transformPosition(new Vector3f((float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, z0));
            Vector3f p2 = model.transformPosition(new Vector3f((float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, z1));
            Vector3f p3 = model.transformPosition(new Vector3f((float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius, z1));
            vertex(p0.x, p0.y, p0.z, c0);
            vertex(p1.x, p1.y, p1.z, c0);
            vertex(p2.x, p2.y, p2.z, c1);
            vertex(p3.x, p3.y, p3.z, c1);
        }
    }

    /** A ring in the XY plane (after {@code model}) at z, between two radii (glow pass). */
    void ring(Matrix4f model, float r0, float r1, float z, int inner, int outer, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            Vector3f p0 = model.transformPosition(new Vector3f((float) Math.cos(a0) * r0, (float) Math.sin(a0) * r0, z));
            Vector3f p1 = model.transformPosition(new Vector3f((float) Math.cos(a1) * r0, (float) Math.sin(a1) * r0, z));
            Vector3f p2 = model.transformPosition(new Vector3f((float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, z));
            Vector3f p3 = model.transformPosition(new Vector3f((float) Math.cos(a0) * r1, (float) Math.sin(a0) * r1, z));
            vertex(p0.x, p0.y, p0.z, inner);
            vertex(p1.x, p1.y, p1.z, inner);
            vertex(p2.x, p2.y, p2.z, outer);
            vertex(p3.x, p3.y, p3.z, outer);
        }
    }

    /** A soft glow that always faces the camera (glow pass). */
    void sprite(Vector3f at, float size, int argb) {
        Vector3f c = view.transformPosition(new Vector3f(at));
        int edge = argb & 0x00FFFFFF;
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            raw(c.x, c.y, c.z, argb);
            raw(c.x, c.y, c.z, argb);
            raw(c.x + (float) Math.cos(a1) * size, c.y + (float) Math.sin(a1) * size, c.z, edge);
            raw(c.x + (float) Math.cos(a0) * size, c.y + (float) Math.sin(a0) * size, c.z, edge);
        }
    }

    /** A thin camera-facing streak through a point, for lens flares (glow pass). */
    void streak(Vector3f at, float length, float width, float angle, int argb) {
        Vector3f c = view.transformPosition(new Vector3f(at));
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float nx = -dy * width, ny = dx * width;
        int edge = argb & 0x00FFFFFF;
        raw(c.x - dx * length, c.y - dy * length, c.z, edge);
        raw(c.x + nx, c.y + ny, c.z, argb);
        raw(c.x + dx * length, c.y + dy * length, c.z, edge);
        raw(c.x - nx, c.y - ny, c.z, argb);
    }

    private void raw(float x, float y, float z, int argb) {
        buf.vertex(x, y, z).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }
}
