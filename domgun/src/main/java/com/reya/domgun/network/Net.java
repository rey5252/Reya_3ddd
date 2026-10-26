package com.reya.domgun.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("domgun", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, ReloadPacket.class, ReloadPacket::encode, ReloadPacket::decode, ReloadPacket::handle);
        CHANNEL.registerMessage(1, ShotPacket.class, ShotPacket::encode, ShotPacket::decode, ShotPacket::handle);
    }
}
