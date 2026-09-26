package com.reya.advancedenchanting.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.reya.advancedenchanting.AdvancedEnchanting;
import com.reya.advancedenchanting.AdvancedTableMenu;
import com.reya.advancedenchanting.EnchantRules;
import com.reya.advancedenchanting.network.EnchantPacket;
import com.reya.advancedenchanting.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Advanced table GUI in the same style as the mob farm: the frame, slot, plates and tracks are one
 * pixel-art texture (tools/gen_gui.py); this class draws the level sliders, row tints, buttons and text.
 * Rows are green when the enchantment can be changed and red when it can't (clashes with a chosen
 * one, a curse outside the full-moon midnight, a treasure).
 */
public class AdvancedTableScreen extends AbstractContainerScreen<AdvancedTableMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(AdvancedEnchanting.MODID, "textures/gui/table.png");
    private static final int MARGIN = 4;
    private static final int TEX_W = AdvancedTableMenu.WIDTH + 2 * MARGIN;
    private static final int TEX_H = AdvancedTableMenu.HEIGHT + 2 * MARGIN;

    private static final int ROWS = 4;
    private static final int ROW_H = 26;
    private static final int LIST_X = 96;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 130;
    private static final int SCROLL_X = 230;
    private static final int INFO_X = 12;
    private static final int INFO_Y = 48;
    private static final int INFO_W = 80;
    private static final int INFO_H = 30;

    private static final int BLACK = 0xFF0A090C;
    private static final int TEXT_DIM = 0xFFD8C8AE;
    private static final int GOLD_L = 0xFFFFE28C;
    private static final int GOLD = 0xFFE3B341;
    private static final int GOLD_D = 0xFF886012;
    private static final int CYAN = 0xFF7EE8F2;
    private static final int GREEN = 0xFF8CF05A;
    private static final int RED = 0xFFFF6B5E;
    private static final int GREY = 0xFFA89C94;
    private static final int PLAQUE_TEXT = 0xFF3A3530;

    private record Entry(Enchantment ench, int current) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<Enchantment, Integer> targets = new LinkedHashMap<>();
    private final LevelSlider[] sliders = new LevelSlider[ROWS];
    private ItemStack lastStack = ItemStack.EMPTY;
    private Button enchantButton;
    private Button resetButton;
    private int scroll;

    public AdvancedTableScreen(AdvancedTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = AdvancedTableMenu.WIDTH;
        imageHeight = AdvancedTableMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < ROWS; i++) {
            sliders[i] = addRenderableWidget(new LevelSlider(leftPos + LIST_X + 4, topPos + LIST_Y + i * ROW_H + 13, LIST_W - 8, 10));
        }
        addRenderableWidget(new ScrollBar(leftPos + SCROLL_X, topPos + LIST_Y, 7, ROWS * ROW_H - 2));
        enchantButton = addRenderableWidget(new StoneButton(leftPos + INFO_X, topPos + 84, INFO_W, 18,
                Component.translatable("gui.advancedenchanting.enchant"), b -> send(), true));
        resetButton = addRenderableWidget(new StoneButton(leftPos + INFO_X, topPos + 106, INFO_W, 14,
                Component.translatable("gui.advancedenchanting.reset"), b -> resetTargets(), false));
    }

    // ---------------------------------------------------------------- state

    private void rebuild(ItemStack stack) {
        entries.clear();
        targets.clear();
        scroll = 0;
        Map<Enchantment, Integer> current = EnchantmentHelper.getEnchantments(stack);
        EnchantRules.options(stack).forEach((e, lvl) -> {
            int cur = current.getOrDefault(e, 0);
            entries.add(new Entry(e, cur));
            targets.put(e, cur);
        });
    }

    private void resetTargets() {
        for (Entry e : entries) targets.put(e.ench(), e.current());
    }

    private boolean changed() {
        for (Entry e : entries) if (targets.getOrDefault(e.ench(), 0) != e.current()) return true;
        return false;
    }

    private Map<Enchantment, Integer> currentMap() {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        for (Entry e : entries) m.put(e.ench(), e.current());
        return m;
    }

    private long cost() {
        return EnchantRules.totalCost(currentMap(), targets);
    }

    private boolean available(Entry e) {
        return minecraft != null && minecraft.level != null
                && EnchantRules.available(e.ench(), e.current(), targets, minecraft.level);
    }

    private boolean free() {
        return minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
    }

    private void send() {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        for (Entry e : entries) {
            int t = targets.getOrDefault(e.ench(), 0);
            if (t == e.current()) continue;
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(e.ench());
            if (id != null) out.put(id, t);
        }
        if (!out.isEmpty()) Net.CHANNEL.sendToServer(new EnchantPacket(out));
    }

    private void updateWidgets() {
        ItemStack cur = menu.item();
        if (!ItemStack.isSameItemSameTags(cur, lastStack)) {
            lastStack = cur.copy();
            rebuild(cur);
        }
        // a row that can't be changed any more goes back to what the item has
        for (Entry e : entries) {
            if (!available(e)) targets.put(e.ench(), e.current());
        }
        int n = entries.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, n - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            sliders[i].visible = idx < n;
            sliders[i].entry = idx < n ? entries.get(idx) : null;
            sliders[i].active = idx < n && available(entries.get(idx));
        }
        long have = minecraft.player == null ? 0L : EnchantRules.points(minecraft.player);
        enchantButton.active = changed() && (free() || have >= cost());
        resetButton.active = changed();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateWidgets();
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) renderOwnTooltips(g, mouseX, mouseY);
    }

    private void renderOwnTooltips(GuiGraphics g, int mx, int my) {
        int lx = mx - leftPos;
        int ly = my - topPos;
        if (lx >= INFO_X && lx < INFO_X + INFO_W && ly >= INFO_Y && ly < INFO_Y + INFO_H) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.advancedenchanting.tip.title").withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("gui.advancedenchanting.tip.slider").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.advancedenchanting.tip.colors").withStyle(ChatFormatting.GRAY));
            boolean open = minecraft.level != null && EnchantRules.cursesOpen(minecraft.level);
            tip.add(Component.translatable(open ? "gui.advancedenchanting.tip.curses_open" : "gui.advancedenchanting.tip.curses")
                    .withStyle(open ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE));
            g.renderComponentTooltip(font, tip, mx, my);
            return;
        }
        if (lx < LIST_X || lx >= LIST_X + LIST_W || ly < LIST_Y || ly >= LIST_Y + ROWS * ROW_H) return;
        int row = (ly - LIST_Y) / ROW_H;
        int idx = scroll + row;
        if (idx >= entries.size() || ly - LIST_Y - row * ROW_H >= 12) return;
        Entry e = entries.get(idx);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(e.ench().getDescriptionId()).withStyle(e.ench().isCurse() ? ChatFormatting.RED : ChatFormatting.AQUA));
        String descKey = e.ench().getDescriptionId() + ".desc";
        Component desc = Component.translatable(descKey);
        if (!desc.getString().equals(descKey)) tip.add(desc.copy().withStyle(ChatFormatting.GRAY));
        if (!available(e)) tip.add(Component.translatable(reasonKey(e)).withStyle(ChatFormatting.RED));
        g.renderComponentTooltip(font, tip, mx, my);
    }

    private String reasonKey(Entry e) {
        if (e.ench().isCurse() && (minecraft.level == null || !EnchantRules.cursesOpen(minecraft.level))) return "gui.advancedenchanting.reason.curse";
        if (e.ench().isTreasureOnly() && !e.ench().isCurse()) return "gui.advancedenchanting.reason.treasure";
        return "gui.advancedenchanting.reason.conflict";
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos - MARGIN, topPos - MARGIN, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        drawTitlePlaque(g, leftPos + imageWidth / 2, topPos - 13);
        int x1 = leftPos + LIST_X;
        if (entries.isEmpty()) {
            int y1 = topPos + LIST_Y;
            int y2 = y1 + ROWS * ROW_H - 2;
            g.fill(x1, y1, x1 + LIST_W, y2, BLACK);
            g.fill(x1 + 1, y1 + 1, x1 + LIST_W - 1, y2 - 1, 0xFF2C1416);
            g.fill(x1 + 1, y1 + 1, x1 + LIST_W - 1, y1 + 2, 0xFF190B0C);
            g.fill(x1 + 1, y2 - 2, x1 + LIST_W - 1, y2 - 1, 0xFF4A2A28);
            return;
        }
        // green / red tint over the row plates from the texture
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= entries.size()) break;
            Entry e = entries.get(idx);
            int ry = topPos + LIST_Y + i * ROW_H;
            boolean ok = available(e);
            boolean touched = targets.getOrDefault(e.ench(), 0) != e.current();
            int tint = ok ? (touched ? 0x5040D060 : 0x2C40C060) : 0x44E04040;
            g.fill(x1 + 1, ry + 2, x1 + LIST_W - 1, ry + ROW_H - 4, tint);
            g.fill(x1 + 1, ry + 1, x1 + 3, ry + ROW_H - 3, ok ? 0xFF5FD070 : 0xFFD05050);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, -10, PLAQUE_TEXT, false);

        long have = minecraft.player == null ? 0L : EnchantRules.points(minecraft.player);
        long cost = cost();
        boolean ok = free() || have >= cost;
        Component xp = Component.translatable("gui.advancedenchanting.xp");
        g.drawString(font, xp, INFO_X + 11, INFO_Y + 5, TEXT_DIM, true);
        g.drawString(font, free() ? "∞" : String.valueOf(have), INFO_X + 13 + font.width(xp), INFO_Y + 5, GREEN, true);
        Component costText = Component.translatable("gui.advancedenchanting.cost");
        g.drawString(font, costText, INFO_X + 11, INFO_Y + 18, TEXT_DIM, true);
        g.drawString(font, String.valueOf(cost), INFO_X + 13 + font.width(costText), INFO_Y + 18, ok ? GOLD_L : RED, true);

        if (entries.isEmpty()) {
            boolean empty = menu.item().isEmpty();
            Component msg = Component.translatable(empty ? "gui.advancedenchanting.insert" : "gui.advancedenchanting.none");
            List<FormattedCharSequence> lines = font.split(msg, LIST_W - 16);
            int ty = LIST_Y + (ROWS * ROW_H - 2 - lines.size() * 11) / 2 + 1;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, LIST_X + (LIST_W - font.width(line)) / 2, ty, TEXT_DIM, true);
                ty += 11;
            }
            return;
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= entries.size()) break;
            Entry e = entries.get(idx);
            int ty = LIST_Y + i * ROW_H + 3;
            int target = targets.getOrDefault(e.ench(), 0);
            String info = roman(target);
            int infoColor = TEXT_DIM;
            if (target != e.current()) {
                info = roman(e.current()) + " > " + roman(target);
                infoColor = target > e.current() ? CYAN : RED;
            }
            int infoW = font.width(info);
            g.drawString(font, info, LIST_X + LIST_W - 4 - infoW, ty, infoColor, true);
            String name = font.plainSubstrByWidth(Component.translatable(e.ench().getDescriptionId()).getString(), LIST_W - 14 - infoW);
            int color = !available(e) ? GREY : e.ench().isCurse() ? RED : GOLD_L;
            g.drawString(font, name, LIST_X + 6, ty, color, true);
        }
    }

    private static String roman(int level) {
        if (level <= 0) return "-";
        String key = "enchantment.level." + level;
        Component c = Component.translatable(key);
        String s = c.getString();
        return s.equals(key) ? String.valueOf(level) : s;
    }

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

    // ---------------------------------------------------------------- input

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

    // ---------------------------------------------------------------- widgets

    /** Pixel button: gold (with a book) for enchanting, grey stone for reset. */
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
            int textX = x + (width - font.width(m)) / 2 + 1;
            if (primary) {
                textX += 7;
                g.renderItem(new ItemStack(Items.ENCHANTED_BOOK), textX - 18, y + (height - 16) / 2);
            }
            g.drawString(font, m, textX, y + (height - 8) / 2 + 1, fg, false);
        }
    }

    /**
     * One notch per level on a sunken track: gold up to what the item has, cyan for levels being
     * added, red stripes for levels being taken off, gold knob on the chosen level.
     */
    private class LevelSlider extends AbstractWidget {
        private static final int HW = 5;
        Entry entry;

        LevelSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty());
        }

        private int max() {
            return EnchantRules.maxLevel(entry.ench(), entry.current());
        }

        private int posFor(int level) {
            return getX() + (int) Math.round((width - HW) * (level / (double) max()));
        }

        private void setFromMouse(double mx) {
            if (entry == null || !active) return;
            double f = Mth.clamp((mx - getX() - HW / 2.0) / (width - HW), 0.0, 1.0);
            targets.put(entry.ench(), (int) Math.round(f * max()));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            if (entry == null) return;
            int x = getX();
            int y = getY();
            int ty = y + 2;
            int th = height - 4;
            int x2 = x + width;
            g.fill(x, ty - 1, x2, ty + th + 1, BLACK);
            g.fill(x + 1, ty, x2 - 1, ty + th, 0xFF2A1416);
            g.fill(x + 1, ty, x2 - 1, ty + 1, 0xFF180A0B);
            int target = targets.getOrDefault(entry.ench(), 0);
            int cx = posFor(entry.current()) + HW / 2;
            int tx = posFor(target) + HW / 2;
            int low = Math.min(cx, tx);
            if (low > x + 1) {
                g.fill(x + 1, ty, low, ty + th, GOLD);
                g.fill(x + 1, ty, low, ty + 1, GOLD_L);
                g.fill(x + 1, ty + th - 1, low, ty + th, GOLD_D);
            }
            long time = Util.getMillis();
            if (tx > cx) {
                g.fill(cx, ty, tx, ty + th, 0xFF3FC4DA);
                g.fill(cx, ty, tx, ty + 1, 0xFFD2FBFF);
                g.fill(cx, ty + th - 1, tx, ty + th, 0xFF17708A);
                stripes(g, cx, tx, ty, th, (int) (time / 90L % 6L), 0x60FFFFFF);
            } else if (tx < cx) {
                g.fill(tx, ty, cx, ty + th, 0xFF7A2226);
                stripes(g, tx, cx, ty, th, (int) (time / 90L % 6L), 0x50FF8080);
            }
            for (int l = 1; l < max(); l++) {
                int nx = posFor(l) + HW / 2;
                g.fill(nx, ty + th - 2, nx + 1, ty + th, 0xAA000000);
            }
            int hx = posFor(target);
            boolean hot = isHoveredOrFocused() && active;
            g.fill(hx, y, hx + HW, y + height, BLACK);
            g.fill(hx + 1, y + 1, hx + HW - 1, y + height - 1, active ? (hot ? GOLD_L : GOLD) : 0xFF6E6A66);
            g.fill(hx + 1, y + 1, hx + HW - 1, y + 2, active ? 0xFFFFF4C8 : 0xFF86827E);
            g.fill(hx + 1, y + height - 2, hx + HW - 1, y + height - 1, active ? GOLD_D : 0xFF4E4A47);
            g.fill(hx + 2, y + 3, hx + 3, y + height - 3, active ? GOLD_D : 0xFF4E4A47);
        }

        private void stripes(GuiGraphics g, int from, int to, int ty, int th, int shift, int color) {
            for (int px = from - 6 + shift; px < to; px += 6) {
                for (int row = 1; row < th - 1; row++) {
                    int sx = px + row;
                    if (sx >= from && sx < to) g.fill(sx, ty + row, sx + 1, ty + row + 1, color);
                }
            }
        }

        @Override
        public void onClick(double mx, double my) {
            setFromMouse(mx);
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            setFromMouse(mx);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
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
            g.fill(getX() + 1, hy, getX() + width - 1, hy + hh, isHoveredOrFocused() ? GOLD_L : GOLD);
            g.fill(getX() + 1, hy, getX() + width - 1, hy + 1, 0xFFFFF4C8);
            g.fill(getX() + 1, hy + hh - 1, getX() + width - 1, hy + hh, GOLD_D);
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
