package com.reya.alfheimheart.machine.altar;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.IItemHandler;

/** The Runic Altar's menu: the machine's layout, and the livingrock slot under the altar. */
public class RuneAltarMenu extends MachineMenu {
    /** The reagent slot's item corner (tools/machines/layout.py ALTAR.SLOT). */
    public static final int REAGENT_X = 112, REAGENT_Y = 84;

    public RuneAltarMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, clientMachine(inventory, buf));
    }

    private RuneAltarMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine) {
        this(id, inventory, machine, clientItems(machine, RuneAltarBlockEntity.SLOTS), new SimpleContainerData(MachineBlockEntity.BASE_DATA));
    }

    public RuneAltarMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine, IItemHandler items, ContainerData data) {
        super(AlfheimHeart.RUNE_ALTAR_MENU.get(), id, inventory, machine, items, data, new int[][]{{REAGENT_X, REAGENT_Y}});
    }
}
