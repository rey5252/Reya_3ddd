package com.reya.boundlessrouters.network;

import com.reya.boundlessrouters.BoundlessRouters;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's packets, all from a player's screen to the server. */
public final class Net {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BoundlessRouters.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SettingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SettingPacket::encode)
                .decoder(SettingPacket::decode)
                .consumerMainThread(SettingPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenModulePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenModulePacket::encode)
                .decoder(OpenModulePacket::decode)
                .consumerMainThread(OpenModulePacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenRouterPacket.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenRouterPacket::encode)
                .decoder(OpenRouterPacket::decode)
                .consumerMainThread(OpenRouterPacket::handle)
                .add();
    }

    public static void toServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    private Net() {
    }
}
