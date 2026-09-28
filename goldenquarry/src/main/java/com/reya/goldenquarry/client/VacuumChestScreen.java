package com.reya.goldenquarry.client;

import java.util.ArrayList;
import java.util.List;

import com.reya.goldenquarry.GoldenQuarry;
import com.reya.goldenquarry.VacuumChestMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vacuum chest GUI, pixel for pixel the reference (textures/gui/vacuum_chest.png, built by
 * tools/extract_vacuum.py). This writes on it what the reference shows in text, in the player's
 * language: the title on the grey plate, "Item filter", "Range" and the range; draws the name plate
 * over it, the white/black list switch in the brown slot, and answers the buttons.
 */
public class VacuumChestScreen extends AbstractContainerScreen<VacuumChestMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/vacuum_chest.png");
    /** Buttons: x, y, width, height (texture pixels) and the menu button id. */
    private static final int[][] BUTTONS = {
            {178, 95, 8, 8, VacuumChestMenu.BUTTON_PLUS},
            {178, 104, 8, 8, VacuumChestMenu.BUTTON_MINUS},
            {169, 117, 16, 16, VacuumChestMenu.BUTTON_AREA},
            {VacuumChestMenu.MODE_X - 1, VacuumChestMenu.FILTER_Y - 1, 18, 18, VacuumChestMenu.BUTTON_MODE}};
    private static final int TITLE_X1 = 57, TITLE_X2 = 149, TITLE_Y = 3;
    private static final int LABEL_Y = 84, FILTER_LABEL_X = 23, RANGE_LABEL_END = 204;
    private static final int RANGE_END = 174, RANGE_Y = 99;

    public VacuumChestScreen(VacuumChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = VacuumChestMenu.WIDTH;
        imageHeight = VacuumChestMenu.HEIGHT;
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
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        Banner.draw(g, leftPos + imageWidth / 2, topPos - Banner.HEIGHT);
        enderParticles(g);
        // the title on the grey plate, in the plate's own little letters
        String title = getTitle().getString();
        PlateFont.draw(g, title, leftPos + (TITLE_X1 + TITLE_X2 + 1) / 2 - PlateFont.width(title) / 2, topPos + TITLE_Y, 0xFF696969);
        g.drawString(font, Component.translatable("gui.goldenquarry.vacuum.filter"), leftPos + FILTER_LABEL_X, topPos + LABEL_Y, 0xFFFFFF, false);
        Component range = Component.translatable("gui.goldenquarry.vacuum.range");
        g.drawString(font, range, leftPos + RANGE_LABEL_END - font.width(range), topPos + LABEL_Y, 0xFFFFFF, false);
        String n = String.valueOf(menu.range());
        g.drawString(font, n, leftPos + RANGE_END - font.width(n), topPos + RANGE_Y, 0xFFFFFF, false);
        // the brown slot shows which list the filter is
        g.renderItem(new ItemStack(menu.blacklist() ? Items.BARRIER : Items.PAPER), leftPos + VacuumChestMenu.MODE_X, topPos + VacuumChestMenu.FILTER_Y);
        for (int[] b : BUTTONS) {
            int x = leftPos + b[0], y = topPos + b[1];
            if (b[4] == VacuumChestMenu.BUTTON_AREA && menu.showArea()) {
                g.renderOutline(x - 1, y - 1, b[2] + 2, b[3] + 2, 0xFFFFE08A);
            }
            if (inside(b, mouseX, mouseY)) g.fill(x, y, x + b[2], y + b[3], 0x40FFFFFF);
        }
    }

    /** Where the ender particles may drift: the purple panel, off the slots and buttons. */
    private static final int[] PANEL = {9, 21, 199, 140};
    private static final int[][] NOT_THERE = {{22, 26, 186, 82}, {22, 94, 136, 114}, {167, 93, 187, 137}};
    private static final int[] PARTICLE_COLOURS = {0xB24BF3, 0xD472FF, 0x8A2BE2, 0xE58CFF, 0x6A1FB0};

    /** Purple sparks rising and fading over the panel, like the ender particles round the chest. */
    private void enderParticles(GuiGraphics g) {
        double t = Util.getMillis() / 50.0D;
        for (int i = 0; i < 40; i++) {
            long h = i * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
            h ^= h >>> 29;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 32;
            int period = 50 + (int) ((h >>> 8) % 40);
            double phase = ((t + (h >>> 16) % 1000) % period) / period;
            double x0 = PANEL[0] + (h >>> 24) % (PANEL[2] - PANEL[0]);
            double y0 = PANEL[1] + 10 + (h >>> 40) % (PANEL[3] - PANEL[1] - 10);
            int x = (int) Math.round(x0 + Math.sin(phase * Math.PI * 2 + i) * 2.5D);
            int y = (int) Math.round(y0 - phase * 12.0D);
            if (x < PANEL[0] || x > PANEL[2] || y < PANEL[1] || y > PANEL[3]) continue;
            boolean blocked = false;
            for (int[] r : NOT_THERE) blocked |= x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
            if (blocked) continue;
            int alpha = (int) (Math.sin(phase * Math.PI) * 230.0D);
            if (alpha < 12) continue;
            int colour = alpha << 24 | PARTICLE_COLOURS[(int) ((h >>> 4) % PARTICLE_COLOURS.length)];
            int ax = leftPos + x, ay = topPos + y;
            g.fill(ax, ay, ax + 1, ay + 1, colour);
            if ((h & 3) == 0 && phase > 0.25D && phase < 0.75D) {      // some are little crosses
                int dim = (alpha / 2) << 24 | (colour & 0xFFFFFF);
                g.fill(ax - 1, ay, ax, ay + 1, dim);
                g.fill(ax + 1, ay, ax + 2, ay + 1, dim);
                g.fill(ax, ay - 1, ax + 1, ay, dim);
                g.fill(ax, ay + 1, ax + 1, ay + 2, dim);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // everything is written in renderBg, where the reference has it
    }

    private boolean inside(int[] b, double mx, double my) {
        double x = mx - leftPos, y = my - topPos;
        return x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            for (int[] b : BUTTONS) {
                if (inside(b, mouseX, mouseY)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, b[4]);
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void ownTooltips(GuiGraphics g, int mx, int my) {
        List<Component> tip = new ArrayList<>();
        int r = menu.range(), side = r * 2 + 1;
        for (int[] b : BUTTONS) {
            if (!inside(b, mx, my)) continue;
            switch (b[4]) {
                case VacuumChestMenu.BUTTON_PLUS -> tip.add(Component.translatable("gui.goldenquarry.vacuum.plus"));
                case VacuumChestMenu.BUTTON_MINUS -> tip.add(Component.translatable("gui.goldenquarry.vacuum.minus"));
                case VacuumChestMenu.BUTTON_AREA -> {
                    tip.add(Component.translatable("gui.goldenquarry.vacuum.area"));
                    tip.add(Component.translatable(menu.showArea() ? "gui.goldenquarry.vacuum.area.on" : "gui.goldenquarry.vacuum.area.off")
                            .withStyle(menu.showArea() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                }
                default -> {
                    tip.add(Component.translatable(menu.blacklist() ? "gui.goldenquarry.vacuum.blacklist" : "gui.goldenquarry.vacuum.whitelist"));
                    tip.add(Component.translatable(menu.blacklist() ? "gui.goldenquarry.vacuum.blacklist.tip" : "gui.goldenquarry.vacuum.whitelist.tip")
                            .withStyle(ChatFormatting.GRAY));
                    tip.add(Component.translatable("gui.goldenquarry.vacuum.switch").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        int lx = mx - leftPos, ly = my - topPos;
        if (tip.isEmpty() && lx >= RANGE_END - 12 && lx < RANGE_END && ly >= RANGE_Y - 1 && ly < RANGE_Y + 9) {
            tip.add(Component.translatable("gui.goldenquarry.vacuum.range_value", r, side, side, side));
        }
        if (tip.isEmpty() && hoveredSlot != null && VacuumChestMenu.isFilterSlot(hoveredSlot.index)) {
            tip.add(Component.translatable("gui.goldenquarry.vacuum.filter_slot"));
            tip.add(Component.translatable("gui.goldenquarry.vacuum.filter_slot.tip").withStyle(ChatFormatting.GRAY));
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }
}
