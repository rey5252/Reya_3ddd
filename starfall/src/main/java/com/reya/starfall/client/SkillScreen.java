package com.reya.starfall.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.reya.starfall.Skill;
import com.reya.starfall.Sounds;
import com.reya.starfall.StellarRemoteItem;
import com.reya.starfall.network.Net;
import com.reya.starfall.network.SelectSkillPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The strike selection menu (H): the world turns white and the three weapons float up as cards with a live
 * preview each. Left click (or 1/2/3, or the arrows and Enter) arms one; right click opens its details, where
 * its key can be changed.
 */
public final class SkillScreen extends Screen {
    private static final int GAP = 12;
    private final Skill armed;
    private int hovered = -1;
    private int focus;
    private int details = -1;
    private boolean binding;
    private int age;
    private int closing = -1;
    private int chosen = -1;
    // layout, worked out each frame
    private int cardW, cardH, top, left;
    private final int[][] buttons = new int[3][4];

    public SkillScreen() {
        super(Component.translatable("menu.starfall.title"));
        Minecraft mc = Minecraft.getInstance();
        ItemStack remote = mc.player == null ? ItemStack.EMPTY : RemoteItems.held(mc.player);
        armed = remote.isEmpty() ? Skill.RAILGUN : StellarRemoteItem.skill(remote);
        focus = armed.ordinal();
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (RemoteItems.held(mc.player).isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("message.starfall.hold_remote"), true);
            return;
        }
        mc.setScreen(new SkillScreen());
        mc.getSoundManager().play(SimpleSoundInstance.forUI(Sounds.FILM_UPLINK.get(), 1.4F, 0.35F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        if (closing > 0 && --closing == 0) onClose();
    }

    private void layout() {
        cardW = Math.min(122, (width - 24 - 2 * GAP) / 3);
        cardH = Math.min(184, height - 74);
        left = (width - (cardW * 3 + GAP * 2)) / 2;
        top = Math.max(36, (height - cardH) / 2 + 6);
    }

    private int cardX(int i) {
        return left + i * (cardW + GAP);
    }

    private int hit(double mx, double my) {
        for (int i = 0; i < 3; i++) {
            int x = cardX(i);
            if (mx >= x && mx < x + cardW && my >= top && my < top + cardH) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        layout();
        float time = (age + partial) / 20.0F;
        float open = RemoteAnimation.smooth((age + partial) / 7.0F);
        // the world turns white
        g.fillGradient(0, 0, width, height, Fx.argb(0xF7F5FB, 0.90F * open), Fx.argb(0xE8E4F2, 0.94F * open));
        for (int i = 0; i < 40; i++) {
            float px = (float) ((i * 97 % width) + Math.sin(time * 0.4F + i) * 12.0F);
            float py = (float) ((i * 61 % height) + time * 6.0F % height);
            g.fill((int) px, (int) py % height, (int) px + 1, (int) py % height + 1, Fx.argb(0x8C86A8, 0.35F * open));
        }
        int titleY = top - 26;
        g.drawCenteredString(font, Component.translatable("menu.starfall.title").withStyle(s -> s.withBold(true)), width / 2, titleY, 0xFF2A2638);
        Component sub = Component.translatable("menu.starfall.subtitle");
        g.drawString(font, sub, (width - font.width(sub)) / 2, titleY + 11, 0xFF6E6886, false);

        hovered = details < 0 ? hit(mouseX, mouseY) : -1;
        for (int i = 0; i < 3; i++) card(g, i, time, open, partial);

        Component hint = Component.translatable("menu.starfall.hint", Keys.MENU.getTranslatedKeyMessage());
        g.drawString(font, hint, (width - font.width(hint)) / 2, Math.min(height - 12, top + cardH + 10), 0xFF6E6886, false);
        if (details >= 0) details(g, mouseX, mouseY, time);
    }

    private void card(GuiGraphics g, int i, float time, float open, float partial) {
        Skill skill = Skill.byIndex(i);
        int color = 0xFF000000 | skill.color;
        boolean hot = i == hovered || (hovered < 0 && i == focus && details < 0);
        boolean isArmed = skill == armed;
        float rise = (1.0F - RemoteAnimation.smooth((age + partial - i * 2.0F) / 9.0F)) * 40.0F;
        int bob = Math.round(Mth.sin(time * 1.6F + i * 1.3F) * 2.0F);
        int x = cardX(i), y = top + Math.round(rise) + bob - (hot ? 4 : 0);
        int w = cardW, h = cardH;
        boolean flash = chosen == i && closing > 0;
        // shadow and body
        g.fill(x + 3, y + 5, x + w + 3, y + h + 5, Fx.argb(0x1A1430, 0.22F * open));
        g.fill(x, y, x + w, y + h, Fx.argb(flash ? Fx.mix(0xFF121018, color, 0.45F) & 0xFFFFFF : 0x121018, 0.96F * open));
        int edge = hot || isArmed ? color : Fx.argb(skill.color, 0.55F);
        g.renderOutline(x, y, w, h, edge);
        if (hot) g.renderOutline(x - 1, y - 1, w + 2, h + 2, Fx.argb(skill.color, 0.45F));
        brackets(g, x - 3, y - 3, w + 6, h + 6, hot ? color : Fx.argb(skill.color, 0.4F));
        // header
        g.drawString(font, skill.tag(), x + 8, y + 7, color, false);
        g.pose().pushPose();
        g.pose().translate(x + 8, y + 18, 0);
        g.pose().scale(1.25F, 1.25F, 1.0F);
        g.drawString(font, skill.title(), 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
        // live preview
        int px0 = x + 6, py0 = y + 34, px1 = x + w - 6, py1 = y + 34 + Math.min(76, (h - 34) / 2);
        g.flush();
        g.enableScissor(px0, py0, px1, py1);
        Canvas c = new Canvas(g.pose().last().pose(), width, height);
        Previews.draw(c, skill, time + i * 0.7F, px0, py0, px1 - px0, py1 - py0);
        c.finish();
        g.disableScissor();
        g.renderOutline(px0, py0, px1 - px0, py1 - py0, Fx.argb(skill.color, 0.6F));
        // what it does
        List<FormattedCharSequence> lines = font.split(Component.translatable("tooltip.starfall.skill." + skill.id), w - 14);
        int ly = py1 + 6;
        for (FormattedCharSequence line : lines) {
            if (ly > y + h - 30) break;
            g.drawString(font, line, x + 7, ly, 0xFFB8B4C8, false);
            ly += 10;
        }
        // key and state at the bottom
        Component key = Keys.SKILLS[i].getTranslatedKeyMessage();
        int kw = font.width(key) + 8;
        g.fill(x + 7, y + h - 18, x + 7 + kw, y + h - 6, Fx.argb(skill.color, 0.25F));
        g.renderOutline(x + 7, y + h - 18, kw, 12, color);
        g.drawString(font, key, x + 11, y + h - 16, 0xFFFFFFFF, false);
        if (isArmed) {
            Component armedText = Component.translatable("menu.starfall.selected");
            int aw = font.width(armedText) + 8;
            g.fill(x + w - 7 - aw, y + h - 18, x + w - 7, y + h - 6, color);
            g.drawString(font, armedText, x + w - 3 - aw, y + h - 16, 0xFF101014, false);
        } else if (hot) {
            Component pick = Component.translatable("menu.starfall.pick");
            g.drawString(font, pick, x + w - 7 - font.width(pick), y + h - 16, 0xFFE0DCF0, false);
        }
    }

    /** The corner brackets the films use, around a box. */
    static void brackets(GuiGraphics g, int x, int y, int w, int h, int color) {
        int l = Math.min(10, Math.min(w, h) / 4);
        g.fill(x, y, x + l, y + 1, color);
        g.fill(x, y, x + 1, y + l, color);
        g.fill(x + w - l, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + l, color);
        g.fill(x, y + h - 1, x + l, y + h, color);
        g.fill(x, y + h - l, x + 1, y + h, color);
        g.fill(x + w - l, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - l, x + w, y + h, color);
    }

    private void details(GuiGraphics g, int mouseX, int mouseY, float time) {
        Skill skill = Skill.byIndex(details);
        int color = 0xFF000000 | skill.color;
        g.fill(0, 0, width, height, Fx.argb(0xF4F2FA, 0.7F));
        int w = Math.min(300, width - 30);
        List<FormattedCharSequence> lines = font.split(Component.translatable("menu.starfall.details." + skill.id), w - 24);
        int h = 52 + lines.size() * 10 + 30;
        int x = (width - w) / 2, y = (height - h) / 2;
        g.fill(x, y, x + w, y + h, 0xF6121018);
        g.renderOutline(x, y, w, h, color);
        brackets(g, x - 4, y - 4, w + 8, h + 8, color);
        g.drawString(font, skill.tag(), x + 12, y + 10, color, false);
        g.drawString(font, skill.title().copy().withStyle(s -> s.withBold(true)), x + 12, y + 22, 0xFFFFFFFF, false);
        int ly = y + 40;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 12, ly, 0xFFD0CCE0, false);
            ly += 10;
        }
        Component[] labels = {
                Component.translatable("menu.starfall.choose"),
                binding ? Component.translatable("menu.starfall.press_key")
                        : Component.translatable("menu.starfall.change_key", Keys.SKILLS[details].getTranslatedKeyMessage()),
                Component.translatable("menu.starfall.back")};
        int bx = x + 12, by = y + h - 24;
        for (int b = 0; b < 3; b++) {
            int bw = font.width(labels[b]) + 14;
            boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 16;
            boolean primary = b == 0 || (b == 1 && binding);
            int fill = primary ? (over ? Fx.mix(color, 0xFFFFFFFF, 0.25F) : color) : over ? 0xFF2E2A3C : 0xFF1E1B28;
            if (b == 1 && binding && ((int) (time * 3)) % 2 == 0) fill = Fx.mix(fill, 0xFFFFE070, 0.4F);
            g.fill(bx, by, bx + bw, by + 16, fill);
            g.renderOutline(bx, by, bw, 16, color);
            g.drawString(font, labels[b], bx + 7, by + 4, primary ? 0xFF101014 : 0xFFE8E4F4, false);
            buttons[b] = new int[]{bx, by, bw, 16};
            bx += bw + 6;
        }
    }

    // ------------------------------------------------------------------ input

    private void choose(int i) {
        if (closing > 0) return;
        Net.CHANNEL.sendToServer(new SelectSkillPacket(i));
        chosen = i;
        closing = 5;
        details = -1;
        binding = false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sounds.UI_SELECT.get(), 1.0F, 0.6F));
    }

    private void openDetails(int i) {
        details = i;
        binding = false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sounds.UI_HOVER.get(), 1.0F, 0.6F));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (details >= 0) {
            for (int b = 0; b < 3; b++) {
                int[] r = buttons[b];
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    if (b == 0) choose(details);
                    else if (b == 1) binding = !binding;
                    else details = -1;
                    return true;
                }
            }
            if (!binding) details = -1;
            return true;
        }
        int i = hit(mx, my);
        if (i < 0) return super.mouseClicked(mx, my, button);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openDetails(i);
        else choose(i);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (details < 0) focus = Math.floorMod(focus + (delta < 0 ? 1 : -1), 3);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (binding && details >= 0) {
            binding = false;
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                KeyMapping mapping = Keys.SKILLS[details];
                mapping.setKey(InputConstants.getKey(key, scancode));
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sounds.UI_SELECT.get(), 1.25F, 0.7F));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && details >= 0) {
            details = -1;
            return true;
        }
        if (Keys.MENU.matches(key, scancode)) {
            onClose();
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_3) {
            choose(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (details < 0) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
                focus = Math.floorMod(focus - 1, 3);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
                focus = Math.floorMod(focus + 1, 3);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) {
                choose(focus);
                return true;
            }
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            choose(details);
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    /** For the showcase recorder: open the details of a card as if it was right-clicked. */
    public void showDetails(int i) {
        openDetails(i);
    }

    /** For the showcase recorder: highlight a card as if the mouse was over it. */
    public void highlight(int i) {
        focus = i;
    }
}
