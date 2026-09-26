package com.overenchant.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.overenchant.Config;
import com.overenchant.EnchantUpgrade;
import com.overenchant.OverEnchant;
import com.overenchant.UpgraderMenu;
import com.overenchant.network.ApplyPacket;
import com.overenchant.network.Net;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Upgrader GUI in the same style as the mob farm: the frame, slot, plates and tracks are one
 * pixel-art texture (tools/gen_gui.py); this class draws the buttons, sliders, scroll handle and text.
 */
public class UpgraderScreen extends AbstractContainerScreen<UpgraderMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(OverEnchant.MODID, "textures/gui/upgrader.png");
    private static final int MARGIN = 4;
    private static final int TEX_W = UpgraderMenu.WIDTH + 2 * MARGIN;
    private static final int TEX_H = UpgraderMenu.HEIGHT + 2 * MARGIN;

    private static final int ROWS = 4;
    private static final int ROW_H = 26;
    private static final int LIST_X = 96;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 130;
    private static final int SCROLL_X = 230;
    private static final int INFO_X = 12;
    private static final int INFO_Y = 48;

    private static final int BLACK = 0xFF0A090C;
    private static final int TEXT = 0xFFF4ECDC;
    private static final int TEXT_DIM = 0xFFD8C8AE;
    private static final int GOLD_L = 0xFFFFE28C;
    private static final int GOLD = 0xFFE3B341;
    private static final int GOLD_D = 0xFF886012;
    private static final int CYAN = 0xFF7EE8F2;
    private static final int GREEN = 0xFF8CF05A;
    private static final int RED = 0xFFFF6B5E;
    private static final int PLAQUE_TEXT = 0xFF3A3530;

    private final int[] pending = new int[UpgraderMenu.MAX_ROWS];
    private final LevelSlider[] sliders = new LevelSlider[ROWS];
    private final Button[] minus = new Button[ROWS];
    private final Button[] plus = new Button[ROWS];
    private List<Entry> entries = new ArrayList<>();
    private ItemStack lastStack = ItemStack.EMPTY;
    private Button applyButton;
    private Button resetButton;
    private int scroll;

    public UpgraderScreen(UpgraderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = UpgraderMenu.WIDTH;
        imageHeight = UpgraderMenu.HEIGHT;
    }

    // ---------------------------------------------------------------- level maths (unchanged)

    /** Slider range: the next "nice" number comfortably above the current level. */
    private static long scaleFor(long level) {
        long need = level + 25L;
        long t = 100L;
        int k = 0;
        while (t < need) {
            t = k % 3 == 0 ? t * 5L / 2L : t * 2L;
            k++;
        }
        return t;
    }

    private static long maxTarget(long level) {
        return Math.max(level, Math.min(scaleFor(level), (long) Config.MAX_LEVEL.get()));
    }

    private boolean isFree() {
        return Config.XP_LEVELS_PER_UPGRADE.get() == 0
                || minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
    }

    private long totalPending() {
        long sum = 0L;
        for (int i = 0; i < entries.size(); i++) sum += pending[i];
        return sum;
    }

    private long totalCost() {
        return totalPending() * Config.XP_LEVELS_PER_UPGRADE.get();
    }

    private List<Entry> readEntries(ItemStack stack) {
        List<Entry> out = new ArrayList<>();
        ListTag list = EnchantUpgrade.getList(stack);
        if (list == null) return out;
        for (int i = 0; i < list.size() && i < UpgraderMenu.MAX_ROWS; i++) {
            CompoundTag tag = list.getCompound(i);
            String id = tag.getString("id");
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Enchantment en = rl == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(rl);
            String name = en != null ? Component.translatable(en.getDescriptionId()).getString() : id;
            out.add(new Entry(name, tag.getInt("lvl"), en != null && en.isCurse()));
        }
        return out;
    }

    private void adjust(int row, int dir) {
        int idx = scroll + row;
        if (idx >= entries.size()) return;
        long level = entries.get(idx).level();
        int step = hasControlDown() ? 100 : hasShiftDown() ? 10 : 1;
        long np = (long) pending[idx] + (long) dir * step;
        pending[idx] = (int) Math.max(0L, Math.min(np, maxTarget(level) - level));
    }

    private void apply() {
        if (entries.isEmpty()) return;
        Net.CHANNEL.sendToServer(new ApplyPacket(Arrays.copyOf(pending, entries.size())));
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        super.init();
        int rx = leftPos + LIST_X;
        for (int i = 0; i < ROWS; i++) {
            int row = i;
            int ry = topPos + LIST_Y + i * ROW_H + 12;
            minus[i] = addRenderableWidget(new StoneButton(rx + 3, ry, 12, 10, Component.literal("-"), b -> adjust(row, -1), false));
            minus[i].setTooltip(Tooltip.create(Component.translatable("gui.overenchant.minus_tip")));
            sliders[i] = addRenderableWidget(new LevelSlider(rx + 17, ry, 96, 10));
            plus[i] = addRenderableWidget(new StoneButton(rx + LIST_W - 15, ry, 12, 10, Component.literal("+"), b -> adjust(row, 1), false));
            plus[i].setTooltip(Tooltip.create(Component.translatable("gui.overenchant.plus_tip")));
        }
        addRenderableWidget(new ScrollBar(leftPos + SCROLL_X, topPos + LIST_Y, 7, ROWS * ROW_H - 2));
        applyButton = addRenderableWidget(new StoneButton(leftPos + INFO_X, topPos + 84, 80, 18,
                Component.translatable("gui.overenchant.apply"), b -> apply(), true));
        resetButton = addRenderableWidget(new StoneButton(leftPos + INFO_X, topPos + 106, 80, 14,
                Component.translatable("gui.overenchant.reset"), b -> Arrays.fill(pending, 0), false));
    }

    private void updateWidgets() {
        ItemStack cur = menu.getSlot(0).getItem();
        if (!ItemStack.isSameItemSameTags(cur, lastStack)) {
            lastStack = cur.copy();
            Arrays.fill(pending, 0);
        }
        entries = readEntries(cur);
        int n = entries.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, n - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            boolean vis = idx < n;
            sliders[i].visible = vis;
            minus[i].visible = vis;
            plus[i].visible = vis;
            if (!vis) {
                sliders[i].index = -1;
                continue;
            }
            Entry e = entries.get(idx);
            sliders[i].bind(idx, e);
            long room = maxTarget(e.level()) - e.level();
            minus[i].active = pending[idx] > 0;
            plus[i].active = pending[idx] < room;
        }
        long cost = totalCost();
        int have = minecraft.player.experienceLevel;
        applyButton.active = totalPending() > 0L && (isFree() || have >= cost);
        resetButton.active = totalPending() > 0L;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateWidgets();
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos - MARGIN, topPos - MARGIN, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        drawTitlePlaque(g, leftPos + imageWidth / 2, topPos - 13);
        if (entries.isEmpty()) {
            int x1 = leftPos + LIST_X;
            int y1 = topPos + LIST_Y;
            int x2 = x1 + LIST_W;
            int y2 = y1 + ROWS * ROW_H - 2;
            g.fill(x1, y1, x2, y2, BLACK);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFF2C1416);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, 0xFF190B0C);
            g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, 0xFF4A2A28);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, -10, PLAQUE_TEXT, false);

        int have = minecraft.player.experienceLevel;
        long cost = totalCost();
        boolean ok = isFree() || have >= cost;
        Component levels = Component.translatable("gui.overenchant.levels");
        g.drawString(font, levels, INFO_X + 11, INFO_Y + 5, TEXT_DIM, true);
        g.drawString(font, String.valueOf(have), INFO_X + 13 + font.width(levels), INFO_Y + 5, GREEN, true);
        Component costText = Component.translatable("gui.overenchant.cost");
        g.drawString(font, costText, INFO_X + 11, INFO_Y + 18, TEXT_DIM, true);
        g.drawString(font, isFree() && cost > 0 ? "0" : String.valueOf(cost), INFO_X + 13 + font.width(costText),
                INFO_Y + 18, ok ? GOLD_L : RED, true);

        if (entries.isEmpty()) {
            boolean empty = menu.getSlot(0).getItem().isEmpty();
            Component msg = Component.translatable(empty ? "gui.overenchant.insert" : "gui.overenchant.none");
            List<FormattedCharSequence> lines = font.split(msg, LIST_W - 16);
            int cy = LIST_Y + (ROWS * ROW_H - 2) / 2;
            int ty = cy - lines.size() * 11 / 2 + 1;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, LIST_X + (LIST_W - font.width(line)) / 2, ty, TEXT_DIM, true);
                ty += 11;
            }
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= entries.size()) break;
            Entry e = entries.get(idx);
            int ty = LIST_Y + i * ROW_H + 3;
            long level = e.level();
            String info = String.valueOf(level);
            int infoColor = TEXT_DIM;
            if (pending[idx] > 0) {
                info = info + " > " + (level + pending[idx]);
                infoColor = CYAN;
            }
            int infoW = font.width(info);
            g.drawString(font, info, LIST_X + LIST_W - 4 - infoW, ty, infoColor, true);
            String name = font.plainSubstrByWidth(e.name(), LIST_W - 12 - infoW);
            g.drawString(font, name, LIST_X + 4, ty, e.curse() ? RED : GOLD_L, true);
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
            scroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private record Entry(String name, int level, boolean curse) {
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
            int tw = font.width(m);
            g.drawString(font, m, x + (width - tw) / 2 + 1, y + (height - 8) / 2 + 1, fg, false);
        }
    }

    /** Level slider on a sunken track: gold up to the current level, glowing cyan for what gets added, gold knob. */
    private class LevelSlider extends AbstractWidget {
        private static final int HW = 5;
        int index = -1;
        long level;
        long scale;
        long maxT;

        LevelSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty());
        }

        void bind(int idx, Entry e) {
            index = idx;
            level = e.level();
            scale = scaleFor(level);
            maxT = maxTarget(level);
            pending[idx] = (int) Math.max(0L, Math.min(pending[idx], maxT - level));
            active = maxT > level;
        }

        private int handleX(long lvl) {
            return getX() + (int) Math.round((width - HW) * ((double) lvl / scale));
        }

        private void setFromMouse(double mx) {
            if (index < 0 || !active) return;
            double f = Mth.clamp((mx - getX() - HW / 2.0) / (width - HW), 0.0, 1.0);
            long target = Math.max(level, Math.min(Math.round(f * scale), maxT));
            pending[index] = (int) (target - level);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            if (index < 0) return;
            int x = getX();
            int y = getY();
            int ty = y + 2;
            int th = height - 4;
            int x2 = x + width;
            g.fill(x, ty - 1, x2, ty + th + 1, BLACK);
            g.fill(x + 1, ty, x2 - 1, ty + th, 0xFF2A1416);
            g.fill(x + 1, ty, x2 - 1, ty + 1, 0xFF180A0B);
            for (int k = 1; k < 4; k++) {
                int tick = x + 1 + (width - 2) * k / 4;
                g.fill(tick, ty + 1, tick + 1, ty + th, 0xFF3C2224);
            }
            int cx = Math.min(x2 - 1, handleX(level) + HW / 2);
            int tx = Math.min(x2 - 1, handleX(level + pending[index]) + HW / 2);
            // what the item already has: gold
            if (cx > x + 1) {
                g.fill(x + 1, ty, cx, ty + th, GOLD);
                g.fill(x + 1, ty, cx, ty + 1, GOLD_L);
                g.fill(x + 1, ty + th - 1, cx, ty + th, GOLD_D);
            }
            // what will be added: glowing cyan with stripes sliding along
            if (tx > cx) {
                int from = Math.max(cx, x + 1);
                g.fill(from, ty, tx, ty + th, 0xFF3FC4DA);
                g.fill(from, ty, tx, ty + 1, 0xFFD2FBFF);
                g.fill(from, ty + th - 1, tx, ty + th, 0xFF17708A);
                int shift = (int) (Util.getMillis() / 90L % 6L);
                for (int px = from - 6 + shift; px < tx; px += 6) {
                    for (int row = 1; row < th - 1; row++) {
                        int sx = px + row;
                        if (sx >= from && sx < tx) g.fill(sx, ty + row, sx + 1, ty + row + 1, 0x60FFFFFF);
                    }
                }
                float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / 180.0F);
                g.fill(Math.max(from, tx - 2), ty, tx, ty + th, ((int) (0x60 + 0x9F * pulse) << 24) | 0xFFFFFF);
            }
            int hx = handleX(level + pending[index]);
            boolean hot = isHoveredOrFocused() && active;
            g.fill(hx, y, hx + HW, y + height, BLACK);
            g.fill(hx + 1, y + 1, hx + HW - 1, y + height - 1, active ? (hot ? GOLD_L : GOLD) : 0xFF6E6A66);
            g.fill(hx + 1, y + 1, hx + HW - 1, y + 2, active ? 0xFFFFF4C8 : 0xFF86827E);
            g.fill(hx + 1, y + height - 2, hx + HW - 1, y + height - 1, active ? GOLD_D : 0xFF4E4A47);
            g.fill(hx + 2, y + 3, hx + 3, y + height - 3, active ? GOLD_D : 0xFF4E4A47);
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
            int hh = handleHeight();
            int hy = getY() + 1 + (maxScroll == 0 ? 0 : (int) ((height - 2 - hh) * ((double) scroll / maxScroll)));
            int x = getX() + 1;
            int x2 = getX() + width - 1;
            if (maxScroll == 0) return;
            boolean hot = isHoveredOrFocused();
            g.fill(x, hy, x2, hy + hh, hot ? GOLD_L : GOLD);
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
