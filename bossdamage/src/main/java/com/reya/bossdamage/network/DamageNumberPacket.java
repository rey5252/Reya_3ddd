package com.reya.bossdamage.network;

import java.util.function.Supplier;

import com.reya.bossdamage.client.DamageNumbers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: an entity took {@code amount} damage at this position. */
public record DamageNumberPacket(double x, double y, double z, float amount, boolean crit) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(amount);
        buf.writeBoolean(crit);
    }

    public static DamageNumberPacket decode(FriendlyByteBuf buf) {
        return new DamageNumberPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DamageNumbers.add(this));
    }
}
