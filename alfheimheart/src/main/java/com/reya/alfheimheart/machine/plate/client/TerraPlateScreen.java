package com.reya.alfheimheart.machine.plate.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.plate.TerraPlateBlockEntity;
import com.reya.alfheimheart.machine.plate.TerraPlateMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Terrestrial Plate's GUI: the plate in the middle (terra_plate.png), its ingredients hovering over the three
 * sockets round it. As a craft charges they circle the plate and close in on its core, beams of light joining
 * them to it, where a ball of light grows from mana blue to terrasteel green; the eight rays of the plate's sun
 * light one after another and its ring brightens; when it is done the ball bursts in a green flash and what it
 * made flies out to the outputs.
 */
public class TerraPlateScreen extends MachineScreen<TerraPlateMenu> {
    public static final ResourceLocation PANEL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/terra_plate.png");
    // the plate (tools/machines/layout.py PLATE; check_layout.py keeps these in step)
    static final int PLATE_X = 120, PLATE_Y = 52, PLATE_R = 21, SOCKET_R = 28, HEART_R = 36;
    static final float CORE_R = 6.5F;
    static final int GEM_X = 120, GEM_Y = 76;
    static final int[][] SOCKETS = {{120, 24}, {144, 66}, {96, 66}};
    static final int HALO_U = 48, HALO_V = 96, HALO_SIZE = 15;
    /** The sun on the plate (as gen_gui.plate_field draws it): the ring, and the pixels of each ray and its tip. */
    static final float SUN_RING_R = 12.5F, RAY_FROM = 8.0F, RAY_TO = 17.5F, TIP_TO = 18.6F;
    private static final int BLUE = 0x3F9BFF, GREEN = 0x5DFF74, WHITE = 0xF2FFF4;

    private static final List<int[]> RING = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private static final List<int[]>[] RAYS = new List[8];

    static {
        for (int k = 0; k < 8; k++) RAYS[k] = new ArrayList<>();
        for (int y = -PLATE_R; y < PLATE_R; y++) {
            for (int x = -PLATE_R; x < PLATE_R; x++) {
                float px = x + 0.5F, py = y + 0.5F;
                float d = Mth.sqrt(px * px + py * py);
                if (Math.abs(d - SUN_RING_R) < 0.6F) {
                    RING.add(new int[]{x, y});
                    continue;
                }
                float ang = (float) Math.atan2(py, px);
                int k = Math.round(ang / (PI / 4.0F));
                float off = Math.abs(ang - k * PI / 4.0F) * d;
                boolean ray = d > RAY_FROM && d < RAY_TO && off < 0.6F, tip = d >= RAY_TO && d < TIP_TO && off < 1.1F;
                // the rays light from the top, clockwise
                if (ray || tip) RAYS[Math.floorMod(k + 2, 8)].add(new int[]{x, y, tip ? 1 : 0});
            }
        }
    }

    @Nullable
    private MachineBlockEntity.Job job;
    private float orbit;
    private List<ItemStack> falling = List.of();
    private float fallingRadius, fallingOrbit;
    private long moteAt;

