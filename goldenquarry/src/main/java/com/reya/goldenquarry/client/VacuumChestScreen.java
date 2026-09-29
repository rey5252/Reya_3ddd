package com.reya.goldenquarry.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.goldenquarry.GoldenQuarry;
import com.reya.goldenquarry.VacuumChestMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vacuum chest GUI: the reference's layout in the chest's own colours, framed like LoliUtility's
 * machines (textures/gui/vacuum_chest.png, built by tools/extract_vacuum.py). Under it turns the
 * vortex (vacuum_chest_vortex.png, twelve frames), ender sparks spiral into its middle, and round
 * it tentacles are drawn into the chest. This writes on it what the reference shows in text, in the
 * player's language: the title on the grey plate, "Item filter", "Range" and the range; draws the
 * name plate over it and the white/black list switch in the brown slot, and answers the buttons.
 * <p>
 * It opens out of a galaxy (vacuum_chest_galaxy.png, by tools/gen_opening.py): the galaxy turns up
 * in the middle, spinning and growing, stars twinkling round it, and the GUI swells out of its
 * middle, a little past its size and back, while the galaxy fades. Then now and then a light runs
 * over the frame (vacuum_chest_glint.png), the crystals and knobs twinkle, and where a tentacle
 * drags a star in, the frame cracks.
 */
