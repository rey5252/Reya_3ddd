package com.reya.starfall.network;

import com.reya.starfall.Starfall;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Starfall.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(StrikePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StrikePacket::encode)
                .decoder(StrikePacket::decode)
                .consumerMainThread((packet, context) -> packet.handle(context))
                .add();
        CHANNEL.messageBuilder(RemotePressPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemotePressPacket::encode)
                .decoder(RemotePressPacket::decode)
                .consumerMainThread((packet, context) -> packet.handle(context))
                .add();
        CHANNEL.messageBuilder(SelectSkillPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectSkillPacket::encode)
                .decoder(SelectSkillPacket::decode)
                .consumerMainThread((packet, context) -> packet.handle(context))
                .add();
    }

    private Net() {
    }
}
