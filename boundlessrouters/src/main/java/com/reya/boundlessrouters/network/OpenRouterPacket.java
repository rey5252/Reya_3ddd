package com.reya.boundlessrouters.network;

import java.util.function.Supplier;

import com.reya.boundlessrouters.module.ModuleMenu;
import com.reya.boundlessrouters.router.RouterBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

/** A module's screen goes back to the router it is in. */
public record OpenRouterPacket() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static OpenRouterPacket decode(FriendlyByteBuf buf) {
        return new OpenRouterPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof ModuleMenu menu && menu.routerPos() != null
                && player.level().getBlockEntity(menu.routerPos()) instanceof RouterBlockEntity router && Container.stillValidBlockEntity(router, player)) {
            NetworkHooks.openScreen(player, router, router.getBlockPos());
        }
        context.get().setPacketHandled(true);
    }
}
