package com.reya.starfall.client;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.reya.starfall.Starfall;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The film's shaders: planets ray-traced per pixel, the sky's nebulae, a galaxy's disc, soft light, and lit metal.
 * If one fails to load on some driver the films fall back to plain drawing instead of breaking the game.
 */
@Mod.EventBusSubscriber(modid = Starfall.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FilmGfx {
    private static final Logger LOGGER = LogUtils.getLogger();
    static ShaderInstance planet, sky, galaxy, soft, hull;
    /** The film's lens: down and up the bloom chain, and the last pass onto the screen. */
    static ShaderInstance postDown, postUp, post;
    /** The strikes' halo, added over the world. */
    static ShaderInstance strikeGlow;
    /** The world's picture reeling while a strike lands. */
    static ShaderInstance worldImpact;

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        planet = sky = galaxy = soft = hull = null;
        postDown = postUp = post = strikeGlow = worldImpact = null;
        FilmMaps.clear();
        register(event, "film_planet", DefaultVertexFormat.POSITION_TEX_COLOR, s -> planet = s);
        register(event, "film_sky", DefaultVertexFormat.POSITION_TEX_COLOR, s -> sky = s);
        register(event, "film_galaxy", DefaultVertexFormat.POSITION_TEX_COLOR, s -> galaxy = s);
        register(event, "film_soft", DefaultVertexFormat.POSITION_TEX_COLOR, s -> soft = s);
        register(event, "film_hull", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, s -> hull = s);
        register(event, "film_post_down", DefaultVertexFormat.POSITION_TEX, s -> postDown = s);
        register(event, "film_post_up", DefaultVertexFormat.POSITION_TEX, s -> postUp = s);
        register(event, "film_post", DefaultVertexFormat.POSITION_TEX, s -> post = s);
        register(event, "strike_glow", DefaultVertexFormat.POSITION_TEX, s -> strikeGlow = s);
        register(event, "world_impact", DefaultVertexFormat.POSITION_TEX, s -> worldImpact = s);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> set) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(Starfall.MODID, name), format), set);
        } catch (Exception e) {
            LOGGER.error("Starfall: the film shader {} did not load, the films will be drawn plainly", name, e);
        }
    }

    /** All the film's shaders are there. */
    static boolean ready() {
        return planet != null && sky != null && galaxy != null && soft != null && hull != null;
    }

    private FilmGfx() {
    }
}
