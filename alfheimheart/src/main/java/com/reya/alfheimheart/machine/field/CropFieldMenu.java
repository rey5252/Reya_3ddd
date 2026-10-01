package com.reya.alfheimheart.machine.field;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.items.IItemHandler;

/** The Crop Field's menu: the machine's layout, and the bone meal slot under the field. */
public class CropFieldMenu extends MachineMenu {
    /** The bone meal slot's item corner (tools/machines/layout.py FIELD.SLOT). */
    public static final int FERTILIZER_X = CropFieldBlockEntity.LAYOUT.special[0][0], FERTILIZER_Y = CropFieldBlockEntity.LAYOUT.special[0][1];

    public CropFieldMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, clientMachine(inventory, buf));
    }

    private CropFieldMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine) {
        this(id, inventory, machine, clientItems(machine, CropFieldBlockEntity.SLOTS), new SimpleContainerData(MachineBlockEntity.BASE_DATA));
    }

    public CropFieldMenu(int id, Inventory inventory, @Nullable MachineBlockEntity machine, IItemHandler items, ContainerData data) {
        super(AlfheimHeart.CROP_FIELD_MENU.get(), id, inventory, machine, items, data, CropFieldBlockEntity.LAYOUT);
    }
}
