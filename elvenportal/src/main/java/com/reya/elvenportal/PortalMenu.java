package com.reya.elvenportal;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The portal's menu: nine input slots on the left (what goes to the elves), nine output slots on the right
 * (what they send back), the player's inventory under the panel.
 */
public class PortalMenu extends AbstractContainerMenu {
    // the layout is the panel texture's (tools/gen_gui.py; tools/check_layout.py keeps the two in step)
    public static final int WIDTH = 240, HEIGHT = 214;
    public static final int INPUT_X = 18, OUTPUT_X = 168, GRID_Y = 30;
    public static final int INV_X = 40, INV_Y = 132, HOTBAR_Y = 190;
    /** Buttons (clickMenuButton): cycle the redstone mode; send the item on the cursor through the portal. */
    public static final int BUTTON_REDSTONE = 0, BUTTON_SEND = 1;

    @Nullable
    private final PortalBlockEntity portal;
    private final BlockPos pos;
    private final IItemHandler items;
    private final ContainerData data;

    public PortalMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof PortalBlockEntity p ? p : null,
                new SimpleContainerData(PortalBlockEntity.DATA_COUNT));
    }

    public PortalMenu(int id, Inventory inventory, @Nullable PortalBlockEntity portal, ContainerData data) {
        super(ElvenPortal.PORTAL_MENU.get(), id);
        this.portal = portal;
        this.pos = portal != null ? portal.getBlockPos() : BlockPos.ZERO;
        this.items = portal != null ? portal.items() : new ItemStackHandler(PortalBlockEntity.SLOTS);
        this.data = data;
        checkContainerDataCount(data, PortalBlockEntity.DATA_COUNT);

        for (int i = 0; i < PortalBlockEntity.INPUTS; i++) {
            addSlot(new SlotItemHandler(items, PortalBlockEntity.INPUT_START + i, INPUT_X + i % 3 * 18, GRID_Y + i / 3 * 18));
        }
        for (int i = 0; i < PortalBlockEntity.OUTPUTS; i++) {
            addSlot(new SlotItemHandler(items, PortalBlockEntity.OUTPUT_START + i, OUTPUT_X + i % 3 * 18, GRID_Y + i / 3 * 18));
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

    @Nullable
    public PortalBlockEntity portal() {
        return portal;
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

    public int cost() {
        return wide(4);
    }

    private int flags() {
        return data.get(6) & 0xFFFF;
    }

    public PortalBlockEntity.Status status() {
        PortalBlockEntity.Status[] all = PortalBlockEntity.Status.values();
        return all[(flags() & 7) % all.length];
    }

    public PortalBlockEntity.RedstoneMode redstone() {
        return PortalBlockEntity.RedstoneMode.byId(flags() >> 3 & 3);
    }

    public boolean open() {
        return (flags() & 1 << 5) != 0;
    }

    public boolean hasPool() {
        return (flags() & 1 << 6) != 0;
    }

    /** Trades made (wraps round); the screen animates each new one. */
    public int trades() {
        return data.get(7) & 0x7FFF;
    }

    public ItemStack lastIn() {
        return stack(wide(8), 1);
    }

    public ItemStack lastOut() {
        return stack(wide(10), Math.max(1, data.get(12) & 0x7FFF));
    }

    private static ItemStack stack(int idPlusOne, int count) {
        if (idPlusOne <= 0) return ItemStack.EMPTY;
        Item item = Item.byId(idPlusOne - 1);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    public int tradeTicks() {
        return Math.max(1, data.get(13) & 0xFFFF);
    }

    /** How many trades the elves offer (recipes that aren't returns). */
    public int offeredTrades() {
        return data.get(14) & 0x7FFF;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (portal == null) return false;
        if (id == BUTTON_REDSTONE) {
            portal.cycleRedstone();
            return true;
        }
        if (id == BUTTON_SEND) {
            // the item on the cursor, clicked on the portal in the GUI, goes to the elves (the input slots)
            ItemStack carried = getCarried();
            if (carried.isEmpty() || !Trades.accepts(player.level(), carried)) return false;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(items, carried.copy(), false);
            if (rest.getCount() == carried.getCount()) return false;
            setCarried(rest);
            player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F, 0.8F);
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
        int machine = PortalBlockEntity.SLOTS;
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, slots.size(), true)) return ItemStack.EMPTY;
        } else if (Trades.accepts(player.level(), stack)) {
            if (!moveItemStackTo(stack, PortalBlockEntity.INPUT_START, PortalBlockEntity.OUTPUT_START, false)) return ItemStack.EMPTY;
        } else if (index < machine + 27) {
            if (!moveItemStackTo(stack, machine + 27, slots.size(), false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, machine, machine + 27, false)) {
            return ItemStack.EMPTY;
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
        return portal != null && !portal.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
