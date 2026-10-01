package com.reya.alfheimheart.machine.apothecary.client;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.apothecary.PetalApothecaryBlockEntity;
import com.reya.alfheimheart.machine.apothecary.PetalApothecaryMenu;
import com.reya.alfheimheart.machine.client.MachineScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Petal Apothecary's GUI: a livingrock bowl of water in the middle (petal_apothecary.png) on its stem over the
 * seeds slot. The petals of the craft its inputs make float on the water, turning; as the craft charges they
 * spiral into the middle, the water takes their colours and the flower they make rises out of it over the bowl,
 * a ring of light closing round it; when it is done the water splashes and the flower flies out to the outputs.
 */
public class PetalApothecaryScreen extends MachineScreen<PetalApothecaryMenu> {
    public static final ResourceLocation PANEL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/petal_apothecary.png");
    // the bowl (tools/machines/layout.py BOWL; check_layout.py keeps these in step)
    static final int BOWL_X = 120, BOWL_Y = 46, WATER_RX = 21, WATER_RY = 5;
    static final int ITEM_X = 120, ITEM_Y = 22, ITEM_RING_R = 11;
    static final int GEM_X = 120, GEM_Y = 57;
    static final int[] HEART_BOX = {90, 8, 150, 80};
    static final int HALO_U = 48, HALO_V = 96, HALO_SIZE = 15;
    private static final int DEEP = 0x14485C, MID = 0x2E86A8, LIGHT = 0x6CC6E0, BRIGHT = 0xCFF4FF;

    @Nullable
    private MachineBlockEntity.Job job;
    @Nullable
    private MachineBlockEntity.Job needsReagent;
    private float swirl;
    private long moteAt;

