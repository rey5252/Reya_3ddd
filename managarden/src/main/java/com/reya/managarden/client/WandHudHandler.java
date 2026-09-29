package com.reya.managarden.client;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.reya.managarden.GreenhouseBlockEntity;
import com.reya.managarden.ManaGarden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import vazkii.botania.api.BotaniaAPIClient;
import vazkii.botania.api.BotaniaForgeClientCapabilities;
import vazkii.botania.api.block.WandHUD;

/**
 * Looking at a greenhouse with the Wand of the Forest shows its mana the way Botania shows a
 * flower's: name, mana bar, and what it is bound to (with a tick or a cross).
 */
@Mod.EventBusSubscriber(modid = ManaGarden.MODID, value = Dist.CLIENT)
public final class WandHudHandler {
    private static final ResourceLocation KEY = new ResourceLocation(ManaGarden.MODID, "wand_hud");

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof GreenhouseBlockEntity be) event.addCapability(KEY, new Provider(be));
    }

    private static final class Provider implements ICapabilityProvider {
        private final LazyOptional<WandHUD> hud;

        Provider(GreenhouseBlockEntity be) {
            WandHUD instance = new Hud(be);
            hud = LazyOptional.of(() -> instance);
        }

        @Override
        public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return BotaniaForgeClientCapabilities.WAND_HUD.orEmpty(cap, hud);
        }
    }

    private record Hud(GreenhouseBlockEntity be) implements WandHUD {
        @Override
        public void renderHUD(GuiGraphics gui, Minecraft mc) {
            String name = be.getBlockState().getBlock().getName().getString();
            int centerX = mc.getWindow().getGuiScaledWidth() / 2;
            int centerY = mc.getWindow().getGuiScaledHeight() / 2;
            int left = (Math.max(102, mc.font.width(name)) + 4) / 2;
            int right = left + 20;
            gui.fill(centerX - left, centerY + 8, centerX + right, centerY + 30, 0x40000000);
            gui.fill(centerX - left - 2, centerY + 6, centerX + right + 2, centerY + 32, 0x40000000);
            BlockPos bound = be.getBinding();
            ItemStack icon = bound != null && be.getLevel() != null
                    ? new ItemStack(be.getLevel().getBlockState(bound).getBlock()) : ItemStack.EMPTY;
            BotaniaAPIClient.instance().drawComplexManaHUD(gui, 0x4CD7FF, be.clientMana(), be.clientCapacity(), name, icon, bound != null);
        }
    }

    private WandHudHandler() {
    }
}
