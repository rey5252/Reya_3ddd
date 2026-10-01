package com.reya.alfheimheart.machine.orechid.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.orechid.OrechidMineBlockEntity;
import com.reya.alfheimheart.machine.orechid.OrechidMineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Orechid Mine's GUI: a window into the rock in the middle (orechid_mine.png) with the block being turned in
 * it, and round it the ores it may become, each on a socket, the likeliest first. As a craft charges, cracks of
 * light spread over the block and a light runs round the sockets; when it is done the block bursts and the ore
 * it became shines on its socket as it flies to the outputs.
 */
public class OrechidMineScreen extends MachineScreen<OrechidMineMenu> {
    public static final ResourceLocation PANEL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/orechid_mine.png");
    // the window and its sockets (tools/machines/layout.py MINE; check_layout.py keeps these in step)
    static final int MINE_X = 120, MINE_Y = 50, MINE_R = 19, ROCK_R = 15, ORE_R = 30, HEART_R = 37;
    static final int GEM_X = 120, GEM_Y = 31;
    static final int[][] ORES = {{131, 22}, {148, 39}, {148, 61}, {131, 78}, {109, 78}, {92, 61}, {92, 39}, {109, 22}};
    static final int HALO_U = 48, HALO_V = 96, HALO_SIZE = 15;
    private static final int CRACK = 0xB6F59A, CRACK_HOT = 0xF4FFE8;
    /** Six jagged cracks from the middle of the block out to its edge, as pixel offsets (worked out once). */
    private static final List<int[][]> CRACKS = new ArrayList<>();

    static {
        RandomSource random = RandomSource.create(1337L);
        for (int k = 0; k < 6; k++) {
            List<int[]> path = new ArrayList<>();
            float a = k * PI / 3.0F + 0.3F, x = 0.0F, y = 0.0F;
            while (x * x + y * y < 11.5F * 11.5F) {
                a += (random.nextFloat() - 0.5F) * 0.9F;
                x += Mth.cos(a);
                y += Mth.sin(a);
                int px = Math.round(x), py = Math.round(y);
                if (path.isEmpty() || path.get(path.size() - 1)[0] != px || path.get(path.size() - 1)[1] != py) path.add(new int[]{px, py});
            }
            CRACKS.add(path.toArray(new int[0][]));
        }
    }

    private ItemStack block = ItemStack.EMPTY;
    private final List<ItemStack> ores = new ArrayList<>();
    private final List<Integer> weights = new ArrayList<>();
    private int total;
    private float chase;

