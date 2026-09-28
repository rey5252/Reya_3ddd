package com.reya.goldenquarry;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Laid out exactly like the reference GUI (textures/gui/quarry.png is built from it): ores 9x3 in
 * the brown slots on top, the energy and progress bars in the middle, the results 9x3 in the golden
 * slots below; three golden upgrade slots and the grey rack on the right, the fortune module's slot
 * left of the bars; the player inventory in its own panel underneath. All positions are texture pixels.
 */
public class QuarryMenu extends AbstractContainerMenu {
    public static final int WIDTH = 212;
    public static final int HEIGHT = 247;
    public static final int INPUT_X = 15, INPUT_Y = 15;
    public static final int OUTPUT_X = 15, OUTPUT_Y = 95;
    /** Upgrade slots: the three golden frames on the right and the frame left of the bars. */
    public static final int[][] UPGRADE_POS = {{181, 19}, {181, 37}, {181, 55}, {16, 73}};
    public static final int INV_X = 26, INV_Y = 165, HOTBAR_Y = 223;

    public static final int INPUT_START = 0;
    public static final int OUTPUT_START = QuarryBlockEntity.STORAGE;
    public static final int UPGRADE_START = QuarryBlockEntity.STORAGE * 2;
    public static final int MACHINE_SLOTS = UPGRADE_START + QuarryBlockEntity.UPGRADES;

    @Nullable
    private final QuarryBlockEntity quarry;
    private final BlockPos pos;
    private final ContainerData data;

    public QuarryMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof QuarryBlockEntity b ? b : null,
                new SimpleContainerData(9));
    }

    public QuarryMenu(int id, Inventory inventory, @Nullable QuarryBlockEntity quarry, ContainerData data) {
        super(GoldenQuarry.QUARRY_MENU.get(), id);
        this.quarry = quarry;
        this.pos = quarry != null ? quarry.getBlockPos() : BlockPos.ZERO;
        this.data = data;
        IItemHandler input = quarry != null ? quarry.input() : new ItemStackHandler(QuarryBlockEntity.STORAGE);
        IItemHandler output = quarry != null ? quarry.output() : new ItemStackHandler(QuarryBlockEntity.STORAGE);
        IItemHandler upgrades = quarry != null ? quarry.upgrades() : new ItemStackHandler(QuarryBlockEntity.UPGRADES);
        addGrid(input, INPUT_X, INPUT_Y, true);
        addGrid(output, OUTPUT_X, OUTPUT_Y, false);
        for (int i = 0; i < QuarryBlockEntity.UPGRADES; i++) {
            addSlot(new SlotItemHandler(upgrades, i, UPGRADE_POS[i][0], UPGRADE_POS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return QuarryBlockEntity.fitsUpgradeSlot(getSlotIndex(), stack);
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inventory, c, INV_X + c * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    /** The ore grid takes ores; the result grid gives things out, nothing goes in. */
    private void addGrid(IItemHandler handler, int x, int y, boolean ores) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new SlotItemHandler(handler, r * 9 + c, x + c * 18, y + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return ores && QuarryBlockEntity.isOre(stack);
                    }
                });
            }
        }
    }

    public int energy() {
        return data.get(0) | data.get(1) << 15;
    }

    public int capacity() {
        return Math.max(1, data.get(2) | data.get(3) << 15);
    }

    public int progress() {
        return data.get(4);
    }

    public int maxProgress() {
        return Math.max(1, data.get(5));
    }

    public int status() {
        return data.get(7);
    }

    public int oresWaiting() {
        return data.get(6);
    }

    public int fortune() {
        return data.get(8);
    }

    @Nullable
    public QuarryBlockEntity quarry() {
        return quarry;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof QuarryUpgradeItem) {
            if (!moveItemStackTo(stack, UPGRADE_START, MACHINE_SLOTS, false)) return ItemStack.EMPTY;
        } else if (QuarryBlockEntity.isOre(stack)) {
            if (!moveItemStackTo(stack, INPUT_START, OUTPUT_START, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return quarry != null && !quarry.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
