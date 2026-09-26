package com.reya.attributeeditor.client;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.reya.attributeeditor.AttributeValues;
import com.reya.attributeeditor.ItemAttributeEditing;
import com.reya.attributeeditor.network.ModNetwork;
import com.reya.attributeeditor.network.SetAttributePacket;
import com.reya.attributeeditor.table.AttributeTableMenu;
import com.reya.attributeeditor.table.TableRow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Dark panel with a gold studded frame. Left: item slot, Apply / Reset and a levels/cost box.
 * Right: a scrollable list of attribute cards, each with -, a slider and +; clicking the value lets
 * you type any number. Changes stay pending ("3 > 10") until Apply sends them to the server.
 * Everything is drawn in code, no GUI texture.
 */
public class AttributeTableScreen extends AbstractContainerScreen<AttributeTableMenu> {
    // Palette
    private static final int BG = 0xFF15122A;
    private static final int CARD = 0xFF2B2445;
    private static final int SLOT = 0xFF2A2440;
    private static final int SLOT_EDGE = 0xFF1C182F;
    private static final int GOLD = 0xFFD9A93A;
    private static final int GOLD_DARK = 0xFF8C6A1E;
    private static final int GOLD_LIGHT = 0xFFF3D27A;
    private static final int GEM = 0xFF7FE8F0;
    private static final int GEM_DARK = 0xFF2A8A9A;
    private static final int TEXT = 0xFFE8E0F0;
    private static final int NAME = 0xFFCDE8F5;
    private static final int VALUE = 0xFFB0A8C0;
    private static final int CHANGED = 0xFF6FE3F0;
    private static final int TEXT_DIM = 0xFF9A90B0;
    private static final int TEXT_DISABLED = 0xFF5E5675;
    private static final int TITLE = 0xFFF0C040;
    private static final int GREEN = 0xFF7CFC7C;
    private static final int RED = 0xFFFF6060;
    private static final int BUTTON = 0xFF3E355E;
    private static final int BUTTON_HOVER = 0xFF5A4C8A;
    private static final int BUTTON_OFF = 0xFF221D38;
    private static final int APPLY = 0xFFE0B040;
    private static final int APPLY_HOVER = 0xFFF2C85A;
    private static final int TRACK = 0xFF0E0C1C;

    // Layout (relative to leftPos/topPos)
    private static final int BORDER = 6;
    private static final int LEFT_X = 10;
    private static final int LEFT_W = 90;
    private static final int APPLY_Y = 50;
    private static final int RESET_Y = 74;
    private static final int BUTTON_H = 20;
    private static final int INFO_Y = 100;
    private static final int INFO_H = 46;
    private static final int LIST_X = 106;
    private static final int LIST_Y = 20;
    private static final int CARD_W = 176;
    private static final int CARD_H = 29;
    private static final int CARD_GAP = 3;
    private static final int VISIBLE_ROWS = 4;
    private static final int SCROLL_X = LIST_X + CARD_W + 3;
    private static final int SCROLL_W = 4;
    private static final int SMALL_W = 14;
    private static final int SMALL_H = 12;
    private static final int SLIDER_PAD = 4;

    /** Index of the extra "Unbreakable" card after the attribute rows. */
    private static final int UNBREAKABLE_CARD = TableRow.ROWS.size();
    private static final int CARD_COUNT = TableRow.ROWS.size() + 1;

    private static final int EDIT_W = 64;

    /** Pending values per row; only rows the player changed. */
    private final Map<Integer, Double> pending = new HashMap<>();
    private EditBox editBox;
    private int editingRow = -1;
    private boolean pendingUnbreakable;
    private ItemStack lastItem = ItemStack.EMPTY;
    private int scroll;
    private int dragging = -1;

    public AttributeTableScreen(AttributeTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 248;
        titleLabelX = LEFT_X;
        titleLabelY = 9;
        inventoryLabelX = AttributeTableMenu.INVENTORY_LEFT;
        inventoryLabelY = AttributeTableMenu.INVENTORY_Y - 11;
    }

    // ---------------------------------------------------------------- state

    private ItemStack item() {
        return menu.getItem();
    }

    private EquipmentSlot itemSlot() {
        return AttributeValues.defaultSlot(item());
    }

    private double current(int row) {
        return ItemAttributeEditing.value(item(), TableRow.ROWS.get(row).attribute().get(), itemSlot());
    }

    private double shown(int row) {
        return pending.getOrDefault(row, current(row));
    }

