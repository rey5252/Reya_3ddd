package com.reya.alfheimheart.machine;

import javax.annotation.Nullable;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * A mana machine's menu, laid out as every machine's GUI is: nine input slots on the left, nine output slots on
 * the right, the machine's own special slots where its heart is drawn (given by the machine's menu), the
 * player's inventory under the panel.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    // the layout of every machine's panel (tools/lib/machine_gui.py; the check_layout scripts keep them in step)
    public static final int WIDTH = 240, HEIGHT = 214;
    public static final int INPUT_X = 18, OUTPUT_X = 168, GRID_Y = 30;
    public static final int INV_X = 40, INV_Y = 132, HOTBAR_Y = 190;
    public static final int BUTTON_REDSTONE = 0;

    @Nullable
    protected final MachineBlockEntity machine;
    private final BlockPos pos;
    protected final IItemHandler items;
    protected final ContainerData data;
    private final int machineSlots;

    /**
     * @param special where the machine's special slots go: {x, y} for each, in the menu's coordinates
     */
    protected MachineMenu(MenuType<?> type, int id, Inventory inventory, @Nullable MachineBlockEntity machine, IItemHandler items,
                          ContainerData data, int[][] special) {
        super(type, id);
        this.machine = machine;
        this.pos = machine != null ? machine.getBlockPos() : BlockPos.ZERO;
        this.items = items;
        this.data = data;
        this.machineSlots = MachineBlockEntity.SPECIAL_START + special.length;
        checkContainerDataCount(data, data.getCount());
        for (int i = 0; i < MachineBlockEntity.INPUTS; i++) {
            addSlot(new SlotItemHandler(items, MachineBlockEntity.INPUT_START + i, INPUT_X + i % 3 * 18, GRID_Y + i / 3 * 18));
        }
        for (int i = 0; i < MachineBlockEntity.OUTPUTS; i++) {
            addSlot(new SlotItemHandler(items, MachineBlockEntity.OUTPUT_START + i, OUTPUT_X + i % 3 * 18, GRID_Y + i / 3 * 18));
        }
        for (int i = 0; i < special.length; i++) {
            addSlot(new SlotItemHandler(items, MachineBlockEntity.SPECIAL_START + i, special[i][0], special[i][1]));
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

    /** The client's menu: the machine's block entity in the client's world, if it is there (the server sent its place). */
    @Nullable
    protected static MachineBlockEntity clientMachine(Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return inventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity be ? be : null;
    }

    /** The client's copy of the machine's slots: it takes what the machine takes, so shift-clicks guess right. */
    protected static IItemHandler clientItems(@Nullable MachineBlockEntity machine, int slots) {
        return new ItemStackHandler(slots) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return machine == null || slot >= machine.items().getSlots() || machine.items().isItemValid(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return machine == null || slot >= machine.items().getSlots() ? 64 : machine.items().getSlotLimit(slot);
            }
        };
    }

    @Nullable
    public MachineBlockEntity machine() {
        return machine;
    }

    /** How many of the menu's slots are the machine's (inputs, outputs, special slots): the player's follow them. */
    public int machineSlots() {
        return machineSlots;
    }

    public IItemHandler items() {
        return items;
    }

    private int wide(int lo) {
        return (data.get(lo) & 0xFFFF) | (data.get(lo + 1) & 0xFFFF) << 16;
    }

    public int mana() {
        return wide(0);
    }

    public int capacity() {
        return Math.max(1, wide(2));
    }

    /** The craft being charged: its mana, and how much is in it so far (both 0 without one). */
    public int jobCost() {
        return wide(4);
    }

    public int jobCharged() {
        return wide(6);
    }

    /** The craft's ticks so far, and its shortest time. */
    public int jobTicks() {
        return data.get(12) & 0x7FFF;
    }

    public int jobMinTicks() {
        return data.get(13) & 0x7FFF;
    }

    /** How far the craft is: as far as its mana and its time allow, whichever is behind (0 without one). */
    public float progress() {
        if (!working()) return 0.0F;
        int cost = jobCost(), minTicks = jobMinTicks();
        float mana = cost > 0 ? Math.min(1.0F, jobCharged() / (float) cost) : 1.0F;
        float time = minTicks > 0 ? Math.min(1.0F, jobTicks() / (float) minTicks) : 1.0F;
        return Math.min(mana, time);
    }

    private int flags() {
        return data.get(8) & 0xFFFF;
    }

    public MachineStatus status() {
        MachineStatus[] all = MachineStatus.values();
        return all[(flags() & 7) % all.length];
    }

    public RedstoneMode redstone() {
        return RedstoneMode.byId(flags() >> 3 & 3);
    }

    public boolean hasPool() {
        return (flags() & 1 << 5) != 0;
    }

    /** Whether a craft is under way (charging, or waiting for mana or room). */
    public boolean working() {
        return (flags() & 1 << 6) != 0;
    }

    /** Crafts made (wraps round): the screen animates each new one. */
    public int crafts() {
        return data.get(9) & 0x7FFF;
    }

    public ItemStack lastOutput() {
        int id = wide(10);
        if (id <= 0) return ItemStack.EMPTY;
        Item item = Item.byId(id - 1);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** One of the machine's own numbers. */
    protected int extra(int index) {
        return data.get(MachineBlockEntity.BASE_DATA + index) & 0xFFFF;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) return false;
        if (id == BUTTON_REDSTONE) {
            machine.cycleRedstone();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inv = machineSlots;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, inv, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            // the special slots first (a reagent, a catalyst), then the inputs
            for (int i = MachineBlockEntity.SPECIAL_START; i < machineSlots && !moved; i++) {
                if (slots.get(i).mayPlace(stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved && slots.get(MachineBlockEntity.INPUT_START).mayPlace(stack)) {
                moved = moveItemStackTo(stack, MachineBlockEntity.INPUT_START, MachineBlockEntity.OUTPUT_START, false);
            }
            if (!moved) {
                if (index < inv + 27) {
                    if (!moveItemStackTo(stack, inv + 27, slots.size(), false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, inv, inv + 27, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine != null && !machine.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
