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
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * One shot of a film in perspective: the sky, planets, a galaxy, lit machines and soft light. Positions go
 * through the camera on the CPU, so what reaches the GPU is already in view space under an identity model-view;
 * depth is cleared before and after, so the shot can be laid over anything.
 */
final class Scene3D {
    /** The screen in GUI units, for putting labels on things. */
    final float width, height;
    final Matrix4f projection = new Matrix4f();
    final Matrix4f view = new Matrix4f();
    final Vector3f eye;
    final float near, far;
    /** How much of this shot shows over the one before it (crossfades). */
    float fade = 1.0F;

    Scene3D(float width, float height, Cam cam) {
        this.width = width;
        this.height = height;
        this.eye = new Vector3f(cam.eye);
        this.near = cam.near;
        this.far = cam.far;
        RenderSystem.backupProjectionMatrix();
        projection.setPerspective((float) Math.toRadians(cam.fov), width / height, cam.near, cam.far);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        view.setLookAt(cam.eye, cam.target, cam.up);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    void end() {
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ where things are

    Vector3f toView(Vector3f world) {
        return view.transformPosition(world, new Vector3f());
    }

    Vector3f dirToView(Vector3f direction) {
        return view.transformDirection(direction, new Vector3f()).normalize();
    }

    /** Where a world point lands on the screen, in GUI units (x, y, distance), or null when it's behind. */
    float[] project(Vector3f world) {
        Vector3f v = toView(world);
        if (v.z > -near) return null;
        float x = projection.m00() * v.x / -v.z, y = projection.m11() * v.y / -v.z;
        return new float[]{(x * 0.5F + 0.5F) * width, (0.5F - y * 0.5F) * height, -v.z};
    }

    /** How big a ball of radius r at a world point looks: its radius on screen in GUI units (0 if behind). */
    float screenRadius(Vector3f world, float r) {
        Vector3f v = toView(world);
        if (v.z > -near) return 0.0F;
        return r / -v.z * projection.m11() * height * 0.5F;
    }

    /** How big something at view depth z has to be to span {@code frac} of the screen's height. */
    float viewSize(float frac, float z) {
        return frac * 2.0F * Math.abs(z) / projection.m11();
    }

    /** A quad given straight in clip space, for the shaders that trace their own rays. */
    private static void clipQuad(float x0, float y0, float x1, float y1, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(x0, y0, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, a).endVertex();
        b.vertex(x1, y0, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, a).endVertex();
        b.vertex(x1, y1, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, a).endVertex();
        b.vertex(x0, y1, 0.0D).uv(0.0F, 0.0F).color(255, 255, 255, a).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /** The part of the screen (in clip space) that a ball of radius r around view-space point c can cover. */
    private float[] bounds(Vector3f c, float r) {
        if (c.z - r > -near) return null;
        if (c.z + r > -near * 4.0F) return new float[]{-1.0F, -1.0F, 1.0F, 1.0F};
        float x0 = 1.0F, y0 = 1.0F, x1 = -1.0F, y1 = -1.0F;
        for (int i = 0; i < 8; i++) {
            float x = c.x + ((i & 1) == 0 ? -r : r), y = c.y + ((i & 2) == 0 ? -r : r), z = c.z + ((i & 4) == 0 ? -r : r);
            float nx = projection.m00() * x / -z, ny = projection.m11() * y / -z;
            x0 = Math.min(x0, nx);
            x1 = Math.max(x1, nx);
            y0 = Math.min(y0, ny);
            y1 = Math.max(y1, ny);
        }
        x0 = Math.max(-1.0F, x0 - 0.01F);
        y0 = Math.max(-1.0F, y0 - 0.01F);
        x1 = Math.min(1.0F, x1 + 0.01F);
        y1 = Math.min(1.0F, y1 + 0.01F);
        return x0 < x1 && y0 < y1 ? new float[]{x0, y0, x1, y1} : null;
    }

    // ------------------------------------------------------------------ the sky

    static final class Sky {
        int nebulaA = 0x1A1F4D, nebulaB = 0x3A0F33;
        float nebula = 1.0F;
        final Vector3f band = new Vector3f(0.3F, 0.9F, 0.2F);
        float bandStrength = 0.6F;
        float seed = 3.0F;
        float stars = 1.0F;
    }

    /** Deep space: the shader's nebulae and the galaxy's band, then the stars over them. */
    void sky(Sky s) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        ShaderInstance sh = FilmGfx.sky;
        if (sh != null) {
            if (fade < 1.0F) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            } else {
                RenderSystem.disableBlend();
            }
            sh.safeGetUniform("ViewX").set(view.m00(), view.m10(), view.m20());
            sh.safeGetUniform("ViewY").set(view.m01(), view.m11(), view.m21());
            sh.safeGetUniform("ViewZ").set(view.m02(), view.m12(), view.m22());
            sh.safeGetUniform("NebulaA").set(r(s.nebulaA), g(s.nebulaA), b(s.nebulaA));
            sh.safeGetUniform("NebulaB").set(r(s.nebulaB), g(s.nebulaB), b(s.nebulaB));
            sh.safeGetUniform("Band").set(s.band.x, s.band.y, s.band.z, s.bandStrength);
            sh.safeGetUniform("Nebula").set(s.nebula);
            sh.safeGetUniform("Seed").set(s.seed);
            sh.safeGetUniform("Fade").set(1.0F);
            RenderSystem.setShader(() -> sh);
            clipQuad(-1.0F, -1.0F, 1.0F, 1.0F, fade);
        } else {
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            b.vertex(-1.0D, -1.0D, 0.0D).color(2, 3, 10, 255).endVertex();
            b.vertex(1.0D, -1.0D, 0.0D).color(2, 3, 10, 255).endVertex();
            b.vertex(1.0D, 1.0D, 0.0D).color(2, 3, 10, 255).endVertex();
            b.vertex(-1.0D, 1.0D, 0.0D).color(2, 3, 10, 255).endVertex();
            Matrix4f saved = new Matrix4f(RenderSystem.getProjectionMatrix());
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.DISTANCE_TO_ORIGIN);
            BufferUploader.drawWithShader(b.end());
            RenderSystem.setProjectionMatrix(saved, VertexSorting.DISTANCE_TO_ORIGIN);
        }
        if (s.stars > 0.0F) Stars.draw(this, s.stars * fade);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    // ------------------------------------------------------------------ planets

    static final class Planet {
        static final int EARTH = 0, JUPITER = 1, SATURN = 2, MOON = 3;
        final Vector3f center = new Vector3f();
        float radius = 1.0F;
        int kind;
        /** The axis is tilted about the world's z, then the planet turns about it. */
        float tilt, spin;
        /** Towards the sun, in the world. */
        final Vector3f sun = new Vector3f(-0.6F, 0.35F, 0.7F);
        int atmosphere = 0x4D8CFF;
        float atmosphereHeight;
        boolean ring;
        float time, fade = 1.0F, night = 1.0F;
        /** A glowing mark on the ground (a laser's spot): a direction in the world, its angular size, colour. */
        Vector3f spot;
        float spotSize = 0.01F;
        int spotColor;
        float spotStrength;

        Vector3f axisY() {
            return new Vector3f((float) -Math.sin(tilt), (float) Math.cos(tilt), 0.0F);
        }

        Vector3f axisX() {
            float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt), cs = (float) Math.cos(spin), ss = (float) Math.sin(spin);
            return new Vector3f(ct * cs, st * cs, -ss);
        }

        Vector3f axisZ() {
            float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt), cs = (float) Math.cos(spin), ss = (float) Math.sin(spin);
            return new Vector3f(ct * ss, st * ss, cs);
        }

