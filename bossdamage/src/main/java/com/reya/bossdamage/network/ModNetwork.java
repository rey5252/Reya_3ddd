package com.reya.bossdamage.network;

import com.reya.bossdamage.BossDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BossDamage.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(BossInfoPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BossInfoPacket::encode)
                .decoder(BossInfoPacket::decode)
                .consumerMainThread((packet, context) -> packet.handle(context))
                .add();
        CHANNEL.messageBuilder(DamageNumberPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DamageNumberPacket::encode)
                .decoder(DamageNumberPacket::decode)
                .consumerMainThread((packet, context) -> packet.handle(context))
                .add();
    }

    private ModNetwork() {
    }
}
