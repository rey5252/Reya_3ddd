package com.reya.attributeeditor.client;

import com.reya.attributeeditor.AttributeValues;
import com.reya.attributeeditor.ItemAttributeEditing;
import com.reya.attributeeditor.table.AttributeTableMenu;
import com.reya.attributeeditor.table.TableRow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Dark panel with a gold studded frame. Left: item slot, Reset / Unbreakable buttons and a
 * levels/cost box. Right: a scrollable list of attribute cards with -/+ buttons.
 * Everything is drawn in code, so no GUI texture is needed.
 */
public class AttributeTableScreen extends AbstractContainerScreen<AttributeTableMenu> {
    // Palette
    private static final int BG = 0xFF15122A;
    private static final int CARD = 0xFF2B2445;
    private static final int CARD_HOVER = 0xFF362D57;
    private static final int SLOT = 0xFF2A2440;
    private static final int SLOT_EDGE = 0xFF1C182F;
    private static final int GOLD = 0xFFD9A93A;
    private static final int GOLD_DARK = 0xFF8C6A1E;
    private static final int GOLD_LIGHT = 0xFFF3D27A;
    private static final int GEM = 0xFF7FE8F0;
    private static final int GEM_DARK = 0xFF2A8A9A;
    private static final int TEXT = 0xFFE8E0F0;
    private static final int TEXT_DIM = 0xFF9A90B0;
    private static final int TEXT_DISABLED = 0xFF5E5675;
    private static final int TITLE = 0xFFF0C040;
    private static final int GREEN = 0xFF7CFC7C;
    private static final int BUTTON = 0xFF3E355E;
    private static final int BUTTON_HOVER = 0xFF5A4C8A;
    private static final int BUTTON_OFF = 0xFF221D38;

    // Layout (relative to leftPos/topPos)
    private static final int BORDER = 6;
    private static final int LEFT_X = 10;
    private static final int LEFT_W = 90;
    private static final int RESET_Y = 52;
    private static final int UNBREAKABLE_Y = 74;
    private static final int BUTTON_H = 18;
    private static final int INFO_Y = 97;
    private static final int INFO_H = 50;
    private static final int LIST_X = 106;
    private static final int LIST_Y = 20;
    private static final int CARD_W = 176;
    private static final int CARD_H = 23;
    private static final int CARD_GAP = 2;
    private static final int VISIBLE_ROWS = 5;
    private static final int SCROLL_X = LIST_X + CARD_W + 3;
    private static final int SCROLL_W = 4;
    private static final int SMALL_W = 16;
    private static final int SMALL_H = 14;

    private int scroll;

    public AttributeTableScreen(AttributeTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 248;
        titleLabelX = LEFT_X;
        titleLabelY = 9;
        inventoryLabelX = AttributeTableMenu.INVENTORY_LEFT;
        inventoryLabelY = AttributeTableMenu.INVENTORY_Y - 11;
    }

    private int maxScroll() {
        return Math.max(0, TableRow.ROWS.size() - VISIBLE_ROWS);
    }

    private ItemStack item() {
        return menu.getItem();
    }

    private boolean freeEdits() {
        return minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
    }

    private int playerLevels() {
        return minecraft != null && minecraft.player != null ? minecraft.player.experienceLevel : 0;
    }

