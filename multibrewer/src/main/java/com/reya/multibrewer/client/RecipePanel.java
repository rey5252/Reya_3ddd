package com.reya.multibrewer.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Side panel next to the brewer: how to brew every vanilla potion, as a little tree like a recipe
 * viewer. Tabs switch between normal, splash and lingering potions; the list scrolls with the wheel.
 * <pre>
 *   water + nether wart -> awkward
 *     |- + sugar -> swiftness  + fermented eye -> slowness
 *     |- ...
 *   modifiers: redstone, glowstone, gunpowder, dragon's breath
 * </pre>
 */
final class RecipePanel {
    static final int W = 132;
    static final int H = 196;
    private static final int ROW = 20;
    private static final int LIST_Y = 40;
    private static final int LIST_H = H - LIST_Y - 8;

    private static final int BLACK = 0xFF0A090C;
    private static final int BG = 0xFF2E1840;
    private static final int BG_ROW = 0xFF3A2150;
    private static final int GOLD_L = 0xFFFFE28C;
    private static final int GOLD = 0xFFE3B341;
    private static final int GOLD_D = 0xFF886012;
    private static final int LINE = 0xFF9A7CB8;
    private static final int TEXT = 0xFFF4ECDC;

    /** One branch off the awkward potion (or water): ingredient -> result [-> fermented eye -> corrupted]. */
    private record Branch(ItemStack from, Item ingredient, Potion result, Potion corrupted) {
    }

    private static final Item[] TAB_ITEMS = {Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION};
    private int tab;
    private float scroll;

    private static List<Branch> branches(Item type) {
        List<Branch> out = new ArrayList<>();
        ItemStack awkward = potion(type, Potions.AWKWARD);
        out.add(new Branch(awkward, Items.SUGAR, Potions.SWIFTNESS, Potions.SLOWNESS));
        out.add(new Branch(awkward, Items.RABBIT_FOOT, Potions.LEAPING, Potions.SLOWNESS));
        out.add(new Branch(awkward, Items.GLISTERING_MELON_SLICE, Potions.HEALING, Potions.HARMING));
        out.add(new Branch(awkward, Items.SPIDER_EYE, Potions.POISON, Potions.HARMING));
        out.add(new Branch(awkward, Items.GHAST_TEAR, Potions.REGENERATION, null));
        out.add(new Branch(awkward, Items.MAGMA_CREAM, Potions.FIRE_RESISTANCE, null));
        out.add(new Branch(awkward, Items.PUFFERFISH, Potions.WATER_BREATHING, null));
        out.add(new Branch(awkward, Items.GOLDEN_CARROT, Potions.NIGHT_VISION, Potions.INVISIBILITY));
        out.add(new Branch(awkward, Items.BLAZE_POWDER, Potions.STRENGTH, null));
        out.add(new Branch(awkward, Items.TURTLE_HELMET, Potions.TURTLE_MASTER, null));
        out.add(new Branch(awkward, Items.PHANTOM_MEMBRANE, Potions.SLOW_FALLING, null));
        out.add(new Branch(potion(type, Potions.WATER), Items.FERMENTED_SPIDER_EYE, Potions.WEAKNESS, null));
        return out;
    }

    private static ItemStack potion(Item type, Potion p) {
        return PotionUtils.setPotion(new ItemStack(type), p);
    }

    private int contentHeight() {
        return (1 + branches(TAB_ITEMS[tab]).size() + 1 + 4) * ROW;
    }

    /** Draws the panel at (x, y); returns the stack under the mouse (for a tooltip) or EMPTY. */
    ItemStack render(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        // frame in the brewer's style: black edge, gold line, dark violet inside
        g.fill(x, y, x + W, y + H, BLACK);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, GOLD_D);
        g.fill(x + 1, y + 1, x + W - 1, y + 2, GOLD_L);
        g.fill(x + 1, y + 1, x + 2, y + H - 1, GOLD_L);
        g.fill(x + 2, y + 2, x + W - 2, y + H - 2, GOLD);
        g.fill(x + 3, y + 3, x + W - 3, y + H - 3, BLACK);
        g.fill(x + 4, y + 4, x + W - 4, y + H - 4, BG);

        Component title = Component.translatable("gui.multibrewer.recipes." + tab);
        g.drawString(font, title, x + (W - font.width(title)) / 2, y + 8, GOLD_L, true);

        ItemStack hovered = ItemStack.EMPTY;
        // tabs
        for (int i = 0; i < 3; i++) {
            int tx = x + 30 + i * 26;
            int ty = y + 19;
            boolean on = i == tab;
            g.fill(tx - 2, ty - 2, tx + 18, ty + 18, on ? GOLD : BLACK);
            g.fill(tx - 1, ty - 1, tx + 17, ty + 17, on ? 0xFF5A3478 : 0xFF221230);
            g.renderItem(potion(TAB_ITEMS[i], Potions.WATER), tx, ty);
            if (in(mx, my, tx, ty)) hovered = potion(TAB_ITEMS[i], Potions.WATER);
        }

        // scrolling list
        int listTop = y + LIST_Y;
        int maxScroll = Math.max(0, contentHeight() - LIST_H);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(x + 4, listTop, x + W - 4, listTop + LIST_H);
        Item type = TAB_ITEMS[tab];
        int ry = listTop - Math.round(scroll);

