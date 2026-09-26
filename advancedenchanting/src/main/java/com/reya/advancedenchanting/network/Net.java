package com.reya.advancedenchanting.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("advancedenchanting", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, EnchantPacket.class, EnchantPacket::encode, EnchantPacket::decode, EnchantPacket::handle);
    }
}
