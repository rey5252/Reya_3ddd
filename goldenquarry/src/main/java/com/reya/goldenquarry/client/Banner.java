package com.reya.goldenquarry.client;

import com.reya.goldenquarry.GoldenQuarry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's name plate over its GUIs, like LoliUtility's: a dark brown plate with a gold rim and
 * rolled scroll ends, a heart and the name, short. The plate's three pieces are in
 * quarry_widgets.png (tools/extract_gui.py): the left end with the heart, a middle column stretched
 * under the name, the right end.
 */
public final class Banner {
    private static final ResourceLocation WIDGETS = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/quarry_widgets.png");
    private static final Component NAME = Component.literal("GOLDQUARRY").withStyle(ChatFormatting.BOLD);
    private static final int[][] OUTLINE = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /** Draws the plate centred on centerX, its top row at y (the plate itself is 16 high). */
    public static void draw(GuiGraphics g, Font font, int centerX, int y) {
        int tw = font.width(NAME) + 2;
        int w = 23 + tw + 15;
        int x = centerX - w / 2;
        g.blit(WIDGETS, x, y, 0, 16, 23, 16, 256, 64);
        g.blit(WIDGETS, x + 23, y, tw, 16, 24, 16, 1, 16, 256, 64);
        g.blit(WIDGETS, x + 23 + tw, y, 26, 16, 15, 16, 256, 64);
        int tx = x + 24, ty = y + 4;
        for (int[] d : OUTLINE) g.drawString(font, NAME, tx + d[0], ty + d[1], 0x2A1408, false);
        g.drawString(font, NAME, tx, ty, 0xF8D890, false);
    }

    private Banner() {
    }
}
