package com.reya.starfall.client;

import com.reya.starfall.StellarRemoteItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class RemoteItems {
    /** The remote in the player's hand (main hand first), or an empty stack. */
    static ItemStack held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof StellarRemoteItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private RemoteItems() {
    }
}
