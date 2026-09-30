package com.reya.elvenportal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.elvenportal.ElvenPortal;
import com.reya.elvenportal.PortalBlock;
import com.reya.elvenportal.PortalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * What moves on the elven moon gate (the ring, the vine, the gem and the pedestal are the block's model):
 * <ul>
 * <li>the portal inside the ring: a swirl of green light (textures/entity/elven_gate/swirl.png, its frames
 * turning the spiral arms), which opens from the middle a little past its size and settles while the portal
 * has mana for a trade, and closes back to nothing when it hasn't; light shimmers over it, and a trade
 * flashes it bright;</li>
 * <li>a ring of runes turning slowly just inside the stone ring while the portal is open;</li>
 * <li>the gold lights on the vine twinkling, the gem glowing, motes circling the ring;</li>
 * <li>four natura crystals over the pedestal's corners, lying low while the portal is shut and rising to
 * float, bob and turn while it is open;</li>
 * <li>each trade's items: the one that went in flies into the swirl from the front, shrinking, and what the
 * elves send back comes out of it, rises and fades away.</li>
 * </ul>
 * Everything is placed in the model's pixels (the gate faces north, its front towards -z) and turned to the
 * block's facing. Glows use an additive render type: their colour is their strength.
 */
public class PortalRenderer implements BlockEntityRenderer<PortalBlockEntity> {
    private static final ResourceLocation SWIRL = texture("swirl"), RUNES = texture("runes"), GLOW = texture("glow"),
            CRYSTAL = texture("crystal");
    /** The portal inside the ring (model pixels): its middle and radius (its dark rim tucks under the ring). */
    private static final float CX = 8.0F, CY = 9.0F, CZ = 8.0F, DISC_R = 5.3F, RUNES_R = 5.0F;
    /** The swirl's frames (one under another, as tools/gen_block.py draws them) and how long each shows. */
    private static final int FRAMES = 20, FRAME_TICKS = 2;
    /** The gold lights on the vine: texture pixels of gate_vines.png (tools/gen_block.py), front pane and back. */
    private static final int[][] LIGHTS = {{14, 1}, {0, 13}};
    private static final float VINE_FRONT_Z = 5.9F, VINE_BACK_Z = 10.1F;
    /** The gem's front and back faces (model pixels). */
    private static final float GEM_X = 8.0F, GEM_Y = 14.5F, GEM_FRONT_Z = 5.0F, GEM_BACK_Z = 11.0F;
    /** The natura crystals over the lower step's corners (model pixels x, z), their height at rest and afloat. */
    private static final float[][] CRYSTALS = {{2.0F, 3.0F}, {14.0F, 3.0F}, {2.0F, 13.0F}, {14.0F, 13.0F}};
    private static final float CRYSTAL_REST_Y = 2.3F, CRYSTAL_FLOAT_Y = 3.6F, CRYSTAL_W = 0.8F, CRYSTAL_H = 1.3F;
    /** How long a trade's flash lasts (ticks, from when the item reaches the swirl). */
    private static final float FLASH_TICKS = 12.0F;
    private static final float PI = (float) Math.PI;

    private final ItemRenderer items;

    public PortalRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(ElvenPortal.MODID, "textures/entity/elven_gate/" + name + ".png");
    }

    @Override
    public void render(PortalBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(PortalBlock.FACING)) return;
        float time = be.clientAge() + partialTick;
        float open = be.openness(partialTick);
        float fade = open * open * (3.0F - 2.0F * open);
        float turn = modelTurn(state.getValue(PortalBlock.FACING));
        float since = time - (be.flyInAge() + PortalBlockEntity.FLY_IN_TICKS);
        float flash = since >= 0.0F && since < FLASH_TICKS ? 1.0F - since / FLASH_TICKS : 0.0F;

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        pose.translate(-0.5D, 0.0D, -0.5D);
        flights(be, pose, buffers, light, time);
        pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);           // model pixels from here on
        float pop = backOut(open);
        if (pop > 0.01F) {
            swirl(pose, buffers, time, pop, fade, flash);
            runes(pose, buffers, time, pop, fade, flash);
        }
        crystals(pose, buffers, time, fade, light, turn);
        lights(pose, buffers, time, fade, flash, turn);
        pose.popPose();
    }

    /** The blockstate's y turn of the model (it faces north). */
    private static float modelTurn(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    /** Opens a little past full size and settles back (0 to 1, overshooting in between). */
    private static float backOut(float p) {
        if (p <= 0.0F) return 0.0F;
        if (p >= 1.0F) return 1.0F;
        float c1 = 1.9F, c3 = c1 + 1.0F, q = p - 1.0F;
        return 1.0F + c3 * q * q * q + c1 * q * q;
    }

    // ------------------------------------------------------------------ the portal

    /**
     * The swirl: its frame on a disc in the ring (drawn once, seen from both sides), light shimmering over
     * it, and a bright flash when a trade's item reaches it.
     */
    private void swirl(PoseStack pose, MultiBufferSource buffers, float time, float pop, float fade, float flash) {
        int frame = (int) (time / FRAME_TICKS) % FRAMES;
        float v1 = frame / (float) FRAMES, v2 = (frame + 1) / (float) FRAMES;
        float r = DISC_R * pop;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer disc = buffers.getBuffer(RenderType.entityTranslucent(SWIRL));
        quad(disc, m, n, CX, CY, CZ, r, 0.0F, 1.0F, v1, v2, 1.0F, 1.0F, 1.0F, fade, LightTexture.FULL_BRIGHT);
        // light over it: a slow shimmer, and the flash of a trade
        float shimmer = 0.18F + 0.1F * Mth.sin(time / 7.0F) + 0.75F * flash;
        float k = shimmer * fade;
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(SWIRL));
        both(glow, m, n, CX, CY, CZ, r, 0.0F, 1.0F, v1, v2, 0.75F * k, k, 0.7F * k);
    }

    /** Runes just inside the ring, turning slowly (the other way at the back), brighter on a trade. */
    private void runes(PoseStack pose, MultiBufferSource buffers, float time, float pop, float fade, float flash) {
        float k = fade * (0.55F + 0.2F * Mth.sin(time / 11.0F) + 0.45F * flash);
        if (k <= 0.01F) return;
        VertexConsumer buffer = buffers.getBuffer(RenderType.eyes(RUNES));
        float r = RUNES_R * (0.6F + 0.4F * pop);
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            pose.translate(CX, CY, side == 0 ? CZ - 0.12F : CZ + 0.12F);
            pose.mulPose(Axis.ZP.rotationDegrees(time * 1.6F * (side == 0 ? 1.0F : -1.0F)));
            both(buffer, pose.last().pose(), pose.last().normal(), 0.0F, 0.0F, 0.0F, r, 0.0F, 1.0F, 0.0F, 1.0F,
                    0.55F * k, k, 0.45F * k);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ lights, motes, crystals

    /**
     * The gold lights on the vine twinkle (brighter while the portal is open), the gem glows green while it
     * is open, and three motes circle the ring, weaving in front of it and behind.
     */
    private void lights(PoseStack pose, MultiBufferSource buffers, float time, float fade, float flash, float turn) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.eyes(GLOW));
        for (int i = 0; i < LIGHTS.length; i++) {
            float beat = 0.5F + 0.5F * Mth.sin(time / 9.0F + i * 2.3F);
            float k = (0.25F + 0.55F * beat * beat) * (0.45F + 0.55F * fade);
            float y = 16.0F - (LIGHTS[i][1] + 0.5F);
            // the vine's texture is drawn as seen from each side: at the front its left is +x, at the back -x
            glow(pose, buffer, 16.0F - (LIGHTS[i][0] + 0.5F), y, VINE_FRONT_Z - 0.3F, 1.4F, k, 0.85F * k, 0.35F * k, turn);
            glow(pose, buffer, LIGHTS[i][0] + 0.5F, y, VINE_BACK_Z + 0.3F, 1.4F, k, 0.85F * k, 0.35F * k, turn);
        }
        if (fade <= 0.01F) return;
        float gem = fade * (0.55F + 0.25F * Mth.sin(time / 5.0F) + 0.4F * flash);
        glow(pose, buffer, GEM_X, GEM_Y, GEM_FRONT_Z - 0.3F, 2.2F, 0.45F * gem, gem, 0.4F * gem, turn);
        glow(pose, buffer, GEM_X, GEM_Y, GEM_BACK_Z + 0.3F, 2.2F, 0.45F * gem, gem, 0.4F * gem, turn);
        int[] colours = {0xFFE27A, 0xFF9AD8, 0xB6F59A};
        for (int k = 0; k < 3; k++) {
            float a = time * 0.07F + k * 2.0F * PI / 3.0F;
            float x = CX + Mth.cos(a) * 6.6F, y = CY + Mth.sin(a) * 6.6F, z = CZ + 3.2F * Mth.sin(a * 2.0F + k);
            int c = colours[k];
            float s = fade * (0.7F + 0.3F * Mth.sin(time / 3.0F + k));
            glow(pose, buffer, x, y, z, 1.1F, (c >> 16 & 0xFF) / 255.0F * s, (c >> 8 & 0xFF) / 255.0F * s, (c & 0xFF) / 255.0F * s, turn);
        }
    }

    /** A glow facing the camera at a point (model pixels), `size` pixels across, of colour (r, g, b) added. */
    private static void glow(PoseStack pose, VertexConsumer buffer, float x, float y, float z, float size, float r, float g, float b,
                             float turn) {
        if (r + g + b <= 0.01F) return;
        Quaternionf camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(turn));               // undo the block's turn, then face the camera
        pose.mulPose(camera);
        both(buffer, pose.last().pose(), pose.last().normal(), 0.0F, 0.0F, 0.0F, size, 0.0F, 1.0F, 0.0F, 1.0F, r, g, b);
        pose.popPose();
    }

    /**
     * The natura crystals: little green double pyramids over the lower step's corners. While the portal is
     * shut they rest low and dim; open, they rise, bob and turn, and glow.
     */
    private void crystals(PoseStack pose, MultiBufferSource buffers, float time, float fade, int light, float turn) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(CRYSTAL));
        int lit = fade > 0.5F ? LightTexture.FULL_BRIGHT : light;
        for (int i = 0; i < CRYSTALS.length; i++) {
            float bob = Mth.sin(time / 10.0F + i * 1.7F) * 0.35F * fade;
            float y = Mth.lerp(fade, CRYSTAL_REST_Y, CRYSTAL_FLOAT_Y) + bob;
            pose.pushPose();
            pose.translate(CRYSTALS[i][0], y, CRYSTALS[i][1]);
            pose.mulPose(Axis.YP.rotationDegrees(time * 3.0F * fade + i * 40.0F));
            crystal(buffer, pose.last().pose(), pose.last().normal(), lit);
            pose.popPose();
        }
        if (fade <= 0.01F) return;
        VertexConsumer glowBuffer = buffers.getBuffer(RenderType.eyes(GLOW));
        for (int i = 0; i < CRYSTALS.length; i++) {
            float bob = Mth.sin(time / 10.0F + i * 1.7F) * 0.35F * fade;
            float y = Mth.lerp(fade, CRYSTAL_REST_Y, CRYSTAL_FLOAT_Y) + bob;
            float k = fade * (0.3F + 0.15F * Mth.sin(time / 6.0F + i));
            glow(pose, glowBuffer, CRYSTALS[i][0], y, CRYSTALS[i][1], 2.4F, 0.4F * k, k, 0.45F * k, turn);
        }
    }

    /** A double pyramid round (0, 0, 0): four faces up to its tip, four down, each face a triangle (a quad with its tip twice). */
    private static void crystal(VertexConsumer buffer, Matrix4f m, Matrix3f n, int light) {
        float w = CRYSTAL_W, h = CRYSTAL_H;
        float[][] rim = {{w, 0.0F}, {0.0F, w}, {-w, 0.0F}, {0.0F, -w}};
        for (int i = 0; i < 4; i++) {
            float[] a = rim[i], b = rim[(i + 1) % 4];
            float shade = i % 2 == 0 ? 1.0F : 0.82F;
            // up: the texture's lit rows; down: its dark rows
            vertex(buffer, m, n, a[0], 0.0F, a[1], 0.0F, 0.5F, shade, shade, shade, 1.0F, light);
            vertex(buffer, m, n, b[0], 0.0F, b[1], 1.0F, 0.5F, shade, shade, shade, 1.0F, light);
            vertex(buffer, m, n, 0.0F, h, 0.0F, 0.5F, 0.0F, shade, shade, shade, 1.0F, light);
            vertex(buffer, m, n, 0.0F, h, 0.0F, 0.5F, 0.0F, shade, shade, shade, 1.0F, light);
            float dark = shade * 0.8F;
            vertex(buffer, m, n, b[0], 0.0F, b[1], 1.0F, 0.5F, dark, dark, dark, 1.0F, light);
            vertex(buffer, m, n, a[0], 0.0F, a[1], 0.0F, 0.5F, dark, dark, dark, 1.0F, light);
            vertex(buffer, m, n, 0.0F, -h, 0.0F, 0.5F, 1.0F, dark, dark, dark, 1.0F, light);
            vertex(buffer, m, n, 0.0F, -h, 0.0F, 0.5F, 1.0F, dark, dark, dark, 1.0F, light);
        }
    }

    // ------------------------------------------------------------------ quads

    /** A square of half-size r round (x, y, z) facing -z (the front), with the texture's (u1..u2, v1..v2) upright. */
    private static void quad(VertexConsumer buffer, Matrix4f m, Matrix3f n, float x, float y, float z, float r,
                             float u1, float u2, float v1, float v2, float red, float green, float blue, float alpha, int light) {
        // seen from the front (-z) its left is +x
        vertex(buffer, m, n, x + r, y + r, z, u1, v1, red, green, blue, alpha, light);
        vertex(buffer, m, n, x + r, y - r, z, u1, v2, red, green, blue, alpha, light);
        vertex(buffer, m, n, x - r, y - r, z, u2, v2, red, green, blue, alpha, light);
        vertex(buffer, m, n, x - r, y + r, z, u2, v1, red, green, blue, alpha, light);
    }

    /** The same square seen from both sides (for render types that cull the back of a face), full bright. */
    private static void both(VertexConsumer buffer, Matrix4f m, Matrix3f n, float x, float y, float z, float r,
                             float u1, float u2, float v1, float v2, float red, float green, float blue) {
        quad(buffer, m, n, x, y, z, r, u1, u2, v1, v2, red, green, blue, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, x - r, y + r, z, u1, v1, red, green, blue, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, x - r, y - r, z, u1, v2, red, green, blue, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, x + r, y - r, z, u2, v2, red, green, blue, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, x + r, y + r, z, u2, v1, red, green, blue, 1.0F, LightTexture.FULL_BRIGHT);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               float red, float green, float blue, float alpha, int light) {
        // lit as a face looking up, so it is as bright from every side
        buffer.vertex(m, x, y, z).color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    // ------------------------------------------------------------------ the traded items

    /** The last trade's items (in blocks): the one that went in flies into the swirl from the front, what came back flies out and up. */
    private void flights(PortalBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time) {
        float middle = CY / 16.0F;
        float in = (time - be.flyInAge()) / PortalBlockEntity.FLY_IN_TICKS;
        if (in >= 0.0F && in < 1.0F && !be.flyIn().isEmpty()) {
            float e = in * in * (3.0F - 2.0F * in);
            item(be, be.flyIn(), pose, buffers, light, Mth.lerp(e, 0.95F, middle), Mth.lerp(e, -0.35F, 0.5F), 1.0F - 0.9F * in * in, time);
        }
        float out = (time - be.flyOutAge()) / PortalBlockEntity.FLY_OUT_TICKS;
        if (out >= 0.0F && out < 1.0F && !be.flyOut().isEmpty()) {
            float e = 1.0F - (1.0F - out) * (1.0F - out);
            // grows out of the swirl, then shrinks away as it rises
            item(be, be.flyOut(), pose, buffers, light, Mth.lerp(e, middle, 1.1F), Mth.lerp(e, 0.5F, -0.4F),
                    Mth.sin(Math.min(1.0F, out * 1.15F) * PI), time);
        }
    }

    private void item(PortalBlockEntity be, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, float y, float z,
                      float scale, float time) {
        if (scale <= 0.01F) return;
        pose.pushPose();
        pose.translate(0.5D, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(time * 9.0F));
        pose.scale(0.75F * scale, 0.75F * scale, 0.75F * scale);
        items.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
