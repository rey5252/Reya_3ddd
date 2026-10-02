package com.reya.alfheimheart.machine.orechid.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineLayouts.Mine;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.machine.client.Gfx;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.orechid.OrechidMineBlockEntity;
import com.reya.alfheimheart.machine.orechid.OrechidMineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.common.block.BotaniaFlowerBlocks;

/**
 * The Orechid Mine's GUI, the gem cavern (orechid_mine.png; tools/machines/cavern.py): the block being turned in a
 * window of livingrock in the middle of a ring of sockets in the rock, the ores it may become shining in them, the
 * likeliest first; the orechid on a mossy stone under it; the inputs in a minecart, a lantern of mana hanging on
 * the left, a chest for the ores on the right.
 * <p>
 * Crystals glow in the rock and dust drifts down through the lantern's light. As a craft charges, cracks of light
 * spread over the block and a light runs round the sockets, the orechid glows green, a ring closes round the
 * window; when it is done the block bursts, the ore it became shines in its socket and flies to the chest. The
 * lantern burns brighter the more mana there is.
 */
public class OrechidMineScreen extends MachineScreen<OrechidMineMenu> {
    private static final int CX = Mine.CX, CY = Mine.CY;
    private static final int CRACK = 0xB6F59A, CRACK_HOT = 0xF4FFE8, CRYSTAL = 0xB48CFF;
    private static final ItemStack ORECHID = new ItemStack(BotaniaFlowerBlocks.orechid);
    /** Six jagged cracks from the middle of the block out to its edge, as pixel offsets (worked out once). */
    private static final List<int[][]> CRACKS = new ArrayList<>();

