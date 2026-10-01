package com.reya.alfheimheart.machine.daisy.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.daisy.PureDaisyBlockEntity;
import com.reya.alfheimheart.machine.daisy.PureDaisyMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.common.block.BotaniaFlowerBlocks;

/**
 * The Pure Daisy's GUI: a round meadow (pure_daisy.png), the daisy in its gold-ringed bed in the middle and eight
 * stones round it, as round the real daisy. The blocks being purified sit on the stones; while the daisy works a
 * white light rises over them and a ring of light runs round the meadow, white petals drift off the daisy, and
 * when it is done the stones flash and show what the blocks became before it flies out to the outputs. Mana makes
 * it hurry: the daisy glows mana blue.
 */
public class PureDaisyScreen extends MachineScreen<PureDaisyMenu> {
    public static final ResourceLocation PANEL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/pure_daisy.png");
    // the meadow (tools/machines/layout.py DAISY; check_layout.py keeps these in step)
    static final int DAISY_X = 120, DAISY_Y = 52, RING_R = 24, HEART_R = 34;
    static final int GEM_X = 120, GEM_Y = 19;
    static final int[][] CELLS = {{129, 30}, {142, 43}, {142, 61}, {129, 74}, {111, 74}, {98, 61}, {98, 43}, {111, 30}};
    static final int HALO_U = 48, HALO_V = 96, HALO_SIZE = 15;
    private static final int WHITE = 0xFFFFFF, PETAL = 0xFFF6FB;
    private static final ItemStack DAISY = new ItemStack(BotaniaFlowerBlocks.pureDaisy);

    @Nullable
    private MachineBlockEntity.Job job;
    private List<ItemStack> done = List.of();
    private long moteAt;

    public PureDaisyScreen(PureDaisyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL, DAISY_X, DAISY_Y, GEM_X, GEM_Y);
    }

    @Override
    protected String key() {
        return "pure_daisy";
    }

    private void refresh(long t) {
        if (itemsChanged(t) && minecraft != null && minecraft.level != null) job = PureDaisyBlockEntity.find(minecraft.level, menu.items());
    }

    /** Whether mana hurries the daisy (it has the mana store for it). */
    private boolean hurried() {
        return menu.mana() > 0 && working();
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = shownProgress();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // the daisy's glow: white, mana blue while mana hurries it
        float halo = busy ? 0.45F + 0.35F * Mth.sin(t / 240.0F) * 0.5F + 0.2F * p : job != null ? 0.25F : 0.12F;
        float flash = t - craftedAt < 500L ? 1.0F - (t - craftedAt) / 500.0F : 0.0F;
        halo = Math.max(halo, flash);
        boolean blue = hurried();
        g.pose().pushPose();
        g.pose().translate(leftPos + DAISY_X, topPos + DAISY_Y, 0.0F);
        g.pose().scale(1.6F + flash, 1.6F + flash, 1.0F);
        g.setColor(blue ? 0.6F : 1.0F, blue ? 0.95F : 1.0F, 1.0F, Math.min(1.0F, halo));
        g.blit(WIDGETS, -HALO_SIZE / 2 - 1, -HALO_SIZE / 2 - 1, HALO_U, HALO_V, HALO_SIZE, HALO_SIZE, WIDGETS_W, WIDGETS_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.pose().popPose();
        if (busy) progressRing(g, DAISY_X, DAISY_Y, RING_R, p, blue ? MANA_BRIGHT : WHITE, t);
        // white petals drifting off the daisy
        if (busy && t - moteAt > (blue ? 70L : 130L)) {
            moteAt = t;
            float a = random.nextFloat() * 2.0F * PI;
            float speed = 10.0F + random.nextFloat() * 12.0F;
            drift(DAISY_X + Mth.cos(a) * 6.0F, DAISY_Y + Mth.sin(a) * 6.0F, Mth.cos(a) * speed, Mth.sin(a) * speed - 4.0F,
                    1.0F + random.nextFloat() * 0.6F, random.nextInt(4) == 0 && blue ? MANA_BRIGHT : PETAL);
        }
    }

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        boolean busy = working();
        float p = shownProgress();
        long since = t - craftedAt;
        // the daisy, breathing
        float breathe = 1.25F + 0.04F * Mth.sin(t / 420.0F) + (since < 400L ? 0.2F * (1.0F - since / 400.0F) : 0.0F);
        floatingItem(g, DAISY, DAISY_X, DAISY_Y, breathe);
        // what the blocks became, a moment after each craft
        if (since < 450L && !done.isEmpty()) {
            for (int i = 0; i < Math.min(done.size(), CELLS.length); i++) {
                floatingItem(g, done.get(i), CELLS[i][0] + 0.5F, CELLS[i][1] + 0.5F, 0.8F);
            }
            return;
        }
        if (job == null || job.units().isEmpty()) return;
        ItemStack block = job.units().get(0);
        int n = Math.min(CELLS.length, block.getCount());
        float grow = since < 650L ? Math.max(0.0F, (since - 450L) / 200.0F) : 1.0F;
        for (int i = 0; i < n; i++) {
            floatingItem(g, block.copyWithCount(1), CELLS[i][0] + 0.5F, CELLS[i][1] + 0.5F, 0.8F * backOut(grow));
        }
        // the white light rising over them as the daisy works
        if (busy && p > 0.0F) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            for (int i = 0; i < n; i++) {
                int x = leftPos + CELLS[i][0] - 6, y = topPos + CELLS[i][1] - 6;
                int h = Math.round(13.0F * p);
                g.fill(x, y + 13 - h, x + 13, y + 13, (int) (60 + 60 * p) << 24 | WHITE);
                if (h > 0 && h < 13) g.fill(x, y + 13 - h, x + 13, y + 14 - h, 0xC0FFFFFF);
            }
            g.pose().popPose();
        }
    }

    @Override
    protected void onCraft(long t) {
        done = new ArrayList<>();
        ItemStack out = menu.lastOutput();
        int n = job != null && !job.units().isEmpty() ? Math.min(CELLS.length, job.units().get(0).getCount()) : 1;
        for (int i = 0; i < n; i++) done.add(out);
        for (int[] c : CELLS) burst(c[0], c[1], 3, WHITE, 0.6F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        float dx = mx - DAISY_X, dy = my - DAISY_Y;
        if (dx * dx + dy * dy > HEART_R * HEART_R) return;
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
