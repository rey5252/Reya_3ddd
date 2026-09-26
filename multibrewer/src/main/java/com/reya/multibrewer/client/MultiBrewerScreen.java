package com.reya.multibrewer.client;

import java.util.ArrayList;
import java.util.List;

import com.reya.multibrewer.BrewLogic;
import com.reya.multibrewer.Config;
import com.reya.multibrewer.MultiBrewer;
import com.reya.multibrewer.MultiBrewerMenu;
import com.reya.multibrewer.UpgradeItem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.items.IItemHandler;

/**
 * Brewer GUI in the same style as the mob farm: the frame, glass flask, tubes, slots and panels are one
 * pixel-art texture (tools/gen_gui.py). This class animates what's inside: the potions' colours running
 * down the tubes, the mixed liquid in the flask rising up the neck while it brews, bubbles, the drop
 * falling into the result slot, the blaze gauge, and the list of effects the potion will have.
 */
public class MultiBrewerScreen extends AbstractContainerScreen<MultiBrewerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MultiBrewer.MODID, "textures/gui/brewer.png");
    private static final int MARGIN = 4;
    private static final int TEX_W = MultiBrewerMenu.WIDTH + 2 * MARGIN;
    private static final int TEX_H = MultiBrewerMenu.HEIGHT + 2 * MARGIN;

    private static final int BLACK = 0xFF0A090C;
    private static final int TEXT = 0xFFF4ECDC;
    private static final int TEXT_DIM = 0xFFD8C8AE;
    private static final int GOLD_L = 0xFFFFE28C;
    private static final int GOLD = 0xFFE3B341;
    private static final int GOLD_D = 0xFF886012;
    private static final int GREEN = 0xFF8CF05A;
    private static final int RED = 0xFFFF6B5E;
    private static final int PLAQUE_TEXT = 0xFF3A3530;

    private static final int PX1 = 130, PY1 = 14, PX2 = 237, PY2 = 97;
    private static final int GX1 = 17, GY1 = 37, GX2 = 44, GY2 = 44;
    private static final int CX = 72, CY = 72;
    private static final int BULB_TOP = 53, BULB_BOTTOM = 91, NECK_TOP = 33;
    private static final int POOL_Y = 132, POOL_X1 = 41, POOL_X2 = 215;
    private static final int PANEL_BG = 0xFF46245C;
    private static final int BREW = 0xFF965ADC, BREW_HI = 0xFFD2AAFF, BREW_LO = 0xFF46288C;

    /** Glass paths from the flask to each potion below it (same as the texture). */
    private static final int[][][] TUBES = {
            {{72, 91}, {72, 96}, {44, 96}, {44, 106}},
            {{72, 91}, {72, 106}},
            {{72, 91}, {72, 96}, {100, 96}, {100, 106}}};
    private static final int[][] INGREDIENT_TUBE = {{72, 29}, {72, 33}};
    private static final int[][] OUTPUT_TUBE = {{92, 72}, {103, 72}};

    private float shownProgress;

    public MultiBrewerScreen(MultiBrewerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = MultiBrewerMenu.WIDTH;
        imageHeight = MultiBrewerMenu.HEIGHT;
    }

    private static boolean flaskInside(int x, int y) {
        if (x >= 68 && x <= 76 && y >= 33 && y <= 56) return true;
        return (x - CX) * (x - CX) + (y - CY) * (y - CY) <= 380;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) renderOwnTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float target = menu.progress() / (float) menu.maxProgress();
        shownProgress = target < shownProgress ? target : shownProgress + (target - shownProgress) * 0.3F;

        g.blit(TEXTURE, leftPos - MARGIN, topPos - MARGIN, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        drawTitlePlaque(g, leftPos + imageWidth / 2, topPos - 13);
        IItemHandler items = menu.items();
        boolean vanilla = BrewLogic.canBrewVanilla(items);
        ItemStack result = vanilla ? BrewLogic.vanillaPreview(items) : BrewLogic.result(items);
        boolean brewing = menu.progress() > 0;
        long time = Util.getMillis();
        g.drawManaged(() -> {
            drawPool(g, brewing, time);
            drawTubes(g, items, vanilla, result, brewing, time);
            drawFlask(g, items, result, vanilla, brewing, time);
            if (brewing) drawSparkles(g, time);
            drawGauge(g, brewing, time);
            drawGhosts(g, items);
        });
    }

    /**
     * Each potion's colour fills the glass down to its bottle. While brewing, brighter drops run along
     * it: up into the flask when mixing, down out of it when brewing the vanilla way.
     */
    private void drawTubes(GuiGraphics g, IItemHandler items, boolean vanilla, ItemStack result, boolean brewing, long time) {
        for (int i = 0; i < 3; i++) {
            ItemStack s = items.getStackInSlot(BrewLogic.IN_1 + i);
            if (!BrewLogic.isPotion(s)) continue;
            int color = PotionUtils.getColor(s);
            List<int[]> path = pathPixels(TUBES[i]);
            for (int[] p : path) px(g, p[0], p[1], 0xFF000000 | shade(color, 0.6F));
            if (brewing) runDrops(g, path, !vanilla, shade(vanilla && result != null && !result.isEmpty()
                    ? PotionUtils.getColor(result) : color, 1.4F), time, i * 3);
        }
        if (brewing && vanilla) runDrops(g, pathPixels(INGREDIENT_TUBE), false, 0xE8F0FF, time, 1);
        if (brewing && !vanilla && shownProgress > 0.7F && result != null) {
            runDrops(g, pathPixels(OUTPUT_TUBE), false, shade(PotionUtils.getColor(result), 1.4F), time, 2);
        }
    }

    private void runDrops(GuiGraphics g, List<int[]> path, boolean backwards, int rgb, long time, int offset) {
        int n = path.size();
        for (int k = 0; k < 2; k++) {
            int at = (int) ((time / 70L + (long) k * n / 2 + offset) % n);
            int[] p = path.get(backwards ? n - 1 - at : at);
            px(g, p[0], p[1], 0xFF000000 | rgb);
        }
    }

    private static List<int[]> pathPixels(int[][] pts) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < pts.length; i++) {
            int x = pts[i][0], y = pts[i][1];
            int tx = pts[i + 1][0], ty = pts[i + 1][1];
            while (x != tx || y != ty) {
                out.add(new int[]{x, y});
                x += Integer.signum(tx - x);
                y += Integer.signum(ty - y);
            }
        }
        int[] last = pts[pts.length - 1];
        out.add(new int[]{last[0], last[1]});
        return out;
    }

    /**
     * Liquid in the colour of what's being made. More potions fill the bulb higher; brewing pushes
     * it up the neck, with a wavy surface, bubbles and a glass shine.
     */
    private void drawFlask(GuiGraphics g, IItemHandler items, ItemStack result, boolean vanilla, boolean brewing, long time) {
        int potions = 0;
        int color = -1;
        for (int i = BrewLogic.IN_1; i <= BrewLogic.IN_3; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!BrewLogic.isPotion(s)) continue;
            potions++;
            if (color == -1) color = PotionUtils.getColor(s);
        }
        if (result != null && !result.isEmpty()) color = PotionUtils.getColor(result);
        if (potions > 0) {
            float fill = potions / 3.0F;
            float surface = BULB_BOTTOM - fill * (BULB_BOTTOM - BULB_TOP - 4);
            if (brewing) surface -= shownProgress * (BULB_TOP - NECK_TOP + 2);
            float t = time / 180.0F;
            // one fill per run of same-coloured pixels in a row, so the flask stays cheap to draw
            for (int y = NECK_TOP; y <= BULB_BOTTOM; y++) {
                int runStart = -1;
                int runColor = 0;
                for (int x = CX - 20; x <= CX + 21; x++) {
                    int c = 0;
                    if (x <= CX + 20 && flaskInside(x, y)) {
                        float wave = brewing ? Mth.sin(t + x * 0.7F) * 0.8F : Mth.sin(t * 0.3F + x * 0.5F) * 0.4F;
                        float top = surface + wave;
                        if (y >= top) {
                            float depth = Mth.clamp((y - surface) / 30.0F, 0.0F, 1.0F);
                            c = 0xFF000000 | (y - top < 1.0F ? shade(color, 1.6F) : shade(color, 1.15F - Math.round(depth * 6) / 6.0F * 0.45F));
                        }
                    }
                    if (c != runColor) {
                        if (runColor != 0) g.fill(leftPos + runStart, topPos + y, leftPos + x, topPos + y + 1, runColor);
                        runStart = x;
                        runColor = c;
                    }
                }
            }
            if (brewing) drawBubbles(g, color, surface, time);
        }
        // glass shine on the bulb and neck
        for (int k = 0; k < 7; k++) px(g, CX - 14 + k / 3, CY - 10 + k, 0x70FFFFFF);
        px(g, CX - 11, CY - 13, 0x90FFFFFF);
        px(g, 69, 36, 0x60FFFFFF);
        px(g, 69, 37, 0x60FFFFFF);
    }

    /** Glints of magic drifting up around the flask while it brews. */
    private void drawSparkles(GuiGraphics g, long time) {
        for (int i = 0; i < 10; i++) {
            float phase = ((time / 1600.0F) + i * 0.1F) % 1.0F;
            double a = i * 0.63D + phase * 0.8D;
            int x = CX + (int) Math.round(Math.cos(a) * 25);
            int y = CY + (int) Math.round(Math.sin(a) * 22) - (int) (phase * 8);
            int alpha = (int) (255 * Mth.sin(phase * (float) Math.PI));
            int c = (alpha << 24) | (i % 2 == 0 ? 0xFFF4C8 : 0xD2AAFF);
            px(g, x, y, c);
            if (i % 3 == 0) {
                int arm = (alpha / 2 << 24) | 0xFFFFFF;
                px(g, x - 1, y, arm);
                px(g, x + 1, y, arm);
                px(g, x, y - 1, arm);
                px(g, x, y + 1, arm);
            }
        }
    }

    /** The pool of brew above the inventory: a moving surface and bubbles popping in it. */
    private void drawPool(GuiGraphics g, boolean brewing, long time) {
        float t = time / (brewing ? 260.0F : 520.0F);
        for (int x = POOL_X1; x < POOL_X2; x++) {
            int surf = POOL_Y + Math.round(Mth.sin(x / 5.0F + t) * 1.2F);
            g.fill(leftPos + x, topPos + POOL_Y - 3, leftPos + x + 1, topPos + surf, PANEL_BG);
            g.fill(leftPos + x, topPos + surf, leftPos + x + 1, topPos + surf + 1, BREW_HI);
            g.fill(leftPos + x, topPos + surf + 1, leftPos + x + 1, topPos + POOL_Y + 4, BREW);
        }
        for (int i = 0; i < (brewing ? 8 : 4); i++) {
            float phase = ((time / 1400.0F) * (0.7F + (i % 3) * 0.2F) + i * 0.29F) % 1.0F;
            int x = POOL_X1 + 6 + (i * 41) % (POOL_X2 - POOL_X1 - 12);
            int y = POOL_Y + 9 - (int) (phase * 9);
            px(g, x, y, 0xC0FFFFFF & BREW_HI | 0xA0000000);
            if (phase > 0.85F) {
                px(g, x - 1, y - 1, 0x80FFFFFF);
                px(g, x + 1, y - 1, 0x80FFFFFF);
            }
        }
    }

    private void drawBubbles(GuiGraphics g, int color, float surface, long time) {
        int light = 0xFF000000 | shade(color, 1.8F);
        for (int i = 0; i < 9; i++) {
            float speed = 0.6F + (i % 3) * 0.25F;
            float phase = ((time / 1000.0F) * speed + i * 0.37F) % 1.0F;
            float y = BULB_BOTTOM - 2 - phase * (BULB_BOTTOM - 2 - surface);
            float x = CX - 12 + (i * 7) % 24 + Mth.sin(time / 250.0F + i) * 1.5F;
            int bx = Math.round(x);
            int by = Math.round(y);
            if (!flaskInside(bx, by) || !flaskInside(bx + 1, by + 1)) continue;
            if (i % 3 == 0) {
                px(g, bx, by, light);
                px(g, bx + 1, by, light);
                px(g, bx, by + 1, light);
                px(g, bx + 1, by + 1, 0x60FFFFFF);
            } else {
                px(g, bx, by, light);
            }
        }
    }

    /** Blaze gauge: how many brews the fuel has left, flickering while it burns. */
    private void drawGauge(GuiGraphics g, boolean brewing, long time) {
        int max = Config.FUEL_PER_BLAZE_POWDER.get();
        float f = Mth.clamp(menu.fuel() / (float) max, 0.0F, 1.0F);
        int w = GX2 - GX1 - 2;
        int filled = Math.round(w * f);
        for (int i = 0; i < filled; i++) {
            int c = lerp(0xFFB83A10, 0xFFFFD35A, i / (float) w);
            if (brewing && ((time / 90L + i) % 7L == 0L)) c = 0xFFFFF4C0;
            g.fill(leftPos + GX1 + 1 + i, topPos + GY1 + 1, leftPos + GX1 + 2 + i, topPos + GY2 - 1, c);
        }
        if (filled > 0) g.fill(leftPos + GX1 + filled, topPos + GY1 + 1, leftPos + GX1 + filled + 1, topPos + GY2 - 1, 0xFFFFF4C0);
    }

    private void drawGhosts(GuiGraphics g, IItemHandler items) {
        for (int i = 0; i < BrewLogic.SLOTS; i++) {
            if (i == BrewLogic.OUTPUT || !items.getStackInSlot(i).isEmpty()) continue;
            String[] icon = switch (i) {
                case BrewLogic.FUEL -> POWDER;
                case BrewLogic.DURATION -> DUST;
                case BrewLogic.POWER -> DUST;
                case BrewLogic.UP_1, BrewLogic.UP_2 -> ARROW;
                case BrewLogic.INGREDIENT -> WART;
                default -> BOTTLE;
            };
            int tint = switch (i) {
                case BrewLogic.FUEL -> 0xFFC89A5A;
                case BrewLogic.DURATION -> 0xFFB06A6A;
                case BrewLogic.POWER -> 0xFFC8B86A;
                case BrewLogic.INGREDIENT -> 0xFFB06A7A;
                default -> 0xFF7E8B77;
            };
            int[] p = MultiBrewerMenu.POS[i];
            drawIcon(g, icon, leftPos + p[0], topPos + p[1], tint);
        }
    }

    private void px(GuiGraphics g, int x, int y, int color) {
        g.fill(leftPos + x, topPos + y, leftPos + x + 1, topPos + y + 1, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, -10, PLAQUE_TEXT, false);
        IItemHandler items = menu.items();
        boolean vanilla = BrewLogic.canBrewVanilla(items);
        ItemStack result = vanilla ? BrewLogic.vanillaPreview(items) : BrewLogic.result(items);
        if (result != null && result.isEmpty()) result = null;

        if (result == null) {
            List<FormattedCharSequence> lines = font.split(Component.translatable("gui.multibrewer.hint"), PX2 - PX1 - 12);
            int ty = PY1 + (PY2 - PY1 - lines.size() * 10) / 2;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, PX1 + (PX2 - PX1 - font.width(line)) / 2, ty, TEXT_DIM, true);
                ty += 10;
            }
        } else {
            g.renderItem(result, PX1 + 3, PY1 + 3);
            g.drawString(font, Component.translatable(vanilla ? "gui.multibrewer.result_vanilla" : "gui.multibrewer.result"),
                    PX1 + 22, PY1 + 7, GOLD_L, true);
            List<MobEffectInstance> effects = PotionUtils.getMobEffects(result);
            int y = PY1 + 22;
            int shown = 0;
            for (MobEffectInstance e : effects) {
                if (y + 10 > PY2 - 1) break;
                TextureAtlasSprite sprite = minecraft.getMobEffectTextures().get(e.getEffect());
                g.pose().pushPose();
                g.pose().translate(PX1 + 4, y, 0);
                g.pose().scale(0.5F, 0.5F, 1.0F);
                g.blit(0, 0, 0, 18, 18, sprite);
                g.pose().popPose();
                String dur = e.getEffect().isInstantenous() ? "" : MobEffectUtil.formatDuration(e, 1.0F).getString();
                int durW = font.width(dur);
                Component name = Component.translatable(e.getDescriptionId());
                if (e.getAmplifier() > 0) name = Component.empty().append(name).append(" " + roman(e.getAmplifier() + 1));
                String text = font.plainSubstrByWidth(name.getString(), PX2 - PX1 - 20 - durW);
                int color = e.getEffect().isBeneficial() ? TEXT : RED;
                g.drawString(font, text, PX1 + 15, y + 1, color, true);
                g.drawString(font, dur, PX2 - 4 - durW, y + 1, TEXT_DIM, true);
                y += 11;
                shown++;
            }
            if (shown < effects.size()) {
                String more = "+" + (effects.size() - shown);
                g.drawString(font, more, PX2 - 4 - font.width(more), PY1 + 7, TEXT_DIM, true);
            }
        }

        Component status;
        int color;
        if (menu.progress() > 0) {
            status = Component.translatable(vanilla ? "gui.multibrewer.brewing_vanilla" : "gui.multibrewer.brewing",
                    Math.round(shownProgress * 100));
            color = GREEN;
        } else if (!vanilla && result != null && !items.getStackInSlot(BrewLogic.OUTPUT).isEmpty()) {
            status = Component.translatable("gui.multibrewer.output_full");
            color = RED;
        } else if (result != null && menu.fuel() <= 0 && items.getStackInSlot(BrewLogic.FUEL).isEmpty()) {
            status = Component.translatable("gui.multibrewer.no_fuel");
            color = RED;
        } else {
            status = Component.translatable(result != null ? "gui.multibrewer.ready" : "gui.multibrewer.idle");
            color = TEXT_DIM;
        }
        List<FormattedCharSequence> lines = font.split(status, 188 - PX1);
        for (int i = 0; i < lines.size() && i < 2; i++) g.drawString(font, lines.get(i), PX1 + 1, 104 + i * 10, color, true);
    }

    private void renderOwnTooltips(GuiGraphics g, int mx, int my) {
        int lx = mx - leftPos;
        int ly = my - topPos;
        List<Component> tip = new ArrayList<>();
        IItemHandler items = menu.items();
        if (lx >= GX1 && lx < GX2 + 8 && ly >= GY1 && ly < GY2) {
            tip.add(Component.translatable("gui.multibrewer.fuel", menu.fuel()));
        } else if (flaskInside(lx, ly)) {
            tip.add(Component.translatable("gui.multibrewer.flask", Math.round(shownProgress * 100),
                    String.format("%.1f", BrewLogic.brewTicks(items) / 20.0F)));
        } else if (hoveredSlot != null && hoveredSlot.index < BrewLogic.SLOTS) {
            String key = switch (hoveredSlot.index) {
                case BrewLogic.OUTPUT -> "gui.multibrewer.slot.output";
                case BrewLogic.FUEL -> "gui.multibrewer.slot.fuel";
                case BrewLogic.DURATION -> "gui.multibrewer.slot.duration";
                case BrewLogic.POWER -> "gui.multibrewer.slot.power";
                case BrewLogic.UP_1, BrewLogic.UP_2 -> "gui.multibrewer.slot.upgrade";
                case BrewLogic.INGREDIENT -> "gui.multibrewer.slot.ingredient";
                default -> "gui.multibrewer.slot.potion";
            };
            tip.add(Component.translatable(key));
            if (hoveredSlot.index == BrewLogic.DURATION) {
                tip.add(Component.translatable("gui.multibrewer.slot.duration.more", Math.round(BrewLogic.durationBonus(items) * 100)));
            } else if (hoveredSlot.index == BrewLogic.POWER) {
                tip.add(Component.translatable("gui.multibrewer.slot.power.more", BrewLogic.levelBonus(items), Config.MAX_LEVEL.get()));
            } else if (hoveredSlot.index == BrewLogic.UP_1 || hoveredSlot.index == BrewLogic.UP_2) {
                tip.add(Component.translatable("gui.multibrewer.upgrades",
                        BrewLogic.upgrades(items, UpgradeItem.Kind.SPEED), BrewLogic.upgrades(items, UpgradeItem.Kind.EFFICIENCY),
                        BrewLogic.upgrades(items, UpgradeItem.Kind.POTENCY)));
            }
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }

    private static String roman(int level) {
        String key = "enchantment.level." + level;
        String s = Component.translatable(key).getString();
        return s.equals(key) ? String.valueOf(level) : s;
    }

    private void drawTitlePlaque(GuiGraphics g, int mid, int y) {
        int half = font.width(title) / 2 + 8;
        int x1 = mid - half;
        int x2 = mid + half;
        g.fill(x1 - 1, y - 1, x2 + 1, y + 13, BLACK);
        g.fill(x1, y, x2, y + 12, 0xFFB4B9B2);
        g.fill(x1, y, x2, y + 1, 0xFFDDE2DA);
        g.fill(x1, y + 11, x2, y + 12, 0xFF7C827A);
        g.fill(x1 + 2, y + 2, x2 - 2, y + 3, 0x30000000);
        for (int side : new int[]{x1 - 5, x2 + 1}) {
            g.fill(side - 1, y + 1, side + 5, y + 11, BLACK);
            g.fill(side, y + 2, side + 4, y + 10, GOLD);
            g.fill(side, y + 2, side + 4, y + 3, GOLD_L);
            g.fill(side, y + 9, side + 4, y + 10, GOLD_D);
        }
    }

    // ---------------------------------------------------------------- pixel icons for empty slots

    private static final String[] BOTTLE = {
            ".....BB.....",
            ".....PP.....",
            "....BPPB....",
            "....B..B....",
            "...B....B...",
            "..B......B..",
            ".B........B.",
            ".B.CCCCCC.B.",
            ".BCCCCCCCCB.",
            ".BCCCCCCCCB.",
            "..BCCCCCCB..",
            "...BBBBBB..."};
    private static final String[] POWDER = {
            "............",
            ".....C......",
            "....CPC.....",
            "...C.P.C....",
            "..C..C..C...",
            ".....C......",
            "...CCCCC....",
            "..CPPPPPC...",
            ".CCCCCCCCC..",
            "............"};
    private static final String[] DUST = {
            "............",
            "...C....C...",
            "......C.....",
            ".C..CCC..C..",
            "...CCPCC....",
            "..CCPPPCC...",
            ".CCCCCCCCC..",
            "............"};
    private static final String[] WART = {
            "............",
            "....C..C....",
            "...CPCCPC...",
            "....CCCC....",
            "..C..CC..C..",
            ".CPC.CC.CPC.",
            "..CCCCCCCC..",
            ".....CC.....",
            ".....BB.....",
            "....BBBB...."};
    private static final String[] ARROW = {
            ".....B......",
            "....BPB.....",
            "...BPPPB....",
            "..BPPPPPB...",
            ".BBBPPPBBB..",
            "...BPPPB....",
            "...BPPPB....",
            "...BBBBB...."};

    private static void drawIcon(GuiGraphics g, String[] icon, int x, int y, int tint) {
        int top = y + (16 - icon.length) / 2;
        for (int r = 0; r < icon.length; r++) {
            String row = icon[r];
            for (int c = 0; c < row.length(); c++) {
                int color = switch (row.charAt(c)) {
                    case 'B' -> 0xFF5C6856;
                    case 'C' -> tint;
                    case 'P' -> 0xFFB6C0AE;
                    default -> 0;
                };
                if (color != 0) g.fill(x + 2 + c, top + r, x + 3 + c, top + r + 1, color);
            }
        }
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int gr = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return r << 16 | gr << 8 | b;
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }
}
