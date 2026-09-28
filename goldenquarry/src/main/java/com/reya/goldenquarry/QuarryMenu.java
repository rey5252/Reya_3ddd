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
 * Laid out exactly like the reference GUI (textures/gui/quarry.png is built from it): plain blocks
 * 9x3 in the brown slots on top, the energy and progress bars in the middle, valuables 9x3 in the
 * golden slots below; three switches and the grey upgrade rack on the right; the player inventory
 * in its own panel underneath. All positions are texture pixels.
 */
public class QuarryMenu extends AbstractContainerMenu {
    public static final int WIDTH = 212;
    public static final int HEIGHT = 247;
    public static final int COMMON_X = 15, COMMON_Y = 15;
    public static final int VALUABLE_X = 15, VALUABLE_Y = 95;
    public static final int UP_X = 185, UP_Y = 84, UP_STEP = 17;
    public static final int BUTTON_X = 181, BUTTON_Y = 18, BUTTON_STEP = 18, BUTTON_SIZE = 18;
    public static final int INV_X = 26, INV_Y = 165, HOTBAR_Y = 223;
    /** Buttons from top to bottom, in the order of their icons in the reference. */
    public static final int BUTTON_AREA = 0, BUTTON_POWER = 1, BUTTON_VOID = 2;

    public static final int COMMON_START = 0;
    public static final int VALUABLE_START = QuarryBlockEntity.STORAGE;
    public static final int UPGRADE_START = QuarryBlockEntity.STORAGE * 2;
    public static final int MACHINE_SLOTS = UPGRADE_START + QuarryBlockEntity.UPGRADES;

    @Nullable
    private final QuarryBlockEntity quarry;
    private final BlockPos pos;
    private final ContainerData data;

    public QuarryMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof QuarryBlockEntity b ? b : null,
                new SimpleContainerData(10));
    }

    public QuarryMenu(int id, Inventory inventory, @Nullable QuarryBlockEntity quarry, ContainerData data) {
        super(GoldenQuarry.QUARRY_MENU.get(), id);
        this.quarry = quarry;
        this.pos = quarry != null ? quarry.getBlockPos() : BlockPos.ZERO;
        this.data = data;
        IItemHandler common = quarry != null ? quarry.common() : new ItemStackHandler(QuarryBlockEntity.STORAGE);
        IItemHandler valuables = quarry != null ? quarry.valuables() : new ItemStackHandler(QuarryBlockEntity.STORAGE);
        IItemHandler upgrades = quarry != null ? quarry.upgrades() : new ItemStackHandler(QuarryBlockEntity.UPGRADES);
        addGrid(common, COMMON_X, COMMON_Y);
        addGrid(valuables, VALUABLE_X, VALUABLE_Y);
        for (int i = 0; i < QuarryBlockEntity.UPGRADES; i++) {
            addSlot(new SlotItemHandler(upgrades, i, UP_X, UP_Y + i * UP_STEP) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof QuarryUpgradeItem;
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

    /** Output grid: things can be taken out, never put in. */
    private void addGrid(IItemHandler handler, int x, int y) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new SlotItemHandler(handler, r * 9 + c, x + c * 18, y + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
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

    public boolean enabled() {
        return (data.get(6) & QuarryBlockEntity.FLAG_ENABLED) != 0;
    }

    public boolean showArea() {
        return (data.get(6) & QuarryBlockEntity.FLAG_SHOW_AREA) != 0;
    }

    public boolean voidJunk() {
        return (data.get(6) & QuarryBlockEntity.FLAG_VOID_JUNK) != 0;
    }

    public int status() {
        return data.get(7);
    }

    public int layer() {
        return data.get(8);
    }

    public int radius() {
        return data.get(9);
    }

    @Nullable
    public QuarryBlockEntity quarry() {
        return quarry;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (quarry == null) return false;
        switch (id) {
            case BUTTON_POWER -> quarry.toggleEnabled();
            case BUTTON_AREA -> quarry.toggleShowArea();
            case BUTTON_VOID -> quarry.toggleVoidJunk();
            default -> {
                return false;
            }
        }
        return true;
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
