package com.reya.boundlessrouters.module;

import javax.annotation.Nullable;

import com.reya.boundlessrouters.BoundlessRouters;
import com.reya.boundlessrouters.gui.Layouts;
import com.reya.boundlessrouters.router.RouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * A module's settings: its filter (nine ghost slots: clicking one with an item puts a copy there, clicking it
 * empty-handed clears it; shift-clicking an item in the inventory lists it), and everything its screen changes
 * through {@link #apply}. It edits the module where it lives, in the player's hand or in a router's slot; the
 * module in hand can't be moved while its settings are open.
 */
public class ModuleMenu extends AbstractContainerMenu {
    private static final int SOURCE_HAND = 0, SOURCE_ROUTER = 1;
    public static final int FILTER_START = 0, FILTER_END = ModuleSettings.FILTER_SIZE;

    private final ModuleHolder holder;
    private final SimpleContainer filter;
    @Nullable
    private final BlockPos routerPos;
    private final int routerSlot;
    private final InteractionHand hand;
    private final int playerStart, lockedSlot, lockedHotbar;
    private boolean loading;

    public static ModuleMenu forHand(int id, Inventory inventory, InteractionHand hand) {
        return new ModuleMenu(id, inventory, ModuleHolder.hand(inventory.player, hand), null, -1, hand, null);
    }

    public static ModuleMenu forRouter(int id, Inventory inventory, RouterBlockEntity router, int slot) {
        return new ModuleMenu(id, inventory, ModuleHolder.router(router, slot), router.getBlockPos(), slot, InteractionHand.MAIN_HAND,
                router.modules());
    }

    /** The client's menu, from what the server sent. */
    public static ModuleMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        if (buf.readByte() == SOURCE_ROUTER) {
            BlockPos pos = buf.readBlockPos();
            int slot = buf.readVarInt();
            ItemStackHandler copy = new ItemStackHandler(1);
            return new ModuleMenu(id, inventory, ModuleHolder.copy(copy), pos, slot, InteractionHand.MAIN_HAND, copy);
        }
        return forHand(id, inventory, buf.readEnum(InteractionHand.class));
    }

    public static void writeHand(FriendlyByteBuf buf, InteractionHand hand) {
        buf.writeByte(SOURCE_HAND);
        buf.writeEnum(hand);
    }

    public static void writeRouter(FriendlyByteBuf buf, BlockPos pos, int slot) {
        buf.writeByte(SOURCE_ROUTER);
        buf.writeBlockPos(pos);
        buf.writeVarInt(slot);
    }

    /**
     * @param modules for a router's module: the handler it is in (the router's on the server, the client's copy);
     *                its slot rides along in the menu, out of sight, so the client sees the module change
     */
    private ModuleMenu(int id, Inventory inventory, ModuleHolder holder, @Nullable BlockPos routerPos, int routerSlot, InteractionHand hand,
                       @Nullable IItemHandlerModifiable modules) {
        super(BoundlessRouters.MODULE_MENU.get(), id);
        this.holder = holder;
        this.routerPos = routerPos;
        this.routerSlot = routerSlot;
        this.hand = hand;
        this.filter = new SimpleContainer(ModuleSettings.FILTER_SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                filterChanged();
            }
        };
        loading = true;
        var items = new ModuleSettings(holder.stack()).filterItems();
        for (int i = 0; i < items.size(); i++) filter.setItem(i, items.get(i));
        loading = false;
        for (int i = 0; i < ModuleSettings.FILTER_SIZE; i++) {
            addSlot(new GhostSlot(filter, i, Layouts.Module.FILTER[i][0], Layouts.Module.FILTER[i][1]));
        }
        if (modules != null) {
            int index = modules instanceof ItemStackHandler handler && handler.getSlots() == 1 ? 0 : routerSlot;
            addSlot(new HiddenSlot(modules, index));
        }
        playerStart = slots.size();
        boolean lockHand = routerPos == null && hand == InteractionHand.MAIN_HAND;
        lockedHotbar = lockHand ? inventory.selected : -1;
        int locked = -1;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, 9 + r * 9 + c, Layouts.Module.INV_X + c * 18, Layouts.Module.INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            if (c == lockedHotbar) {
                locked = slots.size();
                addSlot(new LockedSlot(inventory, c, Layouts.Module.INV_X + c * 18, Layouts.Module.HOTBAR_Y));
            } else {
                addSlot(new Slot(inventory, c, Layouts.Module.INV_X + c * 18, Layouts.Module.HOTBAR_Y));
            }
        }
        lockedSlot = locked;
    }

    private void filterChanged() {
        if (loading) return;
        ItemStack module = holder.stack();
        if (!(module.getItem() instanceof ModuleItem)) return;
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < filter.getContainerSize(); i++) items.add(filter.getItem(i));
        new ModuleSettings(module).setFilterItems(items);
        holder.changed();
    }

    /** The module being set up (on the client: its latest copy). */
    public ItemStack module() {
        return holder.stack();
    }

    public ModuleSettings settings() {
        return new ModuleSettings(holder.stack());
    }

    /** The router the module is in, or null for one in hand. */
    @Nullable
    public BlockPos routerPos() {
        return routerPos;
    }

    public int routerSlot() {
        return routerSlot;
    }

    public InteractionHand hand() {
        return hand;
    }

    /** Changes one of its settings (on the server from a packet; the client does it too, to show it at once). */
    public void apply(ModuleSettings.Setting setting, int value) {
        ItemStack module = holder.stack();
        if (!(module.getItem() instanceof ModuleItem)) return;
        new ModuleSettings(module).apply(setting, value);
        holder.changed();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= FILTER_START && slotId < FILTER_END) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                ItemStack carried = getCarried();
                slots.get(slotId).set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        if (lockedSlot >= 0 && (slotId == lockedSlot || type == ClickType.SWAP && button == lockedHotbar)) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && !(slot instanceof HiddenSlot) && slot.index != lockedSlot && super.canTakeItemForPickAll(stack, slot);
    }

    /** Shift-clicking an item in the inventory lists it in the first free filter slot (it stays where it is). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < playerStart) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < filter.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameTags(filter.getItem(i), stack)) return ItemStack.EMPTY;
        }
        for (int i = 0; i < filter.getContainerSize(); i++) {
            if (filter.getItem(i).isEmpty()) {
                filter.setItem(i, stack.copyWithCount(1));
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return holder.valid(player);
    }

    /** A filter slot: shows an item, holds none. */
    public static class GhostSlot extends Slot {
        public GhostSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** The router's module, carried along unseen so the client's screen sees it change. */
    private static class HiddenSlot extends SlotItemHandler {
        HiddenSlot(IItemHandlerModifiable handler, int index) {
            super(handler, index, -10000, -10000);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }

    /** The hotbar slot holding the module being set up: it stays put. */
    private static class LockedSlot extends Slot {
        LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
