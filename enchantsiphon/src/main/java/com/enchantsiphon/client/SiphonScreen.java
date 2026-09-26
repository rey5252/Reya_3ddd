package com.enchantsiphon.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.enchantsiphon.EnchantSiphon;
import com.enchantsiphon.SiphonMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * Siphon GUI in the same style as the mob farm and the upgrader: the frame, slots, plates and tracks
 * are one pixel-art texture (tools/gen_gui.py); this class draws the buttons, list, handle and text.
 */
public class SiphonScreen extends AbstractContainerScreen<SiphonMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(EnchantSiphon.MODID, "textures/gui/siphon.png");
    private static final int MARGIN = 4;
    private static final int TEX_W = SiphonMenu.WIDTH + 2 * MARGIN;
    private static final int TEX_H = SiphonMenu.HEIGHT + 2 * MARGIN;

    private static final int ROWS = 5;
    private static final int ROW_H = 18;
    private static final int LIST_X = 70;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 158;
    private static final int SCROLL_X = 232;
    private static final int TAKE_W = 34;

    private static final int BLACK = 0xFF0A090C;
    private static final int TEXT_DIM = 0xFFD8C8AE;
    private static final int GOLD_L = 0xFFFFE28C;
    private static final int GOLD = 0xFFE3B341;
    private static final int GOLD_D = 0xFF886012;
    private static final int GREEN = 0xFF8CF05A;
    private static final int RED = 0xFFFF6B5E;
    private static final int GREY = 0xFFA89C94;
    private static final int PLAQUE_TEXT = 0xFF3A3530;

    private int scroll;
    private List<SiphonMenu.Entry> entries = new ArrayList<>();
    private final SiphonMenu.Status[] statuses = new SiphonMenu.Status[ROWS];
    private final Button[] takeButtons = new Button[ROWS];
    private Button takeAll;

    public SiphonScreen(SiphonMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = SiphonMenu.WIDTH;
        imageHeight = SiphonMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < ROWS; i++) {
            int row = i;
            takeButtons[i] = addRenderableWidget(new StoneButton(leftPos + LIST_X + LIST_W - TAKE_W - 3,
                    topPos + LIST_Y + i * ROW_H + 3, TAKE_W, 12, Component.translatable("gui.enchantsiphon.take"),
                    b -> send(scroll + row), false));
        }
        takeAll = addRenderableWidget(new StoneButton(leftPos + LIST_X, topPos + LIST_Y + ROWS * ROW_H + 5,
                SCROLL_X + 7 - LIST_X, 16, Component.translatable("gui.enchantsiphon.take_all"), b -> send(SiphonMenu.TAKE_ALL), true));
        addRenderableWidget(new ScrollBar(leftPos + SCROLL_X, topPos + LIST_Y, 7, ROWS * ROW_H));
    }

    private void send(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void updateWidgets() {
        entries = menu.entries();
        int n = entries.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, n - ROWS)));
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        boolean anyOk = false;
        if (player != null) {
            for (SiphonMenu.Entry e : entries) {
                if (menu.check(player, e) == SiphonMenu.Status.OK) {
                    anyOk = true;
                    break;
                }
            }
        }
        takeAll.active = anyOk;
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            boolean has = idx < n;
            takeButtons[i].visible = has;
            statuses[i] = null;
            if (!has || player == null) continue;
            statuses[i] = menu.check(player, entries.get(idx));
            takeButtons[i].active = statuses[i] == SiphonMenu.Status.OK;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        updateWidgets();
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
        renderRowTooltip(g, mx, my);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index <= SiphonMenu.BOOK_SLOT) {
            g.renderTooltip(font, Component.translatable(hoveredSlot.index == SiphonMenu.ITEM_SLOT
                    ? "gui.enchantsiphon.item" : "gui.enchantsiphon.book"), mx, my);
        }
    }

    private void renderRowTooltip(GuiGraphics g, int mx, int my) {
        int lx = mx - leftPos;
        int ly = my - topPos;
        if (lx < LIST_X || lx >= LIST_X + LIST_W - TAKE_W - 4 || ly < LIST_Y || ly >= LIST_Y + ROWS * ROW_H) return;
        int row = (ly - LIST_Y) / ROW_H;
        int idx = scroll + row;
        if (row >= ROWS || idx >= entries.size()) return;
        SiphonMenu.Entry e = entries.get(idx);
        List<Component> tip = new ArrayList<>();
        tip.add(e.ench().getFullname(e.level()));
        SiphonMenu.Status st = statuses[row];
        if (st != null && st != SiphonMenu.Status.OK) {
            tip.add(Component.translatable("gui.enchantsiphon.reason." + st.name().toLowerCase(Locale.ROOT)));
        }
        g.renderComponentTooltip(font, tip, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.blit(TEXTURE, leftPos - MARGIN, topPos - MARGIN, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        drawTitlePlaque(g, leftPos + imageWidth / 2, topPos - 13);
        if (entries.isEmpty()) {
            int x1 = leftPos + LIST_X;
            int y1 = topPos + LIST_Y;
            int x2 = x1 + LIST_W;
            int y2 = y1 + ROWS * ROW_H;
            g.fill(x1, y1, x2, y2, BLACK);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFF2C1416);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, 0xFF190B0C);
            g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, 0xFF4A2A28);
        }
        if (!menu.getSlot(SiphonMenu.ITEM_SLOT).hasItem()) drawGhost(g, SWORD, leftPos + SiphonMenu.ITEM_X, topPos + SiphonMenu.ITEM_Y);
        if (!menu.getSlot(SiphonMenu.BOOK_SLOT).hasItem()) drawGhost(g, BOOK, leftPos + SiphonMenu.BOOK_X, topPos + SiphonMenu.BOOK_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, -10, PLAQUE_TEXT, false);

        if (entries.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.translatable("gui.enchantsiphon.insert"), LIST_W - 16);
            int ty = LIST_Y + (ROWS * ROW_H - lines.size() * 11) / 2 + 1;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, LIST_X + (LIST_W - font.width(line)) / 2, ty, TEXT_DIM, true);
                ty += 11;
            }
        } else {
            for (int i = 0; i < ROWS; i++) {
                int idx = scroll + i;
                if (idx >= entries.size()) break;
                SiphonMenu.Entry e = entries.get(idx);
                int ry = LIST_Y + i * ROW_H;
                g.renderItem(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(e.ench(), e.level())), LIST_X + 2, ry + 1);
                String name = font.plainSubstrByWidth(e.ench().getFullname(e.level()).getString(), LIST_W - TAKE_W - 28);
                boolean ok = statuses[i] == SiphonMenu.Status.OK;
                int color = e.ench().isCurse() ? RED : ok ? GOLD_L : GREY;
                g.drawString(font, name, LIST_X + 20, ry + 5, color, true);
            }
        }

        // cost on the left, the player's levels on the right, on the red strip above the inventory
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        int cost = menu.costPerEnchant();
        boolean free = cost == 0 || player != null && player.getAbilities().instabuild;
        Component costText = free ? Component.translatable("gui.enchantsiphon.free")
                : Component.translatable("gui.enchantsiphon.cost", cost);
        int textY = 134;
        g.drawString(font, costText, 42, textY, GOLD_L, true);
        if (player != null) {
            Component lvl = player.getAbilities().instabuild ? Component.translatable("gui.enchantsiphon.creative")
                    : Component.translatable("gui.enchantsiphon.levels", player.experienceLevel);
            int col = free || player.experienceLevel >= cost ? GREEN : RED;
            g.drawString(font, lvl, imageWidth - 42 - font.width(lvl), textY, col, true);
        }
    }

    /** Stone name plate hanging over the top edge, with gold caps at its ends. */
    private void drawTitlePlaque(GuiGraphics g, int mid, int y) {
        int half = font.width(title) / 2 + 8;
        int x1 = mid - half;
        int x2 = mid + half;
        g.fill(x1 - 1, y - 1, x2 + 1, y + 13, BLACK);
        g.fill(x1, y, x2, y + 12, 0xFFB4B9B2);
        g.fill(x1, y, x2, y + 1, 0xFFDDE2DA);
        g.fill(x1, y + 11, x2, y + 12, 0xFF7C827A);
        g.fill(x1 + 2, y + 2, x2 - 2, y + 3, 0x30000000);
        for (int side : new int[]{x1 - 5, x2 + 1}) {
            g.fill(side - 1, y + 1, side + 5, y + 11, BLACK);
            g.fill(side, y + 2, side + 4, y + 10, GOLD);
            g.fill(side, y + 2, side + 4, y + 3, GOLD_L);
            g.fill(side, y + 9, side + 4, y + 10, GOLD_D);
        }
    }

    /** Pixel icons engraved into the empty slots: which item goes where. */
    private static final String[] BOOK = {
            "BBBBBBBBBB..",
            "BSCCCCCCCBB.",
            "BSCCCCCCCBPB",
            "BSCCCGCCCBPB",
            "BSCCGgGCCBPB",
            "BSCCCGCCCBPB",
            "BSCCCCCCCBPB",
            "BSCCCCCCCBPB",
            "BSCCCCCCCBPB",
            "BSCCCCCCCBPB",
            "BBBBBBBBBBPB",
            ".BPPPPPPPPPB",
            ".BBBBBBBBBBB"};
    private static final String[] SWORD = {
            "..........BB",
            ".........BPB",
            "........BPB.",
            ".......BPB..",
            "......BPB...",
            ".....BPB....",
            "BB..BPB.....",
            ".BBBPB......",
            "..BGB.......",
            ".BSBBB......",
            "BSB..B......",
            "BB..........",
            "............"};

    private static void drawGhost(GuiGraphics g, String[] icon, int x, int y) {
        for (int r = 0; r < icon.length; r++) {
            String row = icon[r];
            for (int c = 0; c < row.length(); c++) {
                int color = switch (row.charAt(c)) {
                    case 'B' -> 0xFF5C6856;
                    case 'S' -> 0xFF6C7966;
                    case 'C' -> 0xFF7E8B77;
                    case 'P' -> 0xFFB6C0AE;
                    case 'G' -> 0xFF9A7CB8;
                    case 'g' -> 0xFFD8C4F0;
                    default -> 0;
                };
                if (color != 0) g.fill(x + 2 + c, y + 2 + r, x + 3 + c, y + 3 + r, color);
            }
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && getFocused() != null && isDragging() && getFocused().mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        setDragging(false);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (entries.size() > ROWS) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, entries.size() - ROWS);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** Pixel button: gold for the main action, grey stone like the title plate for the rest. */
    private class StoneButton extends Button {
        private final boolean primary;

        StoneButton(int x, int y, int w, int h, Component msg, OnPress press, boolean primary) {
            super(x, y, w, h, msg, press, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hot = isHoveredOrFocused() && active;
            int x = getX();
            int y = getY();
            int x2 = x + width;
            int y2 = y + height;
            int face, hi, lo, fg;
            if (!active) {
                face = 0xFF6E6A66; hi = 0xFF86827E; lo = 0xFF4E4A47; fg = 0xFF3C3936;
            } else if (primary) {
                face = hot ? 0xFFF2C75A : GOLD; hi = GOLD_L; lo = GOLD_D; fg = 0xFF3A2208;
            } else {
                face = hot ? 0xFFCDD2CA : 0xFFB4B9B2; hi = 0xFFE6EBE2; lo = 0xFF7C827A; fg = PLAQUE_TEXT;
            }
            g.fill(x, y, x2, y2, BLACK);
            g.fill(x + 1, y + 1, x2 - 1, y2 - 1, face);
            g.fill(x + 1, y + 1, x2 - 1, y + 2, hi);
            g.fill(x + 1, y + 1, x + 2, y2 - 1, hi);
            g.fill(x + 1, y2 - 2, x2 - 1, y2 - 1, lo);
            g.fill(x2 - 2, y + 1, x2 - 1, y2 - 1, lo);
            Component m = getMessage();
            g.drawString(font, m, x + (width - font.width(m)) / 2 + 1, y + (height - 8) / 2 + 1, fg, false);
        }
    }

    /** Gold handle inside the sunken scroll track from the texture. */
    private class ScrollBar extends AbstractWidget {
        ScrollBar(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty());
        }

        private int handleHeight() {
            int n = entries.size();
            if (n <= ROWS) return height - 2;
            return Math.max(12, (height - 2) * ROWS / n);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int maxScroll = Math.max(0, entries.size() - ROWS);
            if (maxScroll == 0) return;
            int hh = handleHeight();
            int hy = getY() + 1 + (int) ((height - 2 - hh) * ((double) scroll / maxScroll));
            int x = getX() + 1;
            int x2 = getX() + width - 1;
            g.fill(x, hy, x2, hy + hh, isHoveredOrFocused() ? GOLD_L : GOLD);
            g.fill(x, hy, x2, hy + 1, 0xFFFFF4C8);
            g.fill(x, hy + hh - 1, x2, hy + hh, GOLD_D);
        }

        private void setFromMouse(double my) {
            int maxScroll = Math.max(0, entries.size() - ROWS);
            if (maxScroll == 0) return;
            int hh = handleHeight();
            double f = Mth.clamp((my - getY() - hh / 2.0) / (height - hh), 0.0, 1.0);
            scroll = (int) Math.round(f * maxScroll);
        }

        @Override
        public void onClick(double mx, double my) {
            setFromMouse(my);
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            setFromMouse(my);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
        }
    }
}
