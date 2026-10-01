package com.reya.alfheimheart.machine.field.client;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineLayouts.Field;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.machine.client.Gfx;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.field.CropFieldBlockEntity;
import com.reya.alfheimheart.machine.field.CropFieldMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Crop Field's GUI, the golden field (crop_field.png; tools/machines/harvest.py): six plots in two furrows of
 * tilled soil in a field of wheat at sunset, the bone meal in a sack under them, a rain gauge of mana on the left,
 * a crate for the harvest on the right, a scarecrow keeping watch.
 * <p>
 * Wind runs over the wheat in waves and birds cross the sky. As a cycle goes by, the plots with seeds in them
 * glow, green turning to gold as their crops ripen, sprouts of light rising off them, and a little cloud rains
 * into the gauge; when the cycle ends the harvest bursts golden off the plots and flies to the crate. The gauge's
 * mana shimmers at its surface.
 */
public class CropFieldScreen extends MachineScreen<CropFieldMenu> {
    private static final int RAIN = 0x7FC8FF;
    private static final String[] CLOUD = {
            "...xxxx.....",
            ".xxXXXXxx...",
            "xXXXXXXXXxx.",
            "xXXXXXXXXXXx",
            ".xxxxxxxxxx."};

    @Nullable
    private MachineBlockEntity.Job job;
    private final boolean[] planted = new boolean[6];
    private final float[][] drops = new float[8][];
    private long sproutAt;

    public CropFieldScreen(CropFieldMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "crop_field";
    }

    @Override
    protected int titleColour() {
        return 0xFF4A2E10;
    }

    @Override
    protected int titleShadow() {
        return 0xFFFFF4D8;
    }

    @Override
    protected int lightColour() {
        return 0xFFE9A0;
    }

