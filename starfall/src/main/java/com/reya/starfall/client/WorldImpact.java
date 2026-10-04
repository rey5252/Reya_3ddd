package com.reya.starfall.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * The world's own picture reels while a strike lands near you: it smears outwards from where the strike is,
 * the colours part, it drains to the strike's colour and it shakes. Done before the HUD, over the finished frame.
 */
final class WorldImpact {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static TextureTarget copy;
    private static boolean broken;
    private static final Matrix4f VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    /** The camera of the frame being drawn, to find where on the screen a strike is. */
    static void remember(Matrix4f view, Matrix4f projection, Vec3 cam) {
        VIEW.set(view);
        PROJECTION.set(projection);
        camera = cam;
    }

    static void compose(float partial) {
        if (broken || FilmGfx.worldImpact == null || FilmGfx.strikeGlow == null || Film.active()) return;
        ClientStrike.Reel r = ClientStrikes.reel(partial);
        if (r == null || r.weight() < 0.01F) return;
        float strength = Math.min(1.0F, ClientConfig.flash() * 1.5F);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        try {
            if (copy == null) {
                copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            } else if (copy.width != main.width || copy.height != main.height) {
                copy.resize(main.width, main.height, Minecraft.ON_OSX);
            }
        } catch (RuntimeException e) {
            LOGGER.error("Starfall: no buffer for the impact's reel, leaving it out", e);
            broken = true;
            main.bindWrite(true);
            return;
        }
        // where it lands on the screen; behind you it reels from the middle, less
        float cx = 0.5F, cy = 0.5F, k = strength;
        Vector3f v = VIEW.transformPosition((float) (r.at().x - camera.x), (float) (r.at().y - camera.y), (float) (r.at().z - camera.z), new Vector3f());
        Vector4f clip = PROJECTION.transform(new Vector4f(v, 1.0F));
        if (clip.w > 0.01F) {
            cx = Mth.clamp(clip.x / clip.w * 0.5F + 0.5F, -0.5F, 1.5F);
            cy = Mth.clamp(clip.y / clip.w * 0.5F + 0.5F, -0.5F, 1.5F);
        } else {
            k *= 0.5F;
        }
        float time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() % 24000L) + partial;
        float shake = ClientStrikes.shake(partial) * 0.0012F;
        main.setFilterMode(GL11.GL_LINEAR);
        ShaderInstance s = FilmGfx.worldImpact;
        s.safeGetUniform("Centre").set(cx, cy);
        s.safeGetUniform("Zoom").set(r.zoom() * k);
        s.safeGetUniform("Aberration").set(r.aberration() * k);
        s.safeGetUniform("Tint").set(Scene3D.r(r.tint()), Scene3D.g(r.tint()), Scene3D.b(r.tint()), r.tintAmount() * k);
        s.safeGetUniform("Desaturate").set(r.desaturate() * k);
        s.safeGetUniform("Shake").set(shake * Mth.sin(time * 2.9F), shake * Mth.cos(time * 3.7F));
        s.safeGetUniform("Time").set(time);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        BloomChain.pass(copy, s, main.getColorTextureId(), 0);
        main.setFilterMode(GL11.GL_NEAREST);
        // and back onto the screen as it is
        ShaderInstance c = FilmGfx.strikeGlow;
        c.safeGetUniform("Strength").set(1.0F);
        RenderSystem.disableBlend();
        BloomChain.pass(main, c, copy.getColorTextureId(), 0);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private WorldImpact() {
    }
}
