package com.reya.starfall.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.starfall.Skill;
import com.reya.starfall.Starfall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The strikes' pixel-art pictures (see tools/gen_icons.py), 64 by 32: a base layer, and a glow layer drawn over
 * it that breathes, so the beam, the stars and the needle's trail shimmer.
 */
final class Icons {
    static final int W = 64, H = 32;
    private static final ResourceLocation[] BASE = new ResourceLocation[Skill.values().length];
    private static final ResourceLocation[] GLOW = new ResourceLocation[Skill.values().length];

    static {
        for (Skill s : Skill.values()) {
            BASE[s.ordinal()] = new ResourceLocation(Starfall.MODID, "textures/gui/skill/" + s.id + ".png");
            GLOW[s.ordinal()] = new ResourceLocation(Starfall.MODID, "textures/gui/skill/" + s.id + "_glow.png");
        }
    }

    /** Draws the picture of {@code skill} at (x, y), {@code scale} screen pixels to a picture pixel. */
    static void draw(GuiGraphics g, Skill skill, int x, int y, int scale, float time) {
        int w = W * scale, h = H * scale;
        g.blit(BASE[skill.ordinal()], x, y, w, h, 0.0F, 0.0F, W, H, W, H);
        float breathe = 0.45F + 0.55F * (0.5F + 0.5F * Mth.sin(time * 3.2F + skill.ordinal() * 1.7F));
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, breathe);
        g.blit(GLOW[skill.ordinal()], x, y, w, h, 0.0F, 0.0F, W, H, W, H);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** A picture in a frame, with the strike's key in a badge at its bottom-right corner unless badge is 0. */
    static void framed(GuiGraphics g, Skill skill, int x, int y, int scale, float time, int frame, int badge) {
        int w = W * scale, h = H * scale;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF1A0A10);
        g.renderOutline(x - 2, y - 2, w + 4, h + 4, frame);
        draw(g, skill, x, y, scale, time);
        if (badge != 0) badge(g, skill, x + w + 1, y + h + 1, badge);
    }

    /** The key badge: a small box ending at (right, bottom) with the key's name in it. */
    static void badge(GuiGraphics g, Skill skill, int right, int bottom, int frame) {
        Font font = Minecraft.getInstance().font;
        String name = Keys.SKILLS[skill.ordinal()].getTranslatedKeyMessage().getString();
        if (name.length() > 3) name = name.substring(0, 3);
        int w = Math.max(9, font.width(name) + 4), h = 10;
        g.fill(right - w, bottom - h, right, bottom, 0xFF120810);
        g.renderOutline(right - w, bottom - h, w, h, frame);
        g.drawString(font, name, right - w + (w - font.width(name) + 1) / 2, bottom - h + 1, frame, false);
    }

    private Icons() {
    }
}
