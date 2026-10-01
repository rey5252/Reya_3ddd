package com.reya.alfheimheart.machine.infuser.client;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineLayouts.Infuser;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.machine.client.Gfx;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.infuser.ManaInfuserBlockEntity;
import com.reya.alfheimheart.machine.infuser.ManaInfuserMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Mana Infuser's GUI, the crystal fountain (mana_infuser.png; tools/machines/fountain.py): a marble fountain in
 * a courtyard, the inputs in an alcove on its left, the outputs in one on its right, the catalyst the stone it
 * stands on.
 * <p>
 * The fountain's water is the machine's mana: it rises in the basin as the store fills, waves running over it,
 * light playing on the wall above it, deeper and darker when it is low and pink with an Alchemy Catalyst. The
 * item being infused floats over the basin; as the craft charges, streams of mana spiral up into it and a ring of
 * light closes round it; when it is done a ripple runs out over the water, drops splash up and what it made flies
 * to the outputs. A circle of runes round the catalyst glows its colour, brighter while the fountain works.
 */
public class ManaInfuserScreen extends MachineScreen<ManaInfuserMenu> {
    private static final int CX = Infuser.CX, CY = Infuser.CY;
    private static final int ITEM_Y = CY - 27;
    private static final int DEEP = 0x0E3A7A, MID = 0x2A9FE2, LIGHT = 0x55D9F7, BRIGHT = 0xA6F6FF, FOAM = 0xE6FFFF;

    @Nullable
    private MachineBlockEntity.Job job;
    private long moteAt, splashAt = -100000L;

    public ManaInfuserScreen(ManaInfuserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "mana_infuser";
    }

    @Override
    protected int titleColour() {
        return 0xFF0E4A4A;
    }

    @Override
    protected int titleShadow() {
        return 0xFFFFFFFF;
    }

    @Override
    protected int lightColour() {
        return 0xA8F5E6;
    }

    @Override
    protected int[] veinColours() {
        return new int[]{0xFFFFFF, 0xA8F5E6, 0x5FDCCB};
    }

    private void refresh(long t) {
        if (itemsChanged(t) && minecraft != null && minecraft.level != null) job = ManaInfuserBlockEntity.find(minecraft.level, menu.items());
    }

    private float[] tint() {
        return ManaInfuserColours.tint(menu.items().getStackInSlot(ManaInfuserBlockEntity.CATALYST));
    }

