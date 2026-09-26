package com.reya.bossdamage.client;

import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Style picker: a list of all panel styles on the left, a live animated preview on the right.
 * The menu itself is drawn in the highlighted style, so it changes look as you browse.
 */
public class StyleScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 236;
    private static final int LIST_X = 12;
    private static final int LIST_Y = 30;
    private static final int ROW_H = 15;
    private static final int LIST_W = 104;
    private static final int PREVIEW_X = LIST_X + LIST_W + 12;
    private static final int BUTTON_H = 18;

    @Nullable
    private final Screen parent;
    private final long openedAt = Util.getMillis();
    private PanelStyle selected;

    public StyleScreen(@Nullable Screen parent) {
        super(Component.translatable("bossdamage.menu.title"));
        this.parent = parent;
        this.selected = ClientConfig.style();
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        long now = Util.getMillis();
        PanelStyle st = selected;

        // Menu opens with a quick fade + rise.
        float open = Mth.clamp((now - openedAt) / 200.0F, 0.0F, 1.0F);
        int x = left();
        int y = top() + Math.round((1.0F - open) * 12.0F);
        int x2 = x + WIDTH;
        int y2 = y + HEIGHT;

        BossPanelOverlay.drawBox(g, st, x, y, x2, y2, 4242, open, now);
        if (open < 0.3F) return;

        g.drawString(font, title, x + (WIDTH - font.width(title)) / 2, y + 10, st.header, st.textShadow);
        g.fill(x + 12, y + 22, x2 - 12, y + 23, st.divider);

        // Style list
        PanelStyle[] styles = PanelStyle.values();
        for (int i = 0; i < styles.length; i++) {
            PanelStyle s = styles[i];
            int rx = x + LIST_X;
            int ry = y + LIST_Y + i * ROW_H;
            boolean hover = inside(mouseX, mouseY, rx, ry, LIST_W, ROW_H - 2);
            boolean isSelected = s == selected;
            if (isSelected) {
                g.fill(rx - 1, ry - 1, rx + LIST_W + 1, ry + ROW_H - 1, st.frame);
                g.fill(rx, ry, rx + LIST_W, ry + ROW_H - 2, 0xC0000000 | (st.bgTop & 0xFFFFFF));
            } else if (hover) {
                g.fill(rx, ry, rx + LIST_W, ry + ROW_H - 2, 0x40FFFFFF);
            }
            // Swatch: that style's sky and frame colours.
            g.fill(rx + 3, ry + 2, rx + 13, ry + 11, s.frame | 0xFF000000);
            g.fillGradient(rx + 4, ry + 3, rx + 12, ry + 10, s.bgTop | 0xFF000000, s.bgBottom | 0xFF000000);
            g.fill(rx + 7, ry + 5, rx + 9, ry + 7, s.barTo | 0xFF000000);
            int color = isSelected ? st.header : hover ? st.nameText : st.hpText;
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(s.translationKey()).getString(), LIST_W - 20),
                    rx + 17, ry + 3, color, st.textShadow);
        }

        // Live preview
        int px = x + PREVIEW_X;
        int pw = x2 - 12 - px;
        g.drawString(font, Component.translatable("bossdamage.menu.preview"), px, y + LIST_Y, st.hpText, st.textShadow);
        int previewHeight = BossPanelOverlay.drawPreview(g, minecraft, st, px, y + LIST_Y + 14, pw, now);

        // Style name, big
        Component name = Component.translatable(st.translationKey());
        int ny = y + LIST_Y + 14 + previewHeight + 12;
        g.pose().pushPose();
        g.pose().translate(px + pw / 2.0F, ny, 0);
        g.pose().scale(1.5F, 1.5F, 1.0F);
        g.drawString(font, name, -font.width(name) / 2, 0, st.nameText, st.textShadow);
        g.pose().popPose();

        // Buttons
        int by = y2 - 12 - BUTTON_H;
        Component numbers = Component.translatable(ClientConfig.damageNumbers()
                ? "bossdamage.menu.numbers.on" : "bossdamage.menu.numbers.off");
        drawButton(g, st, px, by - BUTTON_H - 6, pw, numbers, mouseX, mouseY);
        drawButton(g, st, px, by, pw, Component.translatable("gui.done"), mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawButton(GuiGraphics g, PanelStyle st, int bx, int by, int bw, Component label, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, bx, by, bw, BUTTON_H);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + BUTTON_H + 1, st.frame);
        g.fill(bx, by, bx + bw, by + BUTTON_H, hover ? (0xE0000000 | (st.bgBottom & 0xFFFFFF)) : (0xE0000000 | (st.bgTop & 0xFFFFFF)));
        if (hover) g.fill(bx, by, bx + bw, by + 1, 0x80FFFFFF);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + (BUTTON_H - 8) / 2 + 1,
                hover ? st.header : st.nameText, st.textShadow);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = left();
        int y = top();

        PanelStyle[] styles = PanelStyle.values();
        for (int i = 0; i < styles.length; i++) {
            if (inside(mouseX, mouseY, x + LIST_X, y + LIST_Y + i * ROW_H, LIST_W, ROW_H - 2)) {
                select(styles[i]);
                return true;
            }
        }

        int px = x + PREVIEW_X;
        int pw = x + WIDTH - 12 - px;
        int by = y + HEIGHT - 12 - BUTTON_H;
        if (inside(mouseX, mouseY, px, by - BUTTON_H - 6, pw, BUTTON_H)) {
            click();
            ClientConfig.DAMAGE_NUMBERS.set(!ClientConfig.damageNumbers());
            ClientConfig.DAMAGE_NUMBERS.save();
            return true;
        }
        if (inside(mouseX, mouseY, px, by, pw, BUTTON_H)) {
            click();
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        PanelStyle[] styles = PanelStyle.values();
        int next = Math.floorMod(selected.ordinal() - (int) Math.signum(delta), styles.length);
        select(styles[next]);
        return true;
    }

    private void select(PanelStyle style) {
        if (style == selected) return;
        click();
        selected = style;
        ClientConfig.STYLE.set(style);
        ClientConfig.STYLE.save();
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
