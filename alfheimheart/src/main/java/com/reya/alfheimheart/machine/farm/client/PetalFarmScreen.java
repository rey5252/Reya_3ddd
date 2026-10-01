package com.reya.alfheimheart.machine.farm.client;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.farm.PetalFarmBlockEntity;
import com.reya.alfheimheart.machine.farm.PetalFarmMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Petal Farm's GUI: a livingwood planter in the middle (petal_farm.png), the kinds of flowers in its inputs
 * growing in it and swaying, a carved arc over it that the sun runs along as a cycle goes by. While it works
 * petals of the flowers' colours float up off them; when a cycle ends the flowers shake out a burst of petals
 * and the harvest flies to the outputs.
 */
public class PetalFarmScreen extends MachineScreen<PetalFarmMenu> {
    public static final ResourceLocation PANEL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/petal_farm.png");
    // the planter (tools/machines/layout.py FARM; check_layout.py keeps these in step)
    static final int BED_X1 = 88, SOIL_Y = 50, BED_X2 = 152, BED_Y2 = 72, SOIL_TOP = 45;
    static final int SUN_X = 120, SUN_Y = 46, SUN_R = 31;
    static final int GEM_X = 120, GEM_Y = 62;
    static final int[] HEART_BOX = {86, 10, 154, 80};
    static final int HALO_U = 48, HALO_V = 96, HALO_SIZE = 15;
    private static final int MOST_SHOWN = 6;

    @Nullable
    private MachineBlockEntity.Job job;
    private long moteAt;

    public PetalFarmScreen(PetalFarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL, SUN_X, SOIL_Y - 6, GEM_X, GEM_Y);
    }

    @Override
    protected String key() {
        return "petal_farm";
    }

    private void refresh(long t) {
        if (itemsChanged(t) && minecraft != null && minecraft.level != null) job = PetalFarmBlockEntity.find(minecraft.level, menu.items());
    }

    /** Where flower i of n stands: its stem's foot on the soil. */
    private static float flowerX(int i, int n) {
        float span = BED_X2 - BED_X1 - 16.0F;
        return BED_X1 + 8.0F + (i + 0.5F) * span / n;
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // the sun, rising on the left and setting on the right as the cycle goes by; low and pale at rest
        float a = PI + PI * (busy ? p : 0.04F);
        float sx = SUN_X + Mth.cos(a) * SUN_R, sy = SUN_Y + Mth.sin(a) * SUN_R;
        float light = busy ? 0.75F + 0.25F * Mth.sin(t / 300.0F) : job != null ? 0.35F : 0.15F;
        g.setColor(1.0F, 0.85F, 0.4F, light);
        g.blit(WIDGETS, leftPos + Math.round(sx) - HALO_SIZE / 2 - 1, topPos + Math.round(sy) - HALO_SIZE / 2 - 1, HALO_U, HALO_V,
                HALO_SIZE, HALO_SIZE, WIDGETS_W, WIDGETS_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = leftPos + Math.round(sx), y = topPos + Math.round(sy);
        int core = (int) (light * 255) << 24;
        g.fill(x - 1, y - 2, x + 1, y + 2, core | 0xFFF3B0);
        g.fill(x - 2, y - 1, x + 2, y + 1, core | 0xFFF3B0);
        g.fill(x - 1, y - 1, x + 1, y + 1, core | 0xFFFFFF);
        if (busy) sparkle(g, x, y, GOLD_LIGHT, 0.5F + 0.5F * Mth.sin(t / 150.0F));
        // petals floating up off the flowers
        if (busy && job != null && !job.units().isEmpty() && t - moteAt > 90L) {
            moteAt = t;
            int n = Math.min(MOST_SHOWN, job.units().size());
            int i = random.nextInt(n);
            float fx = flowerX(i, n) + (random.nextFloat() - 0.5F) * 6.0F, fy = SOIL_Y - 12.0F;
            drift(fx, fy, (random.nextFloat() - 0.5F) * 10.0F, -12.0F - random.nextFloat() * 10.0F, 1.0F + random.nextFloat() * 0.6F,
                    dyeColour(job.units().get(i)));
        }
    }

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        if (job != null && !job.units().isEmpty()) {
            List<ItemStack> units = job.units();
            int n = Math.min(MOST_SHOWN, units.size());
            long since = t - craftedAt;
            float shake = since < 400L ? (1.0F - since / 400.0F) * 12.0F * Mth.sin(since / 40.0F) : 0.0F;
            for (int i = 0; i < n; i++) {
                float fx = flowerX(i, n);
                float sway = 5.0F * Mth.sin(t / 700.0F + i * 1.7F) + shake;
                g.pose().pushPose();
                g.pose().translate(leftPos + fx, topPos + SOIL_Y, 100.0F);
                g.pose().mulPose(Axis.ZP.rotationDegrees(sway));
                g.pose().scale(0.8F, 0.8F, 0.8F);
                g.renderItem(units.get(i), -8, -16);
                g.pose().popPose();
            }
        }
        if (menu.items().getStackInSlot(PetalFarmBlockEntity.FERTILIZER).isEmpty()) {
            ghostSlot(g, PetalFarmMenu.FERTILIZER_X, PetalFarmMenu.FERTILIZER_Y, new ItemStack(Items.BONE_MEAL), false, t);
        }
    }

    @Override
    protected void onCraft(long t) {
        if (job == null) return;
        int n = Math.min(MOST_SHOWN, job.units().size());
        for (int i = 0; i < n; i++) burst(Math.round(flowerX(i, n)), SOIL_Y - 10, 5, dyeColour(job.units().get(i)), 0.8F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        if (mx < HEART_BOX[0] || mx >= HEART_BOX[2] || my < HEART_BOX[1] || my >= HEART_BOX[3]) return;
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
