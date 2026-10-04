package com.reya.starfall.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * How someone pressing a remote looks from outside: the arm comes up and in, the remote held before the face
 * with the button turned towards the eyes, a small push down as the thumb presses, and back down after.
 */
public final class RemotePose {
    /** Made once at client setup, before any model has been posed (the arm-pose switch is built from it). */
    @Nullable
    private static HumanoidModel.ArmPose raised;

    static void init() {
        if (raised == null) raised = HumanoidModel.ArmPose.create("STARFALL_REMOTE_RAISED", false, RemotePose::transform);
    }

    /** The pose of a hand holding a remote: raised while it is being pressed, otherwise the usual (null). */
    @Nullable
    public static HumanoidModel.ArmPose of(LivingEntity entity) {
        float t = RemoteAnimation.age(entity.getId(), Minecraft.getInstance().getFrameTime());
        if (t < 0.0F || RemoteAnimation.raise(t) <= 0.0F) return null;
        return raised != null ? raised : HumanoidModel.ArmPose.SPYGLASS;
    }

    private static void transform(HumanoidModel<?> model, LivingEntity entity, HumanoidArm side) {
        float t = RemoteAnimation.age(entity.getId(), Minecraft.getInstance().getFrameTime());
        float k = RemoteAnimation.raise(t);
        float push = RemoteAnimation.press(t) / 0.42F;
        int s = side == HumanoidArm.RIGHT ? 1 : -1;
        ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        // forward and a little up, in towards the middle of the face; follows where the head looks
        float pitch = Mth.clamp(model.head.xRot - 1.62F + 0.07F * push, -2.5F, 0.2F);
        float yaw = model.head.yRot - s * 0.42F;
        arm.xRot = Mth.lerp(k, arm.xRot * 0.5F - 0.31F, pitch);
        arm.yRot = Mth.lerp(k, 0.0F, yaw);
        arm.zRot = Mth.lerp(k, arm.zRot, s * 0.08F);
    }

    private RemotePose() {
    }
}