    static {
        RandomSource random = RandomSource.create(1337L);
        for (int k = 0; k < 6; k++) {
            List<int[]> path = new ArrayList<>();
            float a = k * PI / 3.0F + 0.3F, x = 0.0F, y = 0.0F;
            while (x * x + y * y < 10.5F * 10.5F) {
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
    private long dustAt;

    public OrechidMineScreen(OrechidMineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "orechid_mine";
    }

    @Override
    protected int titleColour() {
        return 0xFFFFE9B0;
    }

    @Override
    protected int titleShadow() {
        return 0xFF0E0C0A;
    }

    @Override
    protected int lightColour() {
        return CRYSTAL;
    }

    @Override
    protected int[] veinColours() {
        return new int[]{0xF4FFE8, 0xB6F59A, 0x62CC42};
    }

    /** The first block in the inputs the mine takes, and what it may become, the likeliest first. */
    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        block = ItemStack.EMPTY;
        ores.clear();
        weights.clear();
        total = 0;
        for (int i = 0; i < layout.inputCount(); i++) {
            ItemStack stack = menu.items().getStackInSlot(MachineBlockEntity.INPUT_START + i);
            if (stack.isEmpty()) continue;
            Map<Item, Integer> chances = OrechidMineBlockEntity.chances(minecraft.level, stack);
            if (chances.isEmpty()) continue;
            block = stack.copyWithCount(1);
            List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(chances.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            for (Map.Entry<Item, Integer> e : sorted) {
                total += e.getValue();
                if (ores.size() < Mine.ORES.length) {
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
        // the crystals glowing in the rock, each on its own slow beat
        for (int i = 0; i < Mine.CRYSTALS.length; i++) {
            float beat = 0.5F + 0.5F * Mth.sin(t / (900.0F + i * 130.0F) + i * 1.7F);
            int[] c = Mine.CRYSTALS[i];
            Gfx.radial(g, leftPos + c[0], topPos + c[1] - 5.0F, 14.0F, Gfx.argb(CRYSTAL, 0.12F + 0.18F * beat), Gfx.argb(CRYSTAL, 0.0F), true);
        }
        // the orechid's green glow in the window while it works
        float halo = busy ? 0.25F + 0.55F * p : block.isEmpty() ? 0.0F : 0.12F;
        if (since < 450L) halo = Math.max(halo, 1.0F - since / 450.0F);
        if (halo > 0.02F) {
            Gfx.radial(g, leftPos + CX, topPos + CY, 22.0F + 8.0F * p, Gfx.argb(0x8CFF7A, halo * 0.7F), Gfx.argb(0x8CFF7A, 0.0F), true);
            Gfx.radial(g, leftPos + Mine.ORECHID[0], topPos + Mine.ORECHID[1] - 4.0F, 12.0F, Gfx.argb(0x8CFF7A, halo * 0.6F), 0x008CFF7A, true);
        }
        // a light running round the sockets while it works, a ring closing round the window
        if (busy && !ores.isEmpty()) {
            int head = (int) chase % Mine.ORES.length;
            for (int k = 0; k < 3; k++) {
                int i = Math.floorMod(head - k, Mine.ORES.length);
                Gfx.radial(g, leftPos + Mine.ORES[i][0] + 0.5F, topPos + Mine.ORES[i][1] + 0.5F, 9.0F, Gfx.argb(GREEN_LIGHT, 0.5F - k * 0.15F),
                        Gfx.argb(GREEN_LIGHT, 0.0F), true);
            }
            float start = -PI / 2.0F;
            Gfx.arc(g, leftPos + CX, topPos + CY, 18.0F, 21.0F, start, start + p * Gfx.TAU, k -> Gfx.argb(Gfx.mix(0xFF8CFF7A, 0xFFF4FFE8, k) & 0xFFFFFF,
                    0.85F), true, true);
        }
        // dust drifting down through the light
        if (t - dustAt > 400L) {
            dustAt = t;
            drift(20.0F + random.nextFloat() * (layout.width - 40), 10.0F, (random.nextFloat() - 0.5F) * 3.0F, 6.0F + random.nextFloat() * 4.0F,
                    3.0F + random.nextFloat() * 2.0F, 0xD8C8B0);
        }
    }

    // ------------------------------------------------------------------ the lantern

    @Override
    protected void drawGauge(GuiGraphics g, long t, float mana) {
        int[][] rows = Mine.LANTERN_ROWS;
        int n = Math.round(rows.length * Mth.clamp(mana, 0.0F, 1.0F));
        float flicker = 0.85F + 0.1F * Mth.sin(t / 90.0F) + 0.05F * Mth.sin(t / 37.0F);
        g.drawManaged(() -> {
            for (int i = 0; i < n; i++) {
                int y = rows[i][0], x1 = rows[i][1], x2 = rows[i][2];
                int base = Gfx.mix(0xFF2A9FE2, 0xFFE8FFFF, i / (float) rows.length) & 0xFFFFFF;
                for (int x = x1; x < x2; x++) {
                    int c = base;
                    if (i == n - 1) c = Gfx.mix(0xFF000000 | c, 0xFFFFFFFF, 0.5F + 0.5F * Mth.sin(t / 120.0F + x));
                    g.fill(leftPos + x, topPos + y, leftPos + x + 1, topPos + y + 1, (int) (220 * flicker) << 24 | c & 0xFFFFFF);
                }
            }
        });
        // its light on the rock round it
        float light = (0.1F + 0.5F * mana) * flicker;
        Gfx.radial(g, leftPos + Mine.LANTERN[0] + 0.5F, topPos + Mine.LANTERN[1], 30.0F + 14.0F * mana, Gfx.argb(0xCFF4FF, light * 0.5F),
                Gfx.argb(0xCFF4FF, 0.0F), true);
        if (menu.status() == MachineStatus.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            Gfx.radial(g, leftPos + Mine.LANTERN[0] + 0.5F, topPos + Mine.LANTERN[1], 14.0F, Gfx.argb(0xC82C26, 0.15F + 0.3F * beat), 0x00C82C26,
                    false);
        }
    }

    @Override
    protected boolean overGauge(int mx, int my) {
        return Math.abs(mx + 0.5F - Mine.LANTERN[0]) <= Mine.LANTERN[2] + 1 && Math.abs(my + 0.5F - Mine.LANTERN[1]) <= Mine.LANTERN[3] + 6;
    }

    // ------------------------------------------------------------------ over the items

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        boolean busy = working();
        float p = shownProgress();
        long since = t - craftedAt;
        ItemStack last = menu.lastOutput();
        // the orechid on its stone, swaying a little
        float sway = 4.0F * Mth.sin(t / 800.0F);
        g.pose().pushPose();
        g.pose().translate(leftPos + Mine.ORECHID[0], topPos + Mine.ORECHID[1] + 6.0F, 100.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sway));
        g.pose().scale(1.15F, 1.15F, 1.0F);
        g.renderItem(ORECHID, -8, -15);
        g.pose().popPose();
        // the possible ores in their sockets: the one just made bigger and shining
        for (int i = 0; i < ores.size(); i++) {
            boolean made = since < 900L && !last.isEmpty() && last.getItem() == ores.get(i).getItem();
            float scale = made ? 0.6F + 0.25F * (1.0F - since / 900.0F) : 0.6F;
            floatingItem(g, ores.get(i), Mine.ORES[i][0] + 0.5F, Mine.ORES[i][1] + 0.5F, scale);
            if (made) sparkle(g, leftPos + Mine.ORES[i][0] + 4, topPos + Mine.ORES[i][1] - 4, 0xFFFFFF, 1.0F - since / 900.0F);
        }
        if (block.isEmpty()) return;
        // the block being turned, bursting after each craft and growing back
        float grow = since < 250L ? 0.0F : since < 550L ? backOut((since - 250L) / 300.0F) : 1.0F;
        float shake = busy ? 0.3F * p * Mth.sin(t / 25.0F) : 0.0F;
        floatingItem(g, block, CX + shake, CY, 1.3F * grow);
        // cracks of light spreading over it as the craft charges
        if (busy && grow >= 1.0F && p > 0.02F) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            for (int[][] crack : CRACKS) {
                int lit = Math.round(crack.length * p);
                for (int k = 0; k < lit; k++) {
                    int c = k >= lit - 2 ? CRACK_HOT : CRACK;
                    int alpha = 140 + (int) (100 * p);
                    g.fill(leftPos + CX + crack[k][0], topPos + CY + crack[k][1], leftPos + CX + crack[k][0] + 1, topPos + CY + crack[k][1] + 1,
                            alpha << 24 | c);
                }
            }
            g.pose().popPose();
        }
    }

    @Override
    protected void onCraft(long t) {
        burst(CX, CY, 14, GREEN_LIGHT, 1.3F);
        burst(CX, CY, 8, 0xC8C8C8, 0.9F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        for (int i = 0; i < ores.size(); i++) {
            float dx = mx - Mine.ORES[i][0] - 0.5F, dy = my - Mine.ORES[i][1] - 0.5F;
            if (dx * dx + dy * dy <= 49.0F) {
                tip.add(ores.get(i).getHoverName().copy().withStyle(ChatFormatting.WHITE));
                tip.add(Component.translatable("gui.alfheimheart.orechid_mine.chance", chance(weights.get(i))).withStyle(ChatFormatting.GREEN));
                return;
            }
        }
        float dx = mx - CX, dy = my - CY;
        boolean ring = dx * dx + dy * dy <= (Mine.ORE_R + 8) * (Mine.ORE_R + 8);
        boolean flower = Math.abs(mx - Mine.ORECHID[0]) < 12 && Math.abs(my - Mine.ORECHID[1]) < 12;
        if (!ring && !flower) return;
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
