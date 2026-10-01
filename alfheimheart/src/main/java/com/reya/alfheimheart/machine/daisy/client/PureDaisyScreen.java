package com.reya.alfheimheart.machine.daisy.client;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineLayouts.Daisy;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.machine.client.Gfx;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.daisy.PureDaisyBlockEntity;
import com.reya.alfheimheart.machine.daisy.PureDaisyMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.common.block.BotaniaFlowerBlocks;

/**
 * The Pure Daisy's GUI, the dawn garden (pure_daisy.png; tools/machines/garden.py): the daisy in the middle of a
 * square bed of nine tiles, the blocks it purifies set in the eight round it (as round the daisy in the world), a
 * dewdrop of mana on the left, a woven basket of what it has made on the right.
 * <p>
 * The daisy sways in the morning air; butterflies flutter over the garden and light falls from the rising sun.
 * The tiles holding what the daisy can purify glow softly; the one it is purifying fills with white light as it
 * works, petals drifting off the daisy, a ring of light closing round it (mana blue when mana hurries it); when it
 * is done the tile flashes and what the blocks became flies to the basket. The dewdrop fills with the store's
 * mana, its surface shimmering, bubbles rising in it while mana hurries the daisy.
 */
public class PureDaisyScreen extends MachineScreen<PureDaisyMenu> {
    private static final int CX = Daisy.CX, CY = Daisy.CY;
    private static final int WHITE = 0xFFFFFF, PETAL = 0xFFF6FB, PINK = 0xFFD6EC;
    private static final ItemStack DAISY = new ItemStack(BotaniaFlowerBlocks.pureDaisy);

    @Nullable
    private MachineBlockEntity.Job job;
    /** The input being purified (or -1), and which hold what the daisy purifies. */
    private int active = -1;
    private final boolean[] purifiable = new boolean[8];
    private final float[] glow = new float[8];
    private int flashed = -1;
    private long petalAt, bubbleAt;

    public PureDaisyScreen(PureDaisyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "pure_daisy";
    }

    @Override
    protected int titleColour() {
        return 0xFF2E5A1E;
    }

    @Override
    protected int titleShadow() {
        return 0xFFFFFBEA;
    }

    @Override
    protected int lightColour() {
        return 0xE6FFD8;
    }

