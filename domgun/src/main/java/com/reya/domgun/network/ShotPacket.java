package com.reya.domgun.network;

import java.util.function.Supplier;

import com.reya.domgun.client.ClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server tells the shooter's client to kick the camera and show a hit marker. */
public class ShotPacket {
    private final float kick;
    private final float sway;
    private final int result;

    public ShotPacket(float kick, float sway, int result) {
        this.kick = kick;
        this.sway = sway;
        this.result = result;
    }

    public static void encode(ShotPacket pkt, FriendlyByteBuf buf) {
        buf.writeFloat(pkt.kick);
        buf.writeFloat(pkt.sway);
        buf.writeByte(pkt.result);
    }

    public static ShotPacket decode(FriendlyByteBuf buf) {
        return new ShotPacket(buf.readFloat(), buf.readFloat(), buf.readByte());
    }

    public static void handle(ShotPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientEvents.onShot(pkt.kick, pkt.sway, pkt.result)));
        c.setPacketHandled(true);
    }
}
