package com.reya.alfheimheart.machine.farm;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.IItemHandler;

/** The Petal Farm's menu: the machine's layout, and the bone meal slot under the bed. */
public class PetalFarmMenu extends MachineMenu {
    /** The bone meal slot's item corner (tools/machines/layout.py FARM.SLOT). */
    public static final int FERTILIZER_X = 112, FERTILIZER_Y = 84;

    public PetalFarmMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, clientMachine(inventory, buf));
    }

    private PetalFarmMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine) {
        this(id, inventory, machine, clientItems(machine, PetalFarmBlockEntity.SLOTS), new SimpleContainerData(MachineBlockEntity.BASE_DATA));
    }

    public PetalFarmMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine, IItemHandler items, ContainerData data) {
        super(AlfheimHeart.PETAL_FARM_MENU.get(), id, inventory, machine, items, data, new int[][]{{FERTILIZER_X, FERTILIZER_Y}});
    }
}