public class VacuumChestScreen extends AbstractContainerScreen<VacuumChestMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/vacuum_chest.png");
    private static final ResourceLocation GALAXY = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/vacuum_chest_galaxy.png");
    private static final ResourceLocation GLINT = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/vacuum_chest_glint.png");
    private static final int GALAXY_SIZE = 128, GALAXY_FRAMES = 16, GLINT_H = 170, GLINT_FRAMES = 24;
    /** The opening: the galaxy all along, the GUI swelling out from MENU_FROM for MENU_MS. */
    private static final long OPEN_MS = 1500L, MENU_FROM = 320L, MENU_MS = 820L;
    /** The glint runs over the frame for GLINT_MS once every GLINT_EVERY. */
    private static final long GLINT_MS = 1100L, GLINT_EVERY = 5200L;
    /** The middle the GUI opens from: the panel's. */
    private static final int MIDDLE_X = VacuumChestMenu.WIDTH / 2, MIDDLE_Y = 88;
    /** Where it twinkles on the frame: x, y, kind of star, colour (the crystals pink, the knobs gold). */
    private static final int[][] TWINKLES = {{108, 16, 2, 0}, {8, 85, 1, 0}, {207, 85, 1, 0}, {4, 12, 1, 1}, {211, 12, 1, 1},
            {16, 164, 1, 1}, {199, 164, 1, 1}, {30, 20, 0, 2}, {186, 20, 0, 2}};
    private static final ResourceLocation VORTEX = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/vacuum_chest_vortex.png");
    /** The vortex under the panel: down to the inventory, which sits right under the panel as LoliUtility's do. */
    private static final int VORTEX_X = 12, VORTEX_Y = 20, VORTEX_W = 192, VORTEX_H = 137, VORTEX_FRAMES = 12;
    /** Buttons: x, y, width, height (texture pixels) and the menu button id. */
    private static final int[][] BUTTONS = {
            {182, 95, 8, 8, VacuumChestMenu.BUTTON_PLUS},
            {182, 104, 8, 8, VacuumChestMenu.BUTTON_MINUS},
            {173, 117, 16, 16, VacuumChestMenu.BUTTON_AREA},
            {VacuumChestMenu.MODE_X - 1, VacuumChestMenu.FILTER_Y - 1, 18, 18, VacuumChestMenu.BUTTON_MODE}};
    private static final int TITLE_X1 = 61, TITLE_X2 = 153, TITLE_Y = 3;
    private static final int LABEL_Y = 84, FILTER_LABEL_X = 27, RANGE_LABEL_END = 201;
    private static final int RANGE_END = 178, RANGE_Y = 99;
    private static final int[] PARTICLE_COLOURS = {0xB24BF3, 0xD472FF, 0x8A2BE2, 0xE58CFF, 0xF0C8FF};

    private final Tentacles tentacles = new Tentacles();
    private final long openedAt = Util.getMillis();

    public VacuumChestScreen(VacuumChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = VacuumChestMenu.WIDTH;
        imageHeight = VacuumChestMenu.HEIGHT;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        long t = Util.getMillis() - openedAt;
        if (t >= OPEN_MS) {
            super.render(g, mouseX, mouseY, partialTick);
            renderTooltip(g, mouseX, mouseY);
            if (hoveredSlot == null || !hoveredSlot.hasItem()) ownTooltips(g, mouseX, mouseY);
            return;
        }
        int cx = leftPos + MIDDLE_X, cy = topPos + MIDDLE_Y;
        galaxy(g, cx, cy, t);
        float p = (t - MENU_FROM) / (float) MENU_MS;
        if (p > 0.0F) {
            float s = p >= 1.0F ? 1.0F : backOut(p);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.pose().translate(-cx, -cy, 0.0F);
            super.render(g, -1000, -1000, partialTick);                 // nothing hovered while it opens
            g.pose().popPose();
        }
        galaxyStars(g, cx, cy, t);
    }

    /** Eases out past 1 and back: the GUI swells a little past its size and settles. */
    private static float backOut(float p) {
        float c1 = 1.5F, c3 = c1 + 1.0F, q = p - 1.0F;
        return 1.0F + c3 * q * q * q + c1 * q * q;
    }

    /** The galaxy, spinning in from nothing and growing, spinning slower as it grows, then fading as the GUI comes out. */
    private void galaxy(GuiGraphics g, int cx, int cy, long t) {
        float grow = Math.min(1.0F, t / 700.0F);
        float scale = 0.12F + 1.9F * (1.0F - (1.0F - grow) * (1.0F - grow) * (1.0F - grow));
        float alpha = Math.min(1.0F, t / 180.0F) * (t > 850L ? Math.max(0.0F, 1.0F - (t - 850L) / 650.0F) : 1.0F);
        if (alpha <= 0.0F) return;
        // the turn slows down: fast at first, a frame every 40 ms, then every 90
        int frame = (int) ((t < 600L ? t / 40L : 15L + (t - 600L) / 90L) % GALAXY_FRAMES);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(GALAXY, -GALAXY_SIZE / 2, -GALAXY_SIZE / 2, frame % 4 * GALAXY_SIZE, frame / 4 * GALAXY_SIZE,
                GALAXY_SIZE, GALAXY_SIZE, GALAXY_SIZE * 4, GALAXY_SIZE * 4);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.pose().popPose();
    }

    /** Stars twinkling round the galaxy, flying out with it as it grows and going round with it. */
    private void galaxyStars(GuiGraphics g, int cx, int cy, long t) {
        float grow = Math.min(1.0F, t / 900.0F);
        float spread = 1.0F - (1.0F - grow) * (1.0F - grow);
        float fade = t > 1000L ? 1.0F - (t - 1000L) / (float) (OPEN_MS - 1000L) : Math.min(1.0F, t / 150.0F);
        for (int k = 0; k < 34; k++) {
            long h = Tentacles.hash(k * 2654435761L + 17L);
            double r = (18 + (h % 110)) * spread;
            double a = (h >> 8) % 628 / 100.0D + t / 900.0D * (1.4D - r / 140.0D);
            int x = cx + (int) Math.round(Math.cos(a) * r * 1.25D), y = cy + (int) Math.round(Math.sin(a) * r * 0.8D);
            float tw = 0.5F + 0.5F * Mth.sin(t / (90.0F + h % 60) + k);
            Tentacles.star(g, x, y, (int) ((h >> 20) % 3), 1 + (int) ((h >> 24) % 2), (int) ((t / 110L + k) % 8), fade * (0.35F + 0.65F * tw));
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        tentacles.draw(g, leftPos, topPos);
        int frame = (int) (Util.getMillis() / 110L % VORTEX_FRAMES);
        g.blit(VORTEX, leftPos + VORTEX_X, topPos + VORTEX_Y, 0, frame * VORTEX_H, VORTEX_W, VORTEX_H, VORTEX_W, VORTEX_H * VORTEX_FRAMES);
        enderParticles(g);
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        glint(g);
        tentacles.cracks(g, leftPos, topPos);
        twinkles(g);
        Banner.draw(g, leftPos + imageWidth / 2, topPos - Banner.HEIGHT);
        // the title on the grey plate, in the plate's own little letters
        String title = getTitle().getString();
        PlateFont.draw(g, title, leftPos + (TITLE_X1 + TITLE_X2 + 1) / 2 - PlateFont.width(title) / 2, topPos + TITLE_Y, 0xFF696969);
        g.drawString(font, Component.translatable("gui.goldenquarry.vacuum.filter"), leftPos + FILTER_LABEL_X, topPos + LABEL_Y, 0xFFFFFF, false);
        Component range = Component.translatable("gui.goldenquarry.vacuum.range");
        g.drawString(font, range, leftPos + RANGE_LABEL_END - font.width(range), topPos + LABEL_Y, 0xFFFFFF, false);
        String n = String.valueOf(menu.range());
        g.drawString(font, n, leftPos + RANGE_END - font.width(n), topPos + RANGE_Y, 0xFFFFFF, false);
        // the brown slot shows which list the filter is
        g.renderItem(new ItemStack(menu.blacklist() ? Items.BARRIER : Items.PAPER), leftPos + VacuumChestMenu.MODE_X, topPos + VacuumChestMenu.FILTER_Y);
        for (int[] b : BUTTONS) {
            int x = leftPos + b[0], y = topPos + b[1];
            if (b[4] == VacuumChestMenu.BUTTON_AREA && menu.showArea()) {
                g.renderOutline(x - 1, y - 1, b[2] + 2, b[3] + 2, 0xFFFFE08A);
            }
            if (inside(b, mouseX, mouseY)) g.fill(x, y, x + b[2], y + b[3], 0x40FFFFFF);
        }
    }

    /** A light running down over the frame's steel and gold, once in a while (the first as soon as it has opened). */
    private void glint(GuiGraphics g) {
        long t = (Util.getMillis() - openedAt - OPEN_MS + 200L) % GLINT_EVERY;
        if (t < 0L || t >= GLINT_MS) return;
        int frame = (int) (t * GLINT_FRAMES / GLINT_MS);
        RenderSystem.enableBlend();
        g.blit(GLINT, leftPos, topPos, 0, frame * GLINT_H, imageWidth, GLINT_H, imageWidth, GLINT_H * GLINT_FRAMES);
    }

    /** The crystals and knobs on the frame light up by turns, each a star flaring and going out. */
    private void twinkles(GuiGraphics g) {
        long now = Util.getMillis();
        for (int i = 0; i < TWINKLES.length; i++) {
            int[] w = TWINKLES[i];
            long period = 2300L + i * 370L;
            float p = ((now + i * 811L) % period) / (float) period;
            if (p > 0.3F) continue;
            float a = Mth.sin(p / 0.3F * Mth.PI);
            Tentacles.star(g, leftPos + w[0], topPos + w[1], w[2], w[3], (int) (p / 0.3F * 7.99F), a);
        }
    }

    /** Ender sparks spiralling in to the vortex's middle and going out there (drawn under the slots). */
    private void enderParticles(GuiGraphics g) {
        double t = Util.getMillis() / 50.0D;
        double cx = VORTEX_X + VORTEX_W / 2.0D, cy = 80.0D;
        for (int i = 0; i < 46; i++) {
            long h = i * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
            h ^= h >>> 29;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 32;
            int period = 60 + (int) ((h >>> 8) % 50);
            double p = ((t + (h >>> 16) % 1000) % period) / period;          // 0 far out .. 1 in the middle
            double a0 = ((h >>> 24) % 6283) / 1000.0D;
            double r = 1.0D - p;
            double a = a0 + p * 2.4D;
            int x = (int) Math.round(cx + Math.cos(a) * r * 94.0D);
            int y = (int) Math.round(cy + Math.sin(a) * r * 58.0D);
            if (x < VORTEX_X || x >= VORTEX_X + VORTEX_W || y < VORTEX_Y || y > 140) continue;
            int alpha = (int) (Mth.sin((float) (p * Math.PI)) * 235.0F);
            if (alpha < 12) continue;
            int colour = alpha << 24 | PARTICLE_COLOURS[(int) ((h >>> 4) % PARTICLE_COLOURS.length)];
            int ax = leftPos + x, ay = topPos + y;
            g.fill(ax, ay, ax + 1, ay + 1, colour);
            if ((h & 3) == 0 && p > 0.2D && p < 0.7D) {      // some are little crosses
                int dim = (alpha / 2) << 24 | (colour & 0xFFFFFF);
                g.fill(ax - 1, ay, ax, ay + 1, dim);
                g.fill(ax + 1, ay, ax + 2, ay + 1, dim);
                g.fill(ax, ay - 1, ax + 1, ay, dim);
                g.fill(ax, ay + 1, ax + 1, ay + 2, dim);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // everything is written in renderBg, where the reference has it
    }

    private boolean inside(int[] b, double mx, double my) {
        double x = mx - leftPos, y = my - topPos;
        return x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Util.getMillis() - openedAt < OPEN_MS) return true;           // still opening
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            for (int[] b : BUTTONS) {
                if (inside(b, mouseX, mouseY)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, b[4]);
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void ownTooltips(GuiGraphics g, int mx, int my) {
        List<Component> tip = new ArrayList<>();
        int r = menu.range(), side = r * 2 + 1;
        for (int[] b : BUTTONS) {
            if (!inside(b, mx, my)) continue;
            switch (b[4]) {
                case VacuumChestMenu.BUTTON_PLUS -> tip.add(Component.translatable("gui.goldenquarry.vacuum.plus"));
                case VacuumChestMenu.BUTTON_MINUS -> tip.add(Component.translatable("gui.goldenquarry.vacuum.minus"));
                case VacuumChestMenu.BUTTON_AREA -> {
                    tip.add(Component.translatable("gui.goldenquarry.vacuum.area"));
                    tip.add(Component.translatable(menu.showArea() ? "gui.goldenquarry.vacuum.area.on" : "gui.goldenquarry.vacuum.area.off")
                            .withStyle(menu.showArea() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                }
                default -> {
                    tip.add(Component.translatable(menu.blacklist() ? "gui.goldenquarry.vacuum.blacklist" : "gui.goldenquarry.vacuum.whitelist"));
                    tip.add(Component.translatable(menu.blacklist() ? "gui.goldenquarry.vacuum.blacklist.tip" : "gui.goldenquarry.vacuum.whitelist.tip")
                            .withStyle(ChatFormatting.GRAY));
                    tip.add(Component.translatable("gui.goldenquarry.vacuum.switch").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        int lx = mx - leftPos, ly = my - topPos;
        if (tip.isEmpty() && lx >= RANGE_END - 12 && lx < RANGE_END && ly >= RANGE_Y - 1 && ly < RANGE_Y + 9) {
            tip.add(Component.translatable("gui.goldenquarry.vacuum.range_value", r, side, side, side));
        }
        if (tip.isEmpty() && hoveredSlot != null && VacuumChestMenu.isFilterSlot(hoveredSlot.index)) {
            tip.add(Component.translatable("gui.goldenquarry.vacuum.filter_slot"));
            tip.add(Component.translatable("gui.goldenquarry.vacuum.filter_slot.tip").withStyle(ChatFormatting.GRAY));
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }
}