        // water + nether wart -> awkward (+ gunpowder / dragon's breath for the other tabs)
        ItemStack hv = row(g, font, x + 6, ry, new ItemStack[]{potion(Items.POTION, Potions.WATER),
                new ItemStack(Items.NETHER_WART), potion(Items.POTION, Potions.AWKWARD)}, mx, my, listTop);
        if (!hv.isEmpty()) hovered = hv;
        ry += ROW;
        if (tab > 0) {
            hv = row(g, font, x + 6, ry, new ItemStack[]{potion(Items.POTION, Potions.AWKWARD),
                    new ItemStack(tab == 1 ? Items.GUNPOWDER : Items.DRAGON_BREATH), potion(type, Potions.AWKWARD)}, mx, my, listTop);
            if (!hv.isEmpty()) hovered = hv;
            ry += ROW;
        }

        List<Branch> list = branches(type);
        int trunkTop = ry - 4;
        for (Branch b : list) {
            int lx = x + 14;
            g.fill(lx, ry + 7, lx + 8, ry + 8, LINE);
            ItemStack[] items = b.corrupted() == null
                    ? new ItemStack[]{new ItemStack(b.ingredient()), potion(type, b.result())}
                    : new ItemStack[]{new ItemStack(b.ingredient()), potion(type, b.result()),
                    new ItemStack(Items.FERMENTED_SPIDER_EYE), potion(type, b.corrupted())};
            hv = chain(g, font, lx + 8, ry, items, mx, my, listTop);
            if (!hv.isEmpty()) hovered = hv;
            ry += ROW;
        }
        g.fill(x + 14, trunkTop, x + 15, ry - ROW + 8, LINE);

        // modifiers
        ry += 2;
        g.drawString(font, Component.translatable("gui.multibrewer.recipes.modifiers"), x + 8, ry, GOLD_L, true);
        ry += 12;
        Item[] mods = {Items.REDSTONE, Items.GLOWSTONE_DUST, Items.GUNPOWDER, Items.DRAGON_BREATH};
        String[] keys = {"longer", "stronger", "splash", "lingering"};
        for (int i = 0; i < mods.length; i++) {
            ItemStack s = new ItemStack(mods[i]);
            g.renderItem(s, x + 8, ry);
            if (in(mx, my, x + 8, ry) && my >= listTop && my < listTop + LIST_H) hovered = s;
            g.drawString(font, Component.translatable("gui.multibrewer.recipes." + keys[i]), x + 28, ry + 4, TEXT, false);
            ry += 17;
        }
        g.disableScissor();

        // scroll bar
        if (maxScroll > 0) {
            int barH = Math.max(12, LIST_H * LIST_H / contentHeight());
            int barY = listTop + Math.round((LIST_H - barH) * scroll / maxScroll);
            g.fill(x + W - 7, listTop, x + W - 5, listTop + LIST_H, 0xFF221230);
            g.fill(x + W - 7, barY, x + W - 5, barY + barH, GOLD);
        }
        return hovered;
    }

    /** a + b -> c */
    private ItemStack row(GuiGraphics g, Font font, int x, int y, ItemStack[] s, int mx, int my, int top) {
        ItemStack hovered = ItemStack.EMPTY;
        int cx = x;
        for (int i = 0; i < s.length; i++) {
            slot(g, cx, y);
            g.renderItem(s[i], cx, y);
            if (in(mx, my, cx, y) && my >= top && my < top + LIST_H) hovered = s[i];
            cx += 18;
            if (i < s.length - 1) {
                g.drawString(font, i == 0 ? "+" : "→", cx + 1, y + 4, i == 0 ? GOLD_L : LINE, false);
                cx += 10;
            }
        }
        return hovered;
    }

    /** + ingredient -> result [+ eye -> corrupted] */
    private ItemStack chain(GuiGraphics g, Font font, int x, int y, ItemStack[] s, int mx, int my, int top) {
        ItemStack hovered = ItemStack.EMPTY;
        int cx = x;
        for (int i = 0; i < s.length; i++) {
            boolean ingredient = i % 2 == 0;
            if (ingredient) {
                g.drawString(font, "+", cx, y + 4, GOLD_L, false);
                cx += 6;
            }
            if (!ingredient) slot(g, cx, y);
            g.renderItem(s[i], cx, y);
            if (in(mx, my, cx, y) && my >= top && my < top + LIST_H) hovered = s[i];
            cx += 17;
            if (ingredient) {
                g.fill(cx - 1, y + 7, cx + 3, y + 8, LINE);
                cx += 4;
            } else {
                cx += 2;
            }
        }
        return hovered;
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, BLACK);
        g.fill(x, y, x + 16, y + 16, BG_ROW);
    }

    private static boolean in(int mx, int my, int x, int y) {
        return mx >= x && mx < x + 16 && my >= y && my < y + 16;
    }

    boolean click(double mx, double my, int x, int y) {
        for (int i = 0; i < 3; i++) {
            int tx = x + 30 + i * 26;
            int ty = y + 19;
            if (mx >= tx && mx < tx + 16 && my >= ty && my < ty + 16) {
                tab = i;
                scroll = 0;
                return true;
            }
        }
        return mx >= x && mx < x + W && my >= y && my < y + H;
    }

    boolean scroll(double mx, double my, int x, int y, double delta) {
        if (mx < x || mx >= x + W || my < y || my >= y + H) return false;
        scroll -= (float) delta * ROW;
        return true;
    }
}
