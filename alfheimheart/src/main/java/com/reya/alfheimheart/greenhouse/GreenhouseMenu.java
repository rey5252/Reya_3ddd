package com.reya.alfheimheart.greenhouse;

import com.reya.alfheimheart.AlfheimHeart;

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
 * The greenhouse's menu: eight flower slots in a ring round the mana heart, four upgrade slots on the
 * left, the charge slot on the right, the player's inventory under the panel.
 */
public class GreenhouseMenu extends AbstractContainerMenu {
    // the layout is the panel texture's (tools/gen_gui.py; tools/check_layout.py keeps the two in step)
    public static final int WIDTH = 256, HEIGHT = 236;
    /** Middle of the mana heart the flowers ring. */
    public static final int HEART_X = 128, HEART_Y = 60;
    /** Flower slots (item corners), clockwise from the top. */
    public static final int[][] FLOWER_POS = {
            {120, 15}, {146, 26}, {157, 52}, {146, 78}, {120, 89}, {94, 78}, {83, 52}, {94, 26}};
    public static final int UPGRADE_X = 19, UPGRADE_Y = 20, UPGRADE_STEP = 22;
    public static final int CHARGE_X = 221, CHARGE_Y = 20;
    public static final int INV_X = 48, INV_Y = 154, HOTBAR_Y = 212;
    public static final int BUTTON_REDSTONE = 0, BUTTON_OUTPUT = 1, BUTTON_UNBIND = 2;

    @Nullable
    private final GreenhouseBlockEntity greenhouse;
    private final BlockPos pos;
    private final IItemHandler items;
    private final ContainerData data;

    public GreenhouseMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof GreenhouseBlockEntity g ? g : null,
                new SimpleContainerData(GreenhouseBlockEntity.DATA_COUNT));
    }

    public GreenhouseMenu(int id, Inventory inventory, @Nullable GreenhouseBlockEntity greenhouse, ContainerData data) {
        super(AlfheimHeart.GREENHOUSE_MENU.get(), id);
        this.greenhouse = greenhouse;
        this.pos = greenhouse != null ? greenhouse.getBlockPos() : BlockPos.ZERO;
        this.items = greenhouse != null ? greenhouse.items() : new ItemStackHandler(GreenhouseBlockEntity.SLOTS);
        this.data = data;
        checkContainerDataCount(data, GreenhouseBlockEntity.DATA_COUNT);

        for (int i = 0; i < GreenhouseBlockEntity.FLOWERS; i++) {
            addSlot(new SlotItemHandler(items, GreenhouseBlockEntity.FLOWER_START + i, FLOWER_POS[i][0], FLOWER_POS[i][1]));
        }
        for (int i = 0; i < GreenhouseBlockEntity.UPGRADES; i++) {
            addSlot(new SlotItemHandler(items, GreenhouseBlockEntity.UPGRADE_START + i, UPGRADE_X, UPGRADE_Y + i * UPGRADE_STEP));
        }
        addSlot(new SlotItemHandler(items, GreenhouseBlockEntity.CHARGE_SLOT, CHARGE_X, CHARGE_Y));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inventory, c, INV_X + c * 18, HOTBAR_Y));
        }
        addDataSlots(data);
        if (greenhouse != null && !inventory.player.level().isClientSide) greenhouse.startOpen(inventory.player);
    }

    @Nullable
    public GreenhouseBlockEntity greenhouse() {
        return greenhouse;
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

    public int progress() {
        return data.get(4) & 0xFFFF;
    }

    public int cycleTicks() {
        return Math.max(1, data.get(5) & 0xFFFF);
    }

    public int manaPerCycle() {
        return wide(6);
    }

    public int luckPermille() {
        return data.get(8) & 0xFFFF;
    }

    private int flags() {
        return data.get(9) & 0xFFFF;
    }

    public boolean running() {
        return (flags() & 1) != 0;
    }

    public GreenhouseBlockEntity.Status status() {
        return GreenhouseBlockEntity.Status.values()[(flags() >> 1 & 3) % GreenhouseBlockEntity.Status.values().length];
    }

    public GreenhouseBlockEntity.RedstoneMode redstone() {
        return GreenhouseBlockEntity.RedstoneMode.byId(flags() >> 3 & 3);
    }

    public boolean output() {
        return (flags() & 1 << 5) != 0;
    }

    public boolean bound() {
        return (flags() & 1 << 6) != 0;
    }

    public boolean hasTarget() {
        return (flags() & 1 << 7) != 0;
    }

    /** Finished cycles (wraps round); the screen flashes when it changes. */
    public int cycles() {
        return data.get(10) & 0x7FFF;
    }

    public int luckyCycles() {
        return data.get(11) & 0x7FFF;
    }

    public int lastGain() {
        return wide(12);
    }

    /** Different kinds of flowers growing (harmony). */
    public int kinds() {
        return data.get(14) & 0xFFFF;
    }

    public int upgrades(UpgradeKind kind) {
        int word = data.get(kind.ordinal() < 2 ? 15 : 17) & 0xFFFF;
        return word >> kind.ordinal() % 2 * 8 & 0xFF;
    }

    /** How much more a lucky cycle gives, in percent. */
    public int luckBonusPercent() {
        return data.get(16) & 0xFFFF;
    }

    /** Average mana per second, luck included. */
    public double manaPerSecond() {
        double perCycle = manaPerCycle() * (1.0D + luckPermille() / 1000.0D * luckBonusPercent() / 100.0D);
        return perCycle * 20.0D / cycleTicks();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (greenhouse == null) return false;
        switch (id) {
            case BUTTON_REDSTONE -> greenhouse.cycleRedstone();
            case BUTTON_OUTPUT -> greenhouse.toggleOutput();
            case BUTTON_UNBIND -> greenhouse.unbind();
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
        int machine = GreenhouseBlockEntity.SLOTS;
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, slots.size(), true)) return ItemStack.EMPTY;
        } else if (Flowers.isFlower(stack)) {
            if (!moveItemStackTo(stack, GreenhouseBlockEntity.FLOWER_START, GreenhouseBlockEntity.UPGRADE_START, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof UpgradeItem) {
            if (!moveItemStackTo(stack, GreenhouseBlockEntity.UPGRADE_START, GreenhouseBlockEntity.CHARGE_SLOT, false)) return ItemStack.EMPTY;
        } else if (GreenhouseBlockEntity.isManaItem(stack)) {
            if (!moveItemStackTo(stack, GreenhouseBlockEntity.CHARGE_SLOT, machine, false)) return ItemStack.EMPTY;
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
        return greenhouse != null && !greenhouse.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (greenhouse != null && !player.level().isClientSide) greenhouse.stopOpen(player);
    }
}
