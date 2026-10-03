package com.reya.starfall.client;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.logging.LogUtils;
import com.reya.starfall.Starfall;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * The film's photographic maps (textures/film, see tools/fetch_textures.py for where they come from): decoded
 * with STB, which reads JPEG as well as PNG, and uploaded with mipmaps and smooth filtering, wrapping round in
 * longitude. A map that isn't there is 0, and the shaders then draw that body procedurally.
 */
final class FilmMaps {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Integer> LOADED = new HashMap<>();

    static int earthDay() {
        return get("earth_day.jpg");
    }

    /** Red: city lights, green: clouds, blue: water. */
    static int earthAux() {
        return get("earth_aux.png");
    }

    static int moon() {
        return get("moon.jpg");
    }

    static int jupiter() {
        return get("jupiter.jpg");
    }

    static int milkyWay() {
        return get("milky_way.jpg");
    }

    private static int get(String file) {
        Integer id = LOADED.get(file);
        if (id == null) {
            id = load(file);
            LOADED.put(file, id);
        }
        return id;
    }

    private static int load(String file) {
        ResourceLocation rl = new ResourceLocation(Starfall.MODID, "textures/film/" + file);
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) return 0;
        ByteBuffer encoded = null;
        ByteBuffer pixels = null;
        try (InputStream in = res.get().open(); MemoryStack stack = MemoryStack.stackPush()) {
            encoded = TextureUtil.readResource(in);
            encoded.rewind();
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), n = stack.mallocInt(1);
            pixels = STBImage.stbi_load_from_memory(encoded, w, h, n, 4);
            if (pixels == null) throw new IllegalStateException(STBImage.stbi_failure_reason());
            int id = TextureUtil.generateTextureId();
            GlStateManager._bindTexture(id);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w.get(0), h.get(0), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 12);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(0);
            LOGGER.info("Starfall: film map {} ({}x{})", file, w.get(0), h.get(0));
            return id;
        } catch (Exception e) {
            LOGGER.error("Starfall: the film map {} did not load, that body is drawn procedurally", file, e);
            return 0;
        } finally {
            if (pixels != null) STBImage.stbi_image_free(pixels);
            if (encoded != null) MemoryUtil.memFree(encoded);
        }
    }

    /** Forgets the maps (on a resource reload they're read again). */
    static void clear() {
        for (int id : LOADED.values()) if (id != 0) TextureUtil.releaseTextureId(id);
        LOADED.clear();
    }

    private FilmMaps() {
    }
}