    public TerraPlateScreen(TerraPlateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL, PLATE_X, PLATE_Y, GEM_X, GEM_Y);
    }

    @Override
    protected String key() {
        return "terra_plate";
    }

    private void refresh(long t) {
        if (itemsChanged(t) && minecraft != null && minecraft.level != null) job = TerraPlateBlockEntity.find(minecraft.level, menu.items());
    }

    private static int colour(float p) {
        return lerpColour(BLUE, GREEN, p);
    }

    static int lerpColour(int a, int b, float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(p, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(p, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(p, a & 0xFF, b & 0xFF));
        return r << 16 | g << 8 | bl;
    }

    /** Where ingredient i of n is: over its socket at rest, circling inwards as the craft charges. */
    private float[] place(int i, int n, float p, long t) {
        float a = -PI / 2.0F + i * 2.0F * PI / n + orbit;
        float r = SOCKET_R * (1.0F - 0.75F * p);
        float bob = 1.2F * Mth.sin(t / 320.0F + i * 2.1F);
        return new float[]{PLATE_X + 0.5F + Mth.cos(a) * r, PLATE_Y + 0.5F + Mth.sin(a) * r + bob};
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        if (busy) orbit += dt * (0.6F + 3.2F * p);
        int c = colour(p);
        long since = t - craftedAt;
        float flash = since < 600L ? 1.0F - since / 600.0F : 0.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // the sun: its rays light one by one as the craft charges, its ring brightens; a slow shimmer at rest
        g.drawManaged(() -> sun(g, t, busy, p, c, flash));
        drawBall(g, t, busy, p, c, flash);
    }

    private void sun(GuiGraphics g, long t, boolean busy, float p, int c, float flash) {
        int lit = busy ? Math.min(8, (int) (p * 8.0F)) : 0;
        for (int k = 0; k < 8; k++) {
            float a;
            if (k < lit) a = 0.85F;
            else if (busy && k == lit) a = 0.2F + 0.4F * (0.5F + 0.5F * Mth.sin(t / 90.0F));
            else a = job != null ? 0.12F + 0.1F * Mth.sin(t / 400.0F + k * 0.8F) : 0.0F;
            a = Math.max(a, flash);
            if (a <= 0.02F) continue;
            for (int[] px : RAYS[k]) {
                int col = px[2] == 1 ? WHITE : c;
                g.fill(leftPos + PLATE_X + px[0], topPos + PLATE_Y + px[1], leftPos + PLATE_X + px[0] + 1, topPos + PLATE_Y + px[1] + 1,
                        (int) (a * 255) << 24 | col);
            }
        }
        float ring = Math.max(busy ? 0.25F + 0.6F * p : job != null ? 0.12F : 0.0F, flash);
        if (ring > 0.02F) {
            for (int[] px : RING) {
                g.fill(leftPos + PLATE_X + px[0], topPos + PLATE_Y + px[1], leftPos + PLATE_X + px[0] + 1, topPos + PLATE_Y + px[1] + 1,
                        (int) (ring * 200) << 24 | c);
            }
        }
    }

    private void drawBall(GuiGraphics g, long t, boolean busy, float p, int c, float flash) {
        // the ball of light in the core
        float ball = busy ? 0.25F + 0.75F * p : job != null ? 0.15F : 0.0F;
        ball = Math.max(ball, flash);
        if (ball > 0.02F) {
            float[] rgb = {(c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F};
            float scale = 0.8F + 0.6F * p + 1.6F * flash;
            g.pose().pushPose();
            g.pose().translate(leftPos + PLATE_X, topPos + PLATE_Y, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.setColor(rgb[0], rgb[1], rgb[2], Math.min(1.0F, ball * (0.85F + 0.15F * Mth.sin(t / 110.0F))));
            g.blit(WIDGETS, -HALO_SIZE / 2 - 1, -HALO_SIZE / 2 - 1, HALO_U, HALO_V, HALO_SIZE, HALO_SIZE, WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            g.pose().popPose();
            int core = Math.round(1.0F + 3.0F * p * (busy ? 1.0F : 0.0F));
            for (int y = -core; y < core; y++) {
                int w = Math.round(Mth.sqrt(Math.max(0.0F, core * core - (y + 0.5F) * (y + 0.5F))));
                g.fill(leftPos + PLATE_X - w, topPos + PLATE_Y + y, leftPos + PLATE_X + w, topPos + PLATE_Y + y + 1,
                        (int) (ball * 230) << 24 | lerpColour(c, WHITE, 0.6F));
            }
        }
        // beams from the ingredients to the core, and mana drifting in along them
        if (busy && job != null) {
            int n = Math.min(6, job.units().size());
            g.drawManaged(() -> {
                for (int i = 0; i < n; i++) {
                    float[] at = place(i, n, p, t);
                    beam(g, at[0], at[1], PLATE_X, PLATE_Y, c, 0.18F + 0.45F * p, t, i);
                }
            });
            if (t - moteAt > 90L && n > 0) {
                moteAt = t;
                float[] at = place(random.nextInt(n), n, p, t);
                float dx = PLATE_X - at[0], dy = PLATE_Y - at[1];
                float len = Math.max(1.0F, Mth.sqrt(dx * dx + dy * dy));
                float speed = 20.0F + 25.0F * p;
                drift(at[0], at[1], dx / len * speed, dy / len * speed, len / speed, random.nextBoolean() ? c : GOLD_LIGHT);
            }
        }
    }

    /** A dotted line of light from (x1, y1) to (x2, y2), its dots running towards the second end. */
    private void beam(GuiGraphics g, float x1, float y1, float x2, float y2, int rgb, float a, long t, int salt) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        int steps = (int) len;
        float run = (t / 40.0F + salt * 3.0F) % 4.0F;
        for (int s = 6; s < steps - 4; s++) {
            float k = ((s - run) % 4.0F + 4.0F) % 4.0F;
            float alpha = a * (k < 1.0F ? 1.0F : 0.35F);
            int x = leftPos + Math.round(x1 + dx * s / len - 0.5F), y = topPos + Math.round(y1 + dy * s / len - 0.5F);
            g.fill(x, y, x + 1, y + 1, (int) (alpha * 255) << 24 | rgb);
        }
    }

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        long since = t - craftedAt;
        if (since < 380L && !falling.isEmpty()) {
            float e = easeOut(since / 380.0F);
            int n = falling.size();
            for (int i = 0; i < n; i++) {
                float a = -PI / 2.0F + i * 2.0F * PI / n + fallingOrbit;
                float r = fallingRadius * (1.0F - e);
                floatingItem(g, falling.get(i), PLATE_X + 0.5F + Mth.cos(a) * r, PLATE_Y + 0.5F + Mth.sin(a) * r, 0.75F * (1.0F - 0.85F * e));
            }
        }
        if (job == null) return;
        List<ItemStack> units = job.units();
        int n = Math.min(6, units.size());
        float grow = since < 380L ? 0.0F : Math.min(1.0F, (since - 380L) / 300.0F);
        for (int i = 0; i < n; i++) {
            float[] at = place(i, n, p, t);
            floatingItem(g, units.get(i), at[0], at[1], 0.75F * (1.0F - 0.3F * p) * backOut(grow));
        }
    }

    @Override
    protected void onCraft(long t) {
        falling = job == null ? List.of() : new ArrayList<>(job.units().subList(0, Math.min(6, job.units().size())));
        fallingRadius = SOCKET_R * 0.25F;
        fallingOrbit = orbit;
        orbit = 0.0F;
        burst(PLATE_X, PLATE_Y, 16, GREEN, 1.4F);
        burst(PLATE_X, PLATE_Y, 8, WHITE, 0.9F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        float dx = mx - PLATE_X, dy = my - PLATE_Y;
        if (dx * dx + dy * dy > HEART_R * HEART_R) return;
        tip.add(Component.translatable("gui.alfheimheart.terra_plate.plate").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        if (job != null && !job.outputs().isEmpty()) {
            tip.add(Component.translatable("gui.alfheimheart.makes", job.outputs().get(0).getHoverName()).withStyle(ChatFormatting.WHITE));
            tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(job.mana())).withStyle(ChatFormatting.AQUA));
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.terra_plate.hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
