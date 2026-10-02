package com.reya.boundlessrouters.router;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.reya.boundlessrouters.BoundlessRouters;
import com.reya.boundlessrouters.gui.Layouts;
import com.reya.boundlessrouters.module.ModuleItem;
import com.reya.boundlessrouters.upgrade.UpgradeItem;
import com.reya.boundlessrouters.upgrade.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
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

/** The router's menu: its buffer, module and upgrade slots, the player's inventory, and the router's numbers. */
public class RouterMenu extends AbstractContainerMenu {
    public static final int BUFFER = 0, MODULES = 1, UPGRADES = MODULES + RouterBlockEntity.MODULE_SLOTS,
            PLAYER = UPGRADES + RouterBlockEntity.UPGRADE_SLOTS;
    public static final int BUTTON_REDSTONE = 0, BUTTON_REDSTONE_BACK = 1;

    @Nullable
    private final RouterBlockEntity router;
    private final BlockPos pos;
    private final ContainerData data;

    /** The client's menu. */
    public RouterMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), new ItemStackHandler(1), moduleSlots(), upgradeSlots(),
                new SimpleContainerData(RouterBlockEntity.DATA_COUNT));
    }

    /** The server's menu. */
    public RouterMenu(int id, Inventory inventory, RouterBlockEntity router) {
        this(id, inventory, router, router.getBlockPos(), router.buffer(), router.modules(), router.upgrades(), router.data());
    }

    private RouterMenu(int id, Inventory inventory, @Nullable RouterBlockEntity router, BlockPos pos, IItemHandler buffer,
                       IItemHandler modules, IItemHandler upgrades, ContainerData data) {
        super(BoundlessRouters.ROUTER_MENU.get(), id);
        this.router = router;
        this.pos = pos;
        this.data = data;
        checkContainerDataCount(data, RouterBlockEntity.DATA_COUNT);
        addSlot(new SlotItemHandler(buffer, 0, Layouts.Router.BUFFER[0], Layouts.Router.BUFFER[1]));
        for (int i = 0; i < RouterBlockEntity.MODULE_SLOTS; i++) {
            addSlot(new SlotItemHandler(modules, i, Layouts.Router.MODULES[i][0], Layouts.Router.MODULES[i][1]));
        }
        for (int i = 0; i < RouterBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new SlotItemHandler(upgrades, i, Layouts.Router.UPGRADES[i][0], Layouts.Router.UPGRADES[i][1]));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, 9 + r * 9 + c, Layouts.Router.INV_X + c * 18, Layouts.Router.INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, Layouts.Router.INV_X + c * 18, Layouts.Router.HOTBAR_Y));
        addDataSlots(data);
    }

    /** The client's copy of the module slots: modules only, one each. */
    private static ItemStackHandler moduleSlots() {
        return new ItemStackHandler(RouterBlockEntity.MODULE_SLOTS) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.getItem() instanceof ModuleItem;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    private static ItemStackHandler upgradeSlots() {
        return new ItemStackHandler(RouterBlockEntity.UPGRADE_SLOTS) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.getItem() instanceof UpgradeItem;
            }
        };
    }

    @Nullable
    public RouterBlockEntity router() {
        return router;
    }

    public BlockPos pos() {
        return pos;
    }

    public int interval() {
        return data.get(0);
    }

    public int itemsPerRun() {
        return data.get(1);
    }

    public int range() {
        return data.get(2);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(3));
    }

    /** Ticks since the router last ran (counting up to its interval). */
    public int counter() {
        return data.get(4);
    }

    /** The modules that did something the last time any did, a bit each. */
    public int lastRun() {
        return data.get(5);
    }

    public int sinceRun() {
        return data.get(6);
    }

    public boolean powered() {
        return data.get(7) != 0;
    }

    public int upgradeCount(UpgradeKind kind) {
        return data.get(8 + kind.ordinal());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (router == null) return false;
        if (id == BUTTON_REDSTONE || id == BUTTON_REDSTONE_BACK) {
            router.cycleRedstone(id == BUTTON_REDSTONE_BACK);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return router != null && !router.isRemoved() && Container.stillValidBlockEntity(router, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER) {
            if (!moveItemStackTo(stack, PLAYER, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved;
            if (stack.getItem() instanceof ModuleItem) moved = moveItemStackTo(stack, MODULES, UPGRADES, false);
            else if (stack.getItem() instanceof UpgradeItem) moved = moveItemStackTo(stack, UPGRADES, PLAYER, false);
            else moved = moveItemStackTo(stack, BUFFER, MODULES, false);
            if (!moved) {
                int hotbar = PLAYER + 27;
                if (index < hotbar ? !moveItemStackTo(stack, hotbar, slots.size(), false) : !moveItemStackTo(stack, PLAYER, hotbar, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
