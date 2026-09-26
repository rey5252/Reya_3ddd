package com.reya.attributeeditor.network;

import java.util.function.Supplier;

import com.reya.attributeeditor.table.AttributeTableMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: "set attribute row {@code row} of the item in my Attribute Table to {@code value}". */
public record SetAttributePacket(int row, double value) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(row);
        buf.writeDouble(value);
    }

    public static SetAttributePacket decode(FriendlyByteBuf buf) {
        return new SetAttributePacket(buf.readVarInt(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof AttributeTableMenu menu && menu.stillValid(player)) {
            menu.setValue(player, row, value);
        }
    }
}
