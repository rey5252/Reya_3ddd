package com.reya.alfheimheart.machine.altar.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineLayouts.Altar;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.machine.altar.RuneAltarBlockEntity;
import com.reya.alfheimheart.machine.altar.RuneAltarMenu;
import com.reya.alfheimheart.machine.client.Gfx;
import com.reya.alfheimheart.machine.client.MachineScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.common.block.BotaniaBlocks;

/**
 * The Runic Altar's GUI, the rune sanctum (rune_altar.png; tools/machines/sanctum.py): a ritual circle on a slate
 * floor, the altar in its middle, the inputs on a ring of sockets round it, each under one of the eight elements'
 * runes (a rainbow round the ring), the livingrock socket the ninth at the bottom; a crystal column of mana on the
 * left, an arched reliquary for the runes made on the right.
 * <p>
 * At rest a soft light goes round the ring lighting the empty sockets' runes one after another, and motes turn
 * in the altar's hollow. When the inputs make a rune, the sockets that give to it glow in their elements' colours
 * and the rune waits in the hollow, dim; as the craft charges, light runs in along the channels from those
 * sockets to the altar, a ring of every rune's colour fills round the altar, and the hollow glows brighter in the
 * colours of the elements mixing in it while the rune grows. When it is done, the ingredients fall into the
 * hollow, a wave of light runs out over the circle, and the rune flies to the reliquary, whose seal flashes as it
 * lands. The column's mana shimmers at its surface, bubbles rise in it while it works, its crystal glows with
 * how full it is, and the empty part blinks red when a craft waits for mana.
 */
public class RuneAltarScreen extends MachineScreen<RuneAltarMenu> {
    private static final int CX = Altar.CX, CY = Altar.CY, SOCKETS = 9;
    private static final int SEAL_X = (Altar.NICHE[0] + Altar.NICHE[2]) / 2, SEAL_Y = Altar.NICHE[1] + 17;
    private static final String[] ELEMENTS = {"fire", "autumn", "summer", "earth", "winter", "water", "air", "spring"};

    @Nullable
    private MachineBlockEntity.Job job;
    /** The craft the inputs would make with livingrock, while there is none. */
    @Nullable
    private MachineBlockEntity.Job needsReagent;
    /** Which inputs give to the craft shown. */
    private final boolean[] used = new boolean[SOCKETS];
    private final float[] glow = new float[SOCKETS];
    private int blend = MANA_BRIGHT;
    private List<ItemStack> falling = List.of();
    private final List<float[]> fallingFrom = new ArrayList<>();
    private long moteAt, bubbleAt, landedAt = -100000L;

    public RuneAltarScreen(RuneAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "rune_altar";
    }

    @Override
    protected int titleColour() {
        return 0xFFFFE9A8;
    }

    @Override
    protected int titleShadow() {
        return 0xFF0A0B15;
    }

    /** Where socket i is (its middle): the inputs, then the livingrock's. */
    private float[] socket(int i) {
        int[] at = i < layout.inputCount() ? layout.inputs[i] : layout.special[0];
        return new float[]{at[0] + 8.0F, at[1] + 8.0F};
    }

    private static int colour(int i) {
        return i < Altar.RUNE_COLOURS.length ? Altar.RUNE_COLOURS[i] : Altar.REAGENT_COLOUR;
    }

