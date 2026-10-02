package com.reya.singularityfusion.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reya.singularityfusion.SingularityFusion;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.FusionGeometry;
import com.reya.singularityfusion.block.FusionStatus;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import com.reya.singularityfusion.gui.Layouts;
import com.reya.singularityfusion.menu.FusionCoreMenu;
import com.reya.singularityfusion.network.Net;
import com.reya.singularityfusion.network.StartFusionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * The fusion core's screen. In a round viewport in the middle, among the stars, the singularity as it hangs over the
 * core: as big as the core is full (catching up smoothly), its disk turning, collapsing as a fusion ends. Round it the
 * pylons' ingredients in two curved columns, lines of light running from them into the viewport; the energy on the
 * left, the fusion's progress on the right; under the viewport the catalyst, the start button and the output (what
 * the catalyst and the pylons make shown faintly in it); what the core is doing, and what the fusion costs, under them.
 */
public class FusionCoreScreen extends AbstractContainerScreen<FusionCoreMenu> {
    private static final ResourceLocation PANEL = new ResourceLocation(SingularityFusion.MODID, "textures/gui/fusion_core.png");
    private static final ResourceLocation DISK = Fx.effect("disk"), RING = Fx.effect("ring"), GLOW = Fx.effect("glow"), SPARK = Fx.effect("spark");
    private static final int TEXT = 0xFFE6E0F2, TEXT_DIM = 0xFF9C93B4, TEXT_TITLE = 0xFFF0D9A8;
    /** The singularity in the viewport: its shadow's radius when full (pixels), the disk's tilt, its edges (in radii). */
    private static final float HOLE_R = 10.0F, TILT = 0.24F, DISK_IN = 1.55F, DISK_OUT = 3.3F;

    private float shownCharge = -1.0F, shownEnergy = -1.0F, spin;
    private long lastNanos;

