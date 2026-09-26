package com.reya.attributeeditor.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.reya.attributeeditor.AttributeValues;
import com.reya.attributeeditor.ItemAttributeEditing;
import com.reya.attributeeditor.table.AttributeTableMenu;
import com.reya.attributeeditor.table.TableRow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AttributeTableScreen extends AbstractContainerScreen<AttributeTableMenu> {
    private static final int ROW_TOP = 18;
    private static final int ROW_HEIGHT = 11;
    private static final int LIST_LEFT = 54;
    private static final int VALUE_X = 158;
    private static final int MINUS_X = 190;
    private static final int PLUS_X = 202;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int ROW_BG = 0xFFB4B4B4;

    private final List<Button> buttons = new ArrayList<>();

    public AttributeTableScreen(AttributeTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 216;
        imageHeight = 235;
        inventoryLabelX = 27;
        inventoryLabelY = AttributeTableMenu.INVENTORY_Y - 11;
        titleLabelX = 6;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        Tooltip cost = Tooltip.create(Component.translatable("gui.attributeeditor.cost", AttributeTableMenu.LEVEL_COST));
        for (int i = 0; i < TableRow.ROWS.size(); i++) {
            int y = ROW_TOP + i * ROW_HEIGHT;
            addButton(Component.literal("-"), MINUS_X, y, 11, 10, i * 2, null);
            addButton(Component.literal("+"), PLUS_X, y, 11, 10, i * 2 + 1, cost);
        }
        addButton(Component.translatable("gui.attributeeditor.reset"), 4, 50, 46, 14, AttributeTableMenu.RESET, null);
        addButton(Component.translatable("gui.attributeeditor.unbreakable"), 4, 67, 46, 14, AttributeTableMenu.UNBREAKABLE,
                Tooltip.create(Component.translatable("gui.attributeeditor.unbreakable.tooltip", AttributeTableMenu.LEVEL_COST)));
    }

    /** Buttons send their id to the server, which applies the change in {@link AttributeTableMenu#clickMenuButton}. */
    private void addButton(Component label, int x, int y, int width, int height, int id, @Nullable Tooltip tooltip) {
        Button button = Button.builder(label, b -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                    }
                })
                .bounds(leftPos + x, topPos + y, width, height)
                .tooltip(tooltip)
                .build();
        buttons.add(addRenderableWidget(button));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean hasItem = !menu.getItem().isEmpty();
        for (Button button : buttons) {
            button.active = hasItem;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Raised grey panel, like vanilla containers.
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_DARK);
        graphics.fill(x, y, x + imageWidth - 1, y + imageHeight - 1, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);

        // Attribute list background.
        graphics.fill(x + LIST_LEFT - 2, y + ROW_TOP - 2, x + imageWidth - 3,
                y + ROW_TOP + TableRow.ROWS.size() * ROW_HEIGHT, ROW_BG);

        for (Slot slot : menu.slots) {
            drawSlot(graphics, x + slot.x, y + slot.y);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, PANEL_LIGHT);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        ItemStack stack = menu.getItem();
        EquipmentSlot slot = stack.isEmpty() ? EquipmentSlot.MAINHAND : AttributeValues.defaultSlot(stack);
        for (int i = 0; i < TableRow.ROWS.size(); i++) {
            Attribute attribute = TableRow.ROWS.get(i).attribute().get();
            int y = ROW_TOP + i * ROW_HEIGHT + 1;
            String name = font.plainSubstrByWidth(Component.translatable(attribute.getDescriptionId()).getString(),
                    VALUE_X - LIST_LEFT - 4);
            graphics.drawString(font, name, LIST_LEFT, y, 0x404040, false);

            String value = stack.isEmpty() ? "-" : format(ItemAttributeEditing.value(stack, attribute, slot));
            graphics.drawString(font, value, VALUE_X, y, stack.isEmpty() ? 0x808080 : 0x1E6B1E, false);
        }

        if (stack.hasTag() && stack.getTag().getBoolean("Unbreakable")) {
            graphics.drawString(font, "\u221E", 22, 86, 0x6B1E6B, false);
        }
    }

    private static String format(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
