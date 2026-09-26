package com.reya.advancedenchanting.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.reya.advancedenchanting.AdvancedTableMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Client asks to set these enchantment levels on the item in the advanced table. */
public class EnchantPacket {
    private static final int MAX = 256;
    private final Map<ResourceLocation, Integer> levels;

    public EnchantPacket(Map<ResourceLocation, Integer> levels) {
        this.levels = levels;
    }

    public static void encode(EnchantPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.levels.size());
        pkt.levels.forEach((id, lvl) -> {
            buf.writeResourceLocation(id);
            buf.writeVarInt(lvl);
        });
    }

    public static EnchantPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) throw new IllegalArgumentException("Bad packet size " + n);
        Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) levels.put(buf.readResourceLocation(), buf.readVarInt());
        return new EnchantPacket(levels);
    }

    public static void handle(EnchantPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null || !(player.containerMenu instanceof AdvancedTableMenu menu)) return;
            Map<Enchantment, Integer> wanted = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Integer> e : pkt.levels.entrySet()) {
                Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(e.getKey());
                if (ench == null) return;
                wanted.put(ench, e.getValue());
            }
            menu.enchant(player, wanted);
        });
        c.setPacketHandled(true);
    }
}
