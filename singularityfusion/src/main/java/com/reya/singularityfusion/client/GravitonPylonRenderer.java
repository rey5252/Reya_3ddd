package com.reya.singularityfusion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.FusionGeometry;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * A graviton pylon. From a socket on its plinth a dark engraved shaft rises, aimed at its core's singularity (straight
 * up while it serves none), its glyphs glowing brighter the fuller the core; five rings float round its upper shaft,
 * tilted and turning, a wave of light running up them; a crystal at its point, and its ingredient floating beyond. In
 * a fusion the rings spread and spin fast, and the ingredient trembles, then leaves for the singularity in its turn
 * (the core's renderer flies it there).
 */
public class GravitonPylonRenderer implements BlockEntityRenderer<GravitonPylonBlockEntity> {
    private static final ResourceLocation TEXTURE = Fx.entity("pylon"), GLOW_TEXTURE = Fx.entity("pylon_glow"), GLOW = Fx.effect("glow");
    /** Where the pieces are on pylon.png and pylon_glow.png (64 x 64, drawn by tools/gen_textures.py): pixel corners. */
    private static final int SIZE = 64;
    static final int[][] REGIONS = {{0, 0, 16, 32}, {16, 0, 32, 16}, {16, 16, 32, 24}, {16, 24, 32, 28}, {32, 0, 48, 16}, {32, 16, 40, 32},
            {48, 0, 64, 16}, {0, 32, 64, 40}, {0, 40, 64, 44}, {0, 44, 64, 48}};
    private static final float[] SHAFT_SIDE = Fx.uv(REGIONS[0], SIZE), SHAFT_END = Fx.uv(REGIONS[1], SIZE), COLLAR_SIDE = Fx.uv(REGIONS[2], SIZE),
            SOCKET_SIDE = Fx.uv(REGIONS[3], SIZE), COLLAR_END = Fx.uv(REGIONS[4], SIZE), NECK_SIDE = Fx.uv(REGIONS[5], SIZE),
            CRYSTAL = Fx.uv(REGIONS[6], SIZE), RING_FACE = Fx.uv(REGIONS[7], SIZE), RING_WALL = Fx.uv(REGIONS[8], SIZE),
            RING_INNER = Fx.uv(REGIONS[9], SIZE);
    /** The shaft's pieces along the pylon: half their width, from, to (blocks from the plinth's top). */
    private static final float[][] SHAFT = {{0.27F, 0.22F, 0.98F}, {0.235F, 1.04F, 1.72F}, {0.2F, 1.78F, 2.4F}};
    private static final float COLLAR_HALF = 0.31F, COLLAR_FROM = -0.08F, COLLAR_TO = 0.2F, NECK_HALF = 0.11F, NECK_TO = 2.62F;
    private static final float SOCKET_HALF = 0.4F, SOCKET_HEIGHT = 0.12F;
    /** The rings round the upper shaft: where along it, their outer radius. */
    private static final float[][] RINGS = {{1.22F, 0.5F}, {1.48F, 0.47F}, {1.74F, 0.44F}, {2.0F, 0.41F}, {2.26F, 0.38F}};
    private static final float RING_WIDTH = 0.075F, RING_THICKNESS = 0.05F;
    private static final float CRYSTAL_HALF = 0.17F, CRYSTAL_HEIGHT = FusionGeometry.TIP - FusionGeometry.CRYSTAL;
    /** How a pylon looks this frame. */
    private record Look(float time, float spin, float lit, float spread, float charge) {
    }

    private final ItemRenderer items;

    public GravitonPylonRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(GravitonPylonBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockPos pos = be.getBlockPos();
        FusionCoreBlockEntity core = be.core() != null && level.getBlockEntity(be.core()) instanceof FusionCoreBlockEntity c ? c : null;
        BlockPos corePos = core == null ? null : core.getBlockPos();
        float time = (float) (level.getGameTime() % 120000L) + partialTick;

        // how lit it looks catches up with its core's charge, smoothly
        long now = System.nanoTime();
        float dt = be.lastFrameNanos == 0L ? 0.0F : Mth.clamp((now - be.lastFrameNanos) / 1.0E9F, 0.0F, 0.25F);
        be.lastFrameNanos = now;
        float target = core == null ? 0.0F : core.charge();
        be.shownCharge += (target - be.shownCharge) * (1.0F - (float) Math.exp(-dt * 1.1F));
        float charge = be.shownCharge;

        boolean fusing = core != null && core.fusing();
        float progress = fusing ? core.progress(partialTick) : 0.0F;
        float spread = fusing ? Fx.smooth(0.0F, FusionGeometry.BEAMS_IN, progress) * (1.0F - FusionGeometry.collapse(progress)) : 0.0F;
        float lit = core == null ? 0.14F : 0.22F + 0.78F * charge;
        if (fusing) lit = Math.max(lit, 0.8F + 0.2F * Mth.sin(time * 0.7F));
        be.spin = (be.spin + dt * (0.45F + 1.5F * charge + 6.0F * spread)) % (Fx.PI * 40.0F);
        int index = core == null ? -1 : core.pylons().indexOf(pos);
        float departure = index < 0 ? 2.0F : FusionGeometry.departure(index, core.pylons().size());
        boolean holding = !be.item().isEmpty() && !(fusing && progress >= departure);

        Vec3 aim = FusionGeometry.aim(pos, corePos);
        float scale = FusionGeometry.scale(pos, corePos);
        int light = LevelRenderer.getLightColor(level, pos.above());
        Look look = new Look(time, be.spin, lit, spread, charge);

        // the socket on the plinth, then the pylon in a frame of its own (y along it): solid, and its glow over it
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        socket(pose, solid, false, look, light);
        pylon(pose, solid, aim, scale, false, look, light);
        VertexConsumer glyphs = buffers.getBuffer(RenderType.eyes(GLOW_TEXTURE));
        socket(pose, glyphs, true, look, light);
        pylon(pose, glyphs, aim, scale, true, look, light);

        // light round the crystal and the socket
        Vec3 base = new Vec3(0.5D, 1.0D, 0.5D);
        Quaternionf camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.31F);
        float k = lit * pulse;
        billboard(pose, glow, camera, base.add(aim.scale(FusionGeometry.CRYSTAL * scale)), (0.32F + 0.5F * lit) * scale, 0.5F * k, 0.2F * k,
                0.95F * k);
        billboard(pose, glow, camera, base.add(0.0D, 0.1D, 0.0D), 0.62F, 0.22F * k, 0.08F * k, 0.42F * k);

        // the ingredient, floating beyond the point (trembling just before it leaves)
        if (holding) {
            Vec3 at = base.add(aim.scale((FusionGeometry.ITEM + 0.05F * Mth.sin(time / 9.0F)) * scale));
            float shake = fusing ? Fx.smooth(departure - 0.1F, departure, progress) * 0.05F : 0.0F;
            if (shake > 0.0F) {
                at = at.add(Mth.sin(time * 3.7F) * shake, Mth.sin(time * 4.3F + 1.0F) * shake, Mth.sin(time * 3.1F + 2.0F) * shake);
            }
            billboard(pose, glow, camera, at, 0.34F, 0.3F * k, 0.13F * k, 0.5F * k);
            pose.pushPose();
            pose.translate(at.x, at.y - 0.125D * 1.1D, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(time * 4.0F % 360.0F));
            pose.scale(1.1F, 1.1F, 1.1F);
            items.renderStatic(be.item(), ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, level,
                    (int) pos.asLong());
            pose.popPose();
        }
    }

    /** The socket on the plinth's top, the pylon's foot set in it. */
    private static void socket(PoseStack pose, VertexConsumer vc, boolean glow, Look look, int light) {
        float e = glow ? 0.004F : 0.0F, k = glow ? look.lit : 1.0F;
        Fx.box(vc, pose.last().pose(), pose.last().normal(), 0.5F - SOCKET_HALF - e, 1.0F - e, 0.5F - SOCKET_HALF - e, 0.5F + SOCKET_HALF + e,
                1.0F + SOCKET_HEIGHT + e, 0.5F + SOCKET_HALF + e, SOCKET_SIDE, COLLAR_END, k, k, k, 1.0F, glow ? LightTexture.FULL_BRIGHT : light);
    }

    /**
     * The pylon from its foot to its crystal, turned to point along `aim`: its collar, shaft and neck, the rings and the
     * crystal. Drawn twice: solid, lit by the world, then its glow (the same pieces a hair bigger, in what glows).
     */
    private static void pylon(PoseStack pose, VertexConsumer vc, Vec3 aim, float scale, boolean glow, Look look, int light) {
        pose.pushPose();
        pose.translate(0.5D, 1.0D, 0.5D);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(aim.x, aim.z)));
        pose.mulPose(Axis.XP.rotation((float) Math.acos(Mth.clamp(aim.y, -1.0D, 1.0D))));
        pose.scale(scale, scale, scale);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float e = glow ? 0.004F : 0.0F;
        int lightHere = glow ? LightTexture.FULL_BRIGHT : light;

        float k = glow ? look.lit * 0.8F : 1.0F;
        Fx.box(vc, m, n, -COLLAR_HALF - e, COLLAR_FROM - e, -COLLAR_HALF - e, COLLAR_HALF + e, COLLAR_TO + e, COLLAR_HALF + e, COLLAR_SIDE, COLLAR_END,
                k, k, k, 1.0F, lightHere);
        for (int i = 0; i < SHAFT.length; i++) {
            // the glyphs' light rises up the shaft in a slow wave
            k = glow ? look.lit * (0.68F + 0.32F * Mth.sin(look.time * 0.2F - i * 1.1F)) : 1.0F;
            float h = SHAFT[i][0] + e;
            Fx.box(vc, m, n, -h, SHAFT[i][1] - e, -h, h, SHAFT[i][2] + e, h, SHAFT_SIDE, SHAFT_END, k, k, k, 1.0F, lightHere);
        }
        k = glow ? look.lit : 1.0F;
        float neck = NECK_HALF + e;
        Fx.box(vc, m, n, -neck, SHAFT[2][2] - 0.02F, -neck, neck, NECK_TO + e, neck, NECK_SIDE, SHAFT_END, k, k, k, 1.0F, lightHere);

        for (int i = 0; i < RINGS.length; i++) {
            float wave = 0.5F + 0.5F * Mth.sin(look.time * 0.22F - i * 0.9F);
            k = glow ? look.lit * (0.4F + 0.6F * wave) : 1.0F;
            float along = RINGS[i][0] + (i - 2) * 0.08F * look.spread + 0.02F * Mth.sin(look.time * 0.09F + i * 1.7F);
            float ro = RINGS[i][1] + e, ri = RINGS[i][1] - RING_WIDTH - e, half = RING_THICKNESS * 0.5F + e;
            float precess = look.spin * (i % 2 == 0 ? 1.0F : -1.25F) + i * 1.3F;
            float tilt = (0.09F + 0.13F * look.charge + 0.2F * look.spread) * (0.7F + 0.075F * ((i * 37) % 5));
            pose.pushPose();
            pose.translate(0.0F, along, 0.0F);
            pose.mulPose(Axis.YP.rotation(precess));
            pose.mulPose(Axis.XP.rotation(tilt));
            pose.mulPose(Axis.YP.rotation(look.spin * (i % 2 == 0 ? 1.7F : -1.4F)));
            Fx.ring(vc, pose.last().pose(), pose.last().normal(), ri, ro, -half, half, 24, RING_FACE, RING_WALL, RING_INNER, k, k, k, 1.0F,
                    lightHere);
            pose.popPose();
        }

        // the crystal glows on its own
        k = glow ? 0.35F + 0.65F * look.lit : 1.0F;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(look.time * 0.05F % (Fx.PI * 2.0F)));
        Fx.crystal(vc, pose.last().pose(), pose.last().normal(), FusionGeometry.CRYSTAL, CRYSTAL_HALF + e, CRYSTAL_HEIGHT + e, CRYSTAL, k, k, k, 1.0F,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
        pose.popPose();
    }

    /** A glow facing the camera at a point (block coordinates), `size` either side, of colour (r, g, b) added. */
    static void billboard(PoseStack pose, VertexConsumer vc, Quaternionf camera, Vec3 at, float size, float r, float g, float b) {
        if (r + g + b <= 0.004F || size <= 0.0F) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera);
        Fx.square(vc, pose.last().pose(), pose.last().normal(), 0.0F, 0.0F, 0.0F, size, r, g, b, 1.0F);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(GravitonPylonBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
