package com.reya.alfheimheart.machine.infuser.client;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
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
 * The Mana Infuser's GUI: a little mana pool in the middle (mana_infuser.png) standing on the catalyst slot. The
 * mana in it shimmers, fuller and brighter as the store fills, tinted by the catalyst; the item being infused
 * floats over it, mana rising into it as the craft charges, a ring of light closing round it; when it is done
 * a ripple runs over the mana and what it made flies out to the outputs.
 */
public class ManaInfuserScreen extends MachineScreen<ManaInfuserMenu> {
    // the pool (tools/machines/layout.py POOL_; check_layout.py keeps these in step)
    static final int POOL_X = 120, POOL_Y = 50, SURFACE_RX = 23, SURFACE_RY = 7;
    static final int ITEM_X = 120, ITEM_Y = 27, ITEM_RING_R = 12;
    static final int GEM_X = 120, GEM_Y = 66;
    static final int[] HEART_BOX = {90, 12, 150, 80};
    static final int HALO_U = 48, HALO_V = 96, HALO_SIZE = 15;
    private static final int DEEP = 0x123C7C, MID = 0x2A9FE2, LIGHT = 0x55D9F7, BRIGHT = 0xA6F6FF;

    @Nullable
    private MachineBlockEntity.Job job;
    private long moteAt, twinkleAt;
    private float twinkleX, twinkleY;

    public ManaInfuserScreen(ManaInfuserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "mana_infuser";
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

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = shownProgress();
        float fill = menu.capacity() <= 1 ? 0.0F : Mth.clamp(menu.mana() / (float) menu.capacity(), 0.0F, 1.0F);
        float[] tint = tint();
        long since = t - craftedAt;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // the mana: deeper and darker when the store is low, light running over it in waves
        if (fill > 0.002F) {
            g.drawManaged(() -> surface(g, t, fill, tint));
            // a twinkle on the mana now and then
            if (t - twinkleAt > 700L) {
                twinkleAt = t;
                float a = random.nextFloat() * 2.0F * PI, r = random.nextFloat() * 0.8F;
                twinkleX = POOL_X + Mth.cos(a) * r * SURFACE_RX;
                twinkleY = POOL_Y + Mth.sin(a) * r * SURFACE_RY;
            }
            float tw = 1.0F - (t - twinkleAt) / 700.0F;
            sparkle(g, leftPos + Math.round(twinkleX), topPos + Math.round(twinkleY), BRIGHT, tw * tw);
        }
        // a ripple after each infusion
        if (since < 700L) g.drawManaged(() -> ripple(g, since, tint));
        drawItemLight(g, t, busy, p, since, tint);
    }

    private void surface(GuiGraphics g, long t, float fill, float[] tint) {
        float level = 0.35F + 0.65F * fill;
        for (int y = -SURFACE_RY; y < SURFACE_RY; y++) {
            float fy = (y + 0.5F) / SURFACE_RY;
            float w = SURFACE_RX * Mth.sqrt(Math.max(0.0F, 1.0F - fy * fy));
            int x1 = (int) Math.ceil(POOL_X - w - 0.5F), x2 = (int) Math.floor(POOL_X + w - 0.5F);
            for (int x = x1; x <= x2; x++) {
                float wave = Mth.sin((x - POOL_X) * 0.45F + y * 0.9F + t / 260.0F) + 0.6F * Mth.sin((x - POOL_X) * -0.21F + y * 1.3F - t / 410.0F);
                float k = (wave + 1.6F) / 3.2F;
                int col = mix(mix(DEEP, MID, level), LIGHT, Math.max(0.0F, k - 0.45F) * 1.4F * level);
                if (k > 0.86F) col = mix(col, BRIGHT, (k - 0.86F) / 0.14F);
                // the far side a little darker: it is seen at a slant
                col = mix(col, DEEP, Math.max(0.0F, -fy) * 0.25F);
                col = ManaInfuserColours.tint(col, tint);
                g.fill(leftPos + x, topPos + POOL_Y + y, leftPos + x + 1, topPos + POOL_Y + y + 1, 0xF0000000 | col);
            }
        }
    }

    private void ripple(GuiGraphics g, long since, float[] tint) {
        float e = easeOut(since / 700.0F);
        float rx = 3.0F + (SURFACE_RX - 3.0F) * e, ry = 1.0F + (SURFACE_RY - 1.0F) * e;
        int alpha = (int) (200 * (1.0F - e));
        int steps = (int) (rx * 5.0F);
        for (int s = 0; s < steps; s++) {
            float a = s / (float) steps * 2.0F * PI;
            int x = leftPos + POOL_X + Math.round(Mth.cos(a) * rx - 0.5F), y = topPos + POOL_Y + Math.round(Mth.sin(a) * ry - 0.5F);
            g.fill(x, y, x + 1, y + 1, alpha << 24 | ManaInfuserColours.tint(BRIGHT, tint));
        }
    }

    private void drawItemLight(GuiGraphics g, long t, boolean busy, float p, long since, float[] tint) {
        // the light round the floating item
        float halo = busy ? 0.25F + 0.65F * p : job != null ? 0.15F : 0.0F;
        if (since < 400L) halo = Math.max(halo, 1.0F - since / 400.0F);
        if (halo > 0.02F) {
            g.setColor(0.55F * tint[0], 0.95F * tint[1], tint[2], halo);
            g.blit(WIDGETS, leftPos + ITEM_X - HALO_SIZE / 2 - 1, topPos + ITEM_Y - HALO_SIZE / 2 - 1, HALO_U, HALO_V, HALO_SIZE, HALO_SIZE,
                    WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (busy) progressRing(g, ITEM_X, ITEM_Y, ITEM_RING_R, p, ManaInfuserColours.tint(MANA_BRIGHT, tint), t);
        // mana rising from the pool into the item
        if (busy && t - moteAt > 60L) {
            moteAt = t;
            float x = POOL_X + (random.nextFloat() - 0.5F) * SURFACE_RX * 1.4F;
            float y = POOL_Y - SURFACE_RY * 0.4F + random.nextFloat() * SURFACE_RY * 0.8F;
            float dx = ITEM_X - x, dy = ITEM_Y + 4 - y;
            float life = 0.55F + random.nextFloat() * 0.3F;
            drift(x, y, dx / life, dy / life, life, ManaInfuserColours.tint(random.nextInt(3) == 0 ? BRIGHT : LIGHT, tint));
        }
    }

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        ItemStack shown = shownItem();
        if (shown.isEmpty()) return;
        boolean busy = working();
        long since = t - craftedAt;
        float bob = 1.5F * Mth.sin(t / 380.0F);
        float pop = since < 300L ? 0.6F + 0.4F * easeOut(since / 300.0F) : 1.0F;
        floatingItem(g, shown, ITEM_X, ITEM_Y + bob, (busy ? 1.0F + 0.05F * Mth.sin(t / 140.0F) : 1.0F) * pop);
    }

    /** What is being infused: the craft's item, or the first item in the inputs (which nothing infuses). */
    private ItemStack shownItem() {
        if (job != null && !job.units().isEmpty()) return job.units().get(0).copyWithCount(1);
        return ItemStack.EMPTY;
    }

    @Override
    protected void onCraft(long t) {
        burst(POOL_X, POOL_Y, 10, ManaInfuserColours.tint(BRIGHT, tint()), 1.0F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        if (mx < HEART_BOX[0] || mx >= HEART_BOX[2] || my < HEART_BOX[1] || my >= HEART_BOX[3]) return;
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