    private boolean isUnbreakable() {
        ItemStack stack = item();
        return stack.hasTag() && stack.getTag().getBoolean("Unbreakable");
    }

    private boolean hasPending() {
        return !pending.isEmpty() || pendingUnbreakable;
    }

    private boolean freeEdits() {
        return minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
    }

    private int playerLevels() {
        return minecraft != null && minecraft.player != null ? minecraft.player.experienceLevel : 0;
    }

    private long pendingCost() {
        if (freeEdits()) return 0L;
        long cost = pendingUnbreakable ? AttributeTableMenu.LEVEL_COST : 0L;
        for (Map.Entry<Integer, Double> e : pending.entrySet()) {
            cost += TableRow.ROWS.get(e.getKey()).cost(current(e.getKey()), e.getValue());
        }
        return cost;
    }

    private boolean canApply() {
        return !item().isEmpty() && hasPending() && (freeEdits() || pendingCost() <= playerLevels());
    }

    private void setPending(int row, double value) {
        value = TableRow.round(value);
        if (!TableRow.isValid(value)) return;
        if (Math.abs(value - current(row)) < 1.0E-9D) {
            pending.remove(row);
        } else {
            pending.put(row, value);
        }
    }

    // ---------------------------------------------------------------- typing a value

    @Override
    protected void init() {
        super.init();
        editingRow = -1;
        editBox = new EditBox(font, 0, 0, EDIT_W, 11, Component.empty());
        editBox.setMaxLength(16);
        editBox.setFilter(text -> text.matches("-?[0-9]*([.,][0-9]*)?"));
        editBox.setVisible(false);
        addRenderableWidget(editBox);
    }

    private void startEdit(int row, int cardX, int cardY) {
        editingRow = row;
        editBox.setValue(format(shown(row)));
        editBox.setX(cardX + CARD_W - 5 - EDIT_W);
        editBox.setY(cardY + 1);
        editBox.setVisible(true);
        editBox.setFocused(true);
        setFocused(editBox);
    }

    private void commitEdit() {
        if (editingRow < 0) return;
        String text = editBox.getValue().replace(',', '.');
        try {
            if (!text.isEmpty() && !text.equals("-") && !text.equals(".") && !text.equals("-.")) {
                setPending(editingRow, Double.parseDouble(text));
            }
        } catch (NumberFormatException ignored) {
            // keep the old value
        }
        stopEdit();
    }

