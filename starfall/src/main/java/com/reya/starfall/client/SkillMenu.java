package com.reya.starfall.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.reya.starfall.Skill;
import com.reya.starfall.StellarRemoteItem;
import com.reya.starfall.network.Net;
import com.reya.starfall.network.SelectSkillPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

/**
 * The skill menu: the world turns white and the skills float around you. Aim at one with the crosshair and
 * click to pick it; right-click for its details, then press a key to bind it to that skill.
 */
public final class SkillMenu {
    private static final float SPREAD = 48.0F;
    private static final double DISTANCE = 2.6D;
    private static final float AIM = 20.0F;

    private static boolean open;
    private static float baseYaw;
    private static int hovered = -1;
    private static int details = -1;
    private static boolean binding;
    private static float opened;

    public static boolean isOpen() {
        return open;
    }

    static boolean binding() {
        return open && binding;
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (open) {
            close();
            return;
        }
        if (mc.player == null || remote(mc.player).isEmpty()) {
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("message.starfall.hold_remote"), true);
            return;
        }
        open = true;
        baseYaw = mc.player.getYRot();
        hovered = -1;
        details = -1;
        binding = false;
        opened = mc.level == null ? 0 : mc.level.getGameTime();
        mc.player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.4F, 1.8F);
    }

    public static void close() {
        open = false;
        details = -1;
        binding = false;
    }

    static ItemStack remote(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof StellarRemoteItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Which way panel {@code i} floats, as a yaw. */
    private static float panelYaw(int i) {
        return baseYaw + (i - 1) * SPREAD;
    }

    private static Vec3 direction(float yaw, float pitch) {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!open) return;
        if (mc.player == null || mc.screen != null || remote(mc.player).isEmpty()) {
            close();
            return;
        }
        Vec3 look = mc.player.getViewVector(1.0F);
        int best = -1;
        double bestDot = Math.cos(Math.toRadians(AIM));
        for (int i = 0; i < 3; i++) {
            double dot = look.dot(direction(panelYaw(i), 0.0F));
            if (dot > bestDot) {
                bestDot = dot;
                best = i;
            }
        }
        if (best != hovered && best >= 0) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.15F, 2.0F);
        hovered = best;
    }

    /** Left click picks the skill under the crosshair; right click opens its details. */
    public static void click(boolean attack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (binding) {
            binding = false;
            return;
        }
        if (hovered < 0) return;
        if (attack) {
            Net.CHANNEL.sendToServer(new SelectSkillPacket(hovered));
            close();
        } else {
            details = details == hovered ? -1 : hovered;
            binding = details >= 0;
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 1.2F);
        }
    }

    /** While the details are open, the next key pressed becomes that skill's key. Escape keeps the old one. */
    static void key(int key, int scancode) {
        Minecraft mc = Minecraft.getInstance();
        if (!binding() || details < 0 || mc.player == null) return;
        binding = false;
        if (key == GLFW.GLFW_KEY_ESCAPE) return;
        KeyMapping mapping = Keys.SKILLS[details];
        mapping.setKey(InputConstants.getKey(key, scancode));
        KeyMapping.resetMapping();
        mc.options.save();
        Skill skill = Skill.byIndex(details);
        mc.player.displayClientMessage(Component.translatable("message.starfall.bound", skill.title(),
                mapping.getTranslatedKeyMessage()), true);
        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.3F, 1.4F);
    }

    // ------------------------------------------------------------------ drawing

    static void render(PoseStack pose, Camera camera, float partial) {
        if (!open) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float since = mc.level.getGameTime() - opened + partial;
        float fade = Math.min(1.0F, since / 6.0F);
        Matrix4f view = pose.last().pose();

        // the world turns white
        Fx veil = Fx.begin(view, camera, 1.0E6D, false, true);
        veil.veil(Fx.argb(0xF4F2FA, 0.9F * fade));
        veil.end();

        ItemStack stack = remote(mc.player);
        Skill current = StellarRemoteItem.skill(stack);
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (int i = 0; i < 3; i++) {
            Skill skill = Skill.byIndex(i);
            float yaw = panelYaw(i);
            float bob = Mth.sin(since * 0.08F + i * 2.0F) * 0.04F;
            Vec3 at = direction(yaw, 0.0F).scale(DISTANCE).add(0.0D, bob, 0.0D);
            boolean hot = i == hovered;
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(new Quaternionf().rotationYXZ(-yaw * Mth.DEG_TO_RAD, 0.0F, 0.0F));
            float scale = hot ? 1.08F : 1.0F;
            pose.scale(-0.0125F * scale, -0.0125F * scale, 0.0125F * scale);
            Matrix4f m = pose.last().pose();
            panel(m, 0, 0, 80, 46, skill.color, hot, skill == current, fade);
            drawText(font, buffers, m, skill.tag(), 0, -36, 0xFF000000 | skill.color);
            drawText(font, buffers, m, skill.title(), 0, -24, 0xFFFFFFFF);
            drawText(font, buffers, m, Component.translatable("menu.starfall.key", Keys.SKILLS[i].getTranslatedKeyMessage()), 0, 14, 0xFFB8B8C8);
            if (skill == current) drawText(font, buffers, m, Component.translatable("menu.starfall.selected"), 0, 28, 0xFF000000 | skill.color);
            else if (hot) drawText(font, buffers, m, Component.translatable("menu.starfall.pick"), 0, 28, 0xFFE0E0E8);
            buffers.endBatch();
            pose.popPose();
        }
        // hints under the panels, and the details of the skill picked with a right click
        Vec3 below = direction(baseYaw, 0.0F).scale(DISTANCE).add(0.0D, -0.95D, 0.0D);
        pose.pushPose();
        pose.translate(below.x, below.y, below.z);
        pose.mulPose(new Quaternionf().rotationYXZ(-baseYaw * Mth.DEG_TO_RAD, 0.0F, 0.0F));
        pose.scale(-0.0095F, -0.0095F, 0.0095F);
        Matrix4f m = pose.last().pose();
        if (details >= 0) {
            Skill skill = Skill.byIndex(details);
            List<FormattedCharSequence> lines = font.split(Component.translatable("menu.starfall.details." + skill.id), 220);
            int half = 12 + lines.size() * 5 + 10;
            panel(m, 0, 0, 122, half, skill.color, true, false, fade);
            drawText(font, buffers, m, skill.title(), 0, -half + 6, 0xFF000000 | skill.color);
            int y = -half + 20;
            for (FormattedCharSequence line : lines) {
                font.drawInBatch(line, -font.width(line) / 2.0F, y, 0xFFE8E8F0, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
                y += 10;
            }
            Component bind = binding ? Component.translatable("menu.starfall.press_key") : Component.translatable("menu.starfall.key", Keys.SKILLS[details].getTranslatedKeyMessage());
            drawText(font, buffers, m, bind, 0, y + 4, binding ? 0xFFFFE070 : 0xFFB8B8C8);
        } else {
            drawText(font, buffers, m, Component.translatable("menu.starfall.hint"), 0, 0, 0xFF55556A);
        }
        buffers.endBatch();
        pose.popPose();
    }

    /** A panel centred on (x, y), half-size (hw, hh), in the panel's own units. */
    private static void panel(Matrix4f m, float x, float y, float hw, float hh, int color, boolean hot, boolean selected, float fade) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        com.mojang.blaze3d.vertex.BufferBuilder buf = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        buf.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        int bg = Fx.argb(Fx.mix(0xFF0E0C16, 0xFF000000 | color, hot ? 0.28F : 0.12F) & 0xFFFFFF, 0.92F * fade);
        int edge = Fx.argb(color, (hot || selected ? 1.0F : 0.55F) * fade);
        quad(buf, m, x - hw, y - hh, x + hw, y + hh, bg);
        float b = hot ? 2.0F : 1.0F;
        quad(buf, m, x - hw, y - hh, x + hw, y - hh + b, edge);
        quad(buf, m, x - hw, y + hh - b, x + hw, y + hh, edge);
        quad(buf, m, x - hw, y - hh, x - hw + b, y + hh, edge);
        quad(buf, m, x + hw - b, y - hh, x + hw, y + hh, edge);
        quad(buf, m, x - hw + 8, y - 8, x + hw - 8, y - 7, Fx.argb(color, 0.5F * fade));
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void quad(com.mojang.blaze3d.vertex.BufferBuilder buf, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF, a = (argb >>> 24) & 0xFF;
        buf.vertex(m, x0, y0, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(m, x0, y1, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y1, 0.0F).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y0, 0.0F).color(r, g, b, a).endVertex();
    }

    private static void drawText(Font font, MultiBufferSource buffers, Matrix4f m, Component text, float x, float y, int color) {
        font.drawInBatch(text, x - font.width(text) / 2.0F, y, color, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    private SkillMenu() {
    }
}
