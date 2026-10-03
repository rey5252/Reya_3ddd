package com.reya.starfall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.starfall.Skill;
import com.reya.starfall.Starfall;
import com.reya.starfall.StellarRemoteItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the Stellar Remote in 3D from its parts (see tools/gen_textures.py for the geometry): the navy body, the
 * glowing trims, the button and its halo, and the hazard-striped box over it turned about its hinge. The radar on the screen,
 * the skill code and the keypad digits are drawn live. In first person a thumb flips the cover and presses.
 */
public final class RemoteRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation BODY = part("body"), GLOW = part("glow"), BUTTON = part("button"),
            HALO = part("button_halo"), COVER_FRAME = part("cover_frame"), COVER_GLASS = part("cover_glass");
    public static final ResourceLocation[] PARTS = {BODY, GLOW, BUTTON, HALO, COVER_FRAME, COVER_GLASS};

    // geometry in model pixels, matching the part models
    private static final float HINGE_Y = 9.95F, HINGE_Z = 9.47F;
    private static final float TAB_X = 9.3F, TAB_Y = 5.75F, TAB_Z = 10.6F;
    private static final float SCREEN_X0 = 6.25F, SCREEN_X1 = 9.75F, SCREEN_Y0 = 11.55F, SCREEN_Y1 = 14.65F, SCREEN_Z = 9.61F;
    private static final float LABEL_Y = 5.25F, LABEL_Z = 9.51F;
    private static final float KEYS_Z = 9.70F;
    private static final float BUTTON_X = 9.3F, BUTTON_Y = 7.95F, BUTTON_FRONT = 10.15F;
    private static final float[] KEY_ROWS = {2.95F, 1.3F};

    private static final float TWO_PI = (float) (Math.PI * 2.0D);

    /** The living entity being drawn right now, so a remote in a hand knows whose hand it is. */
    static LivingEntity holder;

    private static RemoteRenderer instance;
    private ModelPart thumb;

    private RemoteRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    public static RemoteRenderer get() {
        if (instance == null) instance = new RemoteRenderer(Minecraft.getInstance());
        return instance;
    }

    private static ResourceLocation part(String name) {
        return new ResourceLocation(Starfall.MODID, "item/remote/" + name);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getFrameTime();
        LivingEntity who = context.firstPerson() ? mc.player
                : context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? holder : null;
        float t = who == null ? -1.0F : RemoteAnimation.age(who.getId(), partial);
        float time = (mc.level == null ? 0 : mc.level.getGameTime() % 24000L) + partial;
        Skill skill = StellarRemoteItem.skill(stack);
        ItemRenderer items = mc.getItemRenderer();
        ModelManager models = mc.getModelManager();

        items.renderModelLists(models.getModel(BODY), stack, light, overlay, pose, buffers.getBuffer(Sheets.cutoutBlockSheet()));
        screen(pose, buffers, skill, time, t);
        labels(pose, buffers, skill, light);

        // the button, pushed in as the thumb presses, and the cover swung about its hinge
        float press = RemoteAnimation.press(t);
        float angle = RemoteAnimation.cover(t);
        pose.pushPose();
        pose.translate(0.0F, 0.0F, -press / 16.0F);
        items.renderModelLists(models.getModel(BUTTON), stack, LightTexture.FULL_BRIGHT, overlay, pose, buffers.getBuffer(Sheets.cutoutBlockSheet()));
        pose.popPose();
        pose.pushPose();
        hinge(pose, angle);
        items.renderModelLists(models.getModel(COVER_FRAME), stack, light, overlay, pose, buffers.getBuffer(Sheets.cutoutBlockSheet()));
        pose.popPose();
        // the solid parts must be down before anything is blended over them
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(Sheets.cutoutBlockSheet());

        // glow: the button's halo (brighter as it bottoms out) and the trims
        pose.pushPose();
        pose.translate(0.0F, 0.0F, -press / 16.0F);
        VertexConsumer halo = buffers.getBuffer(RenderType.eyes(TextureAtlas.LOCATION_BLOCKS));
        int passes = 1 + Math.round(RemoteAnimation.flare(t) * 3.0F);
        for (int i = 0; i < passes; i++) {
            items.renderModelLists(models.getModel(HALO), stack, LightTexture.FULL_BRIGHT, overlay, pose, halo);
        }
        pose.popPose();
        items.renderModelLists(models.getModel(GLOW), stack, LightTexture.FULL_BRIGHT, overlay, pose,
                buffers.getBuffer(RenderType.eyes(TextureAtlas.LOCATION_BLOCKS)));

        // and last the cover's glass
        pose.pushPose();
        hinge(pose, angle);
        items.renderModelLists(models.getModel(COVER_GLASS), stack, light, overlay, pose,
                buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)));
        pose.popPose();

        if (context.firstPerson() && t >= 0.0F && t < 15.0F && mc.player != null) {
            thumb(pose, buffers, mc.player, t, angle, press, light);
        }
    }

    private static void hinge(PoseStack pose, float degrees) {
        float y = HINGE_Y / 16.0F, z = HINGE_Z / 16.0F;
        pose.translate(0.0F, y, z);
        pose.mulPose(Axis.XP.rotationDegrees(degrees));
        pose.translate(0.0F, -y, -z);
    }

    /** Where the cover's tab is (model pixels) when the cover is open by {@code degrees}. */
    private static Vector3f tab(float degrees) {
        float a = degrees * Mth.DEG_TO_RAD;
        float dy = TAB_Y - HINGE_Y, dz = TAB_Z - HINGE_Z;
        float cos = Mth.cos(a), sin = Mth.sin(a);
        return new Vector3f(TAB_X, HINGE_Y + dy * cos - dz * sin, HINGE_Z + dy * sin + dz * cos);
    }

    // ------------------------------------------------------------------ the screen, the label and the keys

    private static void screen(PoseStack pose, MultiBufferSource buffers, Skill skill, float time, float t) {
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = pose.last().pose();
        int color = skill.color;
        float cx = (SCREEN_X0 + SCREEN_X1) * 0.5F + 0.45F, cy = (SCREEN_Y0 + SCREEN_Y1) * 0.5F, z = SCREEN_Z;
        float r = 1.25F;
        quad(vc, m, SCREEN_X0, SCREEN_Y0, SCREEN_X1, SCREEN_Y1, z, Fx.argb(color, 0.10F));
        ring(vc, m, cx, cy, z, r, 0.07F, Fx.argb(color, 0.85F));
        ring(vc, m, cx, cy, z, r * 0.55F, 0.05F, Fx.argb(color, 0.5F));
        quad(vc, m, cx - r, cy - 0.025F, cx + r, cy + 0.025F, z, Fx.argb(color, 0.35F));
        quad(vc, m, cx - 0.025F, cy - r, cx + 0.025F, cy + r, z, Fx.argb(color, 0.35F));
        // the sweep and the fading trail behind it
        float sweep = time * 0.16F;
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float a0 = sweep - i * 0.11F, a1 = sweep - (i + 1) * 0.11F;
            int c = Fx.argb(color, 0.6F * (1.0F - i / (float) steps));
            vc.vertex(m, cx / 16.0F, cy / 16.0F, z / 16.0F).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).endVertex();
            vc.vertex(m, cx / 16.0F, cy / 16.0F, z / 16.0F).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).endVertex();
            vc.vertex(m, (cx + Mth.cos(a1) * r) / 16.0F, (cy + Mth.sin(a1) * r) / 16.0F, z / 16.0F).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).endVertex();
            vc.vertex(m, (cx + Mth.cos(a0) * r) / 16.0F, (cy + Mth.sin(a0) * r) / 16.0F, z / 16.0F).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).endVertex();
        }
        // contacts, bright as the sweep passes them
        float[][] blips = {{0.55F, 1.1F}, {0.8F, 3.6F}, {0.35F, 5.0F}};
        for (float[] b : blips) {
            float since = Mth.positiveModulo(sweep - b[1], TWO_PI);
            float k = Math.max(0.15F, 1.0F - since / 2.5F);
            float bx = cx + Mth.cos(b[1]) * r * b[0], by = cy + Mth.sin(b[1]) * r * b[0];
            quad(vc, m, bx - 0.09F, by - 0.09F, bx + 0.09F, by + 0.09F, z, Fx.argb(0xFFFFFF, k));
        }
        // signal bars down the left of the screen
        for (int i = 0; i < 4; i++) {
            float h = 0.25F + 0.2F * i;
            float on = 0.5F + 0.5F * Mth.sin(time * 0.3F + i);
            quad(vc, m, SCREEN_X0 + 0.2F + i * 0.22F, SCREEN_Y0 + 0.25F, SCREEN_X0 + 0.36F + i * 0.22F, SCREEN_Y0 + 0.25F + h, z,
                    Fx.argb(color, 0.4F + 0.5F * on));
        }
        // while pressing: the lock box closes in on the centre and blinks
        if (t >= 0.0F && t < 60.0F) {
            float k = Mth.clamp(t / 8.0F, 0.0F, 1.0F);
            float size = 0.9F - 0.5F * RemoteAnimation.smooth(k);
            boolean on = t < RemoteAnimation.FIRE || ((int) (t * 0.5F)) % 2 == 0;
            if (on) {
                int c = Fx.argb(0xFFFFFF, 0.9F);
                float w = 0.06F;
                quad(vc, m, cx - size, cy - size, cx + size, cy - size + w, z, c);
                quad(vc, m, cx - size, cy + size - w, cx + size, cy + size, z, c);
                quad(vc, m, cx - size, cy - size, cx - size + w, cy + size, z, c);
                quad(vc, m, cx + size - w, cy - size, cx + size, cy + size, z, c);
            }
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        vc.vertex(m, x0 / 16.0F, y0 / 16.0F, z / 16.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
        vc.vertex(m, x1 / 16.0F, y0 / 16.0F, z / 16.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
        vc.vertex(m, x1 / 16.0F, y1 / 16.0F, z / 16.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
        vc.vertex(m, x0 / 16.0F, y1 / 16.0F, z / 16.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float cx, float cy, float z, float r, float w, int argb) {
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            float a0 = TWO_PI * i / seg, a1 = TWO_PI * (i + 1) / seg;
            float ri = r - w * 0.5F, ro = r + w * 0.5F;
            vc.vertex(m, (cx + Mth.cos(a0) * ri) / 16.0F, (cy + Mth.sin(a0) * ri) / 16.0F, z / 16.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
            vc.vertex(m, (cx + Mth.cos(a1) * ri) / 16.0F, (cy + Mth.sin(a1) * ri) / 16.0F, z / 16.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
            vc.vertex(m, (cx + Mth.cos(a1) * ro) / 16.0F, (cy + Mth.sin(a1) * ro) / 16.0F, z / 16.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
            vc.vertex(m, (cx + Mth.cos(a0) * ro) / 16.0F, (cy + Mth.sin(a0) * ro) / 16.0F, z / 16.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
        }
    }

    private static void labels(PoseStack pose, MultiBufferSource buffers, Skill skill, int light) {
        Font font = Minecraft.getInstance().font;
        String code = skill.tag().getString();
        float s = 0.0054F;
        pose.pushPose();
        pose.translate(0.5F, LABEL_Y / 16.0F, LABEL_Z / 16.0F);
        pose.scale(s, -s, s);
        font.drawInBatch(code, -font.width(code) / 2.0F, -3.5F, 0xFF000000 | Fx.mix(0xFFE8E8F0, 0xFF000000 | skill.color, 0.35F),
                false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
        float k = 0.009F;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                String digit = String.valueOf(1 + col + row * 3);
                float x = 6.25F + col * 1.2F + 0.525F, y = KEY_ROWS[row] + 0.75F;
                pose.pushPose();
                pose.translate(x / 16.0F, y / 16.0F, KEYS_Z / 16.0F);
                pose.scale(k, -k, k);
                font.drawInBatch(digit, -font.width(digit) / 2.0F, -3.5F, 0xFFE6EAF4, false, pose.last().pose(), buffers,
                        Font.DisplayMode.POLYGON_OFFSET, 0, light);
                pose.popPose();
            }
        }
    }

    // ------------------------------------------------------------------ the thumb

    private ModelPart thumbPart() {
        if (thumb == null) {
            MeshDefinition mesh = new MeshDefinition();
            // the hand end of the arm's skin texture
            mesh.getRoot().addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(40, 24)
                    .addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
            thumb = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("thumb");
        }
        return thumb;
    }

    private void thumb(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, float t, float angle,
                       float press, int light) {
        Vector3f entry = new Vector3f(17.0F, 5.0F, 11.0F);
        Vector3f underTab = tab(0.0F).add(0.4F, -0.45F, 0.3F);
        Vector3f approach = new Vector3f(BUTTON_X, BUTTON_Y, 11.6F);
        Vector3f contact = new Vector3f(BUTTON_X, BUTTON_Y, BUTTON_FRONT + 0.06F - press);
        Vector3f lift = new Vector3f(BUTTON_X + 1.2F, BUTTON_Y, 12.0F);
        Vector3f exit = new Vector3f(17.5F, 5.5F, 11.5F);
        Vector3f tip;
        if (t < 2.0F) {
            tip = lerp(entry, underTab, RemoteAnimation.smooth(t / 2.0F));
        } else if (t < 5.0F) {
            tip = tab(angle).add(0.4F, -0.45F, 0.3F);
        } else if (t < 7.0F) {
            tip = lerp(tab(RemoteAnimation.COVER_OPEN).add(0.4F, -0.45F, 0.3F), approach, RemoteAnimation.smooth((t - 5.0F) / 2.0F));
        } else if (t < 8.8F) {
            tip = lerp(approach, contact, RemoteAnimation.smooth((t - 7.0F) / 1.8F));
        } else if (t < 10.2F) {
            tip = contact;
        } else if (t < 11.6F) {
            tip = lerp(contact, lift, RemoteAnimation.smooth((t - 10.2F) / 1.4F));
        } else {
            tip = lerp(lift, exit, RemoteAnimation.smooth((t - 11.6F) / 3.4F));
        }
        // from the tip back towards the hand, which holds the remote from the right
        Vector3f dir = new Vector3f(0.9F, -0.3F, 0.3F).normalize();
        pose.pushPose();
        pose.translate(tip.x() / 16.0F, tip.y() / 16.0F, tip.z() / 16.0F);
        pose.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, dir.x(), dir.y(), dir.z()));
        pose.scale(0.62F, 0.56F, 2.2F);
        thumbPart().render(pose, buffers.getBuffer(RenderType.entitySolid(player.getSkinTextureLocation())), light,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float k) {
        return new Vector3f(a).lerp(b, k);
    }

    // ------------------------------------------------------------------ hand and colours

    /**
     * Where the remote sits in first person: the usual spot for an item in hand, lifted towards the eyes and
     * turned to face them while the cover is flipped and the button pressed, with a small dip on the press.
     */
    public static boolean handTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, float partial, float equip) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float t = RemoteAnimation.age(player.getId(), partial);
        float k = RemoteAnimation.raise(t);
        pose.translate(side * (0.56F - 0.14F * k), -0.52F + equip * -0.6F + 0.14F * k, -0.72F + 0.1F * k);
        pose.mulPose(Axis.YP.rotationDegrees(side * -8.0F * k));
        pose.mulPose(Axis.XP.rotationDegrees(14.0F * k));
        float dip = RemoteAnimation.press(t) / 0.42F;
        pose.translate(0.0F, -0.012F * dip, -0.02F * dip);
        return true;
    }

    /** Tints of the part models: 0 button, 1 trims (breathing), 2 status light, 3 antenna tip (blinking). */
    public static int tint(ItemStack stack, int index) {
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level == null ? 0 : mc.level.getGameTime() % 24000L) + mc.getFrameTime();
        int color = StellarRemoteItem.skill(stack).color;
        return switch (index) {
            case 0 -> color;
            case 1 -> scale(color, 0.7F + 0.3F * Mth.sin(time * 0.15F));
            case 2 -> ((int) (time / 6.0F)) % 8 == 0 ? 0x103018 : 0x40FF70;
            case 3 -> ((int) (time / 10.0F)) % 2 == 0 ? color : scale(color, 0.15F);
            default -> 0xFFFFFF;
        };
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) ((rgb & 0xFF) * k));
        return r << 16 | g << 8 | b;
    }
}
