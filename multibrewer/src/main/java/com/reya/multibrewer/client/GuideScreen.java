package com.reya.multibrewer.client;

import java.util.List;

import com.reya.multibrewer.MultiBrewer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import org.lwjgl.glfw.GLFW;

/**
 * The brewer's guide: an open book (pixel-art texture from tools/gen_book.py) with two pages per
 * spread. Each page has a title, a row of item pictures and text from the language file.
 */
public class GuideScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MultiBrewer.MODID, "textures/gui/guide.png");
    private static final int W = 256, H = 180;
    private static final int PAGE_W = 96;
    private static final int INK = 0xFF3A2A1A;
    private static final int TITLE = 0xFF7A1E3A;

    private int spread;
    private ItemStack[][] icons;

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    public GuideScreen() {
        super(Component.translatable("item.multibrewer.guide_book"));
    }

    private ItemStack[][] icons() {
        if (icons == null) {
            ItemStack water = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
            ItemStack swift = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.SWIFTNESS);
            ItemStack heal = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
            ItemStack night = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.NIGHT_VISION);
            icons = new ItemStack[][]{
                    {new ItemStack(MultiBrewer.BREWER_ITEM.get())},
                    {new ItemStack(Items.NETHER_WART), new ItemStack(Items.BLAZE_POWDER), new ItemStack(Items.REDSTONE)},
                    {water, new ItemStack(Items.NETHER_WART), PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD)},
                    {swift, heal, night},
                    {new ItemStack(Items.REDSTONE), new ItemStack(Items.GLOWSTONE_DUST)},
                    {new ItemStack(MultiBrewer.SPEED_UPGRADE.get()), new ItemStack(MultiBrewer.EFFICIENCY_UPGRADE.get()),
                            new ItemStack(MultiBrewer.POTENCY_UPGRADE.get())},
                    {new ItemStack(Items.BLAZE_POWDER), new ItemStack(Items.HOPPER)},
                    {new ItemStack(Items.SPLASH_POTION), new ItemStack(Items.LINGERING_POTION)}};
        }
        return icons;
    }

    private int pages() {
        return icons().length;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        g.blit(TEXTURE, x, y, 0, 0, W, H, W, H);
        for (int side = 0; side < 2; side++) {
            int page = spread * 2 + side;
            if (page >= pages()) break;
            drawPage(g, page, x + (side == 0 ? 20 : 140), y + 18, mouseX, mouseY);
        }
        // page arrows
        if (spread > 0) arrow(g, x + 18, y + 156, false, over(mouseX, mouseY, x + 18, y + 156));
        if ((spread + 1) * 2 < pages()) arrow(g, x + W - 32, y + 156, true, over(mouseX, mouseY, x + W - 32, y + 156));
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawPage(GuiGraphics g, int page, int px, int py, int mouseX, int mouseY) {
        Component title = Component.translatable("guide.multibrewer." + page + ".title");
        g.drawString(font, title, px + (PAGE_W - font.width(title)) / 2, py, TITLE, false);
        g.fill(px + 10, py + 10, px + PAGE_W - 10, py + 11, 0x807A1E3A);

        ItemStack[] row = icons()[page];
        int iconsW = row.length * 20 - 4;
        int ix = px + (PAGE_W - iconsW) / 2;
        ItemStack hovered = ItemStack.EMPTY;
        for (ItemStack stack : row) {
            g.fill(ix - 1, py + 15, ix + 17, py + 33, 0x30A07850);
            g.renderItem(stack, ix, py + 16);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= py + 16 && mouseY < py + 32) hovered = stack;
            ix += 20;
        }

        List<FormattedCharSequence> lines = font.split(Component.translatable("guide.multibrewer." + page + ".text"), PAGE_W);
        int ty = py + 38;
        for (FormattedCharSequence line : lines) {
            if (ty > py + 128) break;
            g.drawString(font, line, px, ty, INK, false);
            ty += 9;
        }
        String num = String.valueOf(page + 1);
        g.drawString(font, num, px + (PAGE_W - font.width(num)) / 2, py + 136, 0xFF8A6A4A, false);
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private static boolean over(double mx, double my, int x, int y) {
        return mx >= x && mx < x + 14 && my >= y && my < y + 10;
    }

    private static void arrow(GuiGraphics g, int x, int y, boolean right, boolean hot) {
        int c = hot ? 0xFFFFE28C : 0xFFE3B341;
        for (int i = 0; i < 5; i++) {
            int len = 5 - i;
            int ax = right ? x + 8 + i : x + 5 - i;
            g.fill(ax, y + 5 - len, ax + 1, y + 5 + len, 0xFF3A2208);
        }
        for (int i = 0; i < 4; i++) {
            int len = 4 - i;
            int ax = right ? x + 8 + i : x + 5 - i;
            g.fill(ax, y + 5 - len + 1, ax + 1, y + 5 + len - 1, c);
        }
        g.fill(right ? x + 1 : x + 6, y + 4, right ? x + 8 : x + 13, y + 6, c);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        if (spread > 0 && over(mx, my, x + 18, y + 156)) {
            turn(-1);
            return true;
        }
        if ((spread + 1) * 2 < pages() && over(mx, my, x + W - 32, y + 156)) {
            turn(1);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) return turn(1);
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) return turn(-1);
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return turn(delta < 0 ? 1 : -1);
    }

    private boolean turn(int dir) {
        int next = spread + dir;
        if (next < 0 || next * 2 >= pages()) return false;
        spread = next;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
