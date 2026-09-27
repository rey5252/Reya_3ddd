package com.reya.goldenquarry.client;

import java.util.ArrayList;
import java.util.List;

import com.reya.goldenquarry.GoldenQuarry;
import com.reya.goldenquarry.QuarryBlockEntity;
import com.reya.goldenquarry.QuarryMenu;
import com.reya.goldenquarry.QuarryUpgradeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Golden quarry GUI: frame, slots and bar tracks are one pixel-art texture (tools/gen_gui.py);
 * this draws the striped energy and progress fills, the three switches and the tooltips.
 */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/quarry.png");
    private static final int TEX = 256;
    /** Button sprites sit right of the screen area: off in the first column, on in the second. */
    private static final int SPRITE_U = 208, SPRITE_SIZE = 18;
    private static final int BAR_X1 = 36, BAR_X2 = 170;
    private static final int ENERGY_Y1 = 70, ENERGY_Y2 = 78;
    private static final int PROGRESS_Y1 = 83, PROGRESS_Y2 = 89;
    private static final int ICON_X = 9, ICON_Y = 72;

    private float shownProgress;
    private float shownEnergy;
    private final ItemStack icon = new ItemStack(GoldenQuarry.QUARRY_ITEM.get());

    public QuarryScreen(QuarryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = QuarryMenu.WIDTH;
        imageHeight = QuarryMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 3; i++) {
            int id = i;
            addRenderableWidget(new SwitchButton(leftPos + QuarryMenu.BUTTON_X, topPos + QuarryMenu.BUTTON_Y + i * QuarryMenu.BUTTON_STEP, id,
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) ownTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float target = menu.progress() / (float) menu.maxProgress();
        shownProgress = target < shownProgress ? target : shownProgress + (target - shownProgress) * 0.3F;
        float energy = menu.energy() / (float) menu.capacity();
        shownEnergy += (energy - shownEnergy) * 0.25F;
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEX, TEX);
        long time = Util.getMillis();
        g.drawManaged(() -> {
            // teal energy with moving diagonal stripes, like the reference
            stripes(g, ENERGY_Y1, ENERGY_Y2, shownEnergy, time / 80L,
                    new int[]{0xFF2FC4A8, 0xFF5BE6C8, 0xFF9CF7E2, 0xFF3AD4B4}, 0xFFD2FFF4, 0xFF167A6A);
            boolean working = menu.status() == QuarryBlockEntity.STATUS_WORKING && menu.enabled();
            stripes(g, PROGRESS_Y1, PROGRESS_Y2, shownProgress, working ? time / 60L : 0L,
                    new int[]{0xFF8E949C, 0xFFB9BEC6, 0xFFDCE0E6, 0xFFA2A8B0}, 0xFFF0F2F5, 0xFF5A5F66);
        });
        g.renderItem(icon, leftPos + ICON_X, topPos + ICON_Y);
    }

    private void stripes(GuiGraphics g, int y1, int y2, float fill, long shift, int[] colors, int top, int bottom) {
        int w = Math.round((BAR_X2 - BAR_X1) * Mth.clamp(fill, 0.0F, 1.0F));
        if (w <= 0) return;
        int x0 = leftPos + BAR_X1;
        for (int y = y1; y < y2; y++) {
            int row = y - y1;
            for (int x = 0; x < w; ) {
                int c = (int) Math.floorMod(x + row - shift, 8L) / 2;
                int run = 1;
                while (x + run < w && (int) Math.floorMod(x + run + row - shift, 8L) / 2 == c) run++;
                int color = y == y1 ? top : y == y2 - 1 ? bottom : colors[c];
                g.fill(x0 + x, topPos + y, x0 + x + run, topPos + y + 1, color);
                x += run;
            }
        }
        // bright leading edge
        g.fill(x0 + w - 1, topPos + y1, x0 + w, topPos + y2, 0xC0FFFFFF);
    }

    private void ownTooltips(GuiGraphics g, int mx, int my) {
        int lx = mx - leftPos, ly = my - topPos;
        List<Component> tip = new ArrayList<>();
        if (lx >= BAR_X1 - 9 && lx < BAR_X2 && ly >= ENERGY_Y1 - 1 && ly < ENERGY_Y2 + 1) {
            tip.add(Component.translatable("gui.goldenquarry.energy", String.format("%,d", menu.energy()), String.format("%,d", menu.capacity())));
            QuarryBlockEntity be = menu.quarry();
            if (be != null) tip.add(Component.translatable("gui.goldenquarry.energy_per_block", be.energyPerBlock()).withStyle(ChatFormatting.GRAY));
        } else if (lx >= BAR_X1 - 9 && lx < BAR_X2 && ly >= PROGRESS_Y1 - 1 && ly < PROGRESS_Y2 + 1
                || lx >= ICON_X && lx < ICON_X + 16 && ly >= ICON_Y && ly < ICON_Y + 16) {
            tip.add(Component.translatable("gui.goldenquarry.status." + menu.status()).withStyle(statusColor(menu.status())));
            int side = menu.radius() * 2 + 1;
            tip.add(Component.translatable("gui.goldenquarry.area", side, side).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.goldenquarry.layer", menu.layer()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.goldenquarry.speed", String.format("%.2f", menu.maxProgress() / 20.0F)).withStyle(ChatFormatting.GRAY));
            QuarryBlockEntity be = menu.quarry();
            if (be != null) {
                tip.add(Component.translatable("gui.goldenquarry.upgrades",
                        be.upgrades(QuarryUpgradeItem.Kind.SPEED), be.upgrades(QuarryUpgradeItem.Kind.RANGE),
                        be.upgrades(QuarryUpgradeItem.Kind.FORTUNE),
                        Component.translatable(be.upgrades(QuarryUpgradeItem.Kind.SILK_TOUCH) > 0 ? "gui.goldenquarry.yes" : "gui.goldenquarry.no"),
                        Component.translatable(be.upgrades(QuarryUpgradeItem.Kind.SMELTING) > 0 ? "gui.goldenquarry.yes" : "gui.goldenquarry.no"))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (hoveredSlot != null && hoveredSlot.index < QuarryMenu.MACHINE_SLOTS && !hoveredSlot.hasItem()) {
            int i = hoveredSlot.index;
            tip.add(Component.translatable(i < QuarryMenu.VALUABLE_START ? "gui.goldenquarry.slot.common"
                    : i < QuarryMenu.UPGRADE_START ? "gui.goldenquarry.slot.valuable" : "gui.goldenquarry.slot.upgrade"));
        } else {
            for (var child : children()) {
                if (child instanceof SwitchButton b && b.isHovered()) {
                    tip.add(Component.translatable("gui.goldenquarry.button." + b.id));
                    tip.add(Component.translatable(b.on() ? "gui.goldenquarry.on" : "gui.goldenquarry.off")
                            .withStyle(b.on() ? ChatFormatting.GREEN : ChatFormatting.RED));
                    tip.add(Component.translatable("gui.goldenquarry.button." + b.id + ".tip").withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }

    private static ChatFormatting statusColor(int status) {
        return switch (status) {
            case QuarryBlockEntity.STATUS_WORKING -> ChatFormatting.GREEN;
            case QuarryBlockEntity.STATUS_FINISHED -> ChatFormatting.GOLD;
            case QuarryBlockEntity.STATUS_STOPPED, QuarryBlockEntity.STATUS_REDSTONE, QuarryBlockEntity.STATUS_WAITING -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // the reference has no titles, the frame speaks for itself
    }

    /** Golden square switch with a pixel icon; lit when on. */
    private class SwitchButton extends Button {
        final int id;

        SwitchButton(int x, int y, int id, OnPress press) {
            super(x, y, SPRITE_SIZE, SPRITE_SIZE, Component.translatable("gui.goldenquarry.button." + id), press, DEFAULT_NARRATION);
            this.id = id;
        }

        boolean on() {
            return switch (id) {
                case QuarryMenu.BUTTON_POWER -> menu.enabled();
                case QuarryMenu.BUTTON_AREA -> menu.showArea();
                default -> menu.voidJunk();
            };
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int u = SPRITE_U + (on() ? SPRITE_SIZE : 0);
            g.blit(TEXTURE, getX(), getY(), u, id * SPRITE_SIZE, SPRITE_SIZE, SPRITE_SIZE, TEX, TEX);
            if (isHoveredOrFocused()) g.fill(getX() + 1, getY() + 1, getX() + SPRITE_SIZE - 1, getY() + SPRITE_SIZE - 1, 0x40FFFFFF);
        }
    }
}
