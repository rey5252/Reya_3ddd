package com.reya.bossdamage.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.reya.bossdamage.client.ClientBossData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: one boss's health and damage leaderboard. */
public record BossInfoPacket(int entityId, ResourceLocation type, Component name, float health, float maxHealth,
                             List<Row> rows) {

    public record Row(String name, float percent) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeResourceLocation(type);
        buf.writeComponent(name);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeUtf(row.name(), 64);
            buf.writeFloat(row.percent());
        }
    }

    public static BossInfoPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        ResourceLocation type = buf.readResourceLocation();
        Component name = buf.readComponent();
        float health = buf.readFloat();
        float maxHealth = buf.readFloat();
        int count = Math.min(buf.readVarInt(), 16);
        List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Row(buf.readUtf(64), buf.readFloat()));
        }
        return new BossInfoPacket(entityId, type, name, health, maxHealth, rows);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBossData.accept(this));
    }
}
