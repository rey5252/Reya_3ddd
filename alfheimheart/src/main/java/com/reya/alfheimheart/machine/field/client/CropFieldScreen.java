package com.reya.alfheimheart.machine.field.client;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.field.CropFieldBlockEntity;
import com.reya.alfheimheart.machine.field.CropFieldMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Crop Field's GUI: a field of tilled furrows in a livingwood frame (crop_field.png), the seeds in the inputs
 * planted in its rows. As a cycle goes by they grow, swaying, under a little cloud that rains on them; when it
 * ends they shake, and the harvest flies out to the outputs.
 */
public class CropFieldScreen extends MachineScreen<CropFieldMenu> {
    public static final ResourceLocation PANEL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/crop_field.png");
    // the field (tools/machines/layout.py FIELD; check_layout.py keeps these in step)
    static final int[] BOX = {88, 24, 152, 76};
    static final int[] ROWS = {38, 54, 70};
    static final int CLOUD_X = 120, CLOUD_Y = 14;
    static final int GEM_X = 120, GEM_Y = 76;
    static final int[] HEART_BOX = {86, 6, 154, 80};
    private static final int MOST_SHOWN = 6, RAIN = 0x7FC8FF;
    private static final String[] CLOUD = {
            "...xxxx.....",
            ".xxXXXXxx...",
            "xXXXXXXXXxx.",
            "xXXXXXXXXXXx",
            ".xxxxxxxxxx."};

    @Nullable
    private MachineBlockEntity.Job job;
    private final float[][] drops = new float[10][];

    public CropFieldScreen(CropFieldMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL, (BOX[0] + BOX[2]) / 2, (BOX[1] + BOX[3]) / 2, GEM_X, GEM_Y);
    }

    @Override
    protected String key() {
        return "crop_field";
    }

    private void refresh(long t) {
        if (itemsChanged(t) && minecraft != null && minecraft.level != null) {
            job = CropFieldBlockEntity.find(minecraft.level, BlockPos.ZERO, menu.items());
        }
    }

    /** Where plant i of n grows: two to a row, the rows filled from the top. */
    private static float[] spot(int i, int n) {
        int row = i % ROWS.length, col = i / ROWS.length;
        int cols = (n + ROWS.length - 1) / ROWS.length;
        float x = cols <= 1 ? (BOX[0] + BOX[2]) / 2.0F : BOX[0] + 16.0F + col * (BOX[2] - BOX[0] - 32.0F) / (cols - 1);
        return new float[]{x, ROWS[row]};
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // the little cloud, drifting a little, raining while the field grows
        float cx = CLOUD_X + 6.0F * Mth.sin(t / 1400.0F);
        int x0 = leftPos + Math.round(cx) - CLOUD[0].length() / 2, y0 = topPos + CLOUD_Y - 2;
        int body = busy ? 0xF0F4FAFF : 0xC0E4EAF0, edge = busy ? 0xF0B8C8D8 : 0xC0A8B4C0;
        for (int j = 0; j < CLOUD.length; j++) {
            for (int i = 0; i < CLOUD[j].length(); i++) {
                char ch = CLOUD[j].charAt(i);
                if (ch != '.') g.fill(x0 + i, y0 + j, x0 + i + 1, y0 + j + 1, ch == 'X' ? body : edge);
            }
        }
        if (busy) {
            g.drawManaged(() -> {
                for (int k = 0; k < drops.length; k++) {
                    if (drops[k] == null || drops[k][1] > BOX[3] - 6) {
                        drops[k] = new float[]{cx - 5.0F + random.nextFloat() * 10.0F, CLOUD_Y + 3 + random.nextFloat() * 20.0F};
                    }
                    drops[k][1] += dt * 60.0F;
                    int x = leftPos + Math.round(drops[k][0]), y = topPos + Math.round(drops[k][1]);
                    g.fill(x, y, x + 1, y + 2, 0xB0000000 | RAIN);
                }
            });
        }
    }

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        if (job != null && !job.units().isEmpty()) {
            List<ItemStack> units = job.units();
            int n = Math.min(MOST_SHOWN, units.size());
            boolean busy = working();
            float p = busy ? shownProgress() : 0.0F;
            long since = t - craftedAt;
            float shake = since < 400L ? (1.0F - since / 400.0F) * 10.0F * Mth.sin(since / 35.0F) : 0.0F;
            for (int i = 0; i < n; i++) {
                float[] at = spot(i, n);
                float scale = 0.5F + 0.4F * p;
                float sway = 4.0F * Mth.sin(t / 650.0F + i * 1.9F) + shake;
                g.pose().pushPose();
                g.pose().translate(leftPos + at[0], topPos + at[1], 100.0F);
                g.pose().mulPose(Axis.ZP.rotationDegrees(sway));
                g.pose().scale(scale, scale, scale);
                g.renderItem(units.get(i), -8, -16);
                g.pose().popPose();
            }
        }
        if (menu.items().getStackInSlot(CropFieldBlockEntity.FERTILIZER).isEmpty()) {
            ghostSlot(g, CropFieldMenu.FERTILIZER_X, CropFieldMenu.FERTILIZER_Y, new ItemStack(Items.BONE_MEAL), false, t);
        }
    }

    @Override
    protected void onCraft(long t) {
        if (job == null) return;
        int n = Math.min(MOST_SHOWN, job.units().size());
        for (int i = 0; i < n; i++) {
            float[] at = spot(i, n);
            burst(Math.round(at[0]), Math.round(at[1]) - 8, 5, GREEN_LIGHT, 0.8F);
        }
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        if (mx < HEART_BOX[0] || mx >= HEART_BOX[2] || my < HEART_BOX[1] || my >= HEART_BOX[3]) return;
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
