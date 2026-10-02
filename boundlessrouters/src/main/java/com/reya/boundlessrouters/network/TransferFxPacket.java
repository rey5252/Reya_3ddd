package com.reya.boundlessrouters.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to the players near a router: what its modules moved in a run, each as a flight from one point to another
 * (an item and the module's colour); their clients draw a line between the two and the item flying along it.
 */
public record TransferFxPacket(List<Flight> flights) {
    /** At most this many flights go in a packet (a router moves at most one thing a module a run). */
    public static final int MAX = 16;

    public record Flight(Vec3 from, Vec3 to, ItemStack item, int colour) {
    }

    public void encode(FriendlyByteBuf buf) {
        int n = Math.min(MAX, flights.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Flight f = flights.get(i);
            writeVec(buf, f.from());
            writeVec(buf, f.to());
            buf.writeItem(f.item());
            buf.writeInt(f.colour());
        }
    }

    public static TransferFxPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(MAX, buf.readVarInt());
        List<Flight> flights = new ArrayList<>(n);
        for (int i = 0; i < n; i++) flights.add(new Flight(readVec(buf), readVec(buf), buf.readItem(), buf.readInt()));
        return new TransferFxPacket(flights);
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.reya.boundlessrouters.client.TransferFx.add(flights));
        context.get().setPacketHandled(true);
    }
}