    @Override
    protected int[] veinColours() {
        return new int[]{0xFFF8E0, 0xFFE27A, 0xF4BA2E};
    }

    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        job = CropFieldBlockEntity.find(minecraft.level, BlockPos.ZERO, menu.items());
        for (int i = 0; i < planted.length && i < layout.inputCount(); i++) {
            ItemStack stack = menu.items().getStackInSlot(i);
            planted[i] = !stack.isEmpty() && CropFieldBlockEntity.grown(stack) != null;
        }
    }

    private float[] plot(int i) {
        return new float[]{layout.inputs[i][0] + 8.0F, layout.inputs[i][1] + 8.0F};
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        long since = t - craftedAt;
        // the sun low on the hills, breathing
        Gfx.radial(g, leftPos + 64, topPos + 29, 26.0F, Gfx.argb(0xFFD890, 0.25F + 0.08F * Mth.sin(t / 900.0F)), 0x00FFD890, true);
        // the wind running over the wheat in waves
        float span = layout.width + 120.0F, left = 7.0F, right = layout.width - 7.0F, y1 = 36.0F, y2 = 148.0F;
        for (int k = 0; k < 2; k++) {
            float x = ((t / 30.0F + k * span / 2.0F) % span) - 60.0F;
            float xa = Math.max(left, x - 30.0F), xm = Mth.clamp(x, left, right), xb = Math.min(right, x + 30.0F);
            if (xm > xa) {
                Gfx.gradient(g, leftPos + xa, topPos + y1, leftPos + xm, topPos + y2, 0x00FFF0B0, 0x16FFF0B0, 0x16FFF0B0, 0x00FFF0B0, true);
            }
            if (xb > xm) {
                Gfx.gradient(g, leftPos + xm, topPos + y1, leftPos + xb, topPos + y2, 0x16FFF0B0, 0x00FFF0B0, 0x00FFF0B0, 0x16FFF0B0, true);
            }
        }
        drawBirds(g, t);
        // the plots: green turning to gold as the crops ripen
        for (int i = 0; i < planted.length && i < layout.inputCount(); i++) {
            if (!planted[i]) continue;
            float[] at = plot(i);
            int col = Gfx.mix(0xFF6FC24A, 0xFFFFD04A, p) & 0xFFFFFF;
            float glow = busy ? 0.2F + 0.35F * p : 0.1F;
            if (since < 450L) glow = Math.max(glow, 1.0F - since / 450.0F);
            Gfx.radial(g, leftPos + at[0], topPos + at[1], 15.0F, Gfx.argb(since < 450L ? 0xFFE27A : col, glow), Gfx.argb(col, 0.0F), true);
        }
        if (busy && t - sproutAt > 140L) {
            sproutAt = t;
            int i = random.nextInt(planted.length);
            if (i < layout.inputCount() && planted[i]) {
                float[] at = plot(i);
                drift(at[0] + (random.nextFloat() - 0.5F) * 12.0F, at[1] + 4.0F, 0.0F, -9.0F - random.nextFloat() * 6.0F, 1.2F,
                        p > 0.6F ? GOLD_LIGHT : GREEN_LIGHT);
            }
        }
        drawCloud(g, t, dt, busy);
    }

    /** Two birds crossing the sky, their wings beating. */
    private void drawBirds(GuiGraphics g, long t) {
        for (int b = 0; b < 2; b++) {
            float period = 14000.0F + b * 5000.0F;
            float k = ((t + b * 6000L) % (long) period) / period;
            float x = 8.0F + k * (layout.width - 16.0F);
            float y = 14.0F + b * 7.0F + 2.0F * Mth.sin(t / 700.0F + b);
            boolean up = (t / 180L + b) % 2L == 0L;
            int bx = leftPos + Math.round(x), by = topPos + Math.round(y);
            int c = 0xC0302040;
            g.fill(bx, by, bx + 1, by + 1, c);
            g.fill(bx - 1, by + (up ? -1 : 0), bx, by + (up ? 0 : 1), c);
            g.fill(bx + 1, by + (up ? -1 : 0), bx + 2, by + (up ? 0 : 1), c);
            g.fill(bx - 2, by + (up ? -1 : 1), bx - 1, by + (up ? 0 : 2), c);
            g.fill(bx + 2, by + (up ? -1 : 1), bx + 3, by + (up ? 0 : 2), c);
        }
    }

    /** The little cloud over the rain gauge, drifting a little, raining into its funnel while the field grows. */
    private void drawCloud(GuiGraphics g, long t, float dt, boolean busy) {
        float cx = Field.CLOUD[0] + 3.0F * Mth.sin(t / 1400.0F);
        int x0 = leftPos + Math.round(cx) - CLOUD[0].length() / 2, y0 = topPos + Field.CLOUD[1] - 2;
        int body = busy ? 0xF0F4FAFF : 0xC0E4EAF0, edge = busy ? 0xF0B8C8D8 : 0xC0A8B4C0;
        for (int j = 0; j < CLOUD.length; j++) {
            for (int i = 0; i < CLOUD[j].length(); i++) {
                char ch = CLOUD[j].charAt(i);
                if (ch != '.') g.fill(x0 + i, y0 + j, x0 + i + 1, y0 + j + 1, ch == 'X' ? body : edge);
            }
        }
        if (!busy) return;
        float funnel = Field.TUBE[1] - Field.TUBE[3] - 6.0F;
        g.drawManaged(() -> {
            for (int k = 0; k < drops.length; k++) {
                if (drops[k] == null || drops[k][1] > funnel) {
                    drops[k] = new float[]{cx - 4.0F + random.nextFloat() * 8.0F, Field.CLOUD[1] + 3 + random.nextFloat() * 8.0F};
                }
                drops[k][1] += dt * 50.0F;
                int x = leftPos + Math.round(drops[k][0]), y = topPos + Math.round(drops[k][1]);
                g.fill(x, y, x + 1, y + 2, 0xB0000000 | RAIN);
            }
        });
    }

    // ------------------------------------------------------------------ the rain gauge

    @Override
    protected void drawGauge(GuiGraphics g, long t, float mana) {
        int[][] rows = Field.TUBE_ROWS;
        int n = Math.round(rows.length * Mth.clamp(mana, 0.0F, 1.0F));
        g.drawManaged(() -> {
            for (int i = 0; i < n; i++) {
                int y = rows[i][0], x1 = rows[i][1], x2 = rows[i][2];
                int base = Gfx.mix(0xFF1B64B8, 0xFF8FEFFF, i / (float) rows.length) & 0xFFFFFF;
                for (int x = x1; x < x2; x++) {
                    int c = base;
                    if (x == x1) c = Gfx.mix(0xFF000000 | c, 0xFFFFFFFF, 0.35F) & 0xFFFFFF;
                    if (i == n - 1) c = Gfx.mix(0xFF000000 | c, 0xFFF2FFFF, 0.55F + 0.45F * Mth.sin(t / 160.0F + x * 0.8F)) & 0xFFFFFF;
                    g.fill(leftPos + x, topPos + y, leftPos + x + 1, topPos + y + 1, 0xE8000000 | c);
                }
            }
        });
        if (n > 0) {
            int[] top = rows[n - 1];
            Gfx.radial(g, leftPos + (top[1] + top[2]) / 2.0F, topPos + top[0], 8.0F, Gfx.argb(MANA_BRIGHT, 0.3F), 0x00A6F6FF, true);
        }
        if (menu.status() == MachineStatus.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            int[] tube = Field.TUBE;
            g.fill(leftPos + tube[0] - tube[2] + 2, topPos + tube[1] - tube[3] + 2, leftPos + tube[0] + tube[2] - 2, topPos + tube[1] + tube[3] - 2,
                    (int) (20 + beat * 60) << 24 | 0xC82C26);
        }
    }

    @Override
    protected boolean overGauge(int mx, int my) {
        int[] tube = Field.TUBE;
        return Math.abs(mx + 0.5F - tube[0]) <= tube[2] + 4 && Math.abs(my + 0.5F - tube[1]) <= tube[3] + 6;
    }

    // ------------------------------------------------------------------ over the items

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        if (menu.items().getStackInSlot(CropFieldBlockEntity.FERTILIZER).isEmpty()) {
            ghostSlot(g, layout.special[0][0], layout.special[0][1], new ItemStack(Items.BONE_MEAL), false, t);
        }
    }

    @Override
    protected void onCraft(long t) {
        for (int i = 0; i < planted.length && i < layout.inputCount(); i++) {
            if (!planted[i]) continue;
            float[] at = plot(i);
            burst(Math.round(at[0]), Math.round(at[1]) - 4, 6, GOLD_LIGHT, 0.8F);
        }
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        int[] a = Field.FURROWS[0], b = Field.FURROWS[1];
        if (mx < a[0] || mx >= a[2] || my < a[1] || my >= b[3]) return;
        tip.add(Component.translatable("gui.alfheimheart.crop_field.field").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        if (job != null) {
            tip.add(Component.translatable("gui.alfheimheart.crop_field.cycle", job.units().size(),
                    String.format(java.util.Locale.ROOT, "%.1f", job.minTicks() / 20.0F)).withStyle(ChatFormatting.WHITE));
            if (job.mana() > 0) tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(job.mana())).withStyle(ChatFormatting.AQUA));
            if (!menu.items().getStackInSlot(CropFieldBlockEntity.FERTILIZER).isEmpty()) {
                tip.add(Component.translatable("gui.alfheimheart.crop_field.fed").withStyle(ChatFormatting.GREEN));
            }
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.crop_field.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void specialSlotTooltip(List<Component> tip, int index) {
        tip.add(Component.translatable("gui.alfheimheart.crop_field.slot.fertilizer").withStyle(ChatFormatting.GREEN));
        tip.add(Component.translatable("gui.alfheimheart.crop_field.slot.fertilizer.tip").withStyle(ChatFormatting.GRAY));
    }
}
