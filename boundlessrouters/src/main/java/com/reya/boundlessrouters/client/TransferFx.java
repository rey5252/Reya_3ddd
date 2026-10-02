package com.reya.boundlessrouters.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.boundlessrouters.BoundlessRouters;
import com.reya.boundlessrouters.network.TransferFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What a player sees of a router at work: for each thing a module moves, a line of light in the module's colour from
 * where it went from to where it went, light running along it, and the item itself flying along it in a little arc;
 * a puff of colour where it lands, then the line fades.
 */
@Mod.EventBusSubscriber(modid = BoundlessRouters.MODID, value = Dist.CLIENT)
public final class TransferFx {
    private static final int FADE = 8, MAX = 512;
    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static ClientLevel level;

    private static final class Flight {
        final Vec3 from, to;
        final ItemStack item;
        final float r, g, b;
        final long start;
        final int duration;
        final double length;
        boolean landed;

        Flight(TransferFxPacket.Flight f, long start) {
            this.from = f.from();
            this.to = f.to();
            this.item = f.item();
            this.r = (f.colour() >> 16 & 255) / 255.0F;
            this.g = (f.colour() >> 8 & 255) / 255.0F;
            this.b = (f.colour() & 255) / 255.0F;
            this.start = start;
            this.length = from.distanceTo(to);
            this.duration = Mth.clamp((int) (6 + length * 1.1D), 8, 36);
        }

        /** How bright the line is at an age, in ticks: up quickly, full while the item flies, then fading. */
        float alpha(double age) {
            float in = (float) Mth.clamp(age / 3.0D, 0.0D, 1.0D);
            float out = age <= duration ? 1.0F : (float) Mth.clamp(1.0D - (age - duration) / FADE, 0.0D, 1.0D);
            return in * out;
        }

        /** Where the item is at an age: along the line, eased, lifted in an arc. */
        Vec3 itemAt(double age) {
            double t = Mth.clamp(age / duration, 0.0D, 1.0D);
            double eased = t * t * (3.0D - 2.0D * t);
            double arc = (0.25D + Math.min(1.0D, length * 0.08D)) * Math.sin(Math.PI * t);
            return from.lerp(to, eased).add(0.0D, arc, 0.0D);
        }
    }

    /** From the server: what a router near this player just moved. */
    public static void add(List<TransferFxPacket.Flight> flights) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (level != mc.level) {
            FLIGHTS.clear();
            level = mc.level;
        }
        long now = mc.level.getGameTime();
        for (TransferFxPacket.Flight f : flights) {
            if (FLIGHTS.size() >= MAX) FLIGHTS.remove(0);
            FLIGHTS.add(new Flight(f, now));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FLIGHTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) {
            FLIGHTS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            long age = now - f.start;
            if (!f.landed && age >= f.duration) {
                f.landed = true;
                // a puff of the module's colour where the item lands
                DustParticleOptions dust = new DustParticleOptions(new Vector3f(f.r, f.g, f.b), 0.8F);
                for (int i = 0; i < 4; i++) {
                    mc.level.addParticle(dust, f.to.x + (mc.level.random.nextDouble() - 0.5D) * 0.4D,
                            f.to.y + (mc.level.random.nextDouble() - 0.5D) * 0.4D, f.to.z + (mc.level.random.nextDouble() - 0.5D) * 0.4D,
                            0.0D, 0.0D, 0.0D);
                }
            }
            if (age > f.duration + FADE || age < 0) it.remove();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || FLIGHTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        double now = mc.level.getGameTime() + event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // the lines, glowing (added light), with light running along them toward where the item goes
        VertexConsumer light = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        for (Flight f : FLIGHTS) {
            double age = now - f.start;
            float alpha = f.alpha(age);
            if (alpha <= 0.0F || f.length < 0.05D) continue;
            Vec3 a = f.from.subtract(cam), b = f.to.subtract(cam);
            glow(m, light, a, b, 0.09D, f.r, f.g, f.b, 0.35F * alpha);
            glow(m, light, a, b, 0.022D, mix(f.r), mix(f.g), mix(f.b), 0.85F * alpha);
            int dashes = Math.max(1, (int) (f.length / 0.8D));
            for (int k = 0; k < dashes; k++) {
                double t = (age * 0.06D + k / (double) dashes) % 1.0D;
                double half = Math.min(0.12D, 0.5D / dashes) / Math.max(0.05D, f.length);
                Vec3 p = a.lerp(b, Math.max(0.0D, t - half)), q = a.lerp(b, Math.min(1.0D, t + half));
                glow(m, light, p, q, 0.05D, 1.0F, 1.0F, 1.0F, 0.6F * alpha);
            }
        }
        buffers.endBatch(RenderType.lightning());

        // the items on their way
        for (Flight f : FLIGHTS) {
            double age = now - f.start;
            if (age < 0.0D || age > f.duration) continue;
            Vec3 p = f.itemAt(age).subtract(cam);
            pose.pushPose();
            pose.translate(p.x, p.y - 0.12D, p.z);
            pose.mulPose(Axis.YP.rotationDegrees((float) (age * 14.0D % 360.0D)));
            pose.scale(1.3F, 1.3F, 1.3F);
            mc.getItemRenderer().renderStatic(f.item, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                    buffers, mc.level, 0);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static float mix(float c) {
        return c + (1.0F - c) * 0.6F;
    }

    /**
     * A band of light from a to b (relative to the camera), turned to face it: bright along its middle, fading to
     * nothing at its edges, w either side.
     */
    private static void glow(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, double w, float r, float g, float bl, float alpha) {
        Vec3 side = b.subtract(a).cross(a.add(b).scale(0.5D));
        double len = side.length();
        if (len < 1.0E-7D) return;
        side = side.scale(w / len);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 ea = a.add(side.scale(s)), eb = b.add(side.scale(s));
            // both windings, so it is seen from either side
            vertex(m, vc, a, r, g, bl, alpha);
            vertex(m, vc, b, r, g, bl, alpha);
            vertex(m, vc, eb, r, g, bl, 0.0F);
            vertex(m, vc, ea, r, g, bl, 0.0F);
            vertex(m, vc, ea, r, g, bl, 0.0F);
            vertex(m, vc, eb, r, g, bl, 0.0F);
            vertex(m, vc, b, r, g, bl, alpha);
            vertex(m, vc, a, r, g, bl, alpha);
        }
    }

    private static void vertex(Matrix4f m, VertexConsumer vc, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    private TransferFx() {
    }
}
