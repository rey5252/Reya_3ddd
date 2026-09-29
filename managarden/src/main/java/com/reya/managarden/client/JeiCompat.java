package com.reya.managarden.client;

import java.util.List;

import com.reya.managarden.ManaGarden;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

/**
 * Only loaded when JEI is installed: tells JEI where the greenhouse GUI draws outside its panel (the
 * keeper, the name plates, the vines), so JEI's item lists keep clear of them.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ManaGarden.MODID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GreenhouseScreen.class, new IGuiContainerHandler<GreenhouseScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(GreenhouseScreen screen) {
                return screen.extraAreas();
            }
        });
    }
}
