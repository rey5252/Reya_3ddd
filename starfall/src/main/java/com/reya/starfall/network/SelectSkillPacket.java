package com.reya.starfall.network;

import java.util.function.Supplier;

import com.reya.starfall.Skill;
import com.reya.starfall.StellarRemoteItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> server: put this weapon on the remote in the player's hand (keys and the skill menu). */
public record SelectSkillPacket(int skill) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(skill);
    }

    public static SelectSkillPacket decode(FriendlyByteBuf buf) {
        return new SelectSkillPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) StellarRemoteItem.select(player, Skill.byIndex(skill));
        context.get().setPacketHandled(true);
    }
}
