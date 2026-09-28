package com.reya.goldenquarry;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Laid out exactly like the reference GUI (textures/gui/vacuum_chest.png is built from it, see
 * tools/extract_vacuum.py): the 27 slots on top, under "Item filter" the white/black list switch
 * (the brown slot) and the five grey filter slots, under "Range" the number with its + and -
 * buttons and the red button that shows the working area; the player inventory in its own panel
 * underneath. All positions are texture pixels.
 */
public class VacuumChestMenu extends AbstractContainerMenu {
    public static final int WIDTH = 208;
    public static final int HEIGHT = 234;
    public static final int GRID_X = 24, GRID_Y = 28;
    public static final int MODE_X = 24, FILTER_X = 46, FILTER_Y = 96;
    public static final int INV_X = 24, INV_Y = 153, HOTBAR_Y = 211;

    public static final int FILTER_START = VacuumChestBlockEntity.SLOTS;
    public static final int MACHINE_SLOTS = FILTER_START + VacuumChestBlockEntity.FILTERS;

    public static final int BUTTON_PLUS = 0, BUTTON_MINUS = 1, BUTTON_AREA = 2, BUTTON_MODE = 3;

    @Nullable
    private final VacuumChestBlockEntity chest;
    private final BlockPos pos;
    private final ContainerData data;

    public VacuumChestMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof VacuumChestBlockEntity b ? b : null,
                new SimpleContainerData(3));
    }

    public VacuumChestMenu(int id, Inventory inventory, @Nullable VacuumChestBlockEntity chest, ContainerData data) {
        super(GoldenQuarry.VACUUM_CHEST_MENU.get(), id);
        this.chest = chest;
        this.pos = chest != null ? chest.getBlockPos() : BlockPos.ZERO;
        this.data = data;
        IItemHandler items = chest != null ? chest.items() : new ItemStackHandler(VacuumChestBlockEntity.SLOTS);
        IItemHandler filter = chest != null ? chest.filter() : new ItemStackHandler(VacuumChestBlockEntity.FILTERS);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new SlotItemHandler(items, r * 9 + c, GRID_X + c * 18, GRID_Y + r * 18));
            }
        }
        // filter slots only show a picture of an item: clicking sets or clears it (see clicked)
        for (int i = 0; i < VacuumChestBlockEntity.FILTERS; i++) {
            addSlot(new SlotItemHandler(filter, i, FILTER_X + i * 18, FILTER_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
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

    public int range() {
        return Math.max(1, data.get(0));
    }

    public boolean blacklist() {
        return data.get(1) != 0;
    }

    public boolean showArea() {
        return data.get(2) != 0;
    }

    public static boolean isFilterSlot(int index) {
        return index >= FILTER_START && index < MACHINE_SLOTS;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (isFilterSlot(slotId)) {
            Slot slot = slots.get(slotId);
            ItemStack carried = getCarried();
            if (slot instanceof SlotItemHandler s && s.getItemHandler() instanceof ItemStackHandler h) {
                h.setStackInSlot(s.getSlotIndex(), carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (chest == null) return false;
        switch (id) {
            case BUTTON_PLUS -> chest.setRange(chest.range() + 1);
            case BUTTON_MINUS -> chest.setRange(chest.range() - 1);
            case BUTTON_AREA -> chest.toggleShowArea();
            case BUTTON_MODE -> chest.toggleBlacklist();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (isFilterSlot(index) || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < FILTER_START) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, FILTER_START, false)) {
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
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !isFilterSlot(slot.index) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return chest != null && !chest.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
