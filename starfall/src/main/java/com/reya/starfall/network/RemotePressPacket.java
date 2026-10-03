package com.reya.starfall.network;

import java.util.function.Supplier;

import com.reya.starfall.client.RemoteAnimation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: someone nearby flipped their remote's cover and is pressing the button. */
public record RemotePressPacket(int entityId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static RemotePressPacket decode(FriendlyByteBuf buf) {
        return new RemotePressPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemoteAnimation.startFor(entityId));
        context.get().setPacketHandled(true);
    }
}