    /** Works out again what the slots make, when they change (and now and then). */
    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        job = RuneAltarBlockEntity.find(minecraft.level, menu.items(), true);
        needsReagent = job == null ? RuneAltarBlockEntity.find(minecraft.level, menu.items(), false) : null;
        MachineBlockEntity.Job shown = shown();
        int r = 0, g = 0, b = 0, n = 0;
        for (int i = 0; i < SOCKETS; i++) {
            ItemStack stack = i < layout.inputCount() ? menu.items().getStackInSlot(i) : menu.items().getStackInSlot(RuneAltarBlockEntity.REAGENT);
            used[i] = shown != null && !stack.isEmpty() && (i == SOCKETS - 1 ? job != null
                    : shown.units().stream().anyMatch(u -> ItemStack.isSameItemSameTags(u, stack)));
            if (used[i] && i < SOCKETS - 1) {
                int c = colour(i);
                r += c >> 16 & 0xFF;
                g += c >> 8 & 0xFF;
                b += c & 0xFF;
                n++;
            }
        }
        blend = n == 0 ? MANA_BRIGHT : (r / n) << 16 | (g / n) << 8 | b / n;
        blend = Gfx.mix(0xFF000000 | blend, 0xFFFFFFFF, 0.25F) & 0xFFFFFF;
    }

    @Nullable
    private MachineBlockEntity.Job shown() {
        return job != null ? job : needsReagent;
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        MachineBlockEntity.Job shown = shown();
        long since = t - craftedAt;
        float flash = since < 500L ? 1.0F - since / 500.0F : 0.0F;

        // the sockets: their glow in their elements' colours, their runes lit while they are empty
        float wave = (t / 950.0F) % SOCKETS;
        for (int i = 0; i < SOCKETS; i++) {
            float target;
            if (used[i] && busy) target = 0.55F + 0.25F * Mth.sin(t / 160.0F + i * 0.9F) + 0.3F * p;
            else if (used[i]) target = 0.28F + 0.1F * Mth.sin(t / 420.0F + i);
            else {
                float d = Math.abs(i - wave);
                d = Math.min(d, SOCKETS - d);
                target = 0.32F * Math.max(0.0F, 1.0F - d / 1.6F);
            }
            target = Math.max(target, flash * (used[i] ? 1.0F : 0.4F));
            glow[i] += (target - glow[i]) * Math.min(1.0F, dt * 7.0F);
            float[] at = socket(i);
            int s = Altar.HALO_SOCKET_SIZE;
            glowBlit(g, at[0] - s / 2.0F, at[1] - s / 2.0F, Altar.HALO_SOCKET_U, Altar.HALO_SOCKET_V, s, s, colour(i), glow[i]);
            boolean empty = i < layout.inputCount() ? menu.items().getStackInSlot(i).isEmpty()
                    : menu.items().getStackInSlot(RuneAltarBlockEntity.REAGENT).isEmpty();
            if (empty && i < SOCKETS - 1) {
                int[] corner = layout.inputs[i];
                glowBlit(g, corner[0] + 4, corner[1] + 4, Altar.GLYPH_U + i * Altar.GLYPH_SIZE, Altar.GLYPH_V, Altar.GLYPH_SIZE,
                        Altar.GLYPH_SIZE, colour(i), glow[i] * 2.2F);
            }
        }

        // the channels in to the altar: light from the sockets that give to the craft
        if (shown != null) {
            for (int i = 0; i < SOCKETS; i++) {
                if (!used[i]) continue;
                float[] sp = Altar.SPOKES[i];
                int c = colour(i);
                float a = busy ? 0.35F + 0.45F * p : 0.12F;
                Gfx.beam(g, leftPos + sp[0], topPos + sp[1], leftPos + sp[2], topPos + sp[3], 3.0F, Gfx.argb(c, a * 0.6F), Gfx.argb(c, a),
                        true);
                if (busy) {
                    // a bright pulse running in along it
                    float k = ((t / 520.0F) + i * 0.37F) % 1.0F;
                    float x = Mth.lerp(k, sp[0], sp[2]), y = Mth.lerp(k, sp[1], sp[3]);
                    Gfx.radial(g, leftPos + x, topPos + y, 3.2F, Gfx.argb(0xFFFFFF, 0.55F + 0.3F * p), Gfx.argb(c, 0.0F), true);
                }
            }
        }

        // the ring round the altar: every rune's colour, filling as the craft charges
        float ringR = Altar.INNER_R;
        if (shown != null) {
            Gfx.arc(g, leftPos + CX, topPos + CY, ringR - 2.2F, ringR + 2.2F, 0.0F, Gfx.TAU, k -> Gfx.argb(0xFFE47D, 0.10F), true, true);
        }
        if (busy && p > 0.0F) {
            float start = -PI / 2.0F;
            float turn = t / 4000.0F;
            Gfx.arc(g, leftPos + CX, topPos + CY, ringR - 2.6F, ringR + 2.6F, start, start + p * Gfx.TAU,
                    k -> Gfx.fade(Gfx.hue(k * p + turn, 0.55F, 1.0F), 0.85F), true, true);
            float head = start + p * Gfx.TAU;
            Gfx.radial(g, leftPos + CX + Mth.cos(head) * ringR, topPos + CY + Mth.sin(head) * ringR, 4.5F, 0xC0FFFFFF, 0x00FFFFFF, true);
        }

        // the hollow's light, in the colours of the elements mixing in it
        float hollow = busy ? 0.35F + 0.55F * p : shown != null ? 0.18F : 0.08F + 0.04F * Mth.sin(t / 700.0F);
        hollow = Math.max(hollow, flash);
        Gfx.radial(g, leftPos + CX, topPos + CY, Altar.HOLLOW_R + 3.0F + 4.0F * p + 6.0F * flash, Gfx.argb(blend, hollow),
                Gfx.argb(blend, 0.0F), true);
        if (shown == null) {
            for (int k = 0; k < 6; k++) {
                float a = t / 900.0F + k * PI / 3.0F;
                float r = 4.5F + 1.5F * Mth.sin(t / 700.0F + k);
                int x = leftPos + CX + Math.round(Mth.cos(a) * r - 0.5F), y = topPos + CY + Math.round(Mth.sin(a) * r - 0.5F);
                int alpha = (int) (60 + 50 * (0.5F + 0.5F * Mth.sin(t / 300.0F + k * 2.0F)));
                g.fill(x, y, x + 1, y + 1, alpha << 24 | MANA_BRIGHT);
            }
        }
        // mana drifting in from the sockets
        if (busy && t - moteAt > 80L) {
            moteAt = t;
            int i = random.nextInt(SOCKETS);
            if (used[i]) {
                float[] sp = Altar.SPOKES[i];
                float dx = sp[2] - sp[0], dy = sp[3] - sp[1], len = Mth.sqrt(dx * dx + dy * dy);
                float speed = 16.0F + 16.0F * p;
                drift(sp[0], sp[1], dx / len * speed, dy / len * speed, len / speed, colour(i));
            }
        }

        // the reliquary's seal: it glows while the altar works, and flashes as a rune lands
        long landed = t - landedAt;
        float seal = Math.max(busy ? 0.18F + 0.12F * Mth.sin(t / 300.0F) : 0.0F, landed < 700L ? 1.0F - landed / 700.0F : 0.0F);
        if (seal > 0.01F) Gfx.radial(g, leftPos + SEAL_X, topPos + SEAL_Y, 11.0F, Gfx.argb(0xD8B8FF, seal * 0.8F), 0x00D8B8FF, true);
    }

    // ------------------------------------------------------------------ the crystal column

    @Override
    protected void drawGauge(GuiGraphics g, long t, float mana) {
        int x1 = Altar.GAUGE[0], y1 = Altar.GAUGE[1], x2 = Altar.GAUGE[2], y2 = Altar.GAUGE[3];
        int h = y2 - y1, w = x2 - x1;
        int fill = Math.round(h * Mth.clamp(mana, 0.0F, 1.0F));
        boolean busy = working();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (fill > 0) {
            g.blit(widgets, leftPos + x1, topPos + y2 - fill, Altar.FILL_U, Altar.FILL_V + h - fill, w, fill, WIDGETS_W, WIDGETS_H);
            // the surface: a bright shimmering line, a soft light over it
            int sy = topPos + y2 - fill;
            for (int x = 0; x < w; x++) {
                float s = 0.55F + 0.45F * Mth.sin(t / 140.0F + x * 0.8F);
                g.fill(leftPos + x1 + x, sy, leftPos + x1 + x + 1, sy + 1, (int) (120 + 135 * s) << 24 | 0xF2FFFF);
            }
            Gfx.radial(g, leftPos + (x1 + x2) / 2.0F, sy, 7.0F, Gfx.argb(MANA_BRIGHT, 0.25F + (busy ? 0.15F : 0.0F)), 0x00A6F6FF, true);
            // a light rising up through it now and then
            float up = (t % 3200L) / 3200.0F;
            float ly = y2 - up * (h + 10);
            if (ly > y2 - fill) {
                Gfx.gradient(g, leftPos + x1, topPos + ly - 5, leftPos + x2, topPos + ly + 5, 0x00FFFFFF, 0x00FFFFFF, 0x30FFFFFF, 0x30FFFFFF, true);
            }
            if (busy && t - bubbleAt > 140L) {
                bubbleAt = t;
                drift(x1 + 1 + random.nextInt(w - 2), y2 - 1, 0.0F, -14.0F - random.nextFloat() * 10.0F, Math.min(3.0F, fill / 20.0F),
                        0xF2FFFF);
            }
        }
        if (menu.status() == MachineStatus.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            g.fill(leftPos + x1, topPos + y1, leftPos + x2, topPos + y2 - fill, (int) (20 + beat * 60) << 24 | 0xC82C26);
        }
        // the crystal on top glows with how full the column is
        float c = 0.15F + 0.5F * mana + 0.1F * Mth.sin(t / 500.0F);
        Gfx.radial(g, leftPos + (x1 + x2) / 2.0F, topPos + y1 - 11, 9.0F, Gfx.argb(MANA_BRIGHT, c), 0x00A6F6FF, true);
    }

    // ------------------------------------------------------------------ over the items

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        MachineBlockEntity.Job shown = shown();
        boolean busy = working();
        float p = shownProgress();
        long since = t - craftedAt;
        // the last craft's ingredients falling in along the channels
        if (since < 420L && !falling.isEmpty()) {
            float e = easeOut(since / 420.0F);
            for (int i = 0; i < falling.size() && i < fallingFrom.size(); i++) {
                float[] from = fallingFrom.get(i);
                floatingItem(g, falling.get(i), Mth.lerp(e, from[0], CX), Mth.lerp(e, from[1], CY), 0.75F * (1.0F - 0.8F * e));
            }
        }
        if (shown != null && !shown.outputs().isEmpty()) {
            // the rune taking shape in the hollow
            ItemStack out = shown.outputs().get(0);
            float grow = since < 420L ? 0.0F : Math.min(1.0F, (since - 420L) / 300.0F);
            float scale = (busy ? 0.6F + 0.35F * p + 0.04F * Mth.sin(t / 120.0F) : 0.6F) * backOut(grow);
            floatingItem(g, out, CX, CY, scale);
            if (job == null) dim(g, CX, CY, Altar.HOLLOW_R + 0.5F, 0x900B1C33);
        }
        // the wave of light running out over the circle as a rune is made
        if (since < 700L) {
            float e = easeOut(since / 700.0F);
            float r = Altar.HOLLOW_R + (Altar.OUTER_R - Altar.HOLLOW_R) * e;
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            Gfx.arc(g, leftPos + CX, topPos + CY, r - 3.0F, r + 3.0F, 0.0F, Gfx.TAU,
                    k -> Gfx.fade(Gfx.hue(k + t / 2000.0F, 0.35F, 1.0F), 0.75F * (1.0F - e)), true, true);
            g.pose().popPose();
        }
        // a ghost of livingrock in the empty reagent socket
        if (menu.items().getStackInSlot(RuneAltarBlockEntity.REAGENT).isEmpty()) {
            ghostSlot(g, layout.special[0][0], layout.special[0][1], new ItemStack(BotaniaBlocks.livingrock), needsReagent != null, t);
        }
    }

    @Override
    protected void onCraft(long t) {
        falling = new ArrayList<>();
        fallingFrom.clear();
        for (int i = 0; i < SOCKETS - 1; i++) {
            if (!used[i]) continue;
            ItemStack stack = menu.items().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            falling.add(stack.copyWithCount(1));
            fallingFrom.add(socket(i));
        }
        burst(CX, CY, 14, MANA_BRIGHT, 1.2F);
        burst(CX, CY, 8, blend, 1.0F);
    }

    @Override
    protected void onLanded(long t) {
        landedAt = t;
        burst(SEAL_X, SEAL_Y, 8, 0xD8B8FF, 0.7F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        float dx = mx - CX, dy = my - CY;
        if (dx * dx + dy * dy > Altar.OUTER_R * Altar.OUTER_R) return;
        tip.add(Component.translatable("gui.alfheimheart.rune_altar.altar").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        MachineBlockEntity.Job shown = shown();
        if (shown != null && !shown.outputs().isEmpty()) {
            tip.add(Component.translatable("gui.alfheimheart.makes", shown.outputs().get(0).getHoverName()).withStyle(ChatFormatting.WHITE));
            tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(shown.mana())).withStyle(ChatFormatting.AQUA));
            if (needsReagent != null) tip.add(Component.translatable("gui.alfheimheart.rune_altar.needs_reagent").withStyle(ChatFormatting.RED));
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.rune_altar.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void inputSlotTooltip(List<Component> tip, int index) {
        if (index < ELEMENTS.length) {
            int c = colour(index);
            tip.add(Component.translatable("gui.alfheimheart.rune_altar.socket." + ELEMENTS[index]).withStyle(s -> s.withColor(c)));
        }
        tip.add(Component.translatable("gui.alfheimheart.rune_altar.slot.input.tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void specialSlotTooltip(List<Component> tip, int index) {
        tip.add(Component.translatable("gui.alfheimheart.rune_altar.slot.reagent").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("gui.alfheimheart.rune_altar.slot.reagent.tip").withStyle(ChatFormatting.GRAY));
    }
}
