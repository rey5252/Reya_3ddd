package com.reya.bossdamage.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reya.bossdamage.network.DamageNumberPacket;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Damage numbers that pop out of hit entities, float up and fade out over 1.5 seconds.
 * Red for normal hits, green for critical hits. They face the camera like name tags.
 */
public final class DamageNumbers {
    private static final long LIFETIME_MS = 1500L;
    private static final int MAX_NUMBERS = 64;
    private static final int NORMAL = 0xFF4040;
    private static final int CRIT = 0x55FF55;

    private record Number(Vec3 pos, float dx, float dz, String text, int color, boolean crit, long spawnedAt) {
    }

    private static final List<Number> NUMBERS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    public static void add(DamageNumberPacket packet) {
        if (NUMBERS.size() >= MAX_NUMBERS) NUMBERS.remove(0);
        // A small random sideways drift so several numbers at once don't overlap.
        float dx = (RANDOM.nextFloat() - 0.5F) * 0.6F;
        float dz = (RANDOM.nextFloat() - 0.5F) * 0.6F;
        NUMBERS.add(new Number(new Vec3(packet.x(), packet.y(), packet.z()), dx, dz, format(packet.amount()),
                packet.crit() ? CRIT : NORMAL, packet.crit(), Util.getMillis()));
    }

    public static void clear() {
        NUMBERS.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || NUMBERS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = Util.getMillis();

        Iterator<Number> it = NUMBERS.iterator();
        while (it.hasNext()) {
            Number n = it.next();
            float age = (now - n.spawnedAt()) / (float) LIFETIME_MS;
            if (age >= 1.0F) {
                it.remove();
                continue;
            }

            // Pop in bigger, settle, rise and fade.
            float pop = age < 0.12F ? 1.0F + (0.12F - age) / 0.12F * 0.8F : 1.0F;
            float scale = 0.025F * pop * (n.crit() ? 1.35F : 1.0F);
            float rise = age * 0.9F;
            float drift = Mth.sin(age * Mth.HALF_PI);
            int alpha = age < 0.6F ? 255 : (int) (255 * (1.0F - (age - 0.6F) / 0.4F));
            if (alpha < 8) continue;

            pose.pushPose();
            pose.translate(n.pos().x + n.dx() * drift - cam.x, n.pos().y + rise - cam.y, n.pos().z + n.dz() * drift - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(-scale, -scale, scale);

            float x = -font.width(n.text()) / 2.0F;
            int color = (alpha << 24) | n.color();
            int shadow = (alpha << 24);
            font.drawInBatch(n.text(), x + 1, 1, shadow, false, pose.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            font.drawInBatch(n.text(), x, 0, color, false, pose.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static String format(float amount) {
        if (amount >= 1000.0F) return BossPanelOverlay.compact(amount);
        float rounded = Math.round(amount * 10.0F) / 10.0F;
        return rounded == Math.round(rounded) ? String.valueOf(Math.round(rounded)) : String.valueOf(rounded);
    }

    private DamageNumbers() {
    }
}
