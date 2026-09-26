package com.reya.chaosspawner.client;

import java.util.ArrayList;
import java.util.List;

import com.reya.chaosspawner.ChaosSpawner;
import com.reya.chaosspawner.ChaosSpawnerBlockEntity;
import com.reya.chaosspawner.ChaosSpawnerMenu;
import com.reya.chaosspawner.SoulCrystalItem;
import com.reya.chaosspawner.SpawnerUpgradeItem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Chaos Spawner GUI in the teal style of the reference: frame, tiles and tracks are one pixel-art
 * texture (tools/gen_gui.py); this draws the round progress, the experience segments, the collect
 * button and the tooltips.
 */
public class ChaosSpawnerScreen extends AbstractContainerScreen<ChaosSpawnerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(ChaosSpawner.MODID, "textures/gui/spawner.png");
    private static final int MARGIN = 4;
    private static final int TEX_W = ChaosSpawnerMenu.WIDTH + 2 * MARGIN;
    private static final int TEX_H = ChaosSpawnerMenu.HEIGHT + 2 * MARGIN;
    private static final int BLACK = 0xFF0A090C;
    private static final int CYAN = 0xFF50DCE6, CYAN_L = 0xFFBEFAFF, CYAN_D = 0xFF1E788C;
    private static final int PLAQUE_TEXT = 0xFF3A3530;
    private static final int XP_X1 = 29, XP_Y1 = 108, XP_X2 = 125, XP_Y2 = 117;
    /** Points for one full bar: what it takes to reach level 30. */
    private static final int XP_FULL = 1395;

    private Button collect;
    private float shownProgress;

    public ChaosSpawnerScreen(ChaosSpawnerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = ChaosSpawnerMenu.WIDTH;
        imageHeight = ChaosSpawnerMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        collect = addRenderableWidget(new TealButton(leftPos + 134, topPos + 104, 94, 18,
                Component.translatable("gui.chaosspawner.collect"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ChaosSpawnerMenu.BUTTON_COLLECT)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        collect.active = menu.storedXp() > 0;
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) ownTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float target = menu.progress() / (float) menu.maxProgress();
        shownProgress = target < shownProgress ? target : shownProgress + (target - shownProgress) * 0.3F;
        g.blit(TEXTURE, leftPos - MARGIN, topPos - MARGIN, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        drawTitlePlaque(g, leftPos + imageWidth / 2, topPos - 13);
        long time = Util.getMillis();
        g.drawManaged(() -> {
            drawProgress(g, time);
            drawXp(g, time);
        });
    }

    /** Glowing line under the souls that fills up once per round; a spark runs along it. */
    private void drawProgress(GuiGraphics g, long time) {
        int x1 = leftPos + 29, x2 = leftPos + 29 + 198, y = topPos + 37;
        int w = Math.round((x2 - x1) * shownProgress);
        if (w <= 0) return;
        int color = menu.running() ? CYAN : 0xFF6A7A88;
        g.fill(x1, y, x1 + w, y + 3, color);
        g.fill(x1, y, x1 + w, y + 1, menu.running() ? CYAN_L : 0xFF9AA8B4);
        g.fill(x1, y + 2, x1 + w, y + 3, CYAN_D);
        if (menu.running()) {
            float pulse = 0.5F + 0.5F * Mth.sin(time / 150.0F);
            g.fill(x1 + w - 2, y - 1, x1 + w, y + 4, ((int) (0x80 + 0x7F * pulse) << 24) | 0xFFFFFF);
        }
    }

    /** Six segments light up as experience collects; a full bar is 30 levels' worth. */
    private void drawXp(GuiGraphics g, long time) {
        float fill = Mth.clamp(menu.storedXp() / (float) XP_FULL, 0.0F, 1.0F);
        int segW = (XP_X2 - XP_X1) / 6;
        for (int k = 0; k < 6; k++) {
            float f = Mth.clamp(fill * 6 - k, 0.0F, 1.0F);
            if (f <= 0.0F) break;
            int sx = leftPos + XP_X1 + k * segW + 2;
            int ex = sx + Math.round((segW - 3) * f);
            int y1 = topPos + XP_Y1 + 2, y2 = topPos + XP_Y2 - 2;
            g.fill(sx, y1, ex, y2, 0xFF3FC4DA);
            g.fill(sx, y1, ex, y1 + 1, CYAN_L);
            g.fill(sx, y2 - 1, ex, y2, CYAN_D);
            if ((time / 120L + k) % 9L == 0L) g.fill(sx, y1 + 1, ex, y1 + 2, 0xFFFFFFFF);
        }
    }

    private void ownTooltips(GuiGraphics g, int mx, int my) {
        int lx = mx - leftPos, ly = my - topPos;
        List<Component> tip = new ArrayList<>();
        if (lx >= XP_X1 && lx < XP_X2 && ly >= XP_Y1 && ly < XP_Y2) {
            tip.add(Component.translatable("gui.chaosspawner.xp", menu.storedXp()));
        } else if (lx >= 29 && lx < 227 && ly >= 35 && ly < 42) {
            tip.add(Component.translatable(menu.running() ? "gui.chaosspawner.running" : "gui.chaosspawner.stopped"));
            tip.add(Component.translatable("gui.chaosspawner.round", String.format("%.1f", menu.maxProgress() / 20.0F)));
            IItemHandler items = menu.items();
            int kills = 0;
            for (int i = ChaosSpawnerBlockEntity.SOUL_START; i < ChaosSpawnerBlockEntity.OUTPUT_START; i++) {
                ItemStack s = items.getStackInSlot(i);
                if (SoulCrystalItem.isSoul(s)) kills += s.getCount();
            }
            int quantity = 0, looting = 0, speed = 0, experience = 0;
            for (int i = ChaosSpawnerBlockEntity.UPGRADE_START; i < ChaosSpawnerBlockEntity.SLOTS; i++) {
                ItemStack s = items.getStackInSlot(i);
                if (!(s.getItem() instanceof SpawnerUpgradeItem u)) continue;
                switch (u.kind) {
                    case QUANTITY -> quantity += s.getCount();
                    case LOOTING -> looting += s.getCount();
                    case SPEED -> speed += s.getCount();
                    case EXPERIENCE -> experience += s.getCount();
                }
            }
            tip.add(Component.translatable("gui.chaosspawner.kills", kills * (1 + quantity)));
            tip.add(Component.translatable("gui.chaosspawner.upgrades", speed, Math.min(10, looting), quantity, experience));
        } else if (hoveredSlot != null && hoveredSlot.index < ChaosSpawnerBlockEntity.SLOTS && !hoveredSlot.hasItem()) {
            int i = hoveredSlot.index;
            tip.add(Component.translatable(i < ChaosSpawnerBlockEntity.OUTPUT_START ? "gui.chaosspawner.slot.soul"
                    : i < ChaosSpawnerBlockEntity.UPGRADE_START ? "gui.chaosspawner.slot.loot" : "gui.chaosspawner.slot.upgrade"));
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, -10, PLAQUE_TEXT, false);
    }

    private void drawTitlePlaque(GuiGraphics g, int mid, int y) {
        int half = font.width(title) / 2 + 8;
        int x1 = mid - half, x2 = mid + half;
        g.fill(x1 - 1, y - 1, x2 + 1, y + 13, BLACK);
        g.fill(x1, y, x2, y + 12, 0xFFB4B9B2);
        g.fill(x1, y, x2, y + 1, 0xFFDDE2DA);
        g.fill(x1, y + 11, x2, y + 12, 0xFF7C827A);
        for (int side : new int[]{x1 - 5, x2 + 1}) {
            g.fill(side - 1, y + 1, side + 5, y + 11, BLACK);
            g.fill(side, y + 2, side + 4, y + 10, CYAN);
            g.fill(side, y + 2, side + 4, y + 3, CYAN_L);
            g.fill(side, y + 9, side + 4, y + 10, CYAN_D);
        }
    }

    /** Teal button with corner brackets, like "Собрать опыт" in the reference. */
    private class TealButton extends Button {
        TealButton(int x, int y, int w, int h, Component msg, OnPress press) {
            super(x, y, w, h, msg, press, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hot = isHoveredOrFocused() && active;
            int x = getX(), y = getY(), x2 = x + width, y2 = y + height;
            int face = !active ? 0xFF3A4E5E : hot ? 0xFF2FA8BC : 0xFF1F7E92;
            g.fill(x, y, x2, y2, BLACK);
            g.fill(x + 1, y + 1, x2 - 1, y2 - 1, face);
            g.fill(x + 1, y + 1, x2 - 1, y + 2, active ? CYAN_L : 0xFF6A7E8E);
            g.fill(x + 1, y2 - 2, x2 - 1, y2 - 1, active ? CYAN_D : 0xFF26323C);
            int b = active ? CYAN_L : 0xFF8A9EAE;
            for (int[] c : new int[][]{{x + 2, y + 3, 1, 1}, {x2 - 3, y + 3, -1, 1}, {x + 2, y2 - 4, 1, -1}, {x2 - 3, y2 - 4, -1, -1}}) {
                g.fill(c[0], c[1], c[0] + 1, c[1] + 1, b);
                g.fill(c[0], c[1] + c[3], c[0] + 1, c[1] + c[3] + 1, b);
                g.fill(c[0] + c[2], c[1], c[0] + c[2] + 1, c[1] + 1, b);
            }
            Component m = getMessage();
            int tw = font.width(m), room = width - 12;
            int color = active ? 0xFFFFFFFF : 0xFF9AA8B4;
            if (tw <= room) {
                g.drawString(font, m, x + (width - tw) / 2, y + (height - 8) / 2 + 1, color, true);
            } else {
                float k = room / (float) tw;
                g.pose().pushPose();
                g.pose().translate(x + width / 2.0F - tw * k / 2.0F, y + height / 2.0F - 4.0F * k + 0.5F, 0.0F);
                g.pose().scale(k, k, 1.0F);
                g.drawString(font, m, 0, 0, color, true);
                g.pose().popPose();
            }
        }
    }
}