    @Override
    protected int[] veinColours() {
        return new int[]{0xFFFFFF, 0xE6FFD8, 0xB6F59A};
    }

    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        job = PureDaisyBlockEntity.find(minecraft.level, menu.items());
        active = -1;
        for (int i = 0; i < purifiable.length && i < layout.inputCount(); i++) {
            ItemStack stack = menu.items().getStackInSlot(i);
            purifiable[i] = !stack.isEmpty() && !PureDaisyBlockEntity.purified(minecraft.level, stack, null).isEmpty();
            if (job != null && active < 0 && job.taken()[i] > 0) active = i;
        }
    }

    /** Whether mana hurries the daisy. */
    private boolean hurried() {
        return menu.mana() > 0 && working();
    }

    private float[] cell(int i) {
        return new float[]{layout.inputs[i][0] + 8.0F, layout.inputs[i][1] + 8.0F};
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        boolean blue = hurried();
        long since = t - craftedAt;

        drawSunlight(g, t);
        // the tiles: a soft glow on those holding what the daisy purifies, the one it works on brightening
        for (int i = 0; i < glow.length; i++) {
            float target = i == active && busy ? 0.35F + 0.55F * p : purifiable[i] ? 0.12F + 0.05F * Mth.sin(t / 500.0F + i) : 0.0F;
            if (i == flashed && since < 500L) target = Math.max(target, 1.0F - since / 500.0F);
            glow[i] += (target - glow[i]) * Math.min(1.0F, dt * 6.0F);
            if (glow[i] > 0.01F) {
                float[] at = cell(i);
                Gfx.radial(g, leftPos + at[0], topPos + at[1], 15.0F, Gfx.argb(i == active && blue ? 0xD8F8FF : WHITE, glow[i] * 0.7F),
                        Gfx.argb(WHITE, 0.0F), true);
            }
        }
        // the daisy's own light, and a ring of it closing round the daisy as it works
        float halo = busy ? 0.35F + 0.25F * p + 0.1F * Mth.sin(t / 240.0F) : job != null ? 0.2F : 0.1F;
        if (since < 500L) halo = Math.max(halo, 1.0F - since / 500.0F);
        int light = blue ? MANA_BRIGHT : WHITE;
        Gfx.radial(g, leftPos + CX, topPos + CY, 14.0F + 4.0F * p, Gfx.argb(light, halo), Gfx.argb(light, 0.0F), true);
        if (busy && p > 0.0F) {
            float start = -PI / 2.0F;
            Gfx.arc(g, leftPos + CX, topPos + CY, 11.0F, 13.5F, start, start + p * Gfx.TAU, k -> Gfx.argb(light, 0.75F), true, true);
        }
        // petals drifting off the daisy while it works
        if (busy && t - petalAt > (blue ? 90L : 160L)) {
            petalAt = t;
            float a = random.nextFloat() * Gfx.TAU;
            float speed = 8.0F + random.nextFloat() * 10.0F;
            drift(CX + Mth.cos(a) * 5.0F, CY + Mth.sin(a) * 5.0F, Mth.cos(a) * speed + 6.0F, Mth.sin(a) * speed - 3.0F,
                    1.4F + random.nextFloat() * 0.8F, random.nextInt(3) == 0 ? PINK : PETAL);
        }
        drawButterflies(g, t);
    }

    /** Soft shafts of light falling from the rising sun in the top right, swaying a little. */
    private void drawSunlight(GuiGraphics g, long t) {
        float sx = layout.width - 26.0F, sy = 18.0F;
        for (int k = 0; k < 3; k++) {
            float a = 2.25F + k * 0.22F + 0.03F * Mth.sin(t / 2200.0F + k);
            float len = 120.0F + 30.0F * k;
            float ex = sx + Mth.cos(a) * len, ey = sy + Mth.sin(a) * len;
            float alpha = 0.07F + 0.03F * Mth.sin(t / 1300.0F + k * 2.0F);
            Gfx.beam(g, leftPos + sx, topPos + sy, leftPos + ex, topPos + ey, 16.0F + 6.0F * k, Gfx.argb(0xFFF1C8, alpha), Gfx.argb(0xFFF1C8, 0.0F),
                    true);
        }
    }

    /** Two butterflies flitting over the garden, their wings beating. */
    private void drawButterflies(GuiGraphics g, long t) {
        int[] tints = {0xFFF2A8, 0xBFE6FF};
        for (int b = 0; b < 2; b++) {
            float time = t / 1000.0F + b * 7.3F;
            float x = layout.width / 2.0F + 92.0F * Mth.sin(time * 0.23F + b) + 14.0F * Mth.sin(time * 1.7F);
            float y = 42.0F + 22.0F * Mth.sin(time * 0.37F + b * 2.0F) + 6.0F * Mth.sin(time * 2.3F);
            boolean open = (t / (110L + b * 30L) + b) % 2L == 0L;
            int s = Daisy.BUTTERFLY_SIZE;
            glowBlit(g, x - s / 2.0F, y - s / 2.0F, Daisy.BUTTERFLY_U + (open ? 0 : s), Daisy.BUTTERFLY_V, s, s, tints[b], 0.95F);
        }
    }

    // ------------------------------------------------------------------ the dewdrop

    @Override
    protected void drawGauge(GuiGraphics g, long t, float mana) {
        int[][] rows = Daisy.DROP_ROWS;
        int n = Math.round(rows.length * Mth.clamp(mana, 0.0F, 1.0F));
        boolean blue = hurried();
        g.drawManaged(() -> {
            for (int i = 0; i < n; i++) {
                int y = rows[i][0], x1 = rows[i][1], x2 = rows[i][2];
                float h = i / (float) rows.length;
                int base = Gfx.mix(0xFF1B64B8, 0xFF8FEFFF, h) & 0xFFFFFF;
                for (int x = x1; x < x2; x++) {
                    int c = base;
                    float edge = Math.min(x - x1, x2 - 1 - x) / Math.max(1.0F, (x2 - x1) / 2.0F);
                    if (edge < 0.25F) c = Gfx.mix(0xFF000000 | c, 0xFF0B2F5C, 0.35F) & 0xFFFFFF;
                    if (i == n - 1) {
                        float s = 0.6F + 0.4F * Mth.sin(t / 150.0F + x * 0.9F);
                        c = Gfx.mix(0xFF000000 | c, 0xFFF2FFFF, s) & 0xFFFFFF;
                    }
                    g.fill(leftPos + x, topPos + y, leftPos + x + 1, topPos + y + 1, 0xE8000000 | c);
                }
            }
        });
        if (n > 0) {
            int[] top = rows[n - 1];
            Gfx.radial(g, leftPos + (top[1] + top[2]) / 2.0F, topPos + top[0], 8.0F, Gfx.argb(MANA_BRIGHT, 0.3F), 0x00A6F6FF, true);
            if (blue && t - bubbleAt > 160L) {
                bubbleAt = t;
                int[] row = rows[random.nextInt(Math.max(1, n / 2))];
                if (row[2] - row[1] > 2) {
                    drift(row[1] + 1 + random.nextInt(row[2] - row[1] - 2), row[0], 0.0F, -12.0F, Math.min(2.5F, (top[0] < row[0] ? row[0] - top[0] : 1) / 12.0F),
                            0xF2FFFF);
                }
            }
        }
        if (menu.status() == MachineStatus.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            Gfx.radial(g, leftPos + Daisy.DROP[0], topPos + Daisy.DROP[1], Daisy.DROP[2], Gfx.argb(0xC82C26, 0.1F + 0.25F * beat), 0x00C82C26,
                    false);
        }
    }

    @Override
    protected boolean overGauge(int mx, int my) {
        for (int[] row : Daisy.DROP_ROWS) {
            if (row[0] == my) return mx >= row[1] - 2 && mx < row[2] + 2;
        }
        return false;
    }

    // ------------------------------------------------------------------ over the items

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        boolean busy = working();
        float p = shownProgress();
        long since = t - craftedAt;
        // the daisy, swaying in the morning air
        float sway = 5.0F * Mth.sin(t / 900.0F) + 2.0F * Mth.sin(t / 370.0F);
        float scale = 1.6F + 0.05F * Mth.sin(t / 420.0F) + (since < 400L ? 0.25F * (1.0F - since / 400.0F) : 0.0F);
        g.pose().pushPose();
        g.pose().translate(leftPos + CX, topPos + CY + 7.0F, 100.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sway));
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(DAISY, -8, -12);
        g.pose().popPose();
        // the white light rising over the block being purified
        if (busy && active >= 0 && p > 0.0F) {
            int[] at = layout.inputs[active];
            int h = Math.round(16.0F * p);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            int x = leftPos + at[0], y = topPos + at[1];
            g.fill(x, y + 16 - h, x + 16, y + 16, (int) (50 + 70 * p) << 24 | (hurried() ? 0xD8F8FF : WHITE));
            if (h > 0 && h < 16) g.fill(x, y + 16 - h, x + 16, y + 17 - h, 0xD0FFFFFF);
            g.pose().popPose();
        }
    }

    @Override
    protected void onCraft(long t) {
        flashed = active;
        if (active >= 0) {
            float[] at = cell(active);
            burst(Math.round(at[0]), Math.round(at[1]), 8, WHITE, 0.8F);
        }
        burst(CX, CY, 10, PETAL, 0.9F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        if (Math.abs(mx - CX) > 3 * Daisy.CELL / 2 || Math.abs(my - CY) > 3 * Daisy.CELL / 2) return;
        tip.add(Component.translatable("gui.alfheimheart.pure_daisy.daisy").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        if (job != null && !job.outputs().isEmpty() && !job.units().isEmpty()) {
            tip.add(Component.translatable("gui.alfheimheart.pure_daisy.makes", job.units().get(0).getCount(), job.units().get(0).getHoverName(),
                    job.outputs().get(0).getHoverName()).withStyle(ChatFormatting.WHITE));
            tip.add(Component.translatable("gui.alfheimheart.pure_daisy.time", String.format(java.util.Locale.ROOT, "%.1f", job.minTicks() / 20.0F))
                    .withStyle(ChatFormatting.GRAY));
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.pure_daisy.hint").withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable(hurried() ? "gui.alfheimheart.pure_daisy.hurried" : "gui.alfheimheart.pure_daisy.mana")
                .withStyle(ChatFormatting.AQUA));
    }
}
