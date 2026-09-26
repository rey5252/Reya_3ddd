package com.reya.mobfarm.farm;

import javax.annotation.Nullable;

import com.reya.mobfarm.FarmTier;
import com.reya.mobfarm.ModRegistry;
import com.reya.mobfarm.lasso.LassoItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
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
 * Symmetric layout: lasso slot on the left, mob window in the middle (drawn by the screen),
 * 3x3 loot grid on the right, player inventory centred underneath.
 */
public class MobFarmMenu extends AbstractContainerMenu {
    public static final int WIDTH = 216;
    public static final int HEIGHT = 206;
    public static final int LASSO_X = 29;
    public static final int LASSO_Y = 42;
    public static final int GRID_X = 153;
    public static final int GRID_Y = 24;
    public static final int INV_X = 27;
    public static final int INV_Y = 124;

    @Nullable
    private final MobFarmBlockEntity farm;
    private final BlockPos pos;
    private final ContainerData data;
    private final IItemHandler handler;

    /** Client side, from the open-screen packet. */
    public MobFarmMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof MobFarmBlockEntity f ? f : null,
                new SimpleContainerData(4));
    }

    /** Server side. */
    public MobFarmMenu(int id, Inventory inventory, MobFarmBlockEntity farm) {
        this(id, inventory, farm, farm.getData());
    }

    private MobFarmMenu(int id, Inventory inventory, @Nullable MobFarmBlockEntity farm, ContainerData data) {
        super(ModRegistry.MOB_FARM_MENU.get(), id);
        this.farm = farm;
        this.pos = farm != null ? farm.getBlockPos() : BlockPos.ZERO;
        this.data = data;
        this.handler = farm != null ? farm.getItems() : new ItemStackHandler(MobFarmBlockEntity.OUTPUT_START + MobFarmBlockEntity.OUTPUT_COUNT);

        addSlot(new SlotItemHandler(handler, MobFarmBlockEntity.LASSO_SLOT, LASSO_X, LASSO_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(handler, MobFarmBlockEntity.OUTPUT_START + row * 3 + col,
                        GRID_X + col * 18, GRID_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    public int progress() {
        return data.get(0);
    }

    public int maxProgress() {
        return Math.max(1, data.get(1));
    }

    public int status() {
        return data.get(2);
    }

    public FarmTier tier() {
        return FarmTier.byOrdinal(data.get(3));
    }

    @Nullable
    public EntityType<?> mobType() {
        return LassoItem.getType(handler.getStackInSlot(MobFarmBlockEntity.LASSO_SLOT));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = 1 + MobFarmBlockEntity.OUTPUT_COUNT;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (LassoItem.hasMob(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
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
        return farm != null && !farm.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