    public PetalApothecaryScreen(PetalApothecaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL, ITEM_X, ITEM_Y, GEM_X, GEM_Y);
    }

    @Override
    protected String key() {
        return "petal_apothecary";
    }

    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        job = PetalApothecaryBlockEntity.find(minecraft.level, menu.items(), true);
        needsReagent = job == null ? PetalApothecaryBlockEntity.find(minecraft.level, menu.items(), false) : null;
    }

    @Nullable
    private MachineBlockEntity.Job shown() {
        return job != null ? job : needsReagent;
    }

    static int colourOf(ItemStack stack) {
        return dyeColour(stack);
    }

    private static int mix(int a, int b, float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);
        return Math.round(Mth.lerp(p, a >> 16 & 0xFF, b >> 16 & 0xFF)) << 16 | Math.round(Mth.lerp(p, a >> 8 & 0xFF, b >> 8 & 0xFF)) << 8
                | Math.round(Mth.lerp(p, a & 0xFF, b & 0xFF));
    }

    /** The colour the petals give the water: theirs, mixed. */
    private int petalTint() {
        MachineBlockEntity.Job shown = shown();
        if (shown == null || shown.units().isEmpty()) return MID;
        int r = 0, g = 0, b = 0, n = 0;
        for (ItemStack unit : shown.units()) {
            int c = colourOf(unit);
            r += c >> 16 & 0xFF;
            g += c >> 8 & 0xFF;
            b += c & 0xFF;
            n++;
        }
        return (r / n) << 16 | (g / n) << 8 | b / n;
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = shownProgress();
        swirl += dt * (0.5F + (busy ? 1.5F + 3.0F * p : 0.0F));
        long since = t - craftedAt;
        int tint = petalTint();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.drawManaged(() -> water(g, t, busy ? p : 0.0F, tint, since));
        // the petals turning on the water, spiralling in as the craft charges
        MachineBlockEntity.Job shown = shown();
        if (shown != null) {
            List<ItemStack> units = shown.units();
            int n = Math.min(12, units.size());
            g.drawManaged(() -> {
                for (int i = 0; i < n; i++) {
                    float a = swirl + i * 2.0F * PI / n;
                    float r = 0.75F - (busy ? 0.6F * p : 0.0F) + 0.06F * Mth.sin(t / 500.0F + i);
                    int x = leftPos + BOWL_X + Math.round(Mth.cos(a) * r * WATER_RX - 1.0F);
                    int y = topPos + BOWL_Y + Math.round(Mth.sin(a) * r * WATER_RY - 0.5F);
                    int c = colourOf(units.get(i));
                    g.fill(x, y, x + 2, y + 1, 0xFF000000 | c);
                    g.fill(x, y + 1, x + 2, y + 2, 0xFF000000 | mix(c, 0x000000, 0.3F));
                }
            });
        }
        // the light round the flower over the bowl
        float halo = busy ? 0.25F + 0.6F * p : job != null ? 0.15F : 0.0F;
        if (since < 400L) halo = Math.max(halo, 1.0F - since / 400.0F);
        if (halo > 0.02F) {
            int c = mix(0xFFFFFF, tint, 0.5F);
            g.setColor((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F, halo);
            g.blit(WIDGETS, leftPos + ITEM_X - HALO_SIZE / 2 - 1, topPos + ITEM_Y - HALO_SIZE / 2 - 1, HALO_U, HALO_V, HALO_SIZE, HALO_SIZE,
                    WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (busy) progressRing(g, ITEM_X, ITEM_Y, ITEM_RING_R, p, mix(0xFFFFFF, tint, 0.6F), t);
        // colour rising from the water into the flower
        if (busy && t - moteAt > 80L && shown != null && !shown.units().isEmpty()) {
            moteAt = t;
            float x = BOWL_X + (random.nextFloat() - 0.5F) * WATER_RX * 1.2F, y = BOWL_Y + (random.nextFloat() - 0.5F) * WATER_RY;
            float life = 0.6F + random.nextFloat() * 0.3F;
            drift(x, y, (ITEM_X - x) / life, (ITEM_Y + 4 - y) / life, life, colourOf(shown.units().get(random.nextInt(shown.units().size()))));
        }
    }

    /** The water in the bowl: light running over it, taking the petals' colours as the craft charges, a ripple after each. */
    private void water(GuiGraphics g, long t, float p, int tint, long since) {
        for (int y = -WATER_RY; y < WATER_RY; y++) {
            float fy = (y + 0.5F) / WATER_RY;
            float w = WATER_RX * Mth.sqrt(Math.max(0.0F, 1.0F - fy * fy));
            int x1 = (int) Math.ceil(BOWL_X - w - 0.5F), x2 = (int) Math.floor(BOWL_X + w - 0.5F);
            for (int x = x1; x <= x2; x++) {
                float wave = Mth.sin((x - BOWL_X) * 0.5F + y * 1.1F + t / 300.0F) + 0.5F * Mth.sin((x - BOWL_X) * -0.25F + y * 1.5F - t / 450.0F);
                float k = (wave + 1.5F) / 3.0F;
                int col = mix(DEEP, MID, 0.4F + 0.6F * k);
                if (k > 0.8F) col = mix(col, LIGHT, (k - 0.8F) / 0.2F);
                col = mix(col, DEEP, Math.max(0.0F, -fy) * 0.3F);
                col = mix(col, tint, 0.15F + 0.45F * p);
                g.fill(leftPos + x, topPos + BOWL_Y + y, leftPos + x + 1, topPos + BOWL_Y + y + 1, 0xF0000000 | col);
            }
        }
        if (since < 600L) {
            float e = easeOut(since / 600.0F);
            float rx = 2.0F + (WATER_RX - 2.0F) * e, ry = 0.6F + (WATER_RY - 0.6F) * e;
            int alpha = (int) (220 * (1.0F - e));
            int steps = (int) (rx * 5.0F);
            for (int s = 0; s < steps; s++) {
                float a = s / (float) steps * 2.0F * PI;
                int x = leftPos + BOWL_X + Math.round(Mth.cos(a) * rx - 0.5F), y = topPos + BOWL_Y + Math.round(Mth.sin(a) * ry - 0.5F);
                g.fill(x, y, x + 1, y + 1, alpha << 24 | BRIGHT);
            }
        }
    }

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        MachineBlockEntity.Job shown = shown();
        boolean busy = working();
        float p = shownProgress();
        if (shown != null && !shown.outputs().isEmpty()) {
            // the flower rising out of the water as the craft charges
            float rise = busy ? p : 0.0F;
            float bob = 1.2F * Mth.sin(t / 360.0F);
            floatingItem(g, shown.outputs().get(0), ITEM_X, ITEM_Y + (1.0F - rise) * 6.0F + bob, 0.7F + 0.3F * rise);
            if (job == null) dim(g, ITEM_X, ITEM_Y + 6.0F, 9.0F, 0x900B1C24);
        }
        if (menu.items().getStackInSlot(PetalApothecaryBlockEntity.REAGENT).isEmpty()) {
            ghostSlot(g, PetalApothecaryMenu.REAGENT_X, PetalApothecaryMenu.REAGENT_Y, new ItemStack(Items.WHEAT_SEEDS), needsReagent != null, t);
        }
    }

    @Override
    protected void onCraft(long t) {
        burst(BOWL_X, BOWL_Y, 12, mix(0xFFFFFF, petalTint(), 0.6F), 1.0F);
        burst(ITEM_X, ITEM_Y, 8, PINK_LIGHT, 0.8F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        if (mx < HEART_BOX[0] || mx >= HEART_BOX[2] || my < HEART_BOX[1] || my >= HEART_BOX[3]) return;
        tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.bowl").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        MachineBlockEntity.Job shown = shown();
        if (shown != null && !shown.outputs().isEmpty()) {
            tip.add(Component.translatable("gui.alfheimheart.makes", shown.outputs().get(0).getHoverName()).withStyle(ChatFormatting.WHITE));
            if (shown.mana() > 0) tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(shown.mana())).withStyle(ChatFormatting.AQUA));
            if (needsReagent != null) tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.needs_reagent").withStyle(ChatFormatting.RED));
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void specialSlotTooltip(List<Component> tip, int index) {
        tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.slot.reagent").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("gui.alfheimheart.petal_apothecary.slot.reagent.tip").withStyle(ChatFormatting.GRAY));
    }
}
