package com.reya.boundlessrouters.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

/** The router's hands: a fake player standing in it, looking the way a module works, for placing and breaking. */
public final class FakePlayers {
    /** The fake player at a router, looking along a direction, holding an item. */
    public static FakePlayer at(ServerLevel level, BlockPos pos, Direction look, Direction facing, ItemStack held) {
        RouterPlayer player = RouterPlayer.get(level);
        player.ready(Vec3.atCenterOf(pos), look, facing, held, false);
        return player;
    }

    /** Empties the fake player's hand after use (it is shared). */
    public static void done(FakePlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private FakePlayers() {
    }
}