    private boolean canAfford() {
        return freeEdits() || playerLevels() >= AttributeTableMenu.LEVEL_COST;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoverTooltips(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        drawFrame(graphics, x, y, imageWidth, imageHeight);

        // Slots: the item slot gets a gold frame, the inventory plain dark squares.
        for (Slot slot : menu.slots) {
            if (slot.index == 0) {
                drawItemSlot(graphics, x + slot.x, y + slot.y);
            } else {
                drawSlot(graphics, x + slot.x, y + slot.y);
            }
        }

        boolean hasItem = !item().isEmpty();
        drawButton(graphics, x + LEFT_X, y + RESET_Y, LEFT_W, BUTTON_H,
                Component.translatable("gui.attributeeditor.reset"), hasItem, mouseX, mouseY);
        drawButton(graphics, x + LEFT_X, y + UNBREAKABLE_Y, LEFT_W, BUTTON_H,
                Component.translatable("gui.attributeeditor.unbreakable"), hasItem && canAfford(), mouseX, mouseY);

        drawInfoBox(graphics, x + LEFT_X, y + INFO_Y);
        drawAttributeList(graphics, x, y, mouseX, mouseY);
    }

    private void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GOLD_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, GOLD);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, GOLD_LIGHT);
        g.fill(x + BORDER - 1, y + BORDER - 1, x + w - BORDER + 1, y + h - BORDER + 1, GOLD_DARK);
        g.fill(x + BORDER, y + BORDER, x + w - BORDER, y + h - BORDER, BG);

        // Studs along the gold border.
        for (int i = 12; i < w - 12; i += 10) {
            g.fill(x + i, y + 2, x + i + 2, y + 4, GOLD_DARK);
            g.fill(x + i, y + h - 4, x + i + 2, y + h - 2, GOLD_DARK);
        }
        for (int i = 12; i < h - 12; i += 10) {
            g.fill(x + 2, y + i, x + 4, y + i + 2, GOLD_DARK);
            g.fill(x + w - 4, y + i, x + w - 2, y + i + 2, GOLD_DARK);
        }

        drawGem(g, x - 1, y - 1);
        drawGem(g, x + w - 7, y - 1);
        drawGem(g, x - 1, y + h - 7);
        drawGem(g, x + w - 7, y + h - 7);
    }

    private static void drawGem(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 8, y + 8, GEM_DARK);
        g.fill(x + 1, y + 1, x + 7, y + 7, GEM);
        g.fill(x + 2, y + 2, x + 4, y + 4, 0xFFFFFFFF);
    }

    private static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT);
    }

    private static void drawItemSlot(GuiGraphics g, int x, int y) {
        g.fill(x - 4, y - 4, x + 20, y + 20, GOLD_DARK);
        g.fill(x - 3, y - 3, x + 19, y + 19, GOLD);
        g.fill(x - 1, y - 1, x + 17, y + 17, GOLD_DARK);
        g.fill(x, y, x + 16, y + 16, 0xFF1E3A50);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label, boolean active,
                            int mouseX, int mouseY) {
        boolean hover = active && inside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, active ? (hover ? BUTTON_HOVER : BUTTON) : BUTTON_OFF);
        if (active) g.fill(x, y, x + w, y + 1, hover ? 0xFF7A6AB0 : 0xFF514676);
        int color = active ? TEXT : TEXT_DISABLED;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    private void drawInfoBox(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + LEFT_W + 1, y + INFO_H + 1, 0xFF3A3358);
        g.fill(x, y, x + LEFT_W, y + INFO_H, 0xFF0E0C1C);

        g.drawString(font, Component.translatable("gui.attributeeditor.levels"), x + 5, y + 5, TEXT, false);
        g.drawString(font, freeEdits() ? "∞" : String.valueOf(playerLevels()), x + 5, y + 15, GREEN, false);

        int cost = freeEdits() ? 0 : AttributeTableMenu.LEVEL_COST;
        Component costLabel = Component.translatable("gui.attributeeditor.cost_label");
        g.drawString(font, costLabel, x + 5, y + 27, TEXT, false);
        g.drawString(font, String.valueOf(cost), x + 5 + font.width(costLabel) + 3, y + 27, TITLE, false);

        ItemStack stack = item();
        if (stack.hasTag() && stack.getTag().getBoolean("Unbreakable")) {
            g.drawString(font, Component.translatable("gui.attributeeditor.is_unbreakable"), x + 5, y + 39, 0xFFC080F0, false);
        }
    }

    private void drawAttributeList(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = item();
        EquipmentSlot slot = stack.isEmpty() ? EquipmentSlot.MAINHAND : AttributeValues.defaultSlot(stack);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int cx = x + LIST_X;
            int cy = y + LIST_Y + i * (CARD_H + CARD_GAP);
            int rowIndex = i + scroll;
            boolean hover = !stack.isEmpty() && inside(mouseX, mouseY, cx, cy, CARD_W, CARD_H);
            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hover ? CARD_HOVER : CARD);

            if (stack.isEmpty()) {
                if (i == 0) {
                    g.drawWordWrap(font, Component.translatable("gui.attributeeditor.place_item"),
                            cx + 6, cy + 3, CARD_W - 12, TEXT);
                }
                continue;
            }
            if (rowIndex >= TableRow.ROWS.size()) continue;

            TableRow row = TableRow.ROWS.get(rowIndex);
            Attribute attribute = row.attribute().get();
            double value = ItemAttributeEditing.value(stack, attribute, slot);

            String name = font.plainSubstrByWidth(Component.translatable(attribute.getDescriptionId()).getString(),
                    CARD_W - 2 * SMALL_W - 18);
            g.drawString(font, name, cx + 6, cy + 3, TEXT, false);
            g.drawString(font, format(value), cx + 6, cy + 13, GREEN, false);

            int bx = cx + CARD_W - 2 * SMALL_W - 8;
            int by = cy + (CARD_H - SMALL_H) / 2;
            boolean canLower = value - row.step() >= row.min() - 1.0E-6;
            drawButton(g, bx, by, SMALL_W, SMALL_H, Component.literal("-"), canLower, mouseX, mouseY);
            drawButton(g, bx + SMALL_W + 3, by, SMALL_W, SMALL_H, Component.literal("+"), canAfford(), mouseX, mouseY);
        }

        // Scrollbar
        int trackX = x + SCROLL_X;
        int trackY = y + LIST_Y;
        int trackH = VISIBLE_ROWS * (CARD_H + CARD_GAP) - CARD_GAP;
        g.fill(trackX, trackY, trackX + SCROLL_W, trackY + trackH, 0xFF0E0C1C);
        int thumbH = Math.max(12, trackH * VISIBLE_ROWS / TableRow.ROWS.size());
        int thumbY = trackY + (maxScroll() == 0 ? 0 : (trackH - thumbH) * scroll / maxScroll());
        g.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, 0xFF4A4068);
    }

    private void renderHoverTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (item().isEmpty()) return;
        if (inside(mouseX, mouseY, leftPos + LEFT_X, topPos + UNBREAKABLE_Y, LEFT_W, BUTTON_H)) {
            g.renderTooltip(font, Component.translatable("gui.attributeeditor.unbreakable.tooltip",
                    AttributeTableMenu.LEVEL_COST), mouseX, mouseY);
            return;
        }
        for (int i = 0; i < VISIBLE_ROWS && i + scroll < TableRow.ROWS.size(); i++) {
            int cy = topPos + LIST_Y + i * (CARD_H + CARD_GAP) + (CARD_H - SMALL_H) / 2;
            int plusX = leftPos + LIST_X + CARD_W - SMALL_W - 5;
            if (inside(mouseX, mouseY, plusX, cy, SMALL_W, SMALL_H)) {
                g.renderTooltip(font, Component.translatable("gui.attributeeditor.cost", AttributeTableMenu.LEVEL_COST),
                        mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_DIM, false);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !item().isEmpty()) {
            int id = buttonAt(mouseX, mouseY);
            if (id >= 0) {
                send(id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Which menu button id is under the mouse, or -1. */
    private int buttonAt(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, leftPos + LEFT_X, topPos + RESET_Y, LEFT_W, BUTTON_H)) {
            return AttributeTableMenu.RESET;
        }
        if (inside(mouseX, mouseY, leftPos + LEFT_X, topPos + UNBREAKABLE_Y, LEFT_W, BUTTON_H)) {
            return AttributeTableMenu.UNBREAKABLE;
        }
        for (int i = 0; i < VISIBLE_ROWS && i + scroll < TableRow.ROWS.size(); i++) {
            int by = topPos + LIST_Y + i * (CARD_H + CARD_GAP) + (CARD_H - SMALL_H) / 2;
            int bx = leftPos + LIST_X + CARD_W - 2 * SMALL_W - 8;
            if (inside(mouseX, mouseY, bx, by, SMALL_W, SMALL_H)) return (i + scroll) * 2;
            if (inside(mouseX, mouseY, bx + SMALL_W + 3, by, SMALL_W, SMALL_H)) return (i + scroll) * 2 + 1;
        }
        return -1;
    }

    private void send(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y, CARD_W + 10,
                VISIBLE_ROWS * (CARD_H + CARD_GAP))) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static String format(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
