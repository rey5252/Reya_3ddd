package com.reya.domgun;

import com.reya.domgun.network.Net;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(DomGun.MODID)
public class DomGun {
    public static final String MODID = "domgun";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> DOM_GUN = ITEMS.register("dom_gun",
            () -> new DomGunItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> DOM_AMMO = ITEMS.register("dom_ammo",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("domgun", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.domgun"))
            .icon(() -> new ItemStack(DOM_GUN.get()))
            .displayItems((params, output) -> {
                ItemStack gun = new ItemStack(DOM_GUN.get());
                DomGunItem.setAmmo(gun, Config.MAGAZINE_SIZE.get());
                output.accept(gun);
                output.accept(DOM_AMMO.get());
            })
            .build());

    public DomGun() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }
}
