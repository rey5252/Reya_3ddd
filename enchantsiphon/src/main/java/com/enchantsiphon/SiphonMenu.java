package com.enchantsiphon;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Item slot and book slot on the left of the ornate panel, player inventory centred below.
 * Button ids: a row index moves that enchantment into the book, {@link #TAKE_ALL} moves all that fit.
 */
public class SiphonMenu extends AbstractContainerMenu {
    public static final int ITEM_SLOT = 0;
    public static final int BOOK_SLOT = 1;
    public static final int TAKE_ALL = 1000;
    public static final int WIDTH = 256;
    public static final int HEIGHT = 236;
    public static final int ITEM_X = 28;
    public static final int ITEM_Y = 28;
    public static final int BOOK_X = 28;
    public static final int BOOK_Y = 78;
    public static final int INV_X = 47;
    public static final int INV_Y = 154;

    private final ContainerLevelAccess access;
    private final DataSlot cost = DataSlot.standalone();
    private final DataSlot curses = DataSlot.standalone();
    private final SimpleContainer container = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };

    public SiphonMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public SiphonMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(EnchantSiphon.SIPHON_MENU.get(), id);
        this.access = access;
        cost.set(Config.COST.get());
        curses.set(Config.ALLOW_CURSES.get() ? 1 : 0);
        addDataSlot(cost);
        addDataSlot(curses);
        addSlot(new Slot(container, ITEM_SLOT, ITEM_X, ITEM_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return hasEnchants(stack);
            }
        });
        addSlot(new Slot(container, BOOK_SLOT, BOOK_X, BOOK_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    public static ListTag enchantList(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        String key = stack.getItem() instanceof EnchantedBookItem ? "StoredEnchantments" : "Enchantments";
        return tag.contains(key, Tag.TAG_LIST) ? tag.getList(key, Tag.TAG_COMPOUND) : null;
    }

    public static boolean hasEnchants(ItemStack stack) {
        ListTag list = enchantList(stack);
        return list != null && !list.isEmpty();
    }

    public int costPerEnchant() {
        return cost.get();
    }

    public boolean cursesAllowed() {
        return curses.get() != 0;
    }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        ListTag list = enchantList(container.getItem(ITEM_SLOT));
        if (list == null) return out;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(t.getString("id"));
            Enchantment e = id == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (e == null) continue;
            out.add(new Entry(id, e, t.getInt("lvl")));
        }
        return out;
    }

    public Status check(Player player, Entry e) {
        ItemStack book = container.getItem(BOOK_SLOT);
        if (book.isEmpty()) return Status.NO_BOOK;
        if (e.ench().isCurse() && !cursesAllowed()) return Status.CURSE;
        int c = cost.get();
        if (c > 0 && !player.getAbilities().instabuild && player.experienceLevel < c) return Status.NO_XP;
        ListTag bl = enchantList(book);
        if (bl != null) {
            for (int i = 0; i < bl.size(); i++) {
                ResourceLocation oid = ResourceLocation.tryParse(bl.getCompound(i).getString("id"));
                if (oid == null || oid.equals(e.id())) continue;
                Enchantment other = ForgeRegistries.ENCHANTMENTS.getValue(oid);
                if (other == null || e.ench().isCompatibleWith(other)) continue;
                return Status.INCOMPAT;
            }
        }
        return Status.OK;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer)) return false;
        boolean done = false;
        if (id == TAKE_ALL) {
            for (Entry e : entries()) {
                Status st = check(player, e);
                if (st == Status.NO_BOOK || st == Status.NO_XP) break;
                if (st == Status.OK) done |= takeOne(player, e);
            }
        } else {
            List<Entry> list = entries();
            if (id >= 0 && id < list.size()) {
                Entry e = list.get(id);
                if (check(player, e) == Status.OK) done = takeOne(player, e);
            }
        }
        if (done) {
            container.setChanged();
            broadcastChanges();
            access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.2F));
        }
        return done;
    }

    private boolean takeOne(Player player, Entry e) {
        ItemStack item = container.getItem(ITEM_SLOT);
        ListTag src = enchantList(item);
        if (src == null) return false;
        int idx = -1;
        for (int i = 0; i < src.size(); i++) {
            if (e.id().toString().equals(src.getCompound(i).getString("id"))) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;

        ItemStack book = container.getItem(BOOK_SLOT);
        boolean multi = book.getCount() > 1;
        ItemStack target;
        if (book.is(Items.BOOK)) {
            if (multi) book.shrink(1);
            target = new ItemStack(Items.ENCHANTED_BOOK);
            if (!multi) container.setItem(BOOK_SLOT, target);
        } else {
            target = multi ? book.split(1) : book;
        }
        EnchantedBookItem.addEnchantment(target, new EnchantmentInstance(e.ench(), e.level()));
        if (multi && !player.getInventory().add(target)) player.drop(target, false);

        src.remove(idx);
        boolean isBook = item.getItem() instanceof EnchantedBookItem;
        String key = isBook ? "StoredEnchantments" : "Enchantments";
        if (src.isEmpty()) {
            item.removeTagKey(key);
            if (item.getTag() != null && item.getTag().isEmpty()) item.setTag(null);
            if (isBook) container.setItem(ITEM_SLOT, new ItemStack(Items.BOOK, item.getCount()));
        } else {
            item.getOrCreateTag().put(key, src);
        }
        int c = cost.get();
        if (c > 0 && !player.getAbilities().instabuild) player.giveExperienceLevels(-c);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            if (index < 2) {
                if (!moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
            } else if (slots.get(ITEM_SLOT).mayPlace(stack) && !slots.get(ITEM_SLOT).hasItem()) {
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else if (slots.get(BOOK_SLOT).mayPlace(stack)) {
                if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, container));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, EnchantSiphon.SIPHON.get());
    }

    public record Entry(ResourceLocation id, Enchantment ench, int level) {
    }

    public enum Status {
        OK, NO_BOOK, NO_XP, INCOMPAT, CURSE
    }
}
