package com.reya.boundlessrouters.module;

import com.reya.boundlessrouters.router.RouterBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Where the module being set up lives: in a player's hand, or in one of a router's module slots. */
public abstract class ModuleHolder {
    /** The module itself (changing its tag changes it where it lives). */
    public abstract ItemStack stack();

    /** Its tag was changed: whatever holds it saves. */
    public abstract void changed();

    public abstract boolean valid(Player player);

    public static ModuleHolder hand(Player owner, InteractionHand hand) {
        return new ModuleHolder() {
            @Override
            public ItemStack stack() {
                return owner.getItemInHand(hand);
            }

            @Override
            public void changed() {
                owner.getInventory().setChanged();
            }

            @Override
            public boolean valid(Player player) {
                return stack().getItem() instanceof ModuleItem;
            }
        };
    }

    public static ModuleHolder router(RouterBlockEntity router, int slot) {
        return new ModuleHolder() {
            @Override
            public ItemStack stack() {
                return router.modules().getStackInSlot(slot);
            }

            @Override
            public void changed() {
                router.moduleChanged(slot);
            }

            @Override
            public boolean valid(Player player) {
                return !router.isRemoved() && Container.stillValidBlockEntity(router, player) && stack().getItem() instanceof ModuleItem;
            }
        };
    }

    /** The client's copy of a router's module, kept up to date by the menu. */
    public static ModuleHolder copy(IItemHandlerModifiable handler) {
        return new ModuleHolder() {
            @Override
            public ItemStack stack() {
                return handler.getStackInSlot(0);
            }

            @Override
            public void changed() {
            }

            @Override
            public boolean valid(Player player) {
                return true;
            }
        };
    }
}
