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

/**
 * Golden quarry GUI, pixel for pixel the reference: textures/gui/quarry.png is read off the
 * reference screenshot (tools/extract_gui.py). This draws on it the energy and progress fills (cut
 * from the same screenshot, in quarry_widgets.png), dims the switches that are off, and the tooltips.
 */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/quarry.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/quarry_widgets.png");
    /** The fills: 128 pixels long, 8 high, at these places of the GUI. */
    private static final int BAR_X = 46, BAR_W = 128, BAR_H = 8;
    private static final int ENERGY_Y = 72, PROGRESS_Y = 83;
    /** Where the energy and progress tooltips answer (bars with their icons in front). */
    private static final int TIP_X1 = 36, TIP_X2 = 175;
    private static final int ICON_X1 = 14, ICON_Y1 = 71, ICON_X2 = 33, ICON_Y2 = 91;
    private static final String[] BUTTON_KEYS = {"area", "power", "void"};

    private float shownProgress;
    private float shownEnergy;

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
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int ew = Math.round(BAR_W * Mth.clamp(shownEnergy, 0.0F, 1.0F));
        if (ew > 0) g.blit(WIDGETS, leftPos + BAR_X, topPos + ENERGY_Y, 0, 0, ew, BAR_H, 256, 64);
        int pw = Math.round(BAR_W * Mth.clamp(shownProgress, 0.0F, 1.0F));
        if (pw > 0) {
            // the stripes move along while it digs
            boolean working = menu.status() == QuarryBlockEntity.STATUS_WORKING && menu.enabled();
            int shift = working ? (int) (Util.getMillis() / 90L % 6L) : 0;
            g.blit(WIDGETS, leftPos + BAR_X, topPos + PROGRESS_Y, 6 - shift, 8, pw, BAR_H, 256, 64);
        }
    }

    private void ownTooltips(GuiGraphics g, int mx, int my) {
        int lx = mx - leftPos, ly = my - topPos;
        List<Component> tip = new ArrayList<>();
        if (lx >= TIP_X1 && lx < TIP_X2 && ly >= ENERGY_Y - 1 && ly < ENERGY_Y + BAR_H) {
            tip.add(Component.translatable("gui.goldenquarry.energy", String.format("%,d", menu.energy()), String.format("%,d", menu.capacity())));
            QuarryBlockEntity be = menu.quarry();
            if (be != null) tip.add(Component.translatable("gui.goldenquarry.energy_per_block", be.energyPerBlock()).withStyle(ChatFormatting.GRAY));
        } else if (lx >= TIP_X1 && lx < TIP_X2 && ly >= PROGRESS_Y - 1 && ly < PROGRESS_Y + BAR_H
                || lx >= ICON_X1 && lx < ICON_X2 && ly >= ICON_Y1 && ly < ICON_Y2) {
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
                    String key = "gui.goldenquarry.button." + BUTTON_KEYS[b.id];
                    tip.add(Component.translatable(key));
                    tip.add(Component.translatable(b.on() ? "gui.goldenquarry.on" : "gui.goldenquarry.off")
                            .withStyle(b.on() ? ChatFormatting.GREEN : ChatFormatting.RED));
                    tip.add(Component.translatable(key + ".tip").withStyle(ChatFormatting.GRAY));
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
        // the reference has no titles
    }

    /**
     * The golden switches are part of the background texture, lit as in the reference; a switch that
     * is off gets its dimmed copy drawn over it.
     */
    private class SwitchButton extends Button {
        final int id;

        SwitchButton(int x, int y, int id, OnPress press) {
            super(x, y, QuarryMenu.BUTTON_SIZE, QuarryMenu.BUTTON_SIZE, Component.translatable("gui.goldenquarry.button." + BUTTON_KEYS[id]), press, DEFAULT_NARRATION);
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
            if (!on()) g.blit(WIDGETS, getX(), getY(), id * 18, 16, width, height, 256, 64);
            if (isHoveredOrFocused()) g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0x30FFFFFF);
        }
    }
}
