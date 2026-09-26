package com.reya.multibrewer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class MultiBrewerBlockEntity extends BlockEntity implements MenuProvider {
    private int progress;
    private int maxProgress = 400;
    private int fuel;

    private final ItemStackHandler items = new ItemStackHandler(BrewLogic.SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return switch (slot) {
                case BrewLogic.IN_1, BrewLogic.IN_2, BrewLogic.IN_3 -> BrewLogic.isPotion(stack) || stack.is(Items.GLASS_BOTTLE);
                case BrewLogic.OUTPUT -> false;
                case BrewLogic.FUEL -> stack.is(Items.BLAZE_POWDER);
                case BrewLogic.DURATION -> stack.is(Items.REDSTONE);
                case BrewLogic.POWER -> stack.is(Items.GLOWSTONE_DUST);
                case BrewLogic.INGREDIENT -> BrewingRecipeRegistry.isValidIngredient(stack);
                default -> stack.getItem() instanceof UpgradeItem;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot <= BrewLogic.OUTPUT ? 1 : slot == BrewLogic.UP_1 || slot == BrewLogic.UP_2 ? 4 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Hoppers: put potions, fuel, ingredients, redstone and glowstone in; take finished potions and bottles out. */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return BrewLogic.SLOTS;
        }

        @Override
        public @Nonnull ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (slot == BrewLogic.OUTPUT || slot == BrewLogic.UP_1 || slot == BrewLogic.UP_2 || stack.is(Items.GLASS_BOTTLE)) return stack;
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            boolean bottle = slot <= BrewLogic.IN_3 && items.getStackInSlot(slot).is(Items.GLASS_BOTTLE);
            return slot == BrewLogic.OUTPUT || bottle ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return items.isItemValid(slot, stack);
        }
    };
    private final LazyOptional<IItemHandler> automationCap = LazyOptional.of(() -> automation);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                default -> fuel;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
                default -> fuel = value;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public MultiBrewerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiBrewer.BREWER_BE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MultiBrewerBlockEntity be) {
        boolean vanilla = BrewLogic.canBrewVanilla(be.items);
        ItemStack result = vanilla ? null : BrewLogic.result(be.items);
        boolean canWork = vanilla || result != null && be.items.getStackInSlot(BrewLogic.OUTPUT).isEmpty();
        if (canWork && be.fuel <= 0 && be.items.getStackInSlot(BrewLogic.FUEL).is(Items.BLAZE_POWDER)) {
            be.items.extractItem(BrewLogic.FUEL, 1, false);
            be.fuel = Config.FUEL_PER_BLAZE_POWDER.get();
            be.setChanged();
        }
        boolean brewing = canWork && be.fuel > 0;
        be.maxProgress = BrewLogic.brewTicks(be.items);
        if (brewing) {
            be.progress++;
            if (be.progress >= be.maxProgress) {
                if (vanilla) {
                    be.finishVanilla(level, pos);
                } else {
                    be.finish(level, pos, result);
                }
            }
        } else if (be.progress != 0) {
            be.progress = 0;
        }
        if (state.getValue(MultiBrewerBlock.BREWING) != brewing) {
            level.setBlock(pos, state.setValue(MultiBrewerBlock.BREWING, brewing), 3);
        }
    }

    /** Like the vanilla stand: every potion that has a recipe with the ingredient turns into its result. */
    private void finishVanilla(Level level, BlockPos pos) {
        progress = 0;
        float save = BrewLogic.saveChance(items);
        ItemStack ingredient = items.getStackInSlot(BrewLogic.INGREDIENT);
        for (int slot = BrewLogic.IN_1; slot <= BrewLogic.IN_3; slot++) {
            ItemStack s = items.getStackInSlot(slot);
            if (s.isEmpty() || !BrewingRecipeRegistry.hasOutput(s, ingredient)) continue;
            items.setStackInSlot(slot, BrewingRecipeRegistry.getOutput(s, ingredient));
        }
        if (level.random.nextFloat() >= save) {
            ItemStack remainder = ingredient.getCraftingRemainingItem();
            items.extractItem(BrewLogic.INGREDIENT, 1, false);
            if (!remainder.isEmpty()) {
                if (items.getStackInSlot(BrewLogic.INGREDIENT).isEmpty()) {
                    items.setStackInSlot(BrewLogic.INGREDIENT, remainder);
                } else {
                    Containers.dropItemStack(level, pos.getX(), pos.getY() + 1, pos.getZ(), remainder);
                }
            }
        }
        if (level.random.nextFloat() >= save) fuel--;
        level.levelEvent(1035, pos, 0);
        setChanged();
    }

    private void finish(Level level, BlockPos pos, ItemStack result) {
        progress = 0;
        float save = BrewLogic.saveChance(items);
        items.setStackInSlot(BrewLogic.OUTPUT, result);
        // the first potion's bottle holds the result; the others come back empty
        boolean first = true;
        for (int slot = BrewLogic.IN_1; slot <= BrewLogic.IN_3; slot++) {
            if (!BrewLogic.isPotion(items.getStackInSlot(slot))) continue;
            items.setStackInSlot(slot, first ? ItemStack.EMPTY : new ItemStack(Items.GLASS_BOTTLE));
            first = false;
        }
        if (level.random.nextFloat() >= save) fuel--;
        for (int slot : new int[]{BrewLogic.DURATION, BrewLogic.POWER}) {
            if (!items.getStackInSlot(slot).isEmpty() && level.random.nextFloat() >= save) items.extractItem(slot, 1, false);
        }
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.multibrewer.brewer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MultiBrewerMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Progress", progress);
        tag.putInt("Fuel", fuel);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound("Items"));
        progress = tag.getInt("Progress");
        fuel = tag.getInt("Fuel");
    }

    @Override
    public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return automationCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        automationCap.invalidate();
    }
}
