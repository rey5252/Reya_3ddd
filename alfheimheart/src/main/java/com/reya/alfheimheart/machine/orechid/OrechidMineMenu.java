package com.reya.alfheimheart.machine.orechid;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.IItemHandler;

/** The Orechid Mine's menu: the machine's layout, no special slots. */
public class OrechidMineMenu extends MachineMenu {
    public OrechidMineMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, clientMachine(inventory, buf));
    }

    private OrechidMineMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine) {
        this(id, inventory, machine, clientItems(machine, OrechidMineBlockEntity.SLOTS), new SimpleContainerData(MachineBlockEntity.BASE_DATA));
    }

    public OrechidMineMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine, IItemHandler items, ContainerData data) {
        super(AlfheimHeart.ORECHID_MINE_MENU.get(), id, inventory, machine, items, data, OrechidMineBlockEntity.LAYOUT);
    }
}