        /** A point on the surface in the world, from a direction in the world. */
        Vector3f surface(Vector3f direction) {
            return new Vector3f(direction).normalize().mul(radius).add(center);
        }
    }

    void planet(Planet p) {
        Vector3f c = toView(p.center);
        float reach = p.radius * (p.ring ? 2.36F : 1.0F + p.atmosphereHeight * 3.0F + 0.06F);
        float[] clip = bounds(c, reach);
        if (clip == null) return;
        ShaderInstance sh = FilmGfx.planet;
        if (sh == null) {
            plainPlanet(p, c);
            return;
        }
        Vector3f ax = dirToView(p.axisX()), ay = dirToView(p.axisY()), az = dirToView(p.axisZ());
        Vector3f sun = dirToView(p.sun);
        sh.safeGetUniform("Center").set(c.x, c.y, c.z);
        sh.safeGetUniform("Radius").set(p.radius);
        sh.safeGetUniform("AxisX").set(ax.x, ax.y, ax.z);
        sh.safeGetUniform("AxisY").set(ay.x, ay.y, ay.z);
        sh.safeGetUniform("AxisZ").set(az.x, az.y, az.z);
        sh.safeGetUniform("SunDir").set(sun.x, sun.y, sun.z);
        sh.safeGetUniform("Kind").set((float) p.kind);
        sh.safeGetUniform("Time").set(p.time);
        sh.safeGetUniform("Atmo").set(r(p.atmosphere), g(p.atmosphere), b(p.atmosphere), p.atmosphereHeight);
        sh.safeGetUniform("Ring").set(1.24F, 2.27F, 1.0F, p.ring ? 1.0F : 0.0F);
        sh.safeGetUniform("Fade").set(p.fade * fade);
        sh.safeGetUniform("Night").set(p.night);
        if (p.spot != null && p.spotStrength > 0.0F) {
            Vector3f d = new Vector3f(p.spot).normalize();
            Vector3f wx = p.axisX(), wy = p.axisY(), wz = p.axisZ();
            sh.safeGetUniform("Spot").set(d.dot(wx), d.dot(wy), d.dot(wz), p.spotSize);
            float k = p.spotStrength;
            sh.safeGetUniform("SpotColor").set(r(p.spotColor) * k, g(p.spotColor) * k, b(p.spotColor) * k);
        } else {
            sh.safeGetUniform("Spot").set(0.0F, 1.0F, 0.0F, 0.01F);
            sh.safeGetUniform("SpotColor").set(0.0F, 0.0F, 0.0F);
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(() -> sh);
        clipQuad(clip[0], clip[1], clip[2], clip[3], 1.0F);
        RenderSystem.defaultBlendFunc();
    }

    /** Without the planet shader: a shaded disc facing the camera, in the planet's main colour. */
    private void plainPlanet(Planet p, Vector3f c) {
        int base = switch (p.kind) {
            case Planet.EARTH -> 0x2E6EB0;
            case Planet.JUPITER -> 0xD9B88F;
            case Planet.SATURN -> 0xD8BE8A;
            default -> 0x9A9A9A;
        };
        Soft s = soft(false);
        float r = p.radius;
        s.discView(c, r, Fx.argb(base, p.fade), Fx.argb(Fx.mix(0xFF000000 | base, 0xFF000000, 0.6F) & 0xFFFFFF, p.fade));
        s.end();
    }

    // ------------------------------------------------------------------ a galaxy

    /** A galaxy's disc in the plane of u and v around a centre, drawn as light. */
    void galaxy(Vector3f center, Vector3f u, Vector3f v, float radius, float spin, float fade) {
        ShaderInstance sh = FilmGfx.galaxy;
        if (sh == null || fade <= 0.0F) return;
        sh.safeGetUniform("Spin").set(spin);
        sh.safeGetUniform("Fade").set(fade * this.fade);
        Vector3f eu = new Vector3f(u).normalize().mul(radius), ev = new Vector3f(v).normalize().mul(radius);
        Vector3f[] corners = {
                new Vector3f(center).sub(eu).sub(ev), new Vector3f(center).add(eu).sub(ev),
                new Vector3f(center).add(eu).add(ev), new Vector3f(center).sub(eu).add(ev)};
        float[][] uv = {{-1.0F, -1.0F}, {1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}};
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 4; i++) {
            Vector3f q = toView(corners[i]);
            b.vertex(q.x, q.y, q.z).uv(uv[i][0], uv[i][1]).color(255, 255, 255, 255).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------------ lit metal and soft light

    static final class Light {
        /** Towards the sun, in the world. */
        final Vector3f sun = new Vector3f(-0.4F, 0.5F, -0.7F);
        int sunColor = 0xFFF6EC;
        float sunStrength = 1.3F;
        int fill = 0x282A40;
        /** The charge's glow: a point light. */
        final Vector3f glow = new Vector3f();
        int glowColor = 0xFF3A30;
        float glowStrength, glowRange = 30.0F;
        float charge, time;
    }

    void mesh(Mesh mesh, Matrix4f model, Light light) {
        mesh.draw(this, model, light);
    }

    Soft soft(boolean additive) {
        return new Soft(this, additive);
    }

    static float r(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    static float g(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    static float b(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }
}
