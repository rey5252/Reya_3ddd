package com.reya.alfheimheart.machine.infuser;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.IItemHandler;

/** The Mana Infuser's menu: its inputs and outputs in alcoves either side of the fountain, the catalyst the stone it stands on. */
public class ManaInfuserMenu extends MachineMenu {
    public ManaInfuserMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, clientMachine(inventory, buf));
    }

    private ManaInfuserMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine) {
        this(id, inventory, machine, clientItems(machine, ManaInfuserBlockEntity.SLOTS), new SimpleContainerData(MachineBlockEntity.BASE_DATA));
    }

    public ManaInfuserMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine, IItemHandler items, ContainerData data) {
        super(AlfheimHeart.MANA_INFUSER_MENU.get(), id, inventory, machine, items, data, ManaInfuserBlockEntity.LAYOUT);
    }
}
