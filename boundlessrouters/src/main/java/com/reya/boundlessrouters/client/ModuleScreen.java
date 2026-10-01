package com.reya.boundlessrouters.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import com.reya.boundlessrouters.RouterConfig;
import com.reya.boundlessrouters.gui.Layouts;
import com.reya.boundlessrouters.gui.Layouts.Module;
import com.reya.boundlessrouters.gui.Layouts.Sheet;
import com.reya.boundlessrouters.module.ModuleItem;
import com.reya.boundlessrouters.module.ModuleKind;
import com.reya.boundlessrouters.module.ModuleMenu;
import com.reya.boundlessrouters.module.ModuleSettings;
import com.reya.boundlessrouters.module.ModuleSettings.Setting;
import com.reya.boundlessrouters.module.Target;
import com.reya.boundlessrouters.network.Net;
import com.reya.boundlessrouters.network.OpenRouterPacket;
import com.reya.boundlessrouters.network.SettingPacket;
import com.reya.boundlessrouters.router.RelativeDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A module's settings: which way it works (an unfolded cube round the router's front), its filter and how the
 * filter matches, whether it stops the modules after it and when redstone lets it work, a readout of what it
 * will do, and under them its own settings: its targets, a flinger's throw, a vacuum's reach, and so on.
 */
public class ModuleScreen extends AbstractContainerScreen<ModuleMenu> {
    private static final ResourceLocation PANEL = Ui.tex("module");
    /** The toggles' settings, in Layouts.Module.TOGGLES' order (the eighth is spare). */
    private static final Setting[] TOGGLES = {Setting.BLACKLIST, Setting.MATCH_DAMAGE, Setting.MATCH_NBT, Setting.MATCH_TAGS,
            Setting.MATCH_MOD, Setting.TERMINATE, Setting.REDSTONE};
    private static final String[] TOGGLE_KEYS = {"list", "damage", "nbt", "tags", "mod", "terminate", "redstone"};

