package com.reya.alfheimheart.machine.apothecary.client;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineLayouts.Apothecary;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.machine.apothecary.PetalApothecaryBlockEntity;
import com.reya.alfheimheart.machine.apothecary.PetalApothecaryMenu;
import com.reya.alfheimheart.machine.client.Gfx;
import com.reya.alfheimheart.machine.client.MachineScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Petal Apothecary's GUI, the flower alchemist's table (petal_apothecary.png; tools/machines/alchemy.py): the
 * apothecary's stone bowl on a walnut table, its nine inputs a fan of petal-shaped brass plates over it, the
 * seeds' slot under it; a round flask of mana on the left, a shelf for the flowers made on the right.
 * <p>
 * The petals of the flower the inputs make turn on the water, which takes on their colours as the craft charges;
 * the plates whose petals go into it glow their colours and petals fly from them into the bowl; steam curls up
 * from the water; the flower takes shape over the bowl, rising and opening, a ring of light closing round it;
 * when it is done the water splashes and the flower flies to the shelf. The flask's mana bubbles while it works.
 */
public class PetalApothecaryScreen extends MachineScreen<PetalApothecaryMenu> {
    private static final int CX = Apothecary.CX, CY = Apothecary.CY, FX = Apothecary.FLOWER[0], FY = Apothecary.FLOWER[1];
    private static final int DEEP = 0x14485C, MID = 0x2E86A8, LIGHT = 0x6CC6E0, BRIGHT = 0xCFF4FF;

    @Nullable
    private MachineBlockEntity.Job job;
    @Nullable
    private MachineBlockEntity.Job needsReagent;
    private final boolean[] used = new boolean[9];
    private final int[] colours = new int[9];
    private final float[] glow = new float[9];
    private float swirl;
    private long steamAt, bubbleAt, moteAt;
    /** Puffs of steam over the bowl: x, y, age (0..1, 1 when gone), drift. */
    private final float[][] steam = {{0, 0, 1, 0}, {0, 0, 1, 0}, {0, 0, 1, 0}, {0, 0, 1, 0}, {0, 0, 1, 0}};

    public PetalApothecaryScreen(PetalApothecaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "petal_apothecary";
    }

    @Override
    protected int titleColour() {
        return 0xFF4A2E14;
    }

    @Override
    protected int titleShadow() {
        return 0xFFFFF6DC;
    }

    @Override
    protected int lightColour() {
        return 0xFFC8DC;
    }

