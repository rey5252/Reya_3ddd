package com.reya.bossdamage.client;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Drag the panel anywhere on screen. The world stays visible (no dimming), guide lines appear
 * and the panel snaps when it is near the middle or an edge. Enter / Done saves, R resets.
 */
public class MovePanelScreen extends Screen {
    private static final int PANEL_W = 200;
    private static final int SNAP = 6;
    private static final int BUTTON_W = 90;
    private static final int BUTTON_H = 18;

    @Nullable
    private final Screen parent;
    private double panelX;
    private double panelY;
    private int panelH = 78;
    private boolean dragging;
    private double grabDx;
    private double grabDy;
    private boolean snappedX;
    private boolean snappedY;

    public MovePanelScreen(@Nullable Screen parent) {
        super(Component.translatable("bossdamage.move.title"));
        this.parent = parent;
        this.panelX = ClientConfig.panelX();
        this.panelY = ClientConfig.panelY();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelLeft() {
        return Mth.clamp((int) Math.round(panelX * width) - PANEL_W / 2, 4, Math.max(4, width - PANEL_W - 4));
    }

    private int panelTop() {
        return Mth.clamp((int) Math.round(panelY * height), 4, Math.max(4, height - panelH - 4));
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PanelStyle st = ClientConfig.style();
        long now = Util.getMillis();
        int x = panelLeft();
        int y = panelTop();

        // Guide lines while dragging; brighter when snapped.
        if (dragging) {
            int cx = width / 2;
            g.fill(cx, 0, cx + 1, height, snappedX ? 0xC0FFFFFF : 0x40FFFFFF);
            int cy = height / 2;
            g.fill(0, cy, width, cy + 1, snappedY ? 0xC0FFFFFF : 0x40FFFFFF);
        }

        panelH = BossPanelOverlay.drawPreview(g, minecraft, st, x, y, PANEL_W, now);

        // Dashed outline around the panel so it's obvious it can be grabbed.
        boolean hover = dragging || inside(mouseX, mouseY, x, y, PANEL_W, panelH);
        int dash = hover ? 0xFFFFFFFF : 0x90FFFFFF;
        for (int i = x - 4; i < x + PANEL_W + 4; i += 6) {
            g.fill(i, y - 5, Math.min(i + 3, x + PANEL_W + 4), y - 4, dash);
            g.fill(i, y + panelH + 4, Math.min(i + 3, x + PANEL_W + 4), y + panelH + 5, dash);
        }
        for (int j = y - 4; j < y + panelH + 4; j += 6) {
            g.fill(x - 5, j, x - 4, Math.min(j + 3, y + panelH + 4), dash);
            g.fill(x + PANEL_W + 4, j, x + PANEL_W + 5, Math.min(j + 3, y + panelH + 4), dash);
        }

        // Hint + buttons at the opposite half of the screen from the panel.
        boolean panelLow = y + panelH / 2 > height / 2;
        int hy = panelLow ? 20 : height - 50;
        Component hint = Component.translatable("bossdamage.move.hint");
        g.fill(width / 2 - font.width(hint) / 2 - 6, hy - 4, width / 2 + font.width(hint) / 2 + 6, hy + 12, 0xA0000000);
        g.drawString(font, hint, width / 2 - font.width(hint) / 2, hy, 0xFFFFFFFF, true);

        int by = hy + 18;
        drawButton(g, st, width / 2 - BUTTON_W - 4, by, Component.translatable("bossdamage.move.reset"), mouseX, mouseY);
        drawButton(g, st, width / 2 + 4, by, Component.translatable("gui.done"), mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private int buttonsY() {
        boolean panelLow = panelTop() + panelH / 2 > height / 2;
        return (panelLow ? 20 : height - 50) + 18;
    }

    private void drawButton(GuiGraphics g, PanelStyle st, int bx, int by, Component label, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, bx, by, BUTTON_W, BUTTON_H);
        g.fill(bx - 1, by - 1, bx + BUTTON_W + 1, by + BUTTON_H + 1, st.frame | 0xFF000000);
        g.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, hover ? (0xF0000000 | (st.bgBottom & 0xFFFFFF)) : (0xF0000000 | (st.bgTop & 0xFFFFFF)));
        if (hover) g.fill(bx, by, bx + BUTTON_W, by + 1, 0x80FFFFFF);
        g.drawString(font, label, bx + (BUTTON_W - font.width(label)) / 2, by + (BUTTON_H - 8) / 2 + 1,
                hover ? st.header : st.nameText, st.textShadow);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int by = buttonsY();
        if (inside(mouseX, mouseY, width / 2 - BUTTON_W - 4, by, BUTTON_W, BUTTON_H)) {
            click();
            reset();
            return true;
        }
        if (inside(mouseX, mouseY, width / 2 + 4, by, BUTTON_W, BUTTON_H)) {
            click();
            onClose();
            return true;
        }
        int x = panelLeft();
        int y = panelTop();
        if (inside(mouseX, mouseY, x - 4, y - 4, PANEL_W + 8, panelH + 8)) {
            dragging = true;
            grabDx = mouseX - x;
            grabDy = mouseY - y;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        double left = mouseX - grabDx;
        double top = mouseY - grabDy;
        double center = left + PANEL_W / 2.0D;

        // Snap to the middle and to the edges.
        snappedX = Math.abs(center - width / 2.0D) < SNAP;
        if (snappedX) center = width / 2.0D;
        double middleY = top + panelH / 2.0D;
        snappedY = Math.abs(middleY - height / 2.0D) < SNAP;
        if (snappedY) top = height / 2.0D - panelH / 2.0D;
        if (top < 4 + SNAP) top = 4;
        if (top > height - panelH - 4 - SNAP) top = height - panelH - 4;

        panelX = Mth.clamp(center / width, 0.0D, 1.0D);
        panelY = Mth.clamp(top / height, 0.0D, 1.0D);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            ClientConfig.setPanelPosition(panelX, panelY);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            reset();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void reset() {
        panelX = ClientConfig.PANEL_X.getDefault();
        panelY = ClientConfig.PANEL_Y.getDefault();
        ClientConfig.setPanelPosition(panelX, panelY);
    }

    @Override
    public void onClose() {
        ClientConfig.setPanelPosition(panelX, panelY);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