    public FusionCoreScreen(FusionCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = Layouts.W;
        imageHeight = Layouts.H;
        inventoryLabelX = Layouts.INV_X;
        inventoryLabelY = Layouts.INV_Y - 11;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        tooltips(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(PANEL, x, y, 0, 0, Layouts.W, Layouts.H, Layouts.W, Layouts.TEX_H);
        FusionCoreBlockEntity core = menu.core();
        float time = (System.currentTimeMillis() % 1000000L) / 50.0F;

        // what is shown catches up with the core smoothly
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Mth.clamp((now - lastNanos) / 1.0E9F, 0.0F, 0.25F);
        lastNanos = now;
        float charge = core == null ? 0.0F : core.charge();
        float energy = core == null ? 0.0F : (float) Mth.clamp(core.energy() / (double) Math.max(1L, core.capacity()), 0.0D, 1.0D);
        shownCharge = shownCharge < 0.0F ? charge : shownCharge + (charge - shownCharge) * (1.0F - (float) Math.exp(-dt * 2.0F));
        shownEnergy = shownEnergy < 0.0F ? energy : shownEnergy + (energy - shownEnergy) * (1.0F - (float) Math.exp(-dt * 6.0F));
        boolean fusing = core != null && core.fusing();
        float progress = core == null ? 0.0F : core.progress(partialTick);
        spin = (spin + dt * (0.05F + 0.12F * shownCharge + (fusing ? 0.25F + 0.7F * FusionGeometry.collapse(progress) : 0.0F))) % 8.0F;

        bars(g, x, y, progress);
        viewport(g, x + Layouts.VIEW[0], y + Layouts.VIEW[1], time, fusing ? FusionGeometry.collapse(progress) : 0.0F, fusing);
        g.blit(PANEL, x + Layouts.VIEW[0] - Layouts.MASK_SIZE / 2, y + Layouts.VIEW[1] - Layouts.MASK_SIZE / 2, Layouts.SHEET_MASK[0],
                Layouts.SHEET_MASK[1], Layouts.MASK_SIZE, Layouts.MASK_SIZE, Layouts.W, Layouts.TEX_H);
        pylons(g, core, x, y, time, fusing, progress);
        startButton(g, core, x, y, mouseX, mouseY);
        preview(g, core, x, y);
    }

    /** The energy (left, filling from the bottom) and the fusion's progress (right). */
    private void bars(GuiGraphics g, int x, int y, float progress) {
        int[] e = Layouts.ENERGY, p = Layouts.PROGRESS;
        int fill = Math.round(e[3] * shownEnergy);
        if (fill > 0) {
            g.blit(PANEL, x + e[0], y + e[1] + e[3] - fill, Layouts.SHEET_BAR[0], Layouts.SHEET_BAR[1] + e[3] - fill, e[2], fill, Layouts.W, Layouts.TEX_H);
        }
        fill = Math.round(p[3] * progress);
        if (fill > 0) {
            g.blit(PANEL, x + p[0], y + p[1] + p[3] - fill, Layouts.SHEET_BAR[0] + e[2], Layouts.SHEET_BAR[1] + p[3] - fill, p[2], fill, Layouts.W,
                    Layouts.TEX_H);
        }
    }

    /**
     * The pylons' frames with their ingredients (empty frames where there are fewer pylons), and a line of light from
     * each pylon into the viewport: faint while it waits, running bright while it pours its item in.
     */
    private void pylons(GuiGraphics g, FusionCoreBlockEntity core, int x, int y, float time, boolean fusing, float progress) {
        List<BlockPos> at = core == null ? List.of() : core.pylons();
        Level level = Minecraft.getInstance().level;
        int shown = Math.min(at.size(), Layouts.PYLONS.length);
        for (int i = 0; i < Layouts.PYLONS.length; i++) {
            int[] p = Layouts.PYLONS[i];
            g.blit(PANEL, x + p[0] - 1, y + p[1] - 1, Layouts.SHEET_PYLON[0] + (i < shown ? 18 : 0), Layouts.SHEET_PYLON[1], 18, 18, Layouts.W,
                    Layouts.TEX_H);
        }
        // the lines first (light added under the items)
        g.flush();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        float cx = x + Layouts.VIEW[0], cy = y + Layouts.VIEW[1];
        for (int i = 0; i < shown; i++) {
            int[] p = Layouts.PYLONS[i];
            boolean left = i < Layouts.PYLONS.length / 2;
            float sx = x + p[0] + (left ? 17.0F : -1.0F), sy = y + p[1] + 8.0F;
            float dx = cx - sx, dy = cy - sy, len = Mth.sqrt(dx * dx + dy * dy);
            float ex = cx - dx / len * (Layouts.VIEW_R + 3.0F), ey = cy - dy / len * (Layouts.VIEW_R + 3.0F);
            float k = 0.16F + 0.3F * shownCharge;
            float departure = FusionGeometry.departure(i, at.size());
            if (fusing && progress >= FusionGeometry.BEAMS_IN) k = progress < departure + FusionGeometry.FLIGHT ? 0.9F : 0.45F;
            line(b, m, sx, sy, ex, ey, 0.55F * k, 0.25F * k, k);
            if (fusing && progress >= FusionGeometry.BEAMS_IN) {
                // pulses running along it into the viewport
                for (int j = 0; j < 3; j++) {
                    float t = (time * 0.05F + j / 3.0F + i * 0.13F) % 1.0F;
                    float px = Mth.lerp(t, sx, ex), py = Mth.lerp(t, sy, ey);
                    dot(b, m, px, py, 1.3F, 0.9F * k, 0.75F * k, k);
                }
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        // the items on them (gone once they have flown)
        for (int i = 0; i < shown; i++) {
            if (level == null || !(level.getBlockEntity(at.get(i)) instanceof GravitonPylonBlockEntity pylon) || pylon.item().isEmpty()) continue;
            if (fusing && progress >= FusionGeometry.departure(i, at.size())) continue;
            int[] p = Layouts.PYLONS[i];
            g.renderItem(pylon.item(), x + p[0], y + p[1]);
        }
        if (at.size() > Layouts.PYLONS.length) {
            Ui.centred(g, font, Component.literal("+" + (at.size() - Layouts.PYLONS.length)), x + Layouts.PYLONS[11][0] + 8,
                    y + Layouts.PYLONS[11][1] + 20, TEXT_DIM);
        }
    }

    private static void line(BufferBuilder b, Matrix4f m, float x1, float y1, float x2, float y2, float r, float g, float bl) {
        float dx = x2 - x1, dy = y2 - y1, len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        float nx = -dy / len * 0.6F, ny = dx / len * 0.6F;
        b.vertex(m, x1 + nx, y1 + ny, 0.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, x2 + nx, y2 + ny, 0.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, x2 - nx, y2 - ny, 0.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, x1 - nx, y1 - ny, 0.0F).color(r, g, bl, 1.0F).endVertex();
    }

    private static void dot(BufferBuilder b, Matrix4f m, float x, float y, float s, float r, float g, float bl) {
        b.vertex(m, x - s, y - s, 0.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, x - s, y + s, 0.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, x + s, y + s, 0.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, x + s, y - s, 0.0F).color(r, g, bl, 1.0F).endVertex();
    }

    /**
     * The singularity in the viewport, drawn as in the world: its glow, the far half of its disk, the shadow over it,
     * the arcs of bent light and the photon ring round the shadow, then the disk's near half across it, and sparks
     * falling in. The viewport's ring is drawn over it afterwards (its mask), so nothing spills out.
     */
    private void viewport(GuiGraphics g, float cx, float cy, float time, float collapse, boolean fusing) {
        float c = shownCharge;
        float radius = HOLE_R * c * (1.0F - 0.4F * collapse);
        if (radius < 0.05F) return;
        float bright = Fx.smooth(0.0F, 0.2F, c) * (0.75F + 0.25F * c) * (1.0F + 0.7F * collapse);
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();

        // glows: warm round the disk, violet round it all
        RenderSystem.setShaderTexture(0, GLOW);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float aura = Fx.smooth(0.12F, 0.5F, c) * 0.55F, k = bright * 0.4F;
        texQuad(b, m, cx, cy, Math.min(38.0F, 3.4F * radius + 3.0F), 0.45F * k, 0.29F * k, 0.15F * k);
        texQuad(b, m, cx, cy, Math.min(40.0F, 4.6F * radius + 9.0F), 0.3F * aura, 0.09F * aura, 0.52F * aura);
        BufferUploader.drawWithShader(b.end());

        // the far half of the disk
        RenderSystem.setShaderTexture(0, DISK);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        disk(b, m, cx, cy, radius, bright, 0.0F);
        BufferUploader.drawWithShader(b.end());

        // the shadow over it
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 40;
        for (int j = 0; j < segments; j++) {
            float a0 = 2.0F * Fx.PI * j / segments, a1 = 2.0F * Fx.PI * (j + 1) / segments;
            b.vertex(m, cx, cy, 0.0F).color(0, 0, 0, 255).endVertex();
            b.vertex(m, cx + radius * Mth.cos(a1), cy + radius * Mth.sin(a1), 0.0F).color(0, 0, 0, 255).endVertex();
            b.vertex(m, cx + radius * Mth.cos(a0), cy + radius * Mth.sin(a0), 0.0F).color(0, 0, 0, 255).endVertex();
            b.vertex(m, cx + radius * Mth.cos(a0), cy + radius * Mth.sin(a0), 0.0F).color(0, 0, 0, 255).endVertex();
        }
        BufferUploader.drawWithShader(b.end());

        // the bent light round the shadow, the photon ring, then the disk's near half
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, DISK);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        arcs(b, m, cx, cy, radius, bright);
        disk(b, m, cx, cy, radius, bright, Fx.PI);
        BufferUploader.drawWithShader(b.end());
        RenderSystem.setShaderTexture(0, RING);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        annulus(b, m, cx, cy, 0.985F * radius, 1.12F * radius, 0.0F, 2.0F * Fx.PI, 48, 4.0F, spin * 0.5F, 0.0F, 1.0F, bright * 1.05F, false);
        BufferUploader.drawWithShader(b.end());

        // sparks spiralling in
        float sparks = Fx.smooth(0.25F, 0.7F, c) + (fusing ? 0.4F : 0.0F);
        if (sparks > 0.0F) {
            RenderSystem.setShaderTexture(0, SPARK);
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int count = Math.min(30, (int) (22.0F * sparks));
            for (int s = 0; s < count; s++) {
                float period = 70.0F + 60.0F * Fx.hash(s, 1, 9);
                float clock = time + period * Fx.hash(s, 2, 9);
                int cycle = (int) (clock / period);
                float life = clock / period - cycle;
                float r0 = radius * (2.1F + 1.4F * Fx.hash(s, cycle, 3));
                float at = r0 + (radius * 1.02F - r0) * (float) Math.pow(life, 1.6D);
                float angle = Fx.hash(s, cycle, 4) * 2.0F * Fx.PI - life * (2.5F + 2.0F * Fx.hash(s, cycle, 5)) * Fx.PI;
                float px = cx + at * Mth.cos(angle), py = cy - at * Mth.sin(angle) * Mth.sin(TILT);
                if (Mth.sin(angle) > 0.0F && Math.abs(px - cx) < radius) continue;       // behind the shadow
                float fade = Fx.smooth(0.0F, 0.15F, life) * (1.0F - Fx.smooth(0.85F, 1.0F, life)) * Math.min(1.0F, bright * 1.4F);
                boolean hot = Fx.hash(s, cycle, 8) < 0.6F;
                texQuad(b, m, px, py, 1.2F + 0.8F * Fx.hash(s, cycle, 7), (hot ? 1.0F : 0.68F) * fade, (hot ? 0.74F : 0.38F) * fade,
                        (hot ? 0.42F : 1.0F) * fade);
            }
            BufferUploader.drawWithShader(b.end());
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    /** Half the disk: the far half (from 0) or the near (from pi), in three bands turning at their speeds. */
    private void disk(BufferBuilder b, Matrix4f m, float cx, float cy, float radius, float bright, float from) {
        float squash = Mth.sin(TILT);
        float[] speeds = {1.0F, 0.625F, 0.375F};
        for (int band = 0; band < 3; band++) {
            float v0 = band / 3.0F, v1 = (band + 1) / 3.0F;
            float r0 = radius * Mth.lerp(v0, DISK_IN, DISK_OUT), r1 = radius * Mth.lerp(v1, DISK_IN, DISK_OUT);
            int segments = 24;
            float phase = spin * speeds[band] % 1.0F;
            for (int j = 0; j < segments; j++) {
                float a0 = from + Fx.PI * j / segments, a1 = from + Fx.PI * (j + 1) / segments;
                float u0 = a0 / (2.0F * Fx.PI) * 3.0F + phase, u1 = a1 / (2.0F * Fx.PI) * 3.0F + phase;
                float k0 = bright * (1.0F + 0.3F * Mth.cos(a0)) * (1.0F + 0.25F * Math.max(0.0F, -Mth.sin(a0)));
                float k1 = bright * (1.0F + 0.3F * Mth.cos(a1)) * (1.0F + 0.25F * Math.max(0.0F, -Mth.sin(a1)));
                vertex(b, m, cx + r0 * Mth.cos(a0), cy - r0 * Mth.sin(a0) * squash, u0, v0, k0);
                vertex(b, m, cx + r0 * Mth.cos(a1), cy - r0 * Mth.sin(a1) * squash, u1, v0, k1);
                vertex(b, m, cx + r1 * Mth.cos(a1), cy - r1 * Mth.sin(a1) * squash, u1, v1, k1);
                vertex(b, m, cx + r1 * Mth.cos(a0), cy - r1 * Mth.sin(a0) * squash, u0, v1, k0);
            }
        }
    }

    /** The light of the disk's far side bent over the shadow's top and, fainter, under it. */
    private void arcs(BufferBuilder b, Matrix4f m, float cx, float cy, float radius, float bright) {
        annulus(b, m, cx, cy, 1.04F * radius, 1.5F * radius, 0.0F, Fx.PI, 24, 1.5F, spin, 0.0F, 0.62F, bright * 0.95F, false);
        annulus(b, m, cx, cy, 1.03F * radius, 1.24F * radius, Fx.PI, 2.0F * Fx.PI, 24, 1.5F, spin, 0.0F, 0.4F, bright * 0.5F, true);
    }

    /** A ring (or part of one) round (cx, cy), up being angle pi/2: u round it (`repeat` times a turn), v from in to out. */
    private static void annulus(BufferBuilder b, Matrix4f m, float cx, float cy, float in, float out, float from, float to, int segments, float repeat,
                                float phase, float vIn, float vOut, float k, boolean backwards) {
        for (int j = 0; j < segments; j++) {
            float a0 = Mth.lerp(j / (float) segments, from, to), a1 = Mth.lerp((j + 1) / (float) segments, from, to);
            float t0 = (a0 - from) / (2.0F * Fx.PI) * repeat, t1 = (a1 - from) / (2.0F * Fx.PI) * repeat;
            float u0 = (backwards ? -t0 : t0) + phase % 1.0F, u1 = (backwards ? -t1 : t1) + phase % 1.0F;
            vertex(b, m, cx + in * Mth.cos(a0), cy - in * Mth.sin(a0), u0, vIn, k);
            vertex(b, m, cx + in * Mth.cos(a1), cy - in * Mth.sin(a1), u1, vIn, k);
            vertex(b, m, cx + out * Mth.cos(a1), cy - out * Mth.sin(a1), u1, vOut, k);
            vertex(b, m, cx + out * Mth.cos(a0), cy - out * Mth.sin(a0), u0, vOut, k);
        }
    }

    private static void texQuad(BufferBuilder b, Matrix4f m, float cx, float cy, float s, float r, float g, float bl) {
        b.vertex(m, cx - s, cy - s, 0.0F).uv(0.0F, 0.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, cx - s, cy + s, 0.0F).uv(0.0F, 1.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, cx + s, cy + s, 0.0F).uv(1.0F, 1.0F).color(r, g, bl, 1.0F).endVertex();
        b.vertex(m, cx + s, cy - s, 0.0F).uv(1.0F, 0.0F).color(r, g, bl, 1.0F).endVertex();
    }

    /** A vertex of the disk's light: white-hot, the texture giving its colour. */
    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float u, float v, float k) {
        b.vertex(m, x, y, 0.0F).uv(u, v).color(k, 0.94F * k, 0.88F * k, 1.0F).endVertex();
    }

    /** The start button: ready, hovered, can't start now, or lit while the core fuses. */
    private void startButton(GuiGraphics g, FusionCoreBlockEntity core, int x, int y, int mouseX, int mouseY) {
        int state;
        if (core != null && core.fusing()) state = 3;
        else if (core == null || core.status() != FusionStatus.READY) state = 2;
        else state = overStart(mouseX, mouseY) ? 1 : 0;
        g.blit(PANEL, x + Layouts.START[0], y + Layouts.START[1], Layouts.SHEET_START[0] + 18 * state, Layouts.SHEET_START[1], 18, 18, Layouts.W,
                Layouts.TEX_H);
    }

    /** What the catalyst and the pylons would make, shown faintly in the empty output slot. */
    private void preview(GuiGraphics g, FusionCoreBlockEntity core, int x, int y) {
        if (core == null || core.preview().isEmpty() || !core.items().getStackInSlot(FusionCoreBlockEntity.OUTPUT).isEmpty()) return;
        int px = x + Layouts.OUTPUT[0], py = y + Layouts.OUTPUT[1];
        g.renderItem(core.preview(), px, py);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.fill(px, py, px + 16, py + 16, 0x9A120E1C);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int[] t = Layouts.TITLE;
        Ui.centredFit(g, font, title, (t[0] + t[2]) / 2, t[1] + (t[3] - t[1] - 8) / 2 + 1, t[2] - t[0] - 16, TEXT_TITLE);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_DIM, false);
        FusionCoreBlockEntity core = menu.core();
        if (core == null) return;
        FusionStatus status = core.status();
        int width = 156;
        Ui.centredFit(g, font, Component.translatable("gui.singularityfusion.status." + status.key()), Layouts.W / 2, Layouts.STATUS_Y[0], width,
                colour(status));
        Component info;
        int infoColour = TEXT;
        if (status == FusionStatus.FUSING && !core.preview().isEmpty()) {
            info = Component.translatable("gui.singularityfusion.making", core.preview().getHoverName(),
                    Math.round(core.progress(0.0F) * 100.0F));
        } else if (core.cost() > 0) {
            info = Component.translatable("gui.singularityfusion.cost", number(core.cost()));
            if (core.energy() < core.cost()) infoColour = 0xFFFFB347;
        } else {
            info = Component.translatable("gui.singularityfusion.stored", number(core.energy()));
            infoColour = TEXT_DIM;
        }
        Ui.centredFit(g, font, info, Layouts.W / 2, Layouts.STATUS_Y[1], width, infoColour);
    }

    private static int colour(FusionStatus status) {
        return switch (status) {
            case NO_FOUNDATION, BLOCKED, NO_PYLONS -> 0xFFFF7A6B;
            case NO_RECIPE -> 0xFFA59CBD;
            case OUTPUT_FULL, NO_ENERGY -> 0xFFFFB347;
            case READY -> 0xFF7CF3C8;
            case FUSING -> 0xFFD7A8FF;
        };
    }

    static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    // ------------------------------------------------------------------ tooltips and clicks

    private void tooltips(GuiGraphics g, int mouseX, int mouseY) {
        FusionCoreBlockEntity core = menu.core();
        if (core == null || !menu.getCarried().isEmpty()) return;
        int x = leftPos, y = topPos;
        int[] e = Layouts.ENERGY, p = Layouts.PROGRESS;
        List<Component> lines = new ArrayList<>();
        if (Ui.in(mouseX, mouseY, x + e[0] - 1, y + e[1] - 1, e[2] + 2, e[3] + 2)) {
            lines.add(Component.translatable("gui.singularityfusion.energy", number(core.energy()), number(core.capacity())));
            lines.add(Component.translatable("gui.singularityfusion.charge", Math.round(core.charge() * 100.0F)).withStyle(ChatFormatting.LIGHT_PURPLE));
            if (core.cost() > 0) lines.add(Component.translatable("gui.singularityfusion.cost", number(core.cost())).withStyle(ChatFormatting.GRAY));
        } else if (Ui.in(mouseX, mouseY, x + p[0] - 1, y + p[1] - 1, p[2] + 2, p[3] + 2)) {
            lines.add(core.fusing() ? Component.translatable("gui.singularityfusion.progress", Math.round(core.progress(0.0F) * 100.0F))
                    : Component.translatable("gui.singularityfusion.idle"));
            if (core.fusing() || core.cost() > 0) {
                lines.add(Component.translatable("gui.singularityfusion.time", String.format(Locale.ROOT, "%.1f", core.craftTime() / 20.0F))
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (overStart(mouseX, mouseY)) {
            if (core.fusing()) lines.add(Component.translatable("gui.singularityfusion.status.fusing"));
            else if (core.status() == FusionStatus.READY) lines.add(Component.translatable("gui.singularityfusion.start"));
            else lines.add(Component.translatable("gui.singularityfusion.status." + core.status().key()).withStyle(ChatFormatting.RED));
            lines.add(Component.translatable("gui.singularityfusion.start.redstone").withStyle(ChatFormatting.DARK_GRAY));
        } else if (Ui.in(mouseX, mouseY, x + Layouts.VIEW[0] - Layouts.VIEW_R, y + Layouts.VIEW[1] - Layouts.VIEW_R, 2 * Layouts.VIEW_R,
                2 * Layouts.VIEW_R)) {
            float dx = mouseX - (x + Layouts.VIEW[0]), dy = mouseY - (y + Layouts.VIEW[1]);
            if (dx * dx + dy * dy <= Layouts.VIEW_R * Layouts.VIEW_R) {
                lines.add(Component.translatable("gui.singularityfusion.singularity", Math.round(core.charge() * 100.0F)));
                if (!core.status().formed()) lines.add(Component.translatable("gui.singularityfusion.unformed").withStyle(ChatFormatting.GRAY));
            }
        } else {
            for (int i = 0; i < Layouts.PYLONS.length; i++) {
                int[] at = Layouts.PYLONS[i];
                if (!Ui.in(mouseX, mouseY, x + at[0] - 1, y + at[1] - 1, 18, 18)) continue;
                Level level = Minecraft.getInstance().level;
                if (i >= core.pylons().size() || level == null) {
                    lines.add(Component.translatable("gui.singularityfusion.pylon.none").withStyle(ChatFormatting.GRAY));
                } else if (level.getBlockEntity(core.pylons().get(i)) instanceof GravitonPylonBlockEntity pylon && !pylon.item().isEmpty()) {
                    g.renderTooltip(font, pylon.item(), mouseX, mouseY);
                    return;
                } else {
                    lines.add(Component.translatable("gui.singularityfusion.pylon.empty"));
                }
            }
        }
        if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private boolean overStart(double mouseX, double mouseY) {
        return Ui.in(mouseX, mouseY, leftPos + Layouts.START[0], topPos + Layouts.START[1], 18, 18);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        FusionCoreBlockEntity core = menu.core();
        if (button == 0 && overStart(mouseX, mouseY) && core != null && !core.fusing() && core.status() == FusionStatus.READY) {
            Net.CHANNEL.sendToServer(new StartFusionPacket());
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.6F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
