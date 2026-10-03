package com.reya.starfall.client;

import com.reya.starfall.Skill;
import com.reya.starfall.StellarRemoteItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Next to the hotbar while the remote is in hand: the armed weapon, a live preview of it, and its key. */
final class RemoteHud {
    static void render(GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || Film.active()) return;
        ItemStack remote = RemoteItems.held(mc.player);
        if (remote.isEmpty()) return;
        Skill skill = StellarRemoteItem.skill(remote);
        Font font = mc.font;
        int color = 0xFF000000 | skill.color;
        int w = 64, h = 32;
        int x = width / 2 + 100, y = height - h - 3;
        Component title = Component.translatable("hud.starfall.armed", skill.tag(), skill.title());
        g.drawString(font, title, x, y - 11, color, true);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        Canvas c = new Canvas(g.pose().last().pose(), width, height);
        float time = (mc.level == null ? 0 : mc.level.getGameTime() % 24000L) + partial;
        Previews.draw(c, skill, time / 20.0F, x, y, w, h);
        c.finish();
        g.disableScissor();
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, color);
        // the key that arms this weapon, in the corner
        Component key = Keys.SKILLS[skill.ordinal()].getTranslatedKeyMessage();
        int kw = Math.max(9, font.width(key) + 4);
        g.fill(x + w - kw + 1, y + h - 9, x + w + 1, y + h + 1, color);
        g.drawString(font, key, x + w - kw + 3, y + h - 8, 0xFF101014, false);
        Component menu = Component.translatable("hud.starfall.menu", Keys.MENU.getTranslatedKeyMessage());
        g.pose().pushPose();
        g.pose().translate(x + w + 5, y + h - 7, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, menu, 0, 0, 0xFFD0D0DC, true);
        g.pose().popPose();
    }

    private RemoteHud() {
    }
}
