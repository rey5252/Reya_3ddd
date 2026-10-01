package com.reya.alfheimheart.machine.altar.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.altar.RuneAltarBlockEntity;
import com.reya.alfheimheart.machine.altar.RuneAltarMenu;
import com.reya.alfheimheart.machine.client.MachineScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.common.block.BotaniaBlocks;

/**
 * The Runic Altar's GUI: the altar's round top in the middle (rune_altar.png). The ingredients of the craft its
 * inputs make circle round it, faster as the mana goes in; the rune they make waits in the dark hollow, growing
 * as the craft charges, while the eight runes round the hollow light one after another and a ring of light runs
 * round the rim; when it is done the ingredients fall into the hollow and the rune flies out to the outputs.
 * The livingrock slot under the altar shows a ghost of livingrock while empty, and the stones up to the altar
 * light while there is some, a pulse running up them as a block of it is used.
 */
public class RuneAltarScreen extends MachineScreen<RuneAltarMenu> {
    public static final ResourceLocation PANEL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/rune_altar.png");
    // the altar (tools/machines/layout.py ALTAR; check_layout.py keeps these in step)
    static final int ALTAR_X = 120, ALTAR_Y = 46, ALTAR_R = 21, RECESS_R = 9, ORBIT_R = 30, HEART_R = 35;
    static final int GEM_X = 120, GEM_Y = 25;
    static final int[][] RUNES = {{123, 30}, {131, 38}, {131, 49}, {123, 57}, {112, 57}, {104, 49}, {104, 38}, {112, 30}};
    static final int[][] STONES = {{120, 72}, {120, 78}};
    static final int RUNE_U = 0, RUNE_V = 96, RUNE_SIZE = 5, HALO_U = 48, HALO_V = 96, HALO_SIZE = 15;

    @Nullable
    private MachineBlockEntity.Job job;
    /** The craft the inputs would make with livingrock, while there is none. */
    @Nullable
    private MachineBlockEntity.Job needsReagent;
    private int itemsHash = 1;
    private long checkedAt = -10000L;
    private float orbit;
    private List<ItemStack> falling = List.of();
    private long moteAt;

