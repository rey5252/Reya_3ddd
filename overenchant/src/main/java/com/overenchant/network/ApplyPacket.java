package com.overenchant.network;

import java.util.function.Supplier;

import com.overenchant.UpgraderMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client asks to add these levels to the enchantments of the item in the upgrader, row by row. */
public class ApplyPacket {
    private static final int MAX = 64;
    private final int[] deltas;

    public ApplyPacket(int[] deltas) {
        this.deltas = deltas;
    }

    public static void encode(ApplyPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.deltas.length);
        for (int d : pkt.deltas) buf.writeVarInt(d);
    }

    public static ApplyPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) throw new IllegalArgumentException("Bad packet size " + n);
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) arr[i] = buf.readVarInt();
        return new ApplyPacket(arr);
    }

    public static void handle(ApplyPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null && player.containerMenu instanceof UpgraderMenu menu) {
                menu.applyUpgrade(player, pkt.deltas);
            }
        });
        c.setPacketHandled(true);
    }
}
