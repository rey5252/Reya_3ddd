package com.reya.starfall;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Starfall's own sounds, synthesized by {@code tools/gen_sounds.py}. */
public final class Sounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Starfall.MODID);

    public static final RegistryObject<SoundEvent> RAILGUN_CHARGE = sound("railgun.charge");
    public static final RegistryObject<SoundEvent> RAILGUN_FIRE = sound("railgun.fire");
    public static final RegistryObject<SoundEvent> RAILGUN_BEAM = sound("railgun.beam");
    public static final RegistryObject<SoundEvent> RAILGUN_IMPACT = sound("railgun.impact");
    public static final RegistryObject<SoundEvent> RAILGUN_ARC = sound("railgun.arc");
    public static final RegistryObject<SoundEvent> FILM_WHOOSH = sound("film.whoosh");
    public static final RegistryObject<SoundEvent> FILM_WARP = sound("film.warp");
    public static final RegistryObject<SoundEvent> FILM_LOCK = sound("film.lock");
    public static final RegistryObject<SoundEvent> FILM_UPLINK = sound("film.uplink");
    public static final RegistryObject<SoundEvent> FILM_TITLE = sound("film.title");
    public static final RegistryObject<SoundEvent> GUNGNIR_SPIN = sound("gungnir.spin");
    public static final RegistryObject<SoundEvent> GUNGNIR_LAUNCH = sound("gungnir.launch");
    public static final RegistryObject<SoundEvent> GUNGNIR_FALL = sound("gungnir.fall");
    public static final RegistryObject<SoundEvent> GUNGNIR_BOOM = sound("gungnir.boom");
    public static final RegistryObject<SoundEvent> SEVEN_WAKE = sound("seven.wake");
    public static final RegistryObject<SoundEvent> SEVEN_ARRAY = sound("seven.array");
    public static final RegistryObject<SoundEvent> SEVEN_BEAM = sound("seven.beam");
    public static final RegistryObject<SoundEvent> SEVEN_IMPACT = sound("seven.impact");
    public static final RegistryObject<SoundEvent> SEVEN_IGNITE = sound("seven.ignite");
    public static final RegistryObject<SoundEvent> SEVEN_FLARE = sound("seven.flare");
    public static final RegistryObject<SoundEvent> REMOTE_COVER = sound("remote.cover");
    public static final RegistryObject<SoundEvent> REMOTE_PRESS = sound("remote.press");
    public static final RegistryObject<SoundEvent> UI_HOVER = sound("ui.hover");
    public static final RegistryObject<SoundEvent> UI_SELECT = sound("ui.select");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Starfall.MODID, name)));
    }

    private Sounds() {
    }
}
