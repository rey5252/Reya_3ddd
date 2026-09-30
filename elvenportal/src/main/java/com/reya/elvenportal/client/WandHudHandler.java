package com.reya.elvenportal.client;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.reya.elvenportal.ElvenPortal;
import com.reya.elvenportal.PortalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
 * Looking at the portal with the Wand of the Forest shows its mana the way Botania shows a mana pool's:
 * its name and a mana bar.
 */
@Mod.EventBusSubscriber(modid = ElvenPortal.MODID, value = Dist.CLIENT)
public final class WandHudHandler {
    private static final ResourceLocation KEY = new ResourceLocation(ElvenPortal.MODID, "wand_hud");

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof PortalBlockEntity be) event.addCapability(KEY, new Provider(be));
    }

    private static final class Provider implements ICapabilityProvider {
        private final LazyOptional<WandHUD> hud;

        Provider(PortalBlockEntity be) {
            WandHUD instance = new Hud(be);
            hud = LazyOptional.of(() -> instance);
        }

        @Override
        public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return BotaniaForgeClientCapabilities.WAND_HUD.orEmpty(cap, hud);
        }
    }

    private record Hud(PortalBlockEntity be) implements WandHUD {
        @Override
        public void renderHUD(GuiGraphics gui, Minecraft mc) {
            String name = be.getBlockState().getBlock().getName().getString();
            BotaniaAPIClient.instance().drawSimpleManaHUD(gui, 0x4CD7FF, be.clientMana(), be.clientCapacity(), name);
        }
    }

    private WandHudHandler() {
    }
}
