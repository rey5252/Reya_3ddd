package com.reya.goldenquarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reya.goldenquarry.GoldenQuarry;
import com.reya.goldenquarry.VacuumChestBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The vacuum chest's tentacles in 3D, and its working area. A few tentacles come out of the
 * crystals on its sides and top, glossy purple backs and pink undersides with suckers
 * (textures/entity/vacuum_tentacle.png): each is a chain of little boxes that bends out of its
 * crystal, sways slowly and lies along the block's faces, falling under its own weight and
 * hanging over the edges (it is kept out of the block). While the red button is on, the working area is
 * outlined in purple.
 */
public class VacuumChestRenderer implements BlockEntityRenderer<VacuumChestBlockEntity> {
    private static final ResourceLocation SKIN = new ResourceLocation(GoldenQuarry.MODID, "textures/entity/vacuum_tentacle.png");
    private static final int SEGMENTS = 18;
    /** Face, where on it (0..1 across, 0..1 up), heading along the face (degrees, 0 = across, 90 = up), length, thickness, phase, curl. */
    private static final Object[][] TENTACLES = {
            {Direction.NORTH, 0.40F, 0.42F, 215.0F, 1.15F, 0.13F, 0.0F, 1.0F},
            {Direction.NORTH, 0.58F, 0.55F, 20.0F, 0.95F, 0.11F, 2.1F, -1.0F},
            {Direction.EAST, 0.45F, 0.40F, 250.0F, 1.2F, 0.13F, 4.0F, -1.0F},
            {Direction.EAST, 0.60F, 0.60F, 150.0F, 0.9F, 0.10F, 1.3F, 1.0F},
            {Direction.SOUTH, 0.50F, 0.45F, 290.0F, 1.1F, 0.12F, 5.2F, 1.0F},
            {Direction.WEST, 0.42F, 0.50F, 200.0F, 1.0F, 0.12F, 3.3F, -1.0F},
            {Direction.WEST, 0.62F, 0.40F, 330.0F, 0.85F, 0.10F, 0.7F, 1.0F},
            {Direction.UP, 0.45F, 0.50F, 160.0F, 1.1F, 0.12F, 2.7F, 1.0F},
            {Direction.UP, 0.58F, 0.45F, 20.0F, 1.0F, 0.11F, 4.6F, -1.0F}};

