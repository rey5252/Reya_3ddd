package com.reya.alfheimheart.machine.infuser.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** How a catalyst tints the infuser's mana: pink for alchemy, rosy white for conjuration, a pale violet for others. */
public final class ManaInfuserColours {
    private static final float[] PLAIN = {1.0F, 1.0F, 1.0F}, ALCHEMY = {1.0F, 0.6F, 1.0F}, CONJURATION = {1.0F, 0.82F, 0.92F},
            OTHER = {0.85F, 0.85F, 1.0F};

    public static float[] tint(ItemStack catalyst) {
        if (catalyst.isEmpty()) return PLAIN;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(catalyst.getItem());
        String path = id == null ? "" : id.getPath();
        if (path.contains("alchemy")) return ALCHEMY;
        if (path.contains("conjuration")) return CONJURATION;
        return OTHER;
    }

    /** A colour 0xRRGGBB tinted. */
    public static int tint(int rgb, float[] tint) {
        int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) * tint[0]));
        int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * tint[1]));
        int b = Math.min(255, Math.round((rgb & 0xFF) * tint[2]));
        return r << 16 | g << 8 | b;
    }

    private ManaInfuserColours() {
    }
}
