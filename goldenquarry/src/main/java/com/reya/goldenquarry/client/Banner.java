package com.reya.goldenquarry.client;

import com.reya.goldenquarry.GoldenQuarry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's name plate over its GUIs: LoliUtility's plate, pixel for pixel from the reference, with
 * "GOLDQUARRY" in its letters (quarry_widgets.png at 0,16, 82x12, made by tools/extract_gui.py).
 */
public final class Banner {
    private static final ResourceLocation WIDGETS = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/quarry_widgets.png");
    public static final int WIDTH = 82, HEIGHT = 12;

    /** Draws the plate centred on centerX, its top row at y. */
    public static void draw(GuiGraphics g, int centerX, int y) {
        g.blit(WIDGETS, centerX - WIDTH / 2, y, 0, 16, WIDTH, HEIGHT, 256, 64);
    }

    private Banner() {
    }
}