    private static int mix(int a, int b, float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);
        return Math.round(Mth.lerp(p, a >> 16 & 0xFF, b >> 16 & 0xFF)) << 16 | Math.round(Mth.lerp(p, a >> 8 & 0xFF, b >> 8 & 0xFF)) << 8
                | Math.round(Mth.lerp(p, a & 0xFF, b & 0xFF));
    }

    /** Where the water's surface is (its middle's y), for how full the store is. */
    private static float waterY(float mana) {
        return CY + Mth.lerp(Mth.clamp(mana, 0.0F, 1.0F), Infuser.WATER_EMPTY, Infuser.WATER_FULL);
    }

    /** How wide the opening is on row y (half its width), or -1 off it. */
    private static float openingHalf(float y, float middle) {
        float fy = (y + 0.5F - middle) / Infuser.OPEN_RY;
        return Math.abs(fy) >= 1.0F ? -1.0F : Infuser.OPEN_RX * Mth.sqrt(1.0F - fy * fy);
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = shownProgress();
        float[] tint = tint();
        long since = t - craftedAt;
        // the circle of runes round the catalyst
        ItemStack catalyst = menu.items().getStackInSlot(ManaInfuserBlockEntity.CATALYST);
        if (!catalyst.isEmpty()) {
            int s = Infuser.CATALYST_RING_SIZE;
            int col = ManaInfuserColours.tint(0xC8B8FF, tint);
            float a = (busy ? 0.6F + 0.3F * Mth.sin(t / 200.0F) : 0.35F + 0.1F * Mth.sin(t / 600.0F));
            int[] slot = layout.special[0];
            glowBlit(g, slot[0] + 8 - s / 2.0F, slot[1] + 8 - s / 2.0F, Infuser.CATALYST_RING_U, Infuser.CATALYST_RING_V, s, s, col, a);
        }
        // the water, its level the store's
        float mana = shownMana();
        if (mana > 0.004F) {
            float wy = waterY(mana);
            g.drawManaged(() -> water(g, t, wy, mana, tint, since));
            // light playing on the far wall over the water
            for (int k = 0; k < 3; k++) {
                float u = (t / 2600.0F + k * 0.37F) % 1.0F;
                float x = CX - Infuser.OPEN_RX * 0.7F + u * Infuser.OPEN_RX * 1.4F;
                float y = wy - Infuser.OPEN_RY + 2.0F - 3.0F * Mth.sin(u * PI);
                if (y > CY - Infuser.OPEN_RY + 1) {
                    Gfx.radial(g, leftPos + x, topPos + y, 5.0F, Gfx.argb(ManaInfuserColours.tint(BRIGHT, tint), 0.25F * Mth.sin(u * PI)),
                            Gfx.argb(BRIGHT, 0.0F), true);
                }
            }
        }
        // the light round the floating item, and a ring of it closing as the craft charges
        float halo = busy ? 0.3F + 0.6F * p : job != null ? 0.15F : 0.0F;
        if (since < 400L) halo = Math.max(halo, 1.0F - since / 400.0F);
        int light = ManaInfuserColours.tint(BRIGHT, tint);
        if (halo > 0.02F) Gfx.radial(g, leftPos + CX, topPos + ITEM_Y, 14.0F, Gfx.argb(light, halo * 0.7F), Gfx.argb(light, 0.0F), true);
        if (busy && p > 0.0F) {
            float start = -PI / 2.0F;
            Gfx.arc(g, leftPos + CX, topPos + ITEM_Y, 11.0F, 14.0F, start, start + p * Gfx.TAU, k -> Gfx.argb(mix(light, FOAM, k), 0.9F), true, true);
        }
        // streams of mana spiralling up from the water into the item
        if (busy && mana > 0.004F) {
            float wy = waterY(mana);
            for (int s = 0; s < 3; s++) {
                float prevX = 0, prevY = 0;
                for (int k = 0; k <= 12; k++) {
                    float h = k / 12.0F;
                    float a = t / 300.0F + s * Gfx.TAU / 3.0F + h * 5.0F;
                    float r = (1.0F - h) * 14.0F + 2.0F;
                    float x = CX + Mth.cos(a) * r, y = Mth.lerp(h, wy - 2.0F, ITEM_Y + 4.0F) + Mth.sin(a) * r * 0.25F;
                    if (k > 0) {
                        Gfx.beam(g, leftPos + prevX, topPos + prevY, leftPos + x, topPos + y, 1.6F, Gfx.argb(light, 0.25F + 0.4F * p * h),
                                Gfx.argb(light, 0.25F + 0.4F * p * h), true);
                    }
                    prevX = x;
                    prevY = y;
                }
            }
            if (t - moteAt > 70L) {
                moteAt = t;
                float x = CX + (random.nextFloat() - 0.5F) * Infuser.OPEN_RX * 1.2F;
                float y = wy + (random.nextFloat() - 0.5F) * Infuser.OPEN_RY;
                float life = 0.5F + random.nextFloat() * 0.3F;
                drift(x, y, (CX - x) / life, (ITEM_Y + 4 - y) / life, life, random.nextInt(3) == 0 ? FOAM : light);
            }
        }
        // drops splashing up after a craft
        if (since < 60L && t - splashAt > 300L) {
            splashAt = t;
            float wy = waterY(mana);
            for (int i = 0; i < 10; i++) {
                float vx = (random.nextFloat() - 0.5F) * 50.0F;
                drift(CX + vx * 0.1F, wy - 1.0F, vx, -30.0F - random.nextFloat() * 30.0F, 0.5F + random.nextFloat() * 0.3F, light);
            }
        }
    }

    /** The water's visible part: its surface where the basin's opening shows it, waves on it, ripples after a craft. */
    private void water(GuiGraphics g, long t, float wy, float mana, float[] tint, long since) {
        int ry = Infuser.OPEN_RY;
        float ripple = since < 900L ? easeOut(since / 900.0F) : -1.0F;
        int top = (int) Math.floor(wy - ry), bottom = (int) Math.ceil(wy + ry);
        for (int y = top; y < bottom; y++) {
            float half = Math.min(openingHalf(y, wy), openingHalf(y, CY));
            if (half <= 0.0F) continue;
            float fy = (y + 0.5F - wy) / ry;                           // -1 at the far edge, 1 at the near one
            int x1 = (int) Math.ceil(CX - half - 0.5F), x2 = (int) Math.floor(CX + half - 0.5F);
            boolean meniscus = openingHalf(y - 1, wy) < 0.0F;
            for (int x = x1; x <= x2; x++) {
                float dx = (x + 0.5F - CX) / Infuser.OPEN_RX;
                float wave = Mth.sin((x - CX) * 0.42F + y * 0.9F + t / 260.0F) + 0.6F * Mth.sin((x - CX) * -0.19F + y * 1.4F - t / 430.0F);
                float k = (wave + 1.6F) / 3.2F;
                float depth = 0.35F + 0.65F * mana;
                int col = mix(DEEP, MID, depth * (0.55F + 0.45F * (fy + 1.0F) / 2.0F));
                col = mix(col, LIGHT, Math.max(0.0F, k - 0.5F) * 1.3F * depth);
                if (k > 0.88F) col = mix(col, BRIGHT, (k - 0.88F) / 0.12F);
                if (meniscus || x == x1 || x == x2) col = mix(col, FOAM, 0.6F);
                if (ripple >= 0.0F) {
                    float r = (float) Math.sqrt(dx * dx + fy * fy);
                    float d = Math.abs(r - ripple * 1.1F);
                    if (d < 0.08F) col = mix(col, FOAM, (1.0F - d / 0.08F) * (1.0F - ripple));
                }
                col = ManaInfuserColours.tint(col, tint);
                g.fill(leftPos + x, topPos + y, leftPos + x + 1, topPos + y + 1, 0xF0000000 | col);
            }
        }
    }

    // ------------------------------------------------------------------ the gauge is the water

    @Override
    protected void drawGauge(GuiGraphics g, long t, float mana) {
        if (menu.status() == MachineStatus.NO_MANA) {
            // the empty basin's floor blinks red: the craft waits for mana
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            float wy = waterY(0.0F);
            Gfx.radial(g, leftPos + CX, topPos + wy, 18.0F, Gfx.argb(0xC82C26, 0.15F + 0.3F * beat), Gfx.argb(0xC82C26, 0.0F), false);
        }
    }

    @Override
    protected boolean overGauge(int mx, int my) {
        float dx = (mx + 0.5F - CX) / Infuser.RIM_RX;
        if (Math.abs(dx) >= 1.0F) return false;
        float rimBottom = CY + Infuser.RIM_RY * Mth.sqrt(1.0F - dx * dx);
        float dy = (my + 0.5F - CY) / Infuser.RIM_RY;
        boolean inRim = dx * dx + dy * dy <= 1.0F;
        return inRim || my >= CY && my <= rimBottom + Infuser.DEPTH;
    }

    // ------------------------------------------------------------------ over the items

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        ItemStack shown = job != null && !job.units().isEmpty() ? job.units().get(0).copyWithCount(1) : ItemStack.EMPTY;
        if (shown.isEmpty()) return;
        boolean busy = working();
        long since = t - craftedAt;
        float bob = 1.5F * Mth.sin(t / 380.0F);
        float pop = since < 300L ? 0.6F + 0.4F * easeOut(since / 300.0F) : 1.0F;
        floatingItem(g, shown, CX, ITEM_Y + bob, (busy ? 1.0F + 0.05F * Mth.sin(t / 140.0F) : 1.0F) * pop);
    }

    @Override
    protected void onCraft(long t) {
        burst(CX, Math.round(waterY(shownMana())), 10, ManaInfuserColours.tint(BRIGHT, tint()), 1.0F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        if (mx < CX - Infuser.OPEN_RX || mx >= CX + Infuser.OPEN_RX || my < ITEM_Y - 14 || my >= CY) return;
        tip.add(Component.translatable("gui.alfheimheart.mana_infuser.pool").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        if (job != null && !job.outputs().isEmpty()) {
            ItemStack out = job.outputs().get(0);
            tip.add(Component.translatable("gui.alfheimheart.mana_infuser.makes", out.getCount(), out.getHoverName(),
                    job.units().isEmpty() ? 1 : job.units().get(0).getCount()).withStyle(ChatFormatting.WHITE));
            tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(job.mana())).withStyle(ChatFormatting.AQUA));
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.mana_infuser.hint").withStyle(ChatFormatting.GRAY));
        }
        ItemStack catalyst = menu.items().getStackInSlot(ManaInfuserBlockEntity.CATALYST);
        if (!catalyst.isEmpty()) {
            tip.add(Component.translatable("gui.alfheimheart.mana_infuser.catalyst", catalyst.getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @Override
    protected void specialSlotTooltip(List<Component> tip, int index) {
        tip.add(Component.translatable("gui.alfheimheart.mana_infuser.slot.catalyst").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.translatable("gui.alfheimheart.mana_infuser.slot.catalyst.tip").withStyle(ChatFormatting.GRAY));
    }
}