    private void stopEdit() {
        editingRow = -1;
        editBox.setVisible(false);
        editBox.setFocused(false);
        setFocused(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingRow >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                stopEdit();
            } else {
                editBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true; // swallow everything, so "E" does not close the screen while typing
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingRow >= 0) {
            return editBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    /** A new or edited item in the slot drops all pending changes. */
    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack stack = item();
        editBox.tick();
        if (!ItemStack.matches(stack, lastItem)) {
            if (editingRow >= 0) stopEdit();
            pending.clear();
            pendingUnbreakable = false;
            dragging = -1;
            lastItem = stack.copy();
        }
    }

    private int maxScroll() {
        return Math.max(0, CARD_COUNT - VISIBLE_ROWS);
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

        for (Slot slot : menu.slots) {
            if (slot.index == 0) {
                drawItemSlot(graphics, x + slot.x, y + slot.y);
            } else {
                drawSlot(graphics, x + slot.x, y + slot.y);
            }
        }

        drawApplyButton(graphics, x + LEFT_X, y + APPLY_Y, mouseX, mouseY);
        drawButton(graphics, x + LEFT_X, y + RESET_Y, LEFT_W, BUTTON_H,
                Component.translatable("gui.attributeeditor.reset"), !item().isEmpty(), mouseX, mouseY);
        drawInfoBox(graphics, x + LEFT_X, y + INFO_Y);
        drawCards(graphics, x, y, mouseX, mouseY);
    }

    private void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GOLD_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, GOLD);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, GOLD_LIGHT);
        g.fill(x + BORDER - 1, y + BORDER - 1, x + w - BORDER + 1, y + h - BORDER + 1, GOLD_DARK);
        g.fill(x + BORDER, y + BORDER, x + w - BORDER, y + h - BORDER, BG);

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
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0E0C1C);
        g.fill(x, y, x + w, y + h, active ? (hover ? BUTTON_HOVER : BUTTON) : BUTTON_OFF);
        if (active) g.fill(x, y, x + w, y + 1, hover ? 0xFF7A6AB0 : 0xFF514676);
        g.drawString(font, label, x + (w - font.width(label)) / 2 + 1, y + (h - 8) / 2 + 1,
                active ? TEXT : TEXT_DISABLED, false);
    }

    private void drawApplyButton(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean active = canApply();
        boolean hover = active && inside(mouseX, mouseY, x, y, LEFT_W, BUTTON_H);
        g.fill(x - 1, y - 1, x + LEFT_W + 1, y + BUTTON_H + 1, active ? GOLD_DARK : 0xFF0E0C1C);
        g.fill(x, y, x + LEFT_W, y + BUTTON_H, active ? (hover ? APPLY_HOVER : APPLY) : BUTTON_OFF);
        if (active) g.fill(x, y, x + LEFT_W, y + 1, GOLD_LIGHT);
        Component label = Component.translatable("gui.attributeeditor.apply");
        g.drawString(font, label, x + (LEFT_W - font.width(label)) / 2 + 1, y + (BUTTON_H - 8) / 2 + 1,
                active ? 0xFF2A1E05 : TEXT_DISABLED, false);
    }

    private void drawInfoBox(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + LEFT_W + 1, y + INFO_H + 1, 0xFF3A3358);
        g.fill(x, y, x + LEFT_W, y + INFO_H, TRACK);

        g.drawString(font, Component.translatable("gui.attributeeditor.levels"), x + 5, y + 5, TEXT, false);
        g.drawString(font, freeEdits() ? "∞" : String.valueOf(playerLevels()), x + 5, y + 16, GREEN, false);

        long cost = pendingCost();
        Component costLabel = Component.translatable("gui.attributeeditor.cost_label");
        g.drawString(font, costLabel, x + 5, y + 31, TEXT, false);
        boolean tooExpensive = !freeEdits() && cost > playerLevels();
        g.drawString(font, String.valueOf(cost), x + 5 + font.width(costLabel) + 4, y + 31,
                tooExpensive ? RED : TITLE, false);
    }

    private int cardY(int visibleIndex) {
        return LIST_Y + visibleIndex * (CARD_H + CARD_GAP);
    }

    private void drawCards(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean hasItem = !item().isEmpty();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int cx = x + LIST_X;
            int cy = y + cardY(i);
            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, CARD);

            int card = i + scroll;
            if (!hasItem) {
                if (i == 0) {
                    g.drawWordWrap(font, Component.translatable("gui.attributeeditor.place_item"),
                            cx + 6, cy + 5, CARD_W - 12, TEXT);
                }
                continue;
            }
            if (card == UNBREAKABLE_CARD) {
                drawUnbreakableCard(g, cx, cy, mouseX, mouseY);
            } else if (card < TableRow.ROWS.size()) {
                drawAttributeCard(g, card, cx, cy, mouseX, mouseY);
            }
        }

        // Scrollbar
        int trackX = x + SCROLL_X;
        int trackY = y + LIST_Y;
        int trackH = VISIBLE_ROWS * (CARD_H + CARD_GAP) - CARD_GAP;
        g.fill(trackX, trackY, trackX + SCROLL_W, trackY + trackH, TRACK);
        int thumbH = Math.max(12, trackH * VISIBLE_ROWS / CARD_COUNT);
        int thumbY = trackY + (maxScroll() == 0 ? 0 : (trackH - thumbH) * scroll / maxScroll());
        g.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, 0xFF4A4068);
    }

    private void drawAttributeCard(GuiGraphics g, int rowIndex, int cx, int cy, int mouseX, int mouseY) {
        TableRow row = TableRow.ROWS.get(rowIndex);
        double now = current(rowIndex);
        double value = shown(rowIndex);
        boolean changed = pending.containsKey(rowIndex);

        String valueText = changed ? format(now) + " > " + format(value) : format(now);
        int valueWidth = editingRow == rowIndex ? EDIT_W : font.width(valueText);
        if (editingRow != rowIndex) {
            boolean hover = inside(mouseX, mouseY, cx + CARD_W / 2, cy, CARD_W / 2, 13);
            g.drawString(font, valueText, cx + CARD_W - 5 - valueWidth, cy + 3,
                    hover ? 0xFFFFFFFF : changed ? CHANGED : VALUE, false);
            if (hover) {
                g.fill(cx + CARD_W - 5 - valueWidth, cy + 12, cx + CARD_W - 5, cy + 13, 0x88FFFFFF);
            }
        }

        String name = font.plainSubstrByWidth(Component.translatable(row.attribute().get().getDescriptionId()).getString(),
                CARD_W - valueWidth - 16);
        g.drawString(font, name, cx + 5, cy + 3, NAME, false);

        int by = cy + 14;
        drawButton(g, cx + SLIDER_PAD, by, SMALL_W, SMALL_H, Component.literal("-"), true, mouseX, mouseY);
        drawButton(g, cx + CARD_W - SLIDER_PAD - SMALL_W, by, SMALL_W, SMALL_H, Component.literal("+"), true, mouseX, mouseY);

        drawSlider(g, sliderX(cx), by + 3, sliderWidth(), row.sliderFraction(value),
                value >= row.sliderMax() - 1.0E-9D, changed ? row.sliderFraction(now) : -1.0D);
    }

    /**
     * @param fraction knob position 0..1
     * @param full     at or past the slider's top: draw the fill as a rainbow
     * @param marker   position 0..1 of the item's current value, or negative for none
     */
    private void drawSlider(GuiGraphics g, int sx, int sy, int sw, double fraction, boolean full, double marker) {
        g.fill(sx - 1, sy - 1, sx + sw + 1, sy + 7, 0xFF3A3358);
        g.fill(sx, sy, sx + sw, sy + 6, TRACK);

        int filled = (int) Math.round(sw * fraction);
        for (int px = 0; px < filled; px++) {
            float t = (float) px / Math.max(1, sw - 1);
            int color = full
                    ? 0xFF000000 | Mth.hsvToRgb(0.78F - t * 0.78F, 0.75F, 1.0F)
                    : lerpColor(0xFF4B2A9A, 0xFFE04060, filled <= 1 ? 1.0F : (float) px / (filled - 1));
            g.fill(sx + px, sy, sx + px + 1, sy + 6, color);
        }

        // Sparkles along the track.
        for (int px = 6; px < sw - 2; px += 17) {
            int dy = (px / 17) % 2 == 0 ? 2 : 3;
            g.fill(sx + px, sy + dy, sx + px + 1, sy + dy + 1, px < filled ? 0xAAFFFFFF : 0x66FFFFFF);
        }

        // Tick for the item's current value, when a change is pending.
        if (marker >= 0.0D) {
            int cxp = sx + (int) Math.round(sw * marker);
            g.fill(cxp, sy - 1, cxp + 1, sy + 7, 0xFFFFFFFF);
        }

        int knob = sx + filled - 2;
        g.fill(knob - 1, sy - 3, knob + 5, sy + 9, GOLD_DARK);
        g.fill(knob, sy - 2, knob + 4, sy + 8, GOLD_LIGHT);
        g.fill(knob + 1, sy - 1, knob + 3, sy + 7, GOLD);
    }

    private void drawUnbreakableCard(GuiGraphics g, int cx, int cy, int mouseX, int mouseY) {
        boolean now = isUnbreakable();
        boolean shown = now != pendingUnbreakable;
        Component yes = Component.translatable("gui.attributeeditor.yes");
        Component no = Component.translatable("gui.attributeeditor.no");
        String valueText = pendingUnbreakable
                ? (now ? yes : no).getString() + " > " + (shown ? yes : no).getString()
                : (now ? yes : no).getString();
        int valueWidth = font.width(valueText);
        g.drawString(font, valueText, cx + CARD_W - 5 - valueWidth, cy + 3, pendingUnbreakable ? CHANGED : VALUE, false);
        g.drawString(font, Component.translatable("gui.attributeeditor.unbreakable"), cx + 5, cy + 3, NAME, false);

        drawButton(g, cx + SLIDER_PAD, cy + 14, CARD_W - 2 * SLIDER_PAD, SMALL_H,
                Component.translatable(shown ? "gui.attributeeditor.unbreakable.off" : "gui.attributeeditor.unbreakable.on"),
                true, mouseX, mouseY);
    }

    private static int sliderX(int cardX) {
        return cardX + SLIDER_PAD + SMALL_W + 6;
    }

    private static int sliderWidth() {
        return CARD_W - 2 * (SLIDER_PAD + SMALL_W + 6);
    }

    private void renderHoverTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (item().isEmpty()) return;
        if (inside(mouseX, mouseY, leftPos + LEFT_X, topPos + RESET_Y, LEFT_W, BUTTON_H)) {
            g.renderTooltip(font, Component.translatable(hasPending()
                    ? "gui.attributeeditor.reset.pending" : "gui.attributeeditor.reset.item"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, leftPos + LEFT_X, topPos + APPLY_Y, LEFT_W, BUTTON_H) && !freeEdits()) {
            g.renderTooltip(font, Component.translatable("gui.attributeeditor.cost", AttributeTableMenu.LEVEL_COST),
                    mouseX, mouseY);
        } else if (editingRow < 0 && valueRowAt(mouseX, mouseY) >= 0) {
            g.renderTooltip(font, Component.translatable("gui.attributeeditor.type_value"), mouseX, mouseY);
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
        if (button == 0 && !item().isEmpty() && handleClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Attribute row whose value text (top-right of its card) is under the mouse, or -1. */
    private int valueRowAt(double mouseX, double mouseY) {
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int card = i + scroll;
            if (card >= TableRow.ROWS.size()) break;
            if (inside(mouseX, mouseY, leftPos + LIST_X + CARD_W / 2, topPos + cardY(i), CARD_W / 2, 13)) return card;
        }
        return -1;
    }

    private boolean handleClick(double mouseX, double mouseY) {
        if (editingRow >= 0) {
            if (editBox.isMouseOver(mouseX, mouseY)) {
                return editBox.mouseClicked(mouseX, mouseY, 0);
            }
            commitEdit();
        }

        int valueRow = valueRowAt(mouseX, mouseY);
        if (valueRow >= 0) {
            click();
            startEdit(valueRow, leftPos + LIST_X, topPos + cardY(valueRow - scroll));
            return true;
        }

        if (inside(mouseX, mouseY, leftPos + LEFT_X, topPos + APPLY_Y, LEFT_W, BUTTON_H)) {
            if (canApply()) apply();
            return true;
        }
        if (inside(mouseX, mouseY, leftPos + LEFT_X, topPos + RESET_Y, LEFT_W, BUTTON_H)) {
            click();
            if (hasPending()) {
                pending.clear();
                pendingUnbreakable = false;
            } else {
                send(AttributeTableMenu.RESET);
            }
            return true;
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int card = i + scroll;
            int cx = leftPos + LIST_X;
            int cy = topPos + cardY(i);
            int by = cy + 14;

            if (card == UNBREAKABLE_CARD) {
                if (inside(mouseX, mouseY, cx + SLIDER_PAD, by, CARD_W - 2 * SLIDER_PAD, SMALL_H)) {
                    click();
                    pendingUnbreakable = !pendingUnbreakable;
                    return true;
                }
                continue;
            }
            if (card >= TableRow.ROWS.size()) continue;

            if (inside(mouseX, mouseY, cx + SLIDER_PAD, by, SMALL_W, SMALL_H)) {
                click();
                setPending(card, shown(card) - TableRow.ROWS.get(card).step());
                return true;
            }
            if (inside(mouseX, mouseY, cx + CARD_W - SLIDER_PAD - SMALL_W, by, SMALL_W, SMALL_H)) {
                click();
                setPending(card, shown(card) + TableRow.ROWS.get(card).step());
                return true;
            }
            if (inside(mouseX, mouseY, sliderX(cx) - 3, by - 2, sliderWidth() + 6, SMALL_H + 4)) {
                dragging = card;
                dragTo(card, mouseX);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging >= 0 && button == 0) {
            dragTo(dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging >= 0 && button == 0) {
            dragging = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dragTo(int row, double mouseX) {
        int sx = sliderX(leftPos + LIST_X);
        double t = Mth.clamp((mouseX - sx) / sliderWidth(), 0.0D, 1.0D);
        setPending(row, TableRow.ROWS.get(row).sliderValue(t));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y, CARD_W + 10,
                VISIBLE_ROWS * (CARD_H + CARD_GAP))) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
            dragging = -1;
            if (editingRow >= 0) commitEdit();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Sends every pending change; the server validates and charges each one. */
    private void apply() {
        click();
        for (Map.Entry<Integer, Double> e : pending.entrySet()) {
            ModNetwork.CHANNEL.sendToServer(new SetAttributePacket(e.getKey(), e.getValue()));
        }
        if (pendingUnbreakable) {
            send(AttributeTableMenu.UNBREAKABLE);
        }
        pending.clear();
        pendingUnbreakable = false;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private static String format(double value) {
        double rounded = TableRow.round(value);
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
