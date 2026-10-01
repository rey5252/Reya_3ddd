package com.reya.boundlessrouters.network;

import java.util.function.Supplier;

import com.reya.boundlessrouters.module.ModuleItem;
import com.reya.boundlessrouters.module.ModuleMenu;
import com.reya.boundlessrouters.router.RouterBlockEntity;
import com.reya.boundlessrouters.router.RouterMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

/** The router's screen asks for the settings of the module in one of its slots. */
public record OpenModulePacket(int slot) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
    }

    public static OpenModulePacket decode(FriendlyByteBuf buf) {
        return new OpenModulePacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.containerMenu instanceof RouterMenu menu && menu.stillValid(player) && menu.router() != null
                && slot >= 0 && slot < RouterBlockEntity.MODULE_SLOTS) {
            RouterBlockEntity router = menu.router();
            ItemStack module = router.modules().getStackInSlot(slot);
            if (module.getItem() instanceof ModuleItem) {
                NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> ModuleMenu.forRouter(id, inventory, router, slot),
                        module.getHoverName()), buf -> ModuleMenu.writeRouter(buf, router.getBlockPos(), slot));
            }
        }
        context.get().setPacketHandled(true);
    }
}
