package com.reya.attributeeditor.table;

import com.reya.attributeeditor.AttributeValues;
import com.reya.attributeeditor.ItemAttributeEditing;
import com.reya.attributeeditor.ModRegistry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One item slot plus the player inventory. The screen's "Apply" sends one
 * {@link com.reya.attributeeditor.network.SetAttributePacket} per changed attribute, and
 * RESET / UNBREAKABLE through {@link #clickMenuButton}.
 */
public class AttributeTableMenu extends AbstractContainerMenu {
    public static final int RESET = 100;
    public static final int UNBREAKABLE = 101;
    /** Experience levels each step upwards (and the unbreakable toggle) costs in survival. */
    public static final int LEVEL_COST = 1;

    public static final int SLOT_X = 47;
    public static final int SLOT_Y = 27;
    public static final int INVENTORY_LEFT = 69;
    public static final int INVENTORY_Y = 164;

    private final Container input = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            AttributeTableMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;

    public AttributeTableMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public AttributeTableMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModRegistry.ATTRIBUTE_TABLE_MENU.get(), id);
        this.access = access;

        addSlot(new Slot(input, 0, SLOT_X, SLOT_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        int left = INVENTORY_LEFT;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, left + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, left + col * 18, INVENTORY_Y + 58));
        }
    }

    public ItemStack getItem() {
        return input.getItem(0);
    }

    /** Sets one attribute of the item to any value; called from {@link com.reya.attributeeditor.network.SetAttributePacket}. */
    public void setValue(Player player, int row, double value) {
        ItemStack stack = input.getItem(0);
        if (stack.isEmpty() || row < 0 || row >= TableRow.ROWS.size() || !TableRow.isValid(value)) return;

        TableRow tableRow = TableRow.ROWS.get(row);
        Attribute attribute = tableRow.attribute().get();
        EquipmentSlot slot = AttributeValues.defaultSlot(stack);
        double current = ItemAttributeEditing.value(stack, attribute, slot);
        if (!payLevels(player, tableRow.cost(current, value))) return;

        ItemAttributeEditing.set(stack, attribute, slot, value);
        changed();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack stack = input.getItem(0);
        if (stack.isEmpty()) return false;

        if (id == RESET) {
            ItemAttributeEditing.reset(stack);
        } else if (id == UNBREAKABLE) {
            if (!payLevels(player, LEVEL_COST)) return false;
            ItemAttributeEditing.toggleUnbreakable(stack);
        } else {
            return false;
        }

        changed();
        return true;
    }

    private void changed() {
        input.setChanged();
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                0.6F, 1.0F + level.random.nextFloat() * 0.2F));
        broadcastChanges();
    }

    private static boolean payLevels(Player player, long cost) {
        if (cost <= 0 || player.getAbilities().instabuild) return true;
        if (player.experienceLevel < cost) return false;
        player.giveExperienceLevels((int) -cost);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (slots.get(0).hasItem()) return ItemStack.EMPTY;
            slots.get(0).set(stack.split(1));
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, input));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModRegistry.ATTRIBUTE_TABLE.get());
    }
}
