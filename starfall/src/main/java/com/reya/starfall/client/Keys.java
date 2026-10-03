package com.reya.starfall.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    private static final String CATEGORY = "key.categories.starfall";

    public static final KeyMapping RAILGUN = new KeyMapping("key.starfall.railgun", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping GUNGNIR = new KeyMapping("key.starfall.gungnir", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);
    public static final KeyMapping SEVEN_STARS = new KeyMapping("key.starfall.seven_stars", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY);
    public static final KeyMapping MENU = new KeyMapping("key.starfall.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping FILM = new KeyMapping("key.starfall.film", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    /** The key that selects each skill, by skill index. */
    public static final KeyMapping[] SKILLS = {RAILGUN, GUNGNIR, SEVEN_STARS};

    private Keys() {
    }
}
