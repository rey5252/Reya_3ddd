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

/** The Petal Apothecary's menu: the machine's layout, and the seeds slot the bowl stands on. */
public class PetalApothecaryMenu extends MachineMenu {
    /** The seeds slot's item corner (tools/machines/layout.py BOWL.SLOT). */
    public static final int REAGENT_X = PetalApothecaryBlockEntity.LAYOUT.special[0][0], REAGENT_Y = PetalApothecaryBlockEntity.LAYOUT.special[0][1];

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
