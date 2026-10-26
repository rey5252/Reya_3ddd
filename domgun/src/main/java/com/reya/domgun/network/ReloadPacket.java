package com.reya.domgun.network;

import java.util.function.Supplier;

import com.reya.domgun.DomGunItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** Client pressed the reload key. */
public class ReloadPacket {
    public static void encode(ReloadPacket pkt, FriendlyByteBuf buf) {
    }

    public static ReloadPacket decode(FriendlyByteBuf buf) {
        return new ReloadPacket();
    }

    public static void handle(ReloadPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof DomGunItem)) return;
            if (!DomGunItem.startReload(player, stack) && DomGunItem.getAmmo(stack) <= 0) DomGunItem.emptyClick(player);
        });
        c.setPacketHandled(true);
    }
}