    public VacuumChestRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(VacuumChestBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        float time = level == null ? 0.0F : (level.getGameTime() + partialTick) / 20.0F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
        for (Object[] t : TENTACLES) {
            Direction face = (Direction) t[0];
            int faceLight = level == null ? light : LevelRenderer.getLightColor(level, be.getBlockPos().relative(face));
            tentacle(pose, vc, face, (float) t[1], (float) t[2], (float) t[3], (float) t[4], (float) t[5], (float) t[6], (float) t[7],
                    time, faceLight);
        }
        if (be.showArea()) {
            int r = be.range();
            AABB box = new AABB(-r, -r, -r, r + 1, r + 1, r + 1);
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 0.66F, 0.25F, 1.0F, 1.0F);
        }
    }

    /** One tentacle: its chain worked out for this moment, then drawn box by box. */
    private static void tentacle(PoseStack pose, VertexConsumer vc, Direction face, float fu, float fv, float heading, float length,
                                 float thick, float phase, float curl, float time, int light) {
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 across, up;
        if (face.getAxis() == Direction.Axis.Y) {
            across = new Vec3(1, 0, 0);
            up = new Vec3(0, 0, 1);
        } else {
            up = new Vec3(0, 1, 0);
            across = up.cross(normal);
        }
        Vec3 centre = new Vec3(0.5, 0.5, 0.5);
        Vec3 p = centre.add(normal.scale(0.5)).add(across.scale(fu - 0.5)).add(up.scale(fv - 0.5)).add(normal.scale(0.01));
        Vec3 dir = normal;
        double seg = length / SEGMENTS;
        Vec3[] pts = new Vec3[SEGMENTS + 1];
        pts[0] = p;
        for (int i = 1; i <= SEGMENTS; i++) {
            double s = i / (double) SEGMENTS;
            double ang = Math.toRadians(heading) + 0.55 * Math.sin(time * 0.9 + phase + s * 2.6) + curl * 1.9 * s * s * s
                    + 0.12 * Math.sin(time * 2.3 + phase * 2 + s * 7.0);
            Vec3 along = across.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
            // out of the crystal first, then drawn to the block's faces and falling under its weight
            Vec3 want = normal.scale(i <= 1 ? 0.8 : -1.4).add(along).add(0, -0.8 * s, 0).normalize();
            dir = dir.scale(0.3).add(want.scale(0.7)).normalize();
            p = keepOut(p.add(dir.scale(seg)));
            pts[i] = p;
        }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int i = 0; i < SEGMENTS; i++) {
            double s = i / (double) SEGMENTS;
            double w = thick * 0.6 * Math.pow(1 - s, 0.7) + 0.018;
            Vec3 a = pts[i], b = pts[i + 1];
            Vec3 tangent = b.subtract(a);
            if (tangent.lengthSqr() < 1.0E-8) continue;
            tangent = tangent.normalize();
            Vec3 mid = a.add(b).scale(0.5);
            // the back faces away from the block, the underside (with the suckers) towards it
            Vec3 out = mid.subtract(centre);
            out = out.subtract(tangent.scale(out.dot(tangent)));
            if (out.lengthSqr() < 1.0E-8) out = normal;
            out = out.normalize();
            Vec3 side = tangent.cross(out).normalize();
            Vec3 half = tangent.scale(seg * 0.6);
            Vec3 ho = out.scale(w / 2), hs = side.scale(w / 2);
            Vec3 c0 = mid.subtract(half), c1 = mid.add(half);
            int back = i % 3 == 1 ? 4 : 1, under = i % 2 == 0 ? 0 : 3;
            quad(vc, m, n, c0.add(ho).subtract(hs), c1.add(ho).subtract(hs), c1.add(ho).add(hs), c0.add(ho).add(hs), out, back, light);
            quad(vc, m, n, c0.subtract(ho).add(hs), c1.subtract(ho).add(hs), c1.subtract(ho).subtract(hs), c0.subtract(ho).subtract(hs),
                    out.scale(-1), under, light);
            quad(vc, m, n, c0.add(hs).add(ho), c1.add(hs).add(ho), c1.add(hs).subtract(ho), c0.add(hs).subtract(ho), side, 2, light);
            quad(vc, m, n, c0.subtract(hs).subtract(ho), c1.subtract(hs).subtract(ho), c1.subtract(hs).add(ho), c0.subtract(hs).add(ho),
                    side.scale(-1), 2, light);
            if (i == SEGMENTS - 1) {
                quad(vc, m, n, c1.add(ho).add(hs), c1.add(ho).subtract(hs), c1.subtract(ho).subtract(hs), c1.subtract(ho).add(hs), tangent, 1, light);
            }
        }
    }

    /** Pushed out of the block (and a hair off its faces), by the shortest way. */
    private static Vec3 keepOut(Vec3 p) {
        double e = 0.02;
        if (p.x <= -e || p.x >= 1 + e || p.y <= -e || p.y >= 1 + e || p.z <= -e || p.z >= 1 + e) return p;
        double[] d = {p.x + e, 1 + e - p.x, p.y + e, 1 + e - p.y, p.z + e, 1 + e - p.z};
        int k = 0;
        for (int i = 1; i < 6; i++) if (d[i] < d[k]) k = i;
        return switch (k) {
            case 0 -> new Vec3(-e, p.y, p.z);
            case 1 -> new Vec3(1 + e, p.y, p.z);
            case 2 -> new Vec3(p.x, -e, p.z);
            case 3 -> new Vec3(p.x, 1 + e, p.z);
            case 4 -> new Vec3(p.x, p.y, -e);
            default -> new Vec3(p.x, p.y, 1 + e);
        };
    }

    /** A quad with one of the skin's 4x4 tiles (0 sucker, 1 back, 2 side, 3 plain underside, 4 back with a spot). */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int tile, int light) {
        float u0 = (tile % 4) * 4 / 16.0F, v0 = (tile / 4) * 4 / 16.0F, u1 = u0 + 0.25F, v1 = v0 + 0.25F;
        vertex(vc, m, n, a, u0, v0, normal, light);
        vertex(vc, m, n, b, u1, v0, normal, light);
        vertex(vc, m, n, c, u1, v1, normal, light);
        vertex(vc, m, n, d, u0, v1, normal, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, Vec3 normal, int light) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(VacuumChestBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
