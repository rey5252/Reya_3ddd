package com.reya.multibrewer;

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
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Three potion slots over the flask, the result under it, blaze powder on the left, redstone and
 * glowstone on the right, two upgrade slots under the effect list; player inventory centred below.
 */
public class MultiBrewerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 236;
    public static final int[][] POS = {
            {22, 18}, {48, 14}, {74, 18},   // potions
            {48, 108},                     // result
            {18, 108},                     // blaze powder
            {88, 50}, {88, 76},            // redstone, glowstone
            {196, 108}, {218, 108}};       // upgrades
    public static final int INV_X = 47;
    public static final int INV_Y = 154;

    @Nullable
    private final MultiBrewerBlockEntity brewer;
    private final BlockPos pos;
    private final IItemHandler items;
    private final ContainerData data;

    /** Client side, from the open-screen packet. */
    public MultiBrewerMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof MultiBrewerBlockEntity b ? b : null,
                new SimpleContainerData(3));
    }

    public MultiBrewerMenu(int id, Inventory inventory, @Nullable MultiBrewerBlockEntity brewer, ContainerData data) {
        super(MultiBrewer.BREWER_MENU.get(), id);
        this.brewer = brewer;
        this.pos = brewer != null ? brewer.getBlockPos() : BlockPos.ZERO;
        this.items = brewer != null ? brewer.items() : new ItemStackHandler(BrewLogic.SLOTS);
        this.data = data;
        for (int i = 0; i < BrewLogic.SLOTS; i++) {
            addSlot(new SlotItemHandler(items, i, POS[i][0], POS[i][1]));
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

    public IItemHandler items() {
        return items;
    }

    public int progress() {
        return data.get(0);
    }

    public int maxProgress() {
        return Math.max(1, data.get(1));
    }

    public int fuel() {
        return data.get(2);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machine = BrewLogic.SLOTS;
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, slots.size(), true)) return ItemStack.EMPTY;
        } else if (BrewLogic.isPotion(stack)) {
            if (!moveItemStackTo(stack, BrewLogic.IN_1, BrewLogic.IN_3 + 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.BLAZE_POWDER)) {
            if (!moveItemStackTo(stack, BrewLogic.FUEL, BrewLogic.FUEL + 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.REDSTONE)) {
            if (!moveItemStackTo(stack, BrewLogic.DURATION, BrewLogic.DURATION + 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.GLOWSTONE_DUST)) {
            if (!moveItemStackTo(stack, BrewLogic.POWER, BrewLogic.POWER + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof UpgradeItem) {
            if (!moveItemStackTo(stack, BrewLogic.UP_1, BrewLogic.UP_2 + 1, false)) return ItemStack.EMPTY;
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
        return brewer != null && !brewer.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
