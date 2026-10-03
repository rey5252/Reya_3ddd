package com.reya.starfall.client;

import com.reya.starfall.Skill;
import com.reya.starfall.StellarRemoteItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Next to the hotbar while the remote is in hand: the armed strike's name, its picture, and its key. */
final class RemoteHud {
    static void render(GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || Film.active()) return;
        ItemStack remote = RemoteItems.held(mc.player);
        if (remote.isEmpty()) return;
        Skill skill = StellarRemoteItem.skill(remote);
        Font font = mc.font;
        int color = 0xFF000000 | skill.color;
        int dim = 0xFF000000 | Fx.mix(0xFF000000 | skill.color, 0xFF000000, 0.6F);
        int w = Icons.W, h = Icons.H;
        int x = width / 2 + 101, y = height - h - 5;
        float time = ((mc.level == null ? 0 : mc.level.getGameTime() % 24000L) + partial) / 20.0F;
        // the strike's name on a dark bar over its picture
        Component title = Component.translatable("hud.starfall.armed", skill.tag(), skill.title());
        int tw = Math.max(w + 4, font.width(title) + 8);
        g.fill(x - 2, y - 14, x - 2 + tw, y - 2, 0xE0120A10);
        g.renderOutline(x - 2, y - 14, tw, 13, dim);
        g.drawString(font, title, x + 2, y - 11, color, false);
        Icons.framed(g, skill, x, y, 1, time, color, color);
        Component menu = Component.translatable("hud.starfall.menu", Keys.MENU.getTranslatedKeyMessage());
        g.pose().pushPose();
        g.pose().translate(x + w + 6, y + h - 7, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, menu, 0, 0, 0xFFD0D0DC, true);
        g.pose().popPose();
    }

    private RemoteHud() {
    }
}
