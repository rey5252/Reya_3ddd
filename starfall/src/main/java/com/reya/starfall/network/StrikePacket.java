package com.reya.starfall.network;

import java.util.function.Supplier;

import com.reya.starfall.client.ClientStrikes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client: a weapon has been fired. The client plays everything from here on its own, timed by the
 * level's game time from {@code start}: the marker, the film (for whoever pressed the button), the impact.
 *
 * @param nodes  Seven Stars only: where each star lands (x, y, z for each)
 * @param sizes  Seven Stars only: crater radius of each star
 */
public record StrikePacket(int id, int skill, BlockPos target, long start, float radius, float yaw, boolean caster,
                           int[] nodes, float[] sizes, float width) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeVarInt(skill);
        buf.writeBlockPos(target);
        buf.writeLong(start);
        buf.writeFloat(radius);
        buf.writeFloat(yaw);
        buf.writeBoolean(caster);
        buf.writeVarIntArray(nodes);
        buf.writeVarInt(sizes.length);
        for (float s : sizes) buf.writeFloat(s);
        buf.writeFloat(width);
    }

    public static StrikePacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int skill = buf.readVarInt();
        BlockPos target = buf.readBlockPos();
        long start = buf.readLong();
        float radius = buf.readFloat();
        float yaw = buf.readFloat();
        boolean caster = buf.readBoolean();
        int[] nodes = buf.readVarIntArray(64);
        float[] sizes = new float[Math.min(buf.readVarInt(), 16)];
        for (int i = 0; i < sizes.length; i++) sizes[i] = buf.readFloat();
        float width = buf.readFloat();
        return new StrikePacket(id, skill, target, start, radius, yaw, caster, nodes, sizes, width);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStrikes.add(this));
        context.get().setPacketHandled(true);
    }
}
