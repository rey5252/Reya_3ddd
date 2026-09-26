package com.reya.attributeeditor.network;

import com.reya.attributeeditor.AttributeEditor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AttributeEditor.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(SetAttributePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetAttributePacket::encode)
                .decoder(SetAttributePacket::decode)
                .consumerMainThread((packet, context) -> packet.handle(context))
                .add();
    }

    private ModNetwork() {
    }
}
