package com.reya.boundlessrouters.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.reya.boundlessrouters.gui.Layouts;
import com.reya.boundlessrouters.gui.Layouts.Router;
import com.reya.boundlessrouters.gui.Layouts.Sheet;
import com.reya.boundlessrouters.module.ModuleItem;
import com.reya.boundlessrouters.network.Net;
import com.reya.boundlessrouters.network.OpenModulePacket;
import com.reya.boundlessrouters.router.RedstoneMode;
import com.reya.boundlessrouters.router.RouterMenu;
import com.reya.boundlessrouters.upgrade.UpgradeKind;
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
 * The router's screen: the buffer in a copper ring, the module slots on a copper bus below it, the upgrades in a
 * column on the right, the router's numbers on readouts on the left. A ring of light round the buffer fills as
 * the router counts toward its next run; when modules do something, light runs down the bus to them, their slots
 * and their lights on the little front panel glow their colours.
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {
    private static final ResourceLocation PANEL = Ui.tex("router");
    private static final long PULSE_MS = 380L, GLOW_MS = 650L;

    private final long[] ranAt = new long[Router.MODULES.length];
    private final List<Pulse> pulses = new ArrayList<>();
    private int prevSince = 1000;
    private long lastWave;

    private record Pulse(int module, long start, int colour) {
    }

    public RouterScreen(RouterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = Layouts.W;
        imageHeight = Router.H;
        inventoryLabelX = Router.INV_X;
        inventoryLabelY = Router.INV_Y - 11;
        for (int i = 0; i < ranAt.length; i++) ranAt[i] = -100000L;
    }

    private ItemStack module(int i) {
        return menu.getSlot(RouterMenu.MODULES + i).getItem();
    }

    private static int colour(ItemStack module) {
        return module.getItem() instanceof ModuleItem item ? item.kind().colour() : Ui.TEAL;
    }

    /** Whether the router's redstone mode lets it run now. */
    private boolean running() {
        return switch (menu.redstoneMode()) {
            case ALWAYS -> true;
            case HIGH -> menu.powered();
            case LOW -> !menu.powered();
            case NEVER, PULSE -> false;
        };
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // the server counts ticks since modules last did something: it starts over when they do again
        int since = menu.sinceRun();
        long now = Util.getMillis();
        if (since < prevSince || since == 0 && now - lastWave > 240L) {
            int mask = menu.lastRun();
            for (int i = 0; i < ranAt.length; i++) {
                if ((mask & 1 << i) == 0) continue;
                ranAt[i] = now;
                pulses.add(new Pulse(i, now, colour(module(i))));
            }
            lastWave = now;
        }
        prevSince = since;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        long now = Util.getMillis();
        g.blit(PANEL, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        float cx = x + Router.CORE[0], cy = y + Router.CORE[1];

        // the core breathes while the router works, and its ring counts toward the next run
        float busy = Mth.clamp(1.0F - menu.sinceRun() / 40.0F, 0.0F, 1.0F);
        if (busy > 0.0F) {
            float breathe = 0.6F + 0.4F * Mth.sin(now / 220.0F);
            Gfx.radial(g, cx, cy, 15.0F, Gfx.argb(Ui.TEAL, 0.22F * busy * breathe), Gfx.argb(Ui.TEAL, 0.0F), true);
        }
        int interval = Math.max(1, menu.interval());
        float r1 = Router.CORE_R - 1.4F, r2 = Router.CORE_R + 0.9F;
        float top = -Mth.HALF_PI;
        if (running()) {
            if (interval <= 4) {
                // too quick to count: a comet runs round
                float head = now / 140.0F % Gfx.TAU;
                Gfx.arc(g, cx, cy, r1, r2, top + head, top + head + 2.2F, t -> Gfx.argb(Ui.TEAL, t * 0.9F), true, true);
            } else {
                float p = Mth.clamp(menu.counter() / (float) interval, 0.0F, 1.0F);
                Gfx.arc(g, cx, cy, r1, r2, top, top + Gfx.TAU * p, t -> Gfx.argb(Gfx.mix(0xFF1C8C80, 0xFF39E6D0, t) & 0xFFFFFF, 0.85F), true, true);
            }
        } else {
            Gfx.arc(g, cx, cy, r1, r2, top, top + Gfx.TAU, t -> Gfx.argb(menu.redstoneMode() == RedstoneMode.NEVER ? 0x5A2020 : 0x30474A, 0.6F),
                    true, false);
        }

        // light running down the bus to the modules that did something, their slots glowing
        for (Iterator<Pulse> it = pulses.iterator(); it.hasNext(); ) {
            Pulse pulse = it.next();
            float t = (now - pulse.start) / (float) PULSE_MS;
            if (t > 1.0F) {
                it.remove();
                continue;
            }
            drawPulse(g, pulse, t);
        }
        for (int i = 0; i < Router.MODULES.length; i++) {
            float k = 1.0F - (now - ranAt[i]) / (float) GLOW_MS;
            if (k <= 0.0F) continue;
            int c = colour(module(i));
            Gfx.radial(g, x + Router.MODULES[i][0] + 8, y + Router.MODULES[i][1] + 8, 14.0F, Gfx.argb(c, 0.55F * k), Gfx.argb(c, 0.0F), true);
        }

        // the module lights: dim in their module's colour while one is in, bright when it works
        for (int i = 0; i < Router.LEDS.length; i++) {
            ItemStack m = module(i);
            if (m.isEmpty()) continue;
            int c = colour(m);
            float k = Mth.clamp(1.0F - (now - ranAt[i]) / (float) GLOW_MS, 0.0F, 1.0F);
            int lx = x + Router.LEDS[i][0], ly = y + Router.LEDS[i][1];
            g.fill(lx + 1, ly + 1, lx + Router.LED - 1, ly + Router.LED - 1, Gfx.argb(Gfx.mix(0xFF000000 | c, 0xFFFFFFFF, 0.3F * k) & 0xFFFFFF,
                    0.45F + 0.55F * k));
            if (k > 0.0F) Gfx.radial(g, lx + Router.LED / 2.0F, ly + Router.LED / 2.0F, 6.0F, Gfx.argb(c, 0.6F * k), Gfx.argb(c, 0.0F), true);
        }

        // the gauge's needle: how fast the router runs (from twenty ticks a run to every tick), quivering while it works
        float speed = running() ? 0.06F + 0.94F * Mth.clamp((20 - menu.interval()) / 19.0F, 0.0F, 1.0F) + 0.012F * Mth.sin(now / 31.0F) : 0.0F;
        double angle = Math.toRadians(135.0D + 270.0D * Mth.clamp(speed, 0.0F, 1.0F));
        float dialX = x + Router.GAUGE[0] + 0.5F, dialY = y + Router.GAUGE[1] + 0.5F;
        for (float t = 0.0F; t <= Router.GAUGE_R - 4.0F; t += 0.5F) {
            int nx = Mth.floor(dialX + Math.cos(angle) * t), ny = Mth.floor(dialY + Math.sin(angle) * t);
            g.fill(nx, ny, nx + 1, ny + 1, 0xFFC8281E);
        }
        g.fill(x + Router.GAUGE[0], y + Router.GAUGE[1], x + Router.GAUGE[0] + 1, y + Router.GAUGE[1] + 1, 0xFF2A2C32);

        // gears under the module slots, the redstone button, the readouts' icons
        for (int i = 0; i < Router.GEARS.length; i++) {
            int gx = x + Router.GEARS[i][0], gy = y + Router.GEARS[i][1];
            int state = module(i).isEmpty() ? 2 : Ui.in(mouseX, mouseY, gx, gy, Router.GEAR, Router.GEAR) ? 1 : 0;
            Ui.blit(g, gx, gy, Sheet.GEAR[0] + Router.GEAR * state, Sheet.GEAR[1], Router.GEAR, Router.GEAR);
        }
        int rx = x + Router.REDSTONE[0], ry = y + Router.REDSTONE[1];
        Ui.button(g, rx, ry, Ui.in(mouseX, mouseY, rx, ry, 16, 16) ? 1 : 0);
        Ui.blit(g, rx + 2, ry + 2, Sheet.REDSTONE[0] + 12 * menu.redstoneMode().ordinal(), Sheet.REDSTONE[1], 12, 12);
        for (int k = 0; k < Router.INFO.length; k++) Ui.glyph(g, Sheet.INFO, k, x + Router.INFO[k][0], y + Router.INFO[k][1]);
    }

    /** One pulse of light: down from the core to the bus, along it, and down into module `pulse.module`'s slot. */
    private void drawPulse(GuiGraphics g, Pulse pulse, float t) {
        float x = leftPos, y = topPos;
        float cx = x + Router.CORE[0], top = y + Router.CORE[1] + Router.CORE_R + 1, bus = y + Router.BUS_Y + 1;
        float mx = x + Router.MODULES[pulse.module][0] + 8, my = y + Router.MODULES[pulse.module][1] - 1;
        float[][] path = {{cx, top}, {cx, bus}, {mx, bus}, {mx, my}};
        float total = 0.0F;
        float[] lengths = new float[path.length - 1];
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = Math.abs(path[i + 1][0] - path[i][0]) + Math.abs(path[i + 1][1] - path[i][1]);
            total += lengths[i];
        }
        float head = t * total, tail = Math.max(0.0F, head - 14.0F);
        float[] a = pointAt(path, lengths, tail), b = pointAt(path, lengths, head);
        float fade = t < 0.85F ? 1.0F : (1.0F - t) / 0.15F;
        Gfx.beam(g, a[0], a[1], b[0], b[1], 3.0F, Gfx.argb(pulse.colour, 0.0F), Gfx.argb(pulse.colour, 0.9F * fade), true);
        Gfx.radial(g, b[0], b[1], 4.0F, Gfx.argb(0xFFFFFF, 0.9F * fade), Gfx.argb(pulse.colour, 0.0F), true);
    }

    private static float[] pointAt(float[][] path, float[] lengths, float d) {
        for (int i = 0; i < lengths.length; i++) {
            if (d <= lengths[i] || i == lengths.length - 1) {
                float k = lengths[i] <= 0.0F ? 0.0F : Mth.clamp(d / lengths[i], 0.0F, 1.0F);
                return new float[]{path[i][0] + (path[i + 1][0] - path[i][0]) * k, path[i][1] + (path[i + 1][1] - path[i][1]) * k};
            }
            d -= lengths[i];
        }
        return path[path.length - 1];
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Ui.centred(g, font, title, imageWidth / 2, Layouts.HEADER[1] + 4, Ui.TEXT_TITLE, true);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.TEXT_DIM, false);
        // the redstone mode, a red dot while the router has a signal
        int lx = Router.REDSTONE[0] + 24, ly = Router.REDSTONE[1] + 4;
        Ui.fit(g, font, Component.translatable("gui.boundlessrouters.redstone." + menu.redstoneMode().key()), lx, ly, 81 - lx, Ui.TEXT_LCD, 1.0F);
        if (menu.powered()) g.fill(83, ly + 2, 86, ly + 5, 0xFFFF4B3E);
        // the readouts (on their screen, which ends at x 88)
        int interval = menu.interval();
        Component[] lines = {
                interval <= 1 ? Component.translatable("gui.boundlessrouters.every_tick") : Ui.count("gui.boundlessrouters.interval", interval),
                Component.translatable("gui.boundlessrouters.items", menu.itemsPerRun()),
                Ui.count("gui.boundlessrouters.range", menu.range())};
        for (int k = 0; k < lines.length; k++) {
            int tx = Router.INFO[k][0] + 13;
            Ui.fit(g, font, lines[k], tx, Router.INFO[k][1] + 1, 86 - tx, Ui.TEXT_LCD, 1.0F);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        ownTooltips(g, mouseX, mouseY);
    }

    private void ownTooltips(GuiGraphics g, int mouseX, int mouseY) {
        List<Component> tip = new ArrayList<>();
        int x = leftPos, y = topPos;
        if (Ui.in(mouseX, mouseY, x + Router.REDSTONE[0], y + Router.REDSTONE[1], 16, 16)) {
            RedstoneMode mode = menu.redstoneMode();
            tip.add(Component.translatable("gui.boundlessrouters.redstone." + mode.key()).withStyle(ChatFormatting.RED));
            tip.add(Component.translatable("gui.boundlessrouters.redstone." + mode.key() + ".tip").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.boundlessrouters.redstone.click").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (int i = 0; i < Router.GEARS.length; i++) {
            if (!module(i).isEmpty() && Ui.in(mouseX, mouseY, x + Router.GEARS[i][0], y + Router.GEARS[i][1], Router.GEAR, Router.GEAR)) {
                tip.add(Component.translatable("gui.boundlessrouters.settings").withStyle(ChatFormatting.AQUA));
            }
        }
        String[] infos = {"interval", "items", "range"};
        for (int k = 0; k < Router.INFO.length; k++) {
            if (Ui.in(mouseX, mouseY, x + Router.INFO[k][0], y + Router.INFO[k][1], 78, 10)) {
                tip.add(Component.translatable("gui.boundlessrouters.info." + infos[k]).withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable("gui.boundlessrouters.info." + infos[k] + ".tip",
                        menu.upgradeCount(k == 0 ? UpgradeKind.SPEED : k == 1 ? UpgradeKind.STACK : UpgradeKind.RANGE)).withStyle(ChatFormatting.GRAY));
            }
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int index = hoveredSlot.index;
            String what = index == RouterMenu.BUFFER ? "buffer" : index < RouterMenu.UPGRADES ? "module" : index < RouterMenu.PLAYER ? "upgrade" : null;
            if (what != null) {
                tip.add(Component.translatable("gui.boundlessrouters.slot." + what).withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("gui.boundlessrouters.slot." + what + ".tip").withStyle(ChatFormatting.GRAY));
            }
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = leftPos, y = topPos;
        if ((button == 0 || button == 1) && Ui.in(mouseX, mouseY, x + Router.REDSTONE[0], y + Router.REDSTONE[1], 16, 16) && minecraft != null
                && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button == 1 ? RouterMenu.BUTTON_REDSTONE_BACK : RouterMenu.BUTTON_REDSTONE);
            Ui.click();
            return true;
        }
        if (button == 0) {
            for (int i = 0; i < Router.GEARS.length; i++) {
                if (!module(i).isEmpty() && Ui.in(mouseX, mouseY, x + Router.GEARS[i][0], y + Router.GEARS[i][1], Router.GEAR, Router.GEAR)) {
                    openModule(i);
                    return true;
                }
            }
        }
        // a middle click on a module opens its settings too
        if (button == 2 && hoveredSlot != null && hoveredSlot.hasItem() && hoveredSlot.index >= RouterMenu.MODULES
                && hoveredSlot.index < RouterMenu.UPGRADES) {
            openModule(hoveredSlot.index - RouterMenu.MODULES);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openModule(int slot) {
        Ui.click();
        Net.toServer(new OpenModulePacket(slot));
    }
}