    /** The controls drawn this frame, for the clicks and tooltips that follow (in screen coordinates). */
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, IntConsumer click, Supplier<List<Component>> tip) {
    }

    public ModuleScreen(ModuleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = Layouts.W;
        imageHeight = Module.H;
        inventoryLabelX = Module.INV_X;
        inventoryLabelY = Module.INV_Y - 11;
    }

    private ModuleSettings settings() {
        return menu.settings();
    }

    private ModuleKind kind() {
        return menu.module().getItem() instanceof ModuleItem item ? item.kind() : ModuleKind.SENDER;
    }

    /** Changes a setting: here at once, and on the server. */
    private void set(Setting setting, int value) {
        menu.apply(setting, value);
        Net.toServer(new SettingPacket(setting.ordinal(), value));
        Ui.click();
    }

    private static int step(int button) {
        int k = Screen.hasShiftDown() ? 10 : 1;
        return button == 1 ? -k : k;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        hits.clear();
        int x = leftPos, y = topPos;
        g.blit(PANEL, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        ModuleSettings s = settings();
        ModuleKind kind = kind();
        int colour = kind.colour();
        long now = Util.getMillis();

        // the header: back to the router, the module's icon in a soft light of its colour
        if (menu.routerPos() != null) {
            int bx = x + Module.BACK[0], by = y + Module.BACK[1];
            boolean hover = Ui.in(mouseX, mouseY, bx, by, 12, 12);
            Ui.blit(g, bx, by, Sheet.BACK[0] + (hover ? 12 : 0), Sheet.BACK[1], 12, 12);
            hits.add(new Hit(bx, by, 12, 12, b -> {
                Ui.click();
                Net.toServer(new OpenRouterPacket());
            }, () -> List.of(Component.translatable("gui.boundlessrouters.back"))));
        }
        float glow = 0.35F + 0.15F * Mth.sin(now / 400.0F);
        Gfx.radial(g, x + Module.ICON[0] + 8, y + Module.ICON[1] + 7, 11.0F, Gfx.argb(colour, glow), Gfx.argb(colour, 0.0F), true);

        // the direction picker
        RelativeDirection current = s.direction();
        for (RelativeDirection dir : RelativeDirection.values()) {
            int cx = x + Module.DIRS[dir.ordinal()][0], cy = y + Module.DIRS[dir.ordinal()][1];
            boolean hover = Ui.in(mouseX, mouseY, cx, cy, 16, 16);
            boolean usable = kind.directional();
            int state = !usable ? 3 : dir == current ? 2 : hover ? 1 : 0;
            Ui.button(g, cx, cy, state);
            if (usable || dir == current) Ui.glyph(g, Sheet.DIRS, dir.ordinal(), cx + 3, cy + 3);
            if (dir == current && usable) {
                Gfx.radial(g, cx + 8, cy + 8, 11.0F, Gfx.argb(Ui.TEAL, 0.25F + 0.1F * Mth.sin(now / 300.0F)), Gfx.argb(Ui.TEAL, 0.0F), true);
            }
            if (usable) {
                hits.add(new Hit(cx, cy, 16, 16, b -> set(Setting.DIRECTION, dir.ordinal()), () -> List.of(
                        Component.translatable("direction.boundlessrouters." + dir.key()).withStyle(ChatFormatting.AQUA),
                        Component.translatable("gui.boundlessrouters.dir." + dir.key()).withStyle(ChatFormatting.GRAY))));
            }
        }

        // the toggles
        for (int i = 0; i < TOGGLES.length; i++) {
            int tx = x + Module.TOGGLES[i][0], ty = y + Module.TOGGLES[i][1];
            Setting setting = TOGGLES[i];
            int value = s.get(setting);
            boolean on = setting == Setting.BLACKLIST ? value == 0 : value != 0;
            boolean hover = Ui.in(mouseX, mouseY, tx, ty, 14, 14);
            Ui.toggle(g, tx, ty, on, hover);
            int glyph = switch (setting) {
                case BLACKLIST -> value != 0 ? 1 : 0;
                case MATCH_DAMAGE -> 2;
                case MATCH_NBT -> 3;
                case MATCH_TAGS -> 4;
                case MATCH_MOD -> 5;
                case TERMINATE -> 6;
                default -> 7 + Mth.clamp(value, 0, 2);
            };
            Ui.glyph(g, Sheet.OPTS, glyph, tx + 2, ty + 2);
            String key = TOGGLE_KEYS[i];
            hits.add(new Hit(tx, ty, 14, 14, b -> {
                if (setting == Setting.REDSTONE) set(setting, Math.floorMod(value + (b == 1 ? -1 : 1), 3));
                else set(setting, value != 0 ? 0 : 1);
            }, () -> toggleTip(key, setting, value)));
        }

        // its own settings
        drawPanel(g, s, kind, mouseX, mouseY);
    }

    private List<Component> toggleTip(String key, Setting setting, int value) {
        List<Component> tip = new ArrayList<>();
        String state = switch (setting) {
            case BLACKLIST -> value != 0 ? "black" : "white";
            case REDSTONE -> ModuleSettings.ModuleRedstone.values()[Mth.clamp(value, 0, 2)].key();
            default -> value != 0 ? "on" : "off";
        };
        tip.add(Component.translatable("gui.boundlessrouters.opt." + key).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("gui.boundlessrouters.opt." + key + "." + state).withStyle(ChatFormatting.WHITE));
        tip.add(Component.translatable("gui.boundlessrouters.opt." + key + ".tip").withStyle(ChatFormatting.GRAY));
        return tip;
    }

    /** The panel along the bottom: the kind's own settings, or what it does. */
    private void drawPanel(GuiGraphics g, ModuleSettings s, ModuleKind kind, int mouseX, int mouseY) {
        int px = leftPos + Module.PANEL[0] + 4, rowA = topPos + Module.PANEL[1] + 4, rowB = topPos + Module.PANEL[1] + 17;
        int right = leftPos + Module.PANEL[2] - 4;
        switch (kind.panel()) {
            case TARGET -> {
                List<Target> targets = s.targets();
                Ui.glyph(g, Sheet.MISC, 9, px, rowA - 1);
                if (targets.isEmpty()) {
                    text(g, Component.translatable("gui.boundlessrouters.no_target"), px + 13, rowA, Ui.TEXT_DIM);
                    text(g, Component.translatable("gui.boundlessrouters.unbound." + kind.id()), px, rowB, Ui.TEXT_DIM);
                } else {
                    Target t = targets.get(0);
                    text(g, describe(t), px + 13, rowA, Ui.TEXT);
                    text(g, Component.translatable("gui.boundlessrouters.bound_anywhere"), px, rowB, Ui.TEXT_DIM);
                    clearButton(g, right - 10, rowA - 1, mouseX, mouseY, Setting.CLEAR_TARGETS, "gui.boundlessrouters.clear_target");
                }
            }
            case DISTRIBUTOR -> {
                List<Target> targets = s.targets();
                ModuleSettings.Strategy strategy = s.strategy();
                int cx = cycleButton(g, px, rowA - 1, mouseX, mouseY, Setting.STRATEGY, strategy.ordinal(), ModuleSettings.Strategy.values().length,
                        "gui.boundlessrouters.strategy");
                text(g, Component.translatable("gui.boundlessrouters.strategy." + strategy.key()), cx, rowA, Ui.TEXT);
                Component count = Component.translatable("gui.boundlessrouters.targets", targets.size());
                text(g, count, right - 14 - font.width(count), rowA, Ui.TEXT_LCD);
                if (!targets.isEmpty()) {
                    clearButton(g, right - 10, rowA - 1, mouseX, mouseY, Setting.CLEAR_TARGETS, "gui.boundlessrouters.clear_targets");
                    StringBuilder list = new StringBuilder();
                    for (int i = 0; i < Math.min(3, targets.size()); i++) list.append(i > 0 ? "  " : "").append(targets.get(i).coords());
                    if (targets.size() > 3) list.append("  …");
                    Ui.small(g, font, Component.literal(list.toString()), px, rowB + 1, Ui.TEXT_DIM, 0.75F);
                } else {
                    text(g, Component.translatable("gui.boundlessrouters.no_targets"), px, rowB, Ui.TEXT_DIM);
                }
            }
            case FLINGER -> {
                int x1 = number(g, px, rowA, mouseX, mouseY, "speed", Setting.SPEED, s.get(Setting.SPEED), String.format(java.util.Locale.ROOT, "%.1f", s.speed()));
                text(g, Component.translatable("gui.boundlessrouters.shift_steps"), x1 + 10, rowA, Ui.TEXT_DIM);
                int x2 = number(g, px, rowB, mouseX, mouseY, "pitch", Setting.PITCH, s.pitch(), s.pitch() + "°");
                number(g, x2 + 12, rowB, mouseX, mouseY, "yaw", Setting.YAW, s.yaw(), s.yaw() + "°");
            }
            case BREAKER -> {
                int x1 = toggleLabel(g, px, rowA - 2, mouseX, mouseY, Setting.SILK, s.silk(), "silk");
                number(g, x1 + 10, rowA, mouseX, mouseY, "fortune", Setting.FORTUNE, s.fortune(), String.valueOf(s.fortune()));
                text(g, Component.translatable("gui.boundlessrouters.breaker_hint"), px, rowB, Ui.TEXT_DIM);
            }
            case VACUUM -> {
                number(g, px, rowA, mouseX, mouseY, "radius", Setting.RADIUS, s.radius(), String.valueOf(s.radius()));
                text(g, Component.translatable("gui.boundlessrouters.vacuum_hint", RouterConfig.MAX_RADIUS.get()), px, rowB, Ui.TEXT_DIM);
            }
            case PLAYER -> {
                Ui.glyph(g, Sheet.MISC, 8, px, rowA - 1);
                if (s.playerId() == null) {
                    text(g, Component.translatable("gui.boundlessrouters.no_player"), px + 13, rowA, Ui.TEXT_DIM);
                } else {
                    text(g, Component.literal(s.playerName()), px + 13, rowA, Ui.TEXT);
                    clearButton(g, right - 10, rowA - 1, mouseX, mouseY, Setting.CLEAR_PLAYER, "gui.boundlessrouters.clear_player");
                }
                int cx = cycleButton(g, px, rowB - 1, mouseX, mouseY, Setting.SECTION, s.section().ordinal(), ModuleSettings.Section.values().length,
                        "gui.boundlessrouters.section");
                cx = text(g, Component.translatable("gui.boundlessrouters.section." + s.section().key()), cx, rowB, Ui.TEXT) + 10;
                cx = cycleButton(g, cx, rowB - 1, mouseX, mouseY, Setting.OPERATION, s.operation().ordinal(), ModuleSettings.Operation.values().length,
                        "gui.boundlessrouters.operation");
                text(g, Component.translatable("gui.boundlessrouters.operation." + s.operation().key()), cx, rowB, Ui.TEXT);
            }
            case DETECTOR -> {
                int x1 = number(g, px, rowA, mouseX, mouseY, "power", Setting.POWER, s.power(), String.valueOf(s.power()));
                toggleLabel(g, x1 + 10, rowA - 2, mouseX, mouseY, Setting.STRONG, s.strong(), "strong");
                text(g, Component.translatable("gui.boundlessrouters.detector_hint"), px, rowB, Ui.TEXT_DIM);
            }
            case EXTRUDER -> {
                text(g, Component.translatable("gui.boundlessrouters.extended", s.extended()), px, rowA, Ui.TEXT);
                text(g, Component.translatable("gui.boundlessrouters.extruder_hint"), px, rowB, Ui.TEXT_DIM);
            }
            case INFO -> {
                List<FormattedCharSequence> lines = font.split(Component.translatable("item.boundlessrouters." + kind.id() + "_module.desc"),
                        Module.PANEL[2] - Module.PANEL[0] - 8);
                for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), px, rowA + 13 * i, Ui.TEXT_DIM, false);
            }
        }
    }

    private Component describe(Target t) {
        return Component.translatable("gui.boundlessrouters.target_at", t.coords(), Component.translatable("direction.boundlessrouters." + t.face().getName()),
                t.dimName());
    }

    /** Text; returns where it ends. */
    private int text(GuiGraphics g, Component text, int x, int y, int colour) {
        g.drawString(font, text, x, y, colour, false);
        return x + font.width(text);
    }

    /** A label, a minus, the value, a plus: right-click or the minus lowers it, shift steps by ten. Returns where it ends. */
    private int number(GuiGraphics g, int x, int y, int mouseX, int mouseY, String key, Setting setting, int value, String shown) {
        Component label = Component.translatable("gui.boundlessrouters.num." + key);
        int lx = text(g, label, x, y, Ui.TEXT_DIM) + 3;
        int minus = lx, valueX = lx + 12, plus = valueX + Math.max(18, font.width(shown) + 4);
        smallButton(g, minus, y - 1, mouseX, mouseY, 1, b -> set(setting, value - Math.abs(step(b))), key);
        Ui.centred(g, font, Component.literal(shown), (valueX + plus - 2) / 2 + 1, y, Ui.TEXT_LCD, false);
        smallButton(g, plus, y - 1, mouseX, mouseY, 0, b -> set(setting, value + (b == 1 ? -Math.abs(step(b)) : Math.abs(step(b)))), key);
        hits.add(new Hit(minus, y - 1, plus + 10 - minus, 10, null, () -> List.of(
                Component.translatable("gui.boundlessrouters.num." + key).withStyle(ChatFormatting.AQUA),
                Component.translatable("gui.boundlessrouters.num." + key + ".tip").withStyle(ChatFormatting.GRAY))));
        return plus + 10;
    }

    /** A little + or - button (glyph 0 plus, 1 minus). */
    private void smallButton(GuiGraphics g, int x, int y, int mouseX, int mouseY, int glyph, IntConsumer click, String key) {
        boolean hover = Ui.in(mouseX, mouseY, x, y, 10, 10);
        Ui.small(g, x, y, hover ? 1 : 0);
        Ui.glyph(g, Sheet.MISC, glyph, x, y);
        hits.add(new Hit(x, y, 10, 10, click, null));
    }

    /** A cycle button: left click forward, right click back. Returns where the text after it starts. */
    private int cycleButton(GuiGraphics g, int x, int y, int mouseX, int mouseY, Setting setting, int value, int count, String key) {
        boolean hover = Ui.in(mouseX, mouseY, x, y, 10, 10);
        Ui.small(g, x, y, hover ? 1 : 0);
        Ui.glyph(g, Sheet.MISC, 2, x, y);
        hits.add(new Hit(x, y, 10, 10, b -> set(setting, Math.floorMod(value + (b == 1 ? -1 : 1), count)), () -> List.of(
                Component.translatable(key).withStyle(ChatFormatting.AQUA),
                Component.translatable(key + ".tip").withStyle(ChatFormatting.GRAY))));
        return x + 13;
    }

    /** A toggle with a label after it; returns where it ends. */
    private int toggleLabel(GuiGraphics g, int x, int y, int mouseX, int mouseY, Setting setting, boolean on, String key) {
        boolean hover = Ui.in(mouseX, mouseY, x, y, 14, 14);
        Ui.toggle(g, x, y, on, hover);
        Ui.glyph(g, Sheet.MISC, setting == Setting.SILK ? 4 : on ? 6 : 7, x + 2, y + 2);
        hits.add(new Hit(x, y, 14, 14, b -> set(setting, on ? 0 : 1), () -> List.of(
                Component.translatable("gui.boundlessrouters.opt." + key).withStyle(ChatFormatting.AQUA),
                Component.translatable("gui.boundlessrouters.opt." + key + "." + (on ? "on" : "off")).withStyle(ChatFormatting.WHITE),
                Component.translatable("gui.boundlessrouters.opt." + key + ".tip").withStyle(ChatFormatting.GRAY))));
        return text(g, Component.translatable("gui.boundlessrouters.opt." + key), x + 17, y + 3, on ? Ui.TEXT : Ui.TEXT_DIM);
    }

    private void clearButton(GuiGraphics g, int x, int y, int mouseX, int mouseY, Setting setting, String key) {
        boolean hover = Ui.in(mouseX, mouseY, x, y, 10, 10);
        Ui.small(g, x, y, hover ? 1 : 0);
        Ui.glyph(g, Sheet.MISC, 3, x, y);
        hits.add(new Hit(x, y, 10, 10, b -> set(setting, 0), () -> List.of(Component.translatable(key).withStyle(ChatFormatting.RED))));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // the title after the icon, the direction's name, the readout
        g.drawString(font, title, Module.ICON[0] + 20, Layouts.HEADER[1] + 4, Ui.TEXT_TITLE, true);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.TEXT_DIM, false);
        ModuleSettings s = settings();
        ModuleKind kind = kind();
        Component dir = kind.directional() ? Component.translatable("direction.boundlessrouters." + s.direction().key())
                : Component.translatable("gui.boundlessrouters.no_direction");
        Ui.centred(g, font, dir, Module.DIR_LABEL[0], Module.DIR_LABEL[1] - 3, kind.directional() ? Ui.TEXT_LCD : Ui.TEXT_DIM, false);
        int ix = Module.INFO[0] + 3, iy = Module.INFO[1] + 3;
        for (Component line : summary(s)) {
            Ui.small(g, font, line, ix, iy, Ui.TEXT_LCD, 0.75F);
            iy += 8;
        }
    }

    /** The readout: what the filter takes, how it matches, what follows it. */
    private List<Component> summary(ModuleSettings s) {
        List<Component> lines = new ArrayList<>();
        long listed = s.filterItems().stream().filter(i -> !i.isEmpty()).count();
        if (listed == 0) lines.add(Component.translatable(s.blacklist() ? "gui.boundlessrouters.sum.all" : "gui.boundlessrouters.sum.none"));
        else lines.add(Component.translatable(s.blacklist() ? "gui.boundlessrouters.sum.but" : "gui.boundlessrouters.sum.only", listed));
        List<String> how = new ArrayList<>();
        if (s.matchMod()) how.add("mod");
        else {
            if (s.matchTags()) how.add("tags");
            if (s.matchDamage()) how.add("damage");
            if (s.matchNbt()) how.add("nbt");
        }
        MutableComponent match = Component.translatable("gui.boundlessrouters.sum.match");
        if (how.isEmpty()) match.append(Component.translatable("gui.boundlessrouters.sum.match.item"));
        for (int i = 0; i < how.size(); i++) {
            if (i > 0) match.append(", ");
            match.append(Component.translatable("gui.boundlessrouters.sum.match." + how.get(i)));
        }
        lines.add(match);
        lines.add(Component.translatable("gui.boundlessrouters.sum.redstone." + s.redstone().key()));
        if (s.terminates()) lines.add(Component.translatable("gui.boundlessrouters.sum.terminate"));
        return lines;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        // the module's icon in the header, over the panel
        ItemStack module = menu.module();
        if (!module.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(leftPos + Module.ICON[0] + 1, topPos + Module.ICON[1], 0.0F);
            g.pose().scale(0.875F, 0.875F, 1.0F);
            g.renderItem(module, 0, 0);
            g.pose().popPose();
        }
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            for (Hit hit : hits) {
                if (hit.tip != null && Ui.in(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                    g.renderComponentTooltip(font, hit.tip.get(), mouseX, mouseY);
                    return;
                }
            }
            if (hoveredSlot != null && hoveredSlot.index < ModuleMenu.FILTER_END) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.boundlessrouters.filter").withStyle(ChatFormatting.GOLD),
                        Component.translatable("gui.boundlessrouters.filter.tip").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            for (Hit hit : hits) {
                if (hit.click != null && Ui.in(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                    hit.click.accept(button);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
