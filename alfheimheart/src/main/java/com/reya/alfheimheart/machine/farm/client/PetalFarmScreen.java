package com.reya.alfheimheart.machine.farm.client;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineLayouts.Farm;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.machine.client.Gfx;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.farm.PetalFarmBlockEntity;
import com.reya.alfheimheart.machine.farm.PetalFarmMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Petal Farm's GUI, the greenhouse (petal_farm.png; tools/machines/greenhouse.py): six clay pots on two
 * shelves under glass, the bone meal in a sack under them, a watering can of mana on the left, jars for the
 * petals on the right.
 * <p>
 * Each cycle is a day: the sun crosses the sky beyond the glass from left to right as it goes by, the light
 * warming at dawn and dusk, petals of the flowers' colours rising off the pots that hold them, a glow over each;
 * when the day is done the flowers shake out a burst of petals and they fly to the jars. The can's mana ripples
 * at its surface, and while the farm works drops fall from its rose.
 */
public class PetalFarmScreen extends MachineScreen<PetalFarmMenu> {
    private static final int SUN_X = Farm.SUN[0], SUN_Y = Farm.SUN[1], SUN_R = Farm.SUN[2];

    @Nullable
    private MachineBlockEntity.Job job;
    private final boolean[] planted = new boolean[6];
    private final int[] colours = new int[6];
    private long petalAt, dropAt;

    public PetalFarmScreen(PetalFarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "petal_farm";
    }

    @Override
    protected int titleColour() {
        return 0xFF2E6A3A;
    }

    @Override
    protected int titleShadow() {
        return 0xFFFFFFFF;
    }

    @Override
    protected int lightColour() {
        return 0xFFC8DC;
    }

