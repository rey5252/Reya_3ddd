package com.reya.chaosspawner;

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
 * A row of eleven soul slots on top, 7x3 loot on the left, 3x3 upgrades on the right, the
 * experience bar and the collect button under them; player inventory centred below.
 */
public class ChaosSpawnerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 236;
    public static final int SOUL_X = 29, SOUL_Y = 16;
    public static final int OUT_X = 29, OUT_Y = 46;
    public static final int UP_X = 173, UP_Y = 46;
    public static final int INV_X = 47, INV_Y = 154;
    public static final int BUTTON_COLLECT = 0;

    @Nullable
    private final ChaosSpawnerBlockEntity spawner;
    private final BlockPos pos;
    private final IItemHandler items;
    private final ContainerData data;

    public ChaosSpawnerMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof ChaosSpawnerBlockEntity b ? b : null,
                new SimpleContainerData(5));
    }

    public ChaosSpawnerMenu(int id, Inventory inventory, @Nullable ChaosSpawnerBlockEntity spawner, ContainerData data) {
        super(ChaosSpawner.SPAWNER_MENU.get(), id);
        this.spawner = spawner;
        this.pos = spawner != null ? spawner.getBlockPos() : BlockPos.ZERO;
        this.items = spawner != null ? spawner.items() : new ItemStackHandler(ChaosSpawnerBlockEntity.SLOTS);
        this.data = data;
        for (int i = 0; i < ChaosSpawnerBlockEntity.SOULS; i++) {
            addSlot(new SlotItemHandler(items, ChaosSpawnerBlockEntity.SOUL_START + i, SOUL_X + i * 18, SOUL_Y));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 7; c++) {
                addSlot(new SlotItemHandler(items, ChaosSpawnerBlockEntity.OUTPUT_START + r * 7 + c, OUT_X + c * 18, OUT_Y + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlot(new SlotItemHandler(items, ChaosSpawnerBlockEntity.UPGRADE_START + r * 3 + c, UP_X + c * 18, UP_Y + r * 18));
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inventory, c, INV_X + c * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    public int progress() {
        return data.get(0);
    }

    public int maxProgress() {
        return Math.max(1, data.get(1));
    }

    public int storedXp() {
        return data.get(2) | data.get(3) << 15;
    }

    public boolean running() {
        return data.get(4) != 0;
    }

    public IItemHandler items() {
        return items;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_COLLECT && spawner != null) {
            spawner.giveExperience(player);
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
        int machine = ChaosSpawnerBlockEntity.SLOTS;
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, slots.size(), true)) return ItemStack.EMPTY;
        } else if (SoulCrystalItem.isSoul(stack)) {
            if (!moveItemStackTo(stack, ChaosSpawnerBlockEntity.SOUL_START, ChaosSpawnerBlockEntity.OUTPUT_START, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof SpawnerUpgradeItem) {
            if (!moveItemStackTo(stack, ChaosSpawnerBlockEntity.UPGRADE_START, machine, false)) return ItemStack.EMPTY;
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
        return spawner != null && !spawner.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
