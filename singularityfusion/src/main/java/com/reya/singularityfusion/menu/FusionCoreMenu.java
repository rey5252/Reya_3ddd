package com.reya.singularityfusion.menu;

import javax.annotation.Nullable;

import com.reya.singularityfusion.SingularityFusion;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.gui.Layouts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** The fusion core's menu: its catalyst and output slots and the player's inventory. The screen reads the rest off the core. */
public class FusionCoreMenu extends AbstractContainerMenu {
    public static final int CATALYST = 0, OUTPUT = 1, PLAYER = 2;

    @Nullable
    private final FusionCoreBlockEntity core;
    private final BlockPos pos;

    public FusionCoreMenu(int id, Inventory inventory, FusionCoreBlockEntity core) {
        this(id, inventory, core, core.getBlockPos(), core.items());
    }

    private FusionCoreMenu(int id, Inventory inventory, @Nullable FusionCoreBlockEntity core, BlockPos pos, IItemHandler items) {
        super(SingularityFusion.FUSION_CORE_MENU.get(), id);
        this.core = core;
        this.pos = pos;
        addSlot(new SlotItemHandler(items, FusionCoreBlockEntity.CATALYST, Layouts.CATALYST[0], Layouts.CATALYST[1]));
        addSlot(new SlotItemHandler(items, FusionCoreBlockEntity.OUTPUT, Layouts.OUTPUT[0], Layouts.OUTPUT[1]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, Layouts.INV_X + col * 18, Layouts.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, Layouts.INV_X + col * 18, Layouts.HOTBAR_Y));
    }

    /** The client's: the core as its world has it (its slots, energy and the rest come from the core's updates). */
    public static FusionCoreMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof FusionCoreBlockEntity core) return new FusionCoreMenu(id, inventory, core);
        return new FusionCoreMenu(id, inventory, null, pos, new ItemStackHandler(2));
    }

    @Nullable
    public FusionCoreBlockEntity core() {
        return core;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < PLAYER) {
            if (!moveItemStackTo(stack, PLAYER, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, CATALYST, CATALYST + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return core != null && Container.stillValidBlockEntity(core, player);
    }
}
