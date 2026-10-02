package com.reya.singularityfusion.network;

import java.util.function.Supplier;

import com.reya.singularityfusion.menu.FusionCoreMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** The start button: the core whose screen the player has open starts a fusion, if it can. */
public record StartFusionPacket() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static StartFusionPacket decode(FriendlyByteBuf buf) {
        return new StartFusionPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof FusionCoreMenu menu && menu.core() != null && menu.stillValid(player)) {
            menu.core().start();
        }
        context.get().setPacketHandled(true);
    }
}
