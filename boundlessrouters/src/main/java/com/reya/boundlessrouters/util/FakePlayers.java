package com.reya.boundlessrouters.util;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/** The router's hands: a fake player standing in it, looking the way a module works, for placing and breaking. */
public final class FakePlayers {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("6b1d3a58-2c4e-4f17-9a3b-8e5d0c2f7a41"), "[BoundlessRouter]");

    /** The fake player at a router, looking along a direction, holding an item. */
    public static FakePlayer at(ServerLevel level, BlockPos pos, Direction look, Direction facing, ItemStack held) {
        FakePlayer player = FakePlayerFactory.get(level, PROFILE);
        player.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D - player.getEyeHeight(), pos.getZ() + 0.5D);
        float yaw = (look.getAxis().isHorizontal() ? look : facing).toYRot();
        float pitch = look == Direction.UP ? -90.0F : look == Direction.DOWN ? 90.0F : 0.0F;
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    /** Empties the fake player's hand after use (it is shared). */
    public static void done(FakePlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private FakePlayers() {
    }
}