    @Override
    protected int[] veinColours() {
        return new int[]{0xFFFFFF, 0xFFD0E0, 0xFF8AB0};
    }

    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        job = PetalFarmBlockEntity.find(minecraft.level, menu.items());
        for (int i = 0; i < planted.length && i < layout.inputCount(); i++) {
            ItemStack stack = menu.items().getStackInSlot(i);
            planted[i] = !PetalFarmBlockEntity.petalOf(stack).isEmpty();
            colours[i] = planted[i] ? dyeColour(stack) : 0xFFFFFF;
        }
    }

    private float[] pot(int i) {
        return new float[]{layout.inputs[i][0] + 8.0F, layout.inputs[i][1] + 8.0F};
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        long since = t - craftedAt;
        // the light of the time of day over everything under the glass: warm at dawn and dusk, clear at noon
        float day = busy ? p : 0.5F;
        float warm = Math.max(0.0F, 1.0F - Math.min(day, 1.0F - day) * 4.0F);
        int tint = Gfx.mix(0xFFFFF0D8, 0xFFFF9A5A, warm) & 0xFFFFFF;
        Gfx.gradient(g, leftPos + 7, topPos + 7, leftPos + layout.width - 7, topPos + 117, Gfx.argb(tint, 0.10F + 0.12F * warm),
                Gfx.argb(tint, 0.10F + 0.12F * warm), Gfx.argb(tint, 0.03F), Gfx.argb(tint, 0.03F), false);
        // the sun crossing the sky beyond the glass
        float a = PI + PI * day;
        float sx = SUN_X + Mth.cos(a) * SUN_R * 2.2F, sy = SUN_Y + Mth.sin(a) * SUN_R * 0.75F;
        int sun = Gfx.mix(0xFFFFF4C0, 0xFFFFB060, warm) & 0xFFFFFF;
        Gfx.radial(g, leftPos + sx, topPos + sy, 16.0F, Gfx.argb(sun, busy ? 0.55F : 0.3F), Gfx.argb(sun, 0.0F), true);
        Gfx.radial(g, leftPos + sx, topPos + sy, 4.0F, Gfx.argb(0xFFFFF8, busy ? 0.95F : 0.6F), Gfx.argb(sun, 0.0F), true);
        // a glow over each pot with a flower in it, petals rising off them while the farm works
        for (int i = 0; i < planted.length && i < layout.inputCount(); i++) {
            if (!planted[i]) continue;
            float[] at = pot(i);
            float glow = busy ? 0.22F + 0.18F * Mth.sin(t / 300.0F + i * 1.3F) : 0.1F;
            if (since < 450L) glow = Math.max(glow, 1.0F - since / 450.0F);
            Gfx.radial(g, leftPos + at[0], topPos + at[1] - 2.0F, 14.0F, Gfx.argb(colours[i], glow), Gfx.argb(colours[i], 0.0F), true);
        }
        if (busy && t - petalAt > 110L) {
            petalAt = t;
            int i = random.nextInt(planted.length);
            if (i < layout.inputCount() && planted[i]) {
                float[] at = pot(i);
                drift(at[0] + (random.nextFloat() - 0.5F) * 10.0F, at[1] - 8.0F, (random.nextFloat() - 0.2F) * 12.0F, -10.0F - random.nextFloat() * 10.0F,
                        1.2F + random.nextFloat() * 0.8F, colours[i]);
            }
        }
    }

    // ------------------------------------------------------------------ the watering can

    @Override
    protected void drawGauge(GuiGraphics g, long t, float mana) {
        int[][] rows = Farm.CAN_ROWS;
        int n = Math.round(rows.length * Mth.clamp(mana, 0.0F, 1.0F));
        g.drawManaged(() -> {
            for (int i = 0; i < n; i++) {
                int y = rows[i][0], x1 = rows[i][1], x2 = rows[i][2];
                int base = Gfx.mix(0xFF1B64B8, 0xFF8FEFFF, i / (float) rows.length) & 0xFFFFFF;
                for (int x = x1; x < x2; x++) {
                    int c = base;
                    if (i == n - 1) c = Gfx.mix(0xFF000000 | c, 0xFFF2FFFF, 0.55F + 0.45F * Mth.sin(t / 160.0F + x * 0.7F)) & 0xFFFFFF;
                    g.fill(leftPos + x, topPos + y, leftPos + x + 1, topPos + y + 1, 0xE8000000 | c);
                }
            }
        });
        if (n > 0) {
            int[] top = rows[n - 1];
            Gfx.radial(g, leftPos + (top[1] + top[2]) / 2.0F, topPos + top[0], 10.0F, Gfx.argb(MANA_BRIGHT, 0.25F), 0x00A6F6FF, true);
        }
        // drops falling from the rose while the farm works
        if (working() && n > 0 && t - dropAt > 150L) {
            dropAt = t;
            float rx = Farm.CAN[2] + 15.0F, ry = Farm.CAN[1] - 5.0F;
            drift(rx + random.nextFloat() * 3.0F, ry, 6.0F + random.nextFloat() * 6.0F, 26.0F, 0.9F, MANA_BRIGHT);
        }
        if (menu.status() == MachineStatus.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            g.fill(leftPos + Farm.CAN[0] + 3, topPos + Farm.CAN[1] + 3, leftPos + Farm.CAN[2] - 3, topPos + Farm.CAN[3] - 3,
                    (int) (20 + beat * 60) << 24 | 0xC82C26);
        }
    }

    @Override
    protected boolean overGauge(int mx, int my) {
        return mx >= Farm.CAN[0] && mx < Farm.CAN[2] && my >= Farm.CAN[1] - 12 && my < Farm.CAN[3];
    }

    // ------------------------------------------------------------------ over the items

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        if (menu.items().getStackInSlot(PetalFarmBlockEntity.FERTILIZER).isEmpty()) {
            ghostSlot(g, layout.special[0][0], layout.special[0][1], new ItemStack(Items.BONE_MEAL), false, t);
        }
    }

    @Override
    protected void onCraft(long t) {
        for (int i = 0; i < planted.length && i < layout.inputCount(); i++) {
            if (!planted[i]) continue;
            float[] at = pot(i);
            burst(Math.round(at[0]), Math.round(at[1]) - 8, 6, colours[i], 0.8F);
        }
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        if (mx < Farm.SHELVES[0][0] || mx >= Farm.SHELVES[0][2] || my < 10 || my >= Farm.SHELVES[1][1] + 6) return;
        tip.add(Component.translatable("gui.alfheimheart.petal_farm.farm").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        if (job != null) {
            int petals = 0;
            for (ItemStack out : job.outputs()) petals += out.getCount();
            tip.add(Component.translatable("gui.alfheimheart.petal_farm.cycle", petals, String.format(java.util.Locale.ROOT, "%.1f",
                    job.minTicks() / 20.0F)).withStyle(ChatFormatting.WHITE));
            if (job.mana() > 0) tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(job.mana())).withStyle(ChatFormatting.AQUA));
            if (!menu.items().getStackInSlot(PetalFarmBlockEntity.FERTILIZER).isEmpty()) {
                tip.add(Component.translatable("gui.alfheimheart.petal_farm.fed").withStyle(ChatFormatting.GREEN));
            }
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.petal_farm.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void specialSlotTooltip(List<Component> tip, int index) {
        tip.add(Component.translatable("gui.alfheimheart.petal_farm.slot.fertilizer").withStyle(ChatFormatting.GREEN));
        tip.add(Component.translatable("gui.alfheimheart.petal_farm.slot.fertilizer.tip").withStyle(ChatFormatting.GRAY));
    }
}
