package com.reya.boundlessrouters.network;

import java.util.function.Supplier;

import com.reya.boundlessrouters.module.ModuleMenu;
import com.reya.boundlessrouters.module.ModuleSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** A module's screen changed one of its settings. */
public record SettingPacket(int setting, int value) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(setting);
        buf.writeVarInt(value);
    }

    public static SettingPacket decode(FriendlyByteBuf buf) {
        return new SettingPacket(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        ModuleSettings.Setting which = ModuleSettings.Setting.byId(setting);
        if (player != null && which != null && player.containerMenu instanceof ModuleMenu menu && menu.stillValid(player)) {
            menu.apply(which, value);
        }
        context.get().setPacketHandled(true);
    }
}