    @Override
    protected int[] veinColours() {
        return new int[]{0xFFF6DC, 0xFFD27A, 0xF2A848};
    }

    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        job = PetalApothecaryBlockEntity.find(minecraft.level, menu.items(), true);
        needsReagent = job == null ? PetalApothecaryBlockEntity.find(minecraft.level, menu.items(), false) : null;
        MachineBlockEntity.Job shown = shown();
        for (int i = 0; i < used.length && i < layout.inputCount(); i++) {
            ItemStack stack = menu.items().getStackInSlot(i);
            used[i] = shown != null && !stack.isEmpty() && shown.units().stream().anyMatch(u -> ItemStack.isSameItemSameTags(u, stack));
            colours[i] = stack.isEmpty() ? 0xFFFFFF : dyeColour(stack);
        }
    }

    @Nullable
    private MachineBlockEntity.Job shown() {
        return job != null ? job : needsReagent;
    }

    private static int mix(int a, int b, float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);
        return Math.round(Mth.lerp(p, a >> 16 & 0xFF, b >> 16 & 0xFF)) << 16 | Math.round(Mth.lerp(p, a >> 8 & 0xFF, b >> 8 & 0xFF)) << 8
                | Math.round(Mth.lerp(p, a & 0xFF, b & 0xFF));
    }

    /** The colour the petals give the water: theirs, mixed. */
    private int petalTint() {
        MachineBlockEntity.Job shown = shown();
        if (shown == null || shown.units().isEmpty()) return MID;
        int r = 0, g = 0, b = 0, n = 0;
        for (ItemStack unit : shown.units()) {
            int c = dyeColour(unit);
            r += c >> 16 & 0xFF;
            g += c >> 8 & 0xFF;
            b += c & 0xFF;
            n++;
        }
        return (r / n) << 16 | (g / n) << 8 | b / n;
    }

    private float[] plate(int i) {
        return new float[]{layout.inputs[i][0] + 8.0F, layout.inputs[i][1] + 8.0F};
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        swirl += dt * (0.5F + (busy ? 1.5F + 3.0F * p : 0.0F));
        long since = t - craftedAt;
        int tint = petalTint();
        MachineBlockEntity.Job shown = shown();

        // the plates whose petals go in glow their colours
        for (int i = 0; i < glow.length && i < layout.inputCount(); i++) {
            float target = used[i] ? (busy ? 0.4F + 0.3F * p + 0.1F * Mth.sin(t / 200.0F + i) : 0.22F) : 0.0F;
            if (since < 400L && used[i]) target = Math.max(target, 1.0F - since / 400.0F);
            glow[i] += (target - glow[i]) * Math.min(1.0F, dt * 6.0F);
            if (glow[i] > 0.01F) {
                float[] at = plate(i);
                Gfx.radial(g, leftPos + at[0], topPos + at[1], 16.0F, Gfx.argb(colours[i], glow[i] * 0.6F), Gfx.argb(colours[i], 0.0F), true);
            }
        }
        // the water, and the petals turning on it
        g.drawManaged(() -> water(g, t, p, tint, since));
        if (shown != null) {
            List<ItemStack> units = shown.units();
            int n = Math.min(12, units.size());
            for (int i = 0; i < n; i++) {
                float a = swirl + i * Gfx.TAU / n;
                float r = 0.75F - (busy ? 0.6F * p : 0.0F) + 0.06F * Mth.sin(t / 500.0F + i);
                float x = CX + Mth.cos(a) * r * Apothecary.WATER_RX - 2.0F, y = CY + Mth.sin(a) * r * Apothecary.WATER_RY - 1.5F;
                glowBlit(g, x, y, Apothecary.PETAL_U, Apothecary.PETAL_V, 4, 3, dyeColour(units.get(i)), 0.95F);
            }
        }
        // petals flying from the glowing plates into the bowl while it works
        if (busy) {
            for (int i = 0; i < used.length && i < layout.inputCount(); i++) {
                if (!used[i]) continue;
                float k = ((t / 900.0F) + i * 0.31F) % 1.0F;
                float[] from = plate(i);
                float mx = (from[0] + CX) / 2.0F, my = Math.min(from[1], CY) - 18.0F;
                float x = (1 - k) * (1 - k) * from[0] + 2 * (1 - k) * k * mx + k * k * CX;
                float y = (1 - k) * (1 - k) * from[1] + 2 * (1 - k) * k * my + k * k * (CY - 1);
                glowBlit(g, x - 2.0F, y - 1.5F, Apothecary.PETAL_U, Apothecary.PETAL_V, 4, 3, colours[i], 0.9F * Mth.sin(k * PI) + 0.1F);
            }
        }
        // steam curling up from the water
        if (t - steamAt > (busy ? 380L : 1100L)) {
            steamAt = t;
            for (int k = 0; k < steam.length; k++) {
                if (steam[k][2] >= 1.0F) {
                    steam[k] = new float[]{CX + (random.nextFloat() - 0.5F) * Apothecary.WATER_RX, CY - 3.0F, 0.0F, (random.nextFloat() - 0.5F) * 5.0F};
                    break;
                }
            }
        }
        int s = Apothecary.STEAM_SIZE;
        for (float[] puff : steam) {
            if (puff[2] >= 1.0F) continue;
            puff[2] += dt / 2.2F;
            puff[1] -= dt * 9.0F;
            puff[0] += dt * puff[3] + Mth.sin(puff[2] * 6.0F) * dt * 3.0F;
            float a = Mth.sin(puff[2] * PI) * 0.35F;
            float grow = 0.6F + puff[2] * 0.9F;
            g.pose().pushPose();
            g.pose().translate(leftPos + puff[0], topPos + puff[1], 0.0F);
            g.pose().scale(grow, grow, 1.0F);
            glowBlit(g, -leftPos - s / 2.0F, -topPos - s / 2.0F, Apothecary.STEAM_U, Apothecary.STEAM_V, s, s, 0xF4F8FF, a);
            g.pose().popPose();
        }
        // the flower's light, and a ring of it closing round the flower as the craft charges
        float halo = busy ? 0.25F + 0.6F * p : job != null ? 0.15F : 0.0F;
        if (since < 400L) halo = Math.max(halo, 1.0F - since / 400.0F);
        int light = mix(0xFFFFFF, tint, 0.5F);
        if (halo > 0.02F) Gfx.radial(g, leftPos + FX, topPos + FY, 15.0F, Gfx.argb(light, halo * 0.7F), Gfx.argb(light, 0.0F), true);
        if (busy && p > 0.0F) {
            float start = -PI / 2.0F;
            Gfx.arc(g, leftPos + FX, topPos + FY, 11.0F, 13.5F, start, start + p * Gfx.TAU, k -> Gfx.argb(mix(light, 0xFFFFFF, k), 0.85F), true,
                    true);
        }
        // colour rising from the water into the flower
        if (busy && shown != null && !shown.units().isEmpty() && t - moteAt > 90L) {
            moteAt = t;
            float x = CX + (random.nextFloat() - 0.5F) * Apothecary.WATER_RX * 1.2F, y = CY + (random.nextFloat() - 0.5F) * Apothecary.WATER_RY;
            float life = 0.6F + random.nextFloat() * 0.3F;
            drift(x, y, (FX - x) / life, (FY + 4 - y) / life, life, dyeColour(shown.units().get(random.nextInt(shown.units().size()))));
        }
    }

    /** The water in the bowl: light running over it, taking the petals' colours as the craft charges, a ripple after each. */
    private void water(GuiGraphics g, long t, float p, int tint, long since) {
        int rx = Apothecary.WATER_RX, ry = Apothecary.WATER_RY;
        for (int y = -ry; y < ry; y++) {
            float fy = (y + 0.5F) / ry;
            float w = rx * Mth.sqrt(Math.max(0.0F, 1.0F - fy * fy));
            int x1 = (int) Math.ceil(CX - w - 0.5F), x2 = (int) Math.floor(CX + w - 0.5F);
            for (int x = x1; x <= x2; x++) {
                float wave = Mth.sin((x - CX) * 0.5F + y * 1.1F + t / 300.0F) + 0.5F * Mth.sin((x - CX) * -0.25F + y * 1.5F - t / 450.0F);
                float k = (wave + 1.5F) / 3.0F;
                int col = mix(DEEP, MID, 0.4F + 0.6F * k);
                if (k > 0.8F) col = mix(col, LIGHT, (k - 0.8F) / 0.2F);
                col = mix(col, DEEP, Math.max(0.0F, -fy) * 0.3F);
                col = mix(col, tint, 0.08F + 0.35F * p);
                g.fill(leftPos + x, topPos + CY + y, leftPos + x + 1, topPos + CY + y + 1, 0xF0000000 | col);
            }
        }
        if (since < 600L) {
            float e = easeOut(since / 600.0F);
            float erx = 2.0F + (rx - 2.0F) * e, ery = 0.6F + (ry - 0.6F) * e;
            int alpha = (int) (220 * (1.0F - e));
            int steps = (int) (erx * 5.0F);
            for (int s = 0; s < steps; s++) {
                float a = s / (float) steps * Gfx.TAU;
                int x = leftPos + CX + Math.round(Mth.cos(a) * erx - 0.5F), y = topPos + CY + Math.round(Mth.sin(a) * ery - 0.5F);
                g.fill(x, y, x + 1, y + 1, alpha << 24 | BRIGHT);
            }
        }
    }

    // ------------------------------------------------------------------ the flask

    @Override
    protected void drawGauge(GuiGraphics g, long t, float mana) {
        int[][] rows = Apothecary.FLASK_ROWS;
        int n = Math.round(rows.length * Mth.clamp(mana, 0.0F, 1.0F));
        g.drawManaged(() -> {
            for (int i = 0; i < n; i++) {
                int y = rows[i][0], x1 = rows[i][1], x2 = rows[i][2];
                float h = i / (float) rows.length;
                int base = mix(0x1B64B8, 0x6FD8FF, h);
                for (int x = x1; x < x2; x++) {
                    int c = base;
                    if (x == x1 || x == x2 - 1) c = mix(c, 0x0B2F5C, 0.4F);
                    if (i == n - 1) c = mix(c, 0xF2FFFF, 0.6F + 0.4F * Mth.sin(t / 150.0F + x * 0.9F));
                    g.fill(leftPos + x, topPos + y, leftPos + x + 1, topPos + y + 1, 0xE8000000 | c);
                }
            }
        });
        if (n > 0) {
            int[] top = rows[n - 1];
            Gfx.radial(g, leftPos + (top[1] + top[2]) / 2.0F, topPos + top[0], 7.0F, Gfx.argb(MANA_BRIGHT, 0.3F), 0x00A6F6FF, true);
            if (working() && t - bubbleAt > 180L) {
                bubbleAt = t;
                int[] row = rows[random.nextInt(Math.max(1, Math.min(n, 12)))];
                if (row[2] - row[1] > 2) {
                    drift(row[1] + 1 + random.nextInt(row[2] - row[1] - 2), row[0], 0.0F, -12.0F, Math.max(0.2F, (row[0] - top[0]) / 12.0F), 0xF2FFFF);
                }
            }
        }
        if (menu.status() == MachineStatus.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            Gfx.radial(g, leftPos + Apothecary.FLASK[0] + 0.5F, topPos + Apothecary.FLASK[1], Apothecary.FLASK[2],
                    Gfx.argb(0xC82C26, 0.1F + 0.25F * beat), 0x00C82C26, false);
        }
    }

    @Override
    protected boolean overGauge(int mx, int my) {
        for (int[] row : Apothecary.FLASK_ROWS) {
            if (row[0] == my) return mx >= row[1] - 2 && mx < row[2] + 2;
        }
        return false;
    }

    // ------------------------------------------------------------------ over the items

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        MachineBlockEntity.Job shown = shown();
        boolean busy = working();
        float p = shownProgress();
        if (shown != null && !shown.outputs().isEmpty()) {
            // the flower rising over the bowl and opening as the craft charges
            float rise = busy ? p : 0.0F;
            float bob = 1.2F * Mth.sin(t / 360.0F);
            floatingItem(g, shown.outputs().get(0), FX, FY + (1.0F - rise) * 6.0F + bob, 0.75F + 0.35F * rise);
            if (job == null) dim(g, FX, FY + 6.0F, 9.0F, 0x901C120A);
        }
        if (menu.items().getStackInSlot(PetalApothecaryBlockEntity.REAGENT).isEmpty()) {
            ghostSlot(g, layout.special[0][0], layout.special[0][1], new ItemStack(Items.WHEAT_SEEDS), needsReagent != null, t);
        }
    }

    @Override
    protected void onCraft(long t) {
        burst(CX, CY, 12, mix(0xFFFFFF, petalTint(), 0.6F), 1.0F);
        burst(FX, FY, 8, PINK_LIGHT, 0.8F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        if (mx < CX - Apothecary.RIM_RX || mx >= CX + Apothecary.RIM_RX || my < FY - 14 || my >= CY + Apothecary.RIM_RY + 12) return;
        tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.bowl").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        MachineBlockEntity.Job shown = shown();
        if (shown != null && !shown.outputs().isEmpty()) {
            tip.add(Component.translatable("gui.alfheimheart.makes", shown.outputs().get(0).getHoverName()).withStyle(ChatFormatting.WHITE));
            if (shown.mana() > 0) tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(shown.mana())).withStyle(ChatFormatting.AQUA));
            if (needsReagent != null) tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.needs_reagent").withStyle(ChatFormatting.RED));
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void specialSlotTooltip(List<Component> tip, int index) {
        tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.slot.reagent").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.slot.reagent.tip").withStyle(ChatFormatting.GRAY));
    }
}
