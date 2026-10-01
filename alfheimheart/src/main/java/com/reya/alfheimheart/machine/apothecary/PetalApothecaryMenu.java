package com.reya.alfheimheart.machine.apothecary;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.IItemHandler;

/** The Petal Apothecary's menu: its nine inputs a fan of petals over the bowl, the seeds' slot under the bowl. */
public class PetalApothecaryMenu extends MachineMenu {
    public PetalApothecaryMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, clientMachine(inventory, buf));
    }

    private PetalApothecaryMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine) {
        this(id, inventory, machine, clientItems(machine, PetalApothecaryBlockEntity.SLOTS),
                new SimpleContainerData(MachineBlockEntity.BASE_DATA));
    }

    public PetalApothecaryMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine, IItemHandler items, ContainerData data) {
        super(AlfheimHeart.PETAL_APOTHECARY_MENU.get(), id, inventory, machine, items, data, PetalApothecaryBlockEntity.LAYOUT);
    }
}
