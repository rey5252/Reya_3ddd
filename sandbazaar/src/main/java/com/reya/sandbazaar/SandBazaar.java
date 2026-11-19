package com.reya.sandbazaar;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(SandBazaar.MOD_ID)
public class SandBazaar {
    public static final String MOD_ID = "sandbazaar";

    public SandBazaar() {
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        BazaarCommand.register(event.getDispatcher());
    }
}
