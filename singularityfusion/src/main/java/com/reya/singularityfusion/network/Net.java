package com.reya.singularityfusion.network;

import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's one packet: the core's screen starting a fusion. */
public final class Net {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SingularityFusion.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(StartFusionPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StartFusionPacket::encode)
                .decoder(StartFusionPacket::decode)
                .consumerMainThread(StartFusionPacket::handle)
                .add();
    }

    private Net() {
    }
}
