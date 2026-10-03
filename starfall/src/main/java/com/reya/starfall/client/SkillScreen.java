package com.reya.starfall.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.reya.starfall.Config;
import com.reya.starfall.Skill;
import com.reya.starfall.Sounds;
import com.reya.starfall.Starfall;
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
 * The Stellar Remote's panel (H): a dark screen with the strikes as pictures on the left and the one under the
 * mouse (or picked with the keys) described on the right. Left click arms a strike, right click sets its key.
 */
public final class SkillScreen extends Screen {
    static final int RED = 0xFFE0394E, RED_DIM = 0xFF6A1E2C, RED_DARK = 0xFF2C0C16;
    static final int TEXT = 0xFFE8E4F0, GREY = 0xFF8E889C, GREEN = 0xFF72E496, AMBER = 0xFFE8B04A, ROSE = 0xFFB0405A;
    private static final int TILE_W = Icons.W + 4, TILE_H = Icons.H + 4, TILE_GAP = 8, LEFT = TILE_W * 2 + TILE_GAP;

    private Skill armed;
    private int hovered = -1;
    private int focus;
    /** The strike whose key is being set, or -1. */
    private int binding = -1;
    private int age;
    private int flash = -1, flashAt;
    private boolean mouseMoved;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;
    // layout, worked out each frame
    private int px, py, pw, ph, gridX, gridY;

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
    }

    private void layout() {
        pw = Math.min(344, width - 12);
        ph = Math.min(252, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int rows = (Skill.values().length + 1) / 2;
        int gridH = rows * TILE_H + (rows - 1) * TILE_GAP;
        gridX = px + 10;
        gridY = py + 34 + Math.max(0, (ph - 34 - 20 - gridH) / 2);
    }

    private int tileX(int i) {
        return gridX + (i % 2) * (TILE_W + TILE_GAP);
    }

    private int tileY(int i) {
        return gridY + (i / 2) * (TILE_H + TILE_GAP);
    }

    private int hit(double mx, double my) {
        for (int i = 0; i < Skill.values().length; i++) {
            int x = tileX(i), y = tileY(i);
            if (mx >= x && mx < x + TILE_W && my >= y && my < y + TILE_H) return i;
        }
        return -1;
    }

    /** The strike described on the right: the one under the mouse once it has moved, else the one picked. */
    private int shown() {
        return hovered >= 0 ? hovered : focus;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        layout();
        if (!Double.isNaN(lastMouseX) && (mouseX != lastMouseX || mouseY != lastMouseY)) mouseMoved = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hovered = mouseMoved ? hit(mouseX, mouseY) : -1;
        float time = (age + partial) / 20.0F;
        float open = RemoteAnimation.smooth((age + partial) / 6.0F);

        g.fillGradient(0, 0, width, height, Fx.argb(0x000000, 0.35F * open), Fx.argb(0x080206, 0.55F * open));
        // the panel opens like an old screen warming up: a line, then the whole picture
        int cy = py + ph / 2, half = Math.max(1, Math.round(ph / 2.0F * open));
        g.enableScissor(px - 2, cy - half, px + pw + 2, cy + half);
        panel(g, time);
        header(g, time);
        for (int i = 0; i < Skill.values().length; i++) tile(g, i, time);
        details(g, shown(), time, partial);
        footer(g);
        g.disableScissor();
        if (open < 1.0F) g.fill(px - 2, cy - half, px + pw + 2, cy - half + 1, Fx.argb(0xFFD0D8, 1.0F - open));
    }

    private void panel(GuiGraphics g, float time) {
        g.fillGradient(px, py, px + pw, py + ph, 0xF2120E18, 0xF20A080E);
        // faint scanlines
        for (int y = py + 1; y < py + ph; y += 3) g.fill(px + 1, y, px + pw - 1, y + 1, 0x0CFFFFFF);
        g.renderOutline(px, py, pw, ph, RED);
        g.renderOutline(px + 2, py + 2, pw - 4, ph - 4, RED_DARK);
        int[][] corners = {{px - 1, py - 1}, {px + pw - 2, py - 1}, {px - 1, py + ph - 2}, {px + pw - 2, py + ph - 2}};
        for (int[] c : corners) g.fill(c[0], c[1], c[0] + 3, c[1] + 3, 0xFFFF6A7A);
        // the column divider
        int dx = px + 10 + LEFT + 6;
        g.fill(dx, py + 32, dx + 1, py + ph - 18, RED_DARK);
    }

    private void header(GuiGraphics g, float time) {
        int ix = px + 8, iy = py + 7;
        g.fill(ix, iy, ix + 18, iy + 18, 0xFF0C0810);
        g.renderOutline(ix, iy, 18, 18, RED);
        // a little remote in the badge
        g.fill(ix + 6, iy + 3, ix + 12, iy + 16, 0xFF2A3658);
        g.fill(ix + 7, iy + 4, ix + 11, iy + 7, 0xFF06070C);
        g.fill(ix + 9, iy + 9, ix + 11, iy + 12, (age / 10) % 2 == 0 ? 0xFFFF3040 : 0xFFA01828);
        g.fill(ix + 7, iy + 1, ix + 8, iy + 3, 0xFF9AA0B0);
        // the title, its letters spread out
        String title = Component.translatable("menu.starfall.title").getString();
        int tx = ix + 26;
        for (int i = 0; i < title.length(); i++) {
            String c = String.valueOf(title.charAt(i));
            g.drawString(font, c, tx, py + 7, RED, false);
            tx += font.width(c) + 3;
        }
        // and under it a line of text running past
        int mx0 = ix + 26, mx1 = px + pw - 8;
        String sub = Component.translatable("menu.starfall.subtitle").getString() + "   ·   ";
        int sw = font.width(sub);
        int off = Math.round(time * 26.0F) % Math.max(1, sw);
        g.enableScissor(mx0, py + 17, mx1, py + 27);
        for (int x = mx0 - off; x < mx1; x += sw) g.drawString(font, sub, x, py + 18, 0xFFB8B2C4, false);
        g.disableScissor();
        // the rule under the header, with a mark in the middle
        g.fill(px + 4, py + 29, px + pw - 4, py + 30, RED_DIM);
        int mid = px + pw / 2;
        g.fill(mid - 2, py + 28, mid + 3, py + 31, RED);
    }

    private void tile(GuiGraphics g, int i, float time) {
        Skill skill = Skill.byIndex(i);
        int x = tileX(i) + 2, y = tileY(i) + 2;
        boolean hot = i == hovered || (hovered < 0 && i == focus);
        boolean isArmed = skill == armed;
        int frame = isArmed ? RED : hot ? 0xFFFFA8B4 : RED_DIM;
        if (isArmed || hot) g.renderOutline(x - 3, y - 3, Icons.W + 6, Icons.H + 6, Fx.argb(0xE0394E, hot ? 0.6F : 0.35F));
        Icons.framed(g, skill, x, y, 1, time + i * 0.4F, frame, RED);
        // a quick flash when it's armed
        if (flash == i) {
            float k = 1.0F - (age - flashAt) / 6.0F;
            if (k > 0.0F) g.fill(x, y, x + Icons.W, y + Icons.H, Fx.argb(0xFFE0E4, 0.6F * k));
        }
        if (binding == i && (age / 4) % 2 == 0) g.renderOutline(x - 2, y - 2, Icons.W + 4, Icons.H + 4, AMBER);
    }

    private void details(GuiGraphics g, int i, float time, float partial) {
        Skill skill = Skill.byIndex(i);
        int rx = px + 10 + LEFT + 14, rw = px + pw - 10 - rx;
        int ry = py + 36;
        Icons.framed(g, skill, rx + 2, ry + 2, 1, time, RED, 0);
        int tx = rx + Icons.W + 10, tw = rx + rw - tx;
        fitted(g, Component.translatable("menu.starfall.series"), tx, ry + 2, tw, 0.75F, ROSE);
        fitted(g, Component.translatable("menu.starfall.name", skill.tag(), skill.title()), tx, ry + 11, tw, 1.0F, RED);
        Component state = skill == armed ? Component.translatable("menu.starfall.selected")
                : Component.translatable("menu.starfall.pick");
        fitted(g, state, tx, ry + 24, tw, 0.75F, skill == armed ? GREEN : GREY);

        int infoY = py + ph - 20 - 34;
        List<FormattedCharSequence> lines = font.split(Component.translatable("menu.starfall.details." + skill.id), rw);
        int ly = ry + Icons.H + 10;
        for (FormattedCharSequence line : lines) {
            if (ly + 9 > infoY - 2) break;
            g.drawString(font, line, rx, ly, TEXT, false);
            ly += 9;
        }
        g.fill(rx, infoY, rx + rw, infoY + 1, RED_DARK);
        // the remote's charge, its key, and whatever else that key does
        g.drawString(font, Component.translatable("menu.starfall.recharge", Config.COOLDOWN.get()), rx, infoY + 4, GREY, false);
        Minecraft mc = Minecraft.getInstance();
        float cooling = mc.player == null ? 0.0F : mc.player.getCooldowns().getCooldownPercent(Starfall.STELLAR_REMOTE.get(), partial);
        Component ready = cooling > 0.0F ? Component.translatable("menu.starfall.recharging", Math.round((1.0F - cooling) * 100.0F))
                : Component.translatable("menu.starfall.ready");
        g.drawString(font, ready, rx + rw - font.width(ready), infoY + 4, cooling > 0.0F ? AMBER : GREEN, false);
        KeyMapping key = Keys.SKILLS[i];
        if (binding == i) {
            if ((age / 6) % 2 == 0) fitted(g, Component.translatable("menu.starfall.press_key"), rx, infoY + 14, rw, 1.0F, AMBER);
        } else {
            Component by = key.isUnbound() ? Component.translatable("menu.starfall.unbound")
                    : Component.translatable("menu.starfall.bound", key.getTranslatedKeyMessage());
            g.drawString(font, by, rx, infoY + 14, GREY, false);
        }
        List<Component> also = conflicts(key);
        if (!also.isEmpty()) {
            Component joined = also.get(0);
            for (int k = 1; k < also.size(); k++) joined = Component.translatable("menu.starfall.and", joined, also.get(k));
            fitted(g, Component.translatable("menu.starfall.also", joined), rx, infoY + 24, rw, 1.0F, RED);
        }
    }

    private void footer(GuiGraphics g) {
        int fy = py + ph - 16;
        g.fill(px + 4, fy, px + pw - 4, fy + 1, RED_DARK);
        Component hint = Component.translatable("menu.starfall.hint", Keys.MENU.getTranslatedKeyMessage());
        int w = font.width(hint);
        float s = Math.min(1.0F, (pw - 16) / (float) w);
        g.pose().pushPose();
        g.pose().translate(px + pw / 2.0F - w * s / 2.0F, fy + 4.5F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, hint, 0, 0, GREY, false);
        g.pose().popPose();
    }

    /** Text squeezed to fit {@code width} if it has to. */
    private void fitted(GuiGraphics g, Component text, int x, int y, int width, float scale, int color) {
        int w = font.width(text);
        float s = Math.min(scale, width / (float) Math.max(1, w));
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Other actions bound to the same key as {@code mine}. */
    private static List<Component> conflicts(KeyMapping mine) {
        List<Component> out = new ArrayList<>();
        if (mine.isUnbound()) return out;
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != mine && other.same(mine)) out.add(Component.translatable(other.getName()));
        }
        return out;
    }

    // ------------------------------------------------------------------ input

    private void choose(int i) {
        Net.CHANNEL.sendToServer(new SelectSkillPacket(i));
        armed = Skill.byIndex(i);
        focus = i;
        flash = i;
        flashAt = age;
        binding = -1;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sounds.UI_SELECT.get(), 1.0F, 0.6F));
    }

    private void startBinding(int i) {
        binding = i;
        focus = i;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sounds.UI_HOVER.get(), 1.0F, 0.6F));
    }

    @Override
    public void mouseMoved(double mx, double my) {
        mouseMoved = true;
        super.mouseMoved(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = hit(mx, my);
        if (i < 0) {
            binding = -1;
            return super.mouseClicked(mx, my, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) startBinding(i);
        else choose(i);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        focus = Math.floorMod(focus + (delta < 0 ? 1 : -1), Skill.values().length);
        mouseMoved = false;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        int n = Skill.values().length;
        if (binding >= 0) {
            KeyMapping mapping = Keys.SKILLS[binding];
            binding = -1;
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                mapping.setKey(InputConstants.getKey(key, scancode));
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sounds.UI_SELECT.get(), 1.25F, 0.7F));
            }
            return true;
        }
        if (Keys.MENU.matches(key, scancode)) {
            onClose();
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + n) {
            choose(key - GLFW.GLFW_KEY_1);
            return true;
        }
        int move = switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> -1;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> 1;
            default -> 0;
        };
        if (move != 0) {
            focus = Math.floorMod(focus + move, n);
            mouseMoved = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) {
            choose(shown());
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    /** For the showcase recorder: describe a strike as if it was picked with the keys. */
    public void showDetails(int i) {
        focus = i;
        mouseMoved = false;
    }

    /** For the showcase recorder: highlight a strike as if the mouse was over it. */
    public void highlight(int i) {
        focus = i;
        mouseMoved = false;
    }
}
