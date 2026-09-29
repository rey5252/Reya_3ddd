package com.reya.quantumsolar.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.quantumsolar.Format;
import com.reya.quantumsolar.PanelBlockEntity;
import com.reya.quantumsolar.PanelMenu;
import com.reya.quantumsolar.Panels;
import com.reya.quantumsolar.QuantumSolar;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A panel's GUI (textures/gui/panel.png, by tools/gui.py): the block itself turning slowly in its
 * box over its own top, its name, what it makes now and at most, why, a bar of the energy it holds
 * in its colour with a light running along it, and a row of lamps lit as far as it is making.
 */
public class PanelScreen extends AbstractContainerScreen<PanelMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(QuantumSolar.MODID, "textures/gui/panel.png");
    private static final int W = 196, H = 108;
    private static final int BOX_X = 10, BOX_Y = 22, BOX = 36;
    private static final int BAR_X = 12, BAR_Y = 66, BAR_W = 172, BAR_H = 10;
    private static final int LAMPS = 15, LAMP_X = 16, LAMP_Y = 86, LAMP_STEP = 11;
    private static final int TEXT_X = 52;

    private final Panels.Panel info;
    private final ItemStack icon;
    private final ResourceLocation top;

    public PanelScreen(PanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        info = menu.info();
        int index = java.util.Arrays.asList(Panels.ALL).indexOf(info);
        icon = new ItemStack(QuantumSolar.PANEL_ITEMS.get(Math.max(0, index)).get());
        top = new ResourceLocation(QuantumSolar.MODID, "textures/block/" + info.id() + "_top.png");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= BAR_X - 2 && x < BAR_X + BAR_W + 2 && y >= BAR_Y - 2 && y < BAR_Y + BAR_H + 2) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.quantumsolar.stored", String.format("%,d", menu.energy()), String.format("%,d", info.capacity())),
                    Component.translatable("gui.quantumsolar.output", Format.energy(info.output())).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        long now = Util.getMillis();
        float r = (info.colour() >> 16 & 255) / 255.0F, gr = (info.colour() >> 8 & 255) / 255.0F, b = (info.colour() & 255) / 255.0F;
        g.blit(TEXTURE, leftPos, topPos, 0, 0, W, H);
        boolean working = menu.making() > 0;

        // the block's own top, dimmed, behind it in its box; the box glows in its colour while it works
        RenderSystem.setShaderColor(0.35F, 0.35F, 0.35F, 1.0F);
        g.blit(top, leftPos + BOX_X + 2, topPos + BOX_Y + 2, 0, 0, BOX - 4, BOX - 4, BOX - 4, BOX - 4);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (working) {
            float pulse = 0.55F + 0.45F * Mth.sin(now / 400.0F);
            int glow = (int) (pulse * 200) << 24 | info.colour();
            g.renderOutline(leftPos + BOX_X - 1, topPos + BOX_Y - 1, BOX + 2, BOX + 2, glow);
        }
        g.pose().pushPose();
        g.pose().translate(leftPos + BOX_X + BOX / 2.0F - 16.0F, topPos + BOX_Y + BOX / 2.0F - 16.0F, 0.0F);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();

        // the name on the title strip, smaller if it is long
        Component name = getTitle();
        int nw = font.width(name);
        float s = Math.min(1.0F, (W - 30) / (float) nw);
        g.pose().pushPose();
        g.pose().translate(leftPos + W / 2.0F - nw * s / 2.0F, topPos + 8 + (1 - s) * 4, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, name, 0, 0, 0xFFFFFF, true);
        g.pose().popPose();

        // what it makes, at most, and why
        g.drawString(font, Component.translatable("gui.quantumsolar.making", Format.energy(menu.making())),
                leftPos + TEXT_X, topPos + 24, working ? 0x7CFF8A : 0x9A9AA6, false);
        g.drawString(font, Component.translatable("gui.quantumsolar.max", Format.energy(info.generation())),
                leftPos + TEXT_X, topPos + 35, 0xC8C8D2, false);
        int status = menu.status();
        int iconIndex = info.quantum() ? 5 : switch (status) {
            case PanelBlockEntity.STATUS_NIGHT -> 1;
            case PanelBlockEntity.STATUS_RAIN -> 2;
            case PanelBlockEntity.STATUS_NO_SKY -> 3;
            case PanelBlockEntity.STATUS_FULL -> 4;
            default -> 0;
        };
        if (info.quantum() && status == PanelBlockEntity.STATUS_FULL) iconIndex = 4;
        g.blit(TEXTURE, leftPos + TEXT_X, topPos + 45, iconIndex * 12, 132, 12, 12);
        String key = switch (status) {
            case PanelBlockEntity.STATUS_NIGHT -> "night";
            case PanelBlockEntity.STATUS_RAIN -> "rain";
            case PanelBlockEntity.STATUS_NO_SKY -> "no_sky";
            case PanelBlockEntity.STATUS_FULL -> "full";
            default -> info.quantum() ? "quantum" : "sun";
        };
        g.drawString(font, Component.translatable("gui.quantumsolar.status." + key), leftPos + TEXT_X + 15, topPos + 47,
                status == PanelBlockEntity.STATUS_WORKING ? 0xFFE27A : status == PanelBlockEntity.STATUS_FULL ? 0x7CFF8A : 0xFF8A7A, false);

        // the energy bar in the block's colour, a light running along it
        int filled = info.capacity() <= 0 ? 0 : (int) ((long) menu.energy() * BAR_W / info.capacity());
        if (filled > 0) {
            RenderSystem.setShaderColor(r, gr, b, 1.0F);
            g.blit(TEXTURE, leftPos + BAR_X, topPos + BAR_Y, 0, 112, filled, BAR_H);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int shine = (int) (now / 12L % (BAR_W + 40)) - 20;
            for (int i = 0; i < 12; i++) {
                int sx = shine + i;
                if (sx < 0 || sx >= filled) continue;
                int a = (int) (Mth.sin(i / 11.0F * Mth.PI) * 110);
                g.fill(leftPos + BAR_X + sx, topPos + BAR_Y, leftPos + BAR_X + sx + 1, topPos + BAR_Y + BAR_H, a << 24 | 0xFFFFFF);
            }
        }
        String amount = Format.energy(menu.energy()) + " / " + Format.energy(info.capacity()) + " FE";
        g.drawString(font, amount, leftPos + BAR_X + BAR_W / 2 - font.width(amount) / 2, topPos + BAR_Y + 1, 0xFFFFFF, true);

        // the lamps: as many lit as it is making of its most, a pulse running along the lit ones
        int lit = info.generation() <= 0 ? 0 : (int) Math.ceil(menu.making() * (double) LAMPS / info.generation());
        int pulse = (int) (now / 90L % (LAMPS + 6));
        for (int i = 0; i < LAMPS; i++) {
            int lx = leftPos + LAMP_X + i * LAMP_STEP, ly = topPos + LAMP_Y;
            if (i < lit) {
                float k = i == pulse ? 1.0F : Math.abs(i - pulse) == 1 ? 0.85F : 0.7F;
                RenderSystem.setShaderColor(Math.min(1.0F, r * k + (k - 0.7F)), Math.min(1.0F, gr * k + (k - 0.7F)), Math.min(1.0F, b * k + (k - 0.7F)), 1.0F);
                g.blit(TEXTURE, lx, ly, 12, 124, 10, 6);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
                g.blit(TEXTURE, lx, ly, 0, 124, 10, 6);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // all drawn in renderBg
    }
}