    public OrechidMineScreen(OrechidMineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL, MINE_X, MINE_Y, GEM_X, GEM_Y);
    }

    @Override
    protected String key() {
        return "orechid_mine";
    }

    /** The first block in the inputs the mine takes, and what it may become, the likeliest first. */
    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        block = ItemStack.EMPTY;
        ores.clear();
        weights.clear();
        total = 0;
        for (int i = 0; i < MachineBlockEntity.INPUTS; i++) {
            ItemStack stack = menu.items().getStackInSlot(MachineBlockEntity.INPUT_START + i);
            if (stack.isEmpty()) continue;
            Map<Item, Integer> chances = OrechidMineBlockEntity.chances(minecraft.level, stack);
            if (chances.isEmpty()) continue;
            block = stack.copyWithCount(1);
            List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(chances.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            for (Map.Entry<Item, Integer> e : sorted) {
                total += e.getValue();
                if (ores.size() < ORES.length) {
                    ores.add(new ItemStack(e.getKey()));
                    weights.add(e.getValue());
                }
            }
            return;
        }
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = shownProgress();
        if (busy) chase += dt * (2.0F + 6.0F * p);
        long since = t - craftedAt;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // the orechid's green glow in the window while it works
        float halo = busy ? 0.25F + 0.55F * p : 0.0F;
        if (since < 450L) halo = Math.max(halo, 1.0F - since / 450.0F);
        if (halo > 0.02F) {
            g.pose().pushPose();
            g.pose().translate(leftPos + MINE_X, topPos + MINE_Y, 0.0F);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.setColor(0.55F, 1.0F, 0.5F, halo);
            g.blit(WIDGETS, -HALO_SIZE / 2 - 1, -HALO_SIZE / 2 - 1, HALO_U, HALO_V, HALO_SIZE, HALO_SIZE, WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            g.pose().popPose();
        }
        // a light running round the sockets while it works
        if (busy && !ores.isEmpty()) {
            int head = (int) chase % ORES.length;
            for (int k = 0; k < 3; k++) {
                int i = Math.floorMod(head - k, ORES.length);
                sparkle(g, leftPos + ORES[i][0], topPos + ORES[i][1] - 7, GREEN_LIGHT, 1.0F - k * 0.3F);
            }
        }
        if (busy) progressRing(g, MINE_X, MINE_Y, MINE_R + 1.5F, p, GREEN_LIGHT, t);
    }

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        boolean busy = working();
        float p = shownProgress();
        long since = t - craftedAt;
        ItemStack last = menu.lastOutput();
        // the possible ores on their sockets: the one just made bigger and shining
        for (int i = 0; i < ores.size(); i++) {
            boolean made = since < 900L && !last.isEmpty() && last.getItem() == ores.get(i).getItem();
            float scale = made ? 0.55F + 0.25F * (1.0F - since / 900.0F) : 0.55F;
            floatingItem(g, ores.get(i), ORES[i][0] + 0.5F, ORES[i][1] + 0.5F, scale);
            if (made) sparkle(g, leftPos + ORES[i][0] + 4, topPos + ORES[i][1] - 4, 0xFFFFFF, 1.0F - since / 900.0F);
        }
        if (block.isEmpty()) return;
        // the block being turned, bursting after each craft and growing back
        float grow = since < 250L ? 0.0F : since < 550L ? backOut((since - 250L) / 300.0F) : 1.0F;
        float shake = busy ? 0.3F * p * Mth.sin(t / 25.0F) : 0.0F;
        floatingItem(g, block, MINE_X + shake, MINE_Y, 1.35F * grow);
        // cracks of light spreading over it as the craft charges
        if (busy && grow >= 1.0F && p > 0.02F) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            for (int[][] crack : CRACKS) {
                int lit = Math.round(crack.length * p);
                for (int k = 0; k < lit; k++) {
                    int c = k >= lit - 2 ? CRACK_HOT : CRACK;
                    int alpha = 140 + (int) (100 * p);
                    g.fill(leftPos + MINE_X + crack[k][0], topPos + MINE_Y + crack[k][1], leftPos + MINE_X + crack[k][0] + 1,
                            topPos + MINE_Y + crack[k][1] + 1, alpha << 24 | c);
                }
            }
            g.pose().popPose();
        }
    }

    @Override
    protected void onCraft(long t) {
        burst(MINE_X, MINE_Y, 14, GREEN_LIGHT, 1.3F);
        burst(MINE_X, MINE_Y, 8, 0xC8C8C8, 0.9F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        for (int i = 0; i < ores.size(); i++) {
            float dx = mx - ORES[i][0] - 0.5F, dy = my - ORES[i][1] - 0.5F;
            if (dx * dx + dy * dy <= 49.0F) {
                tip.add(ores.get(i).getHoverName().copy().withStyle(ChatFormatting.WHITE));
                tip.add(Component.translatable("gui.alfheimheart.orechid_mine.chance", chance(weights.get(i))).withStyle(ChatFormatting.GREEN));
                return;
            }
        }
        float dx = mx - MINE_X, dy = my - MINE_Y;
        if (dx * dx + dy * dy > HEART_R * HEART_R) return;
        tip.add(Component.translatable("gui.alfheimheart.orechid_mine.mine").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        if (!block.isEmpty()) {
            tip.add(Component.translatable("gui.alfheimheart.orechid_mine.turns", block.getHoverName(), ores.size()).withStyle(ChatFormatting.WHITE));
            if (menu.jobCost() > 0) tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(menu.jobCost())).withStyle(ChatFormatting.AQUA));
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.orechid_mine.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    private String chance(int weight) {
        float pct = total <= 0 ? 0.0F : weight * 100.0F / total;
        return pct >= 10.0F ? String.valueOf(Math.round(pct)) : String.format(java.util.Locale.ROOT, "%.1f", pct);
    }
}