    public RuneAltarScreen(RuneAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL, ALTAR_X, ALTAR_Y, GEM_X, GEM_Y);
    }

    @Override
    protected String key() {
        return "rune_altar";
    }

    /** Works out again what the slots make, when they change (and now and then). */
    private void refresh(long t) {
        int hash = 1;
        for (int i = 0; i < RuneAltarBlockEntity.SLOTS; i++) {
            ItemStack s = menu.items().getStackInSlot(i);
            hash = hash * 31 + (s.isEmpty() ? 0 : s.getItem().hashCode() * 7 + s.getCount());
        }
        if (hash == itemsHash && t - checkedAt < 1000L) return;
        itemsHash = hash;
        checkedAt = t;
        if (minecraft == null || minecraft.level == null) return;
        job = RuneAltarBlockEntity.find(minecraft.level, menu.items(), true);
        needsReagent = job == null ? RuneAltarBlockEntity.find(minecraft.level, menu.items(), false) : null;
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
        float p = shownProgress();
        orbit += dt * (0.55F + (busy ? 1.4F + 4.5F * p : 0.0F));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // the hollow's glow
        float halo = busy ? 0.3F + 0.6F * p : job != null ? 0.22F : 0.0F;
        if (halo > 0.0F) {
            g.setColor(0.55F, 0.95F, 1.0F, halo * (0.85F + 0.15F * Mth.sin(t / 200.0F)));
            g.blit(WIDGETS, leftPos + ALTAR_X - HALO_SIZE / 2 - 1, topPos + ALTAR_Y - HALO_SIZE / 2 - 1, HALO_U, HALO_V, HALO_SIZE, HALO_SIZE,
                    WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        // motes turning in the hollow while nothing is made
        if (shown() == null) {
            for (int k = 0; k < 6; k++) {
                float a = t / 900.0F + k * PI / 3.0F;
                float r = 4.5F + 1.5F * Mth.sin(t / 700.0F + k);
                int x = leftPos + ALTAR_X + Math.round(Mth.cos(a) * r - 0.5F), y = topPos + ALTAR_Y + Math.round(Mth.sin(a) * r - 0.5F);
                int alpha = (int) (60 + 50 * (0.5F + 0.5F * Mth.sin(t / 300.0F + k * 2.0F)));
                g.fill(x, y, x + 1, y + 1, alpha << 24 | MANA_BRIGHT);
            }
        }
        // the runes: lit one after another as the craft charges, a light running round them while it waits
        int lit = busy ? Math.min(8, (int) (p * 8.0F)) : 0;
        for (int k = 0; k < RUNES.length; k++) {
            float a;
            if (k < lit) a = 1.0F;
            else if (busy && k == lit) a = 0.25F + 0.5F * (0.5F + 0.5F * Mth.sin(t / 90.0F));
            else if (job != null) a = runeChase(t, k);
            else a = 0.0F;
            float flash = t - craftedAt < 500L ? 1.0F - (t - craftedAt) / 500.0F : 0.0F;
            a = Math.max(a, flash);
            if (a <= 0.02F) continue;
            g.setColor(1.0F, 1.0F, 1.0F, a);
            g.blit(WIDGETS, leftPos + RUNES[k][0], topPos + RUNES[k][1], RUNE_U + k * RUNE_SIZE, RUNE_V, RUNE_SIZE, RUNE_SIZE, WIDGETS_W, WIDGETS_H);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (busy) progressRing(g, ALTAR_X, ALTAR_Y, ALTAR_R + 1.5F, p, MANA_BRIGHT, t);
        drawStones(g, t);
        // mana drifting in from the circling ingredients
        if (busy && t - moteAt > 70L) {
            moteAt = t;
            float a = random.nextFloat() * 2.0F * PI;
            float x = ALTAR_X + Mth.cos(a) * ORBIT_R, y = ALTAR_Y + Mth.sin(a) * ORBIT_R;
            float speed = 22.0F + 18.0F * p;
            drift(x, y, -Mth.cos(a) * speed, -Mth.sin(a) * speed, (ORBIT_R - RECESS_R) / speed, random.nextInt(3) == 0 ? GOLD_LIGHT : MANA_BRIGHT);
        }
    }

    private static float runeChase(long t, int k) {
        float head = (t / 220.0F) % 8.0F;
        float d = (head - k + 8.0F) % 8.0F;
        return d < 2.0F ? 0.4F * (1.0F - d / 2.0F) : 0.0F;
    }

    /** The stones up to the altar: lit while there is livingrock, a pulse running up them as a block of it is used. */
    private void drawStones(GuiGraphics g, long t) {
        boolean reagent = !menu.items().getStackInSlot(RuneAltarBlockEntity.REAGENT).isEmpty();
        long since = t - craftedAt;
        for (int i = 0; i < STONES.length; i++) {
            int x = leftPos + STONES[i][0], y = topPos + STONES[i][1];
            float a = reagent ? 0.35F + 0.15F * Mth.sin(t / 400.0F + i) : 0.0F;
            long at = (STONES.length - 1 - i) * 90L;
            if (since >= at && since < at + 260L) a = Math.max(a, 1.0F - (since - at) / 260.0F);
            if (a > 0.02F) sparkle(g, x, y, GOLD_LIGHT, a);
        }
    }

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        MachineBlockEntity.Job shown = shown();
        boolean busy = working();
        float p = shownProgress();
        long since = t - craftedAt;
        // the last craft's ingredients falling into the hollow
        if (since < 360L && !falling.isEmpty()) {
            float e = easeOut(since / 360.0F);
            int n = falling.size();
            for (int i = 0; i < n; i++) {
                float a = orbit + i * 2.0F * PI / n;
                float r = ORBIT_R * (1.0F - e);
                floatingItem(g, falling.get(i), ALTAR_X + Mth.cos(a) * r, ALTAR_Y + Mth.sin(a) * r, 0.62F * (1.0F - 0.8F * e));
            }
        }
        if (shown != null) {
            // the ingredients circling the altar (they grow back in after a craft)
            List<ItemStack> units = shown.units();
            int n = Math.min(8, units.size());
            float grow = since < 360L ? 0.0F : Math.min(1.0F, (since - 360L) / 300.0F);
            for (int i = 0; i < n; i++) {
                float a = orbit + i * 2.0F * PI / n;
                float bob = 1.2F * Mth.sin(t / 300.0F + i * 1.3F);
                floatingItem(g, units.get(i), ALTAR_X + Mth.cos(a) * ORBIT_R, ALTAR_Y + Mth.sin(a) * ORBIT_R + bob, 0.62F * backOut(grow));
            }
            // the rune they make, in the hollow
            ItemStack out = shown.outputs().isEmpty() ? ItemStack.EMPTY : shown.outputs().get(0);
            float scale = busy ? 0.55F + 0.3F * p + 0.04F * Mth.sin(t / 120.0F) : 0.55F;
            floatingItem(g, out, ALTAR_X, ALTAR_Y, scale);
            if (job == null) dim(g, ALTAR_X, ALTAR_Y, RECESS_R + 0.5F, 0x900B1C33);
        }
        // a ghost of livingrock in the empty reagent slot
        if (menu.items().getStackInSlot(RuneAltarBlockEntity.REAGENT).isEmpty()) {
            floatingItem(g, new ItemStack(BotaniaBlocks.livingrock), RuneAltarMenu.REAGENT_X + 8, RuneAltarMenu.REAGENT_Y + 8, 1.0F);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            int pulse = needsReagent != null ? (int) (40 + 40 * (0.5F + 0.5F * Mth.sin(t / 160.0F))) : 0;
            g.fill(leftPos + RuneAltarMenu.REAGENT_X, topPos + RuneAltarMenu.REAGENT_Y, leftPos + RuneAltarMenu.REAGENT_X + 16,
                    topPos + RuneAltarMenu.REAGENT_Y + 16, 0xA0200F08);
            if (pulse > 0) {
                g.renderOutline(leftPos + RuneAltarMenu.REAGENT_X - 1, topPos + RuneAltarMenu.REAGENT_Y - 1, 18, 18, pulse * 2 << 24 | 0xFF5A4A);
            }
            g.pose().popPose();
        }
    }

    /** A dark veil over a round part of the GUI (over the items there): a ghost of what isn't ready. */
    private void dim(GuiGraphics g, int cx, int cy, float r, int argb) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        for (int y = (int) -r; y < r; y++) {
            float w = Mth.sqrt(Math.max(0.0F, r * r - (y + 0.5F) * (y + 0.5F)));
            int x1 = Math.round(cx - w), x2 = Math.round(cx + w);
            g.fill(leftPos + x1, topPos + cy + y, leftPos + x2, topPos + cy + y + 1, argb);
        }
        g.pose().popPose();
    }

    @Override
    protected void onCraft(long t) {
        MachineBlockEntity.Job shown = shown();
        falling = shown == null ? List.of() : new ArrayList<>(shown.units());
        burst(ALTAR_X, ALTAR_Y, 12, MANA_BRIGHT, 1.1F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        float dx = mx - ALTAR_X, dy = my - ALTAR_Y;
        if (dx * dx + dy * dy > HEART_R * HEART_R) return;
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
    protected void specialSlotTooltip(List<Component> tip, int index) {
        tip.add(Component.translatable("gui.alfheimheart.rune_altar.slot.reagent").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("gui.alfheimheart.rune_altar.slot.reagent.tip").withStyle(ChatFormatting.GRAY));
    }
}
