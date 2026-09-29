package com.reya.quantumsolar;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** What a panel's GUI shows: its energy, what it makes now and why (no slots). */
public class PanelMenu extends AbstractContainerMenu {
    public static final int DATA = 5;

    @Nullable
    private final PanelBlockEntity panel;
    private final BlockPos pos;
    private final Panels.Panel info;
    private final ContainerData data;

    public PanelMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof PanelBlockEntity b ? b : null,
                new SimpleContainerData(DATA));
    }

    public PanelMenu(int id, Inventory inventory, @Nullable PanelBlockEntity panel, ContainerData data) {
        super(QuantumSolar.PANEL_MENU.get(), id);
        this.panel = panel;
        this.pos = panel != null ? panel.getBlockPos() : BlockPos.ZERO;
        this.info = panel != null ? panel.panel() : Panels.ALL[0];
        this.data = data;
        addDataSlots(data);
    }

    public Panels.Panel info() {
        return info;
    }

    public int energy() {
        return (data.get(0) & 0xFFFF) | (data.get(1) & 0xFFFF) << 16;
    }

    public int making() {
        return (data.get(2) & 0xFFFF) | (data.get(3) & 0xFFFF) << 16;
    }

    public int status() {
        return data.get(4);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return panel != null && !panel.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
