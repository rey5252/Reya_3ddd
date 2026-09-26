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
 * One item slot plus the player inventory. The screen's buttons arrive through
 * {@link #clickMenuButton}: id = row * 2 (minus) or row * 2 + 1 (plus), plus RESET and UNBREAKABLE.
 */
public class AttributeTableMenu extends AbstractContainerMenu {
    public static final int RESET = 100;
    public static final int UNBREAKABLE = 101;
    /** Experience levels each "+" click costs in survival. */
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

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack stack = input.getItem(0);
        if (stack.isEmpty()) return false;

        if (id == RESET) {
            ItemAttributeEditing.reset(stack);
        } else if (id == UNBREAKABLE) {
            if (!payLevels(player)) return false;
            ItemAttributeEditing.toggleUnbreakable(stack);
        } else {
            int row = id / 2;
            if (id < 0 || row >= TableRow.ROWS.size()) return false;
            TableRow tableRow = TableRow.ROWS.get(row);
            Attribute attribute = tableRow.attribute().get();
            boolean up = id % 2 == 1;

            EquipmentSlot slot = AttributeValues.defaultSlot(stack);
            double current = ItemAttributeEditing.value(stack, attribute, slot);
            double next = Math.round((current + (up ? tableRow.step() : -tableRow.step())) * 100.0D) / 100.0D;
            if (next < tableRow.min()) return false;
            if (up && !payLevels(player)) return false;

            ItemAttributeEditing.set(stack, attribute, slot, next);
        }

        input.setChanged();
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                0.6F, 1.0F + level.random.nextFloat() * 0.2F));
        broadcastChanges();
        return true;
    }

    private static boolean payLevels(Player player) {
        if (player.getAbilities().instabuild) return true;
        if (player.experienceLevel < LEVEL_COST) return false;
        player.giveExperienceLevels(-LEVEL_COST);
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
