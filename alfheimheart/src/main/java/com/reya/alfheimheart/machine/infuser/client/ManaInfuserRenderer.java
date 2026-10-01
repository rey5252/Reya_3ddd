package com.reya.alfheimheart.machine.infuser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineRender;
import com.reya.alfheimheart.machine.infuser.ManaInfuserBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * What moves in the Mana Infuser (the stand, the column and the basin are the block's model):
 * <ul>
 * <li>the mana in the basin, rising with the store, light running over it (mana.png's frames), tinted by a
 * catalyst: pink for alchemy, rosy white for conjuration;</li>
 * <li>the item being infused bobs and turns on the mana, glowing brighter as the craft charges;</li>
 * <li>a finished infusion sends a ripple over the mana, and what it made pops up and fades.</li>
 * </ul>
 */
public class ManaInfuserRenderer implements BlockEntityRenderer<ManaInfuserBlockEntity> {
    private static final float FLOOR = 7.0F / 16.0F, BRIM = 11.6F / 16.0F, IN1 = 2.0F / 16.0F, IN2 = 14.0F / 16.0F;
    private static final float CRAFT_TICKS = 20.0F;

    private final ItemRenderer items;

    public ManaInfuserRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ManaInfuserBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float time = be.clientAge() + partialTick;
        float act = be.activity(partialTick);
        float prog = be.progress(partialTick);
        float since = be.sinceCrafted(partialTick);
        float flash = since < CRAFT_TICKS ? 1.0F - since / CRAFT_TICKS : 0.0F;
        float fill = Mth.clamp(be.clientMana() / (float) be.clientCapacity(), 0.0F, 1.0F);
        float[] tint = ManaInfuserColours.tint(be.clientItems().get(ManaInfuserBlockEntity.CATALYST));
        float surface = fill > 0.002F ? FLOOR + 0.02F + (BRIM - FLOOR - 0.02F) * fill : FLOOR + 0.005F;

        // the mana
        if (fill > 0.002F) {
            int frame = (int) (time / 2.0F) % MachineRender.MANA_FRAMES;
            float v1 = frame / (float) MachineRender.MANA_FRAMES, v2 = (frame + 1) / (float) MachineRender.MANA_FRAMES;
            VertexConsumer mana = buffers.getBuffer(RenderType.entityTranslucent(MachineRender.MANA));
            float bright = 0.85F + 0.15F * MachineRender.pulse(time, 40.0F, 0.0F) + 0.2F * flash;
            MachineRender.flat(pose, mana, IN1, IN1, IN2, IN2, surface, 0.0F, v1, 1.0F, v2,
                    Math.min(1.0F, tint[0] * bright), Math.min(1.0F, tint[1] * bright), Math.min(1.0F, tint[2] * bright), 0.92F,
                    LightTexture.FULL_BRIGHT, true);
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(MachineRender.GLOW));
        // a ripple after each infusion
        if (flash > 0.0F) {
            float r = (1.0F - flash) * 0.42F + 0.05F;
            float s = flash * 0.7F;
            MachineRender.flat(pose, glow, 0.5F - r, 0.5F - r, 0.5F + r, 0.5F + r, surface + 0.003F, 0.0F, 0.0F, 1.0F, 1.0F,
                    tint[0] * 0.5F * s, tint[1] * s, tint[2] * s, 1.0F, LightTexture.FULL_BRIGHT, false);
        }

        // the item on the mana
        ItemStack stack = shownItem(be);
        if (!stack.isEmpty()) {
            float y = surface + 0.1F + 0.03F * Mth.sin(time * 0.12F) + 0.06F * act;
            MachineRender.item(items, be.getLevel(), stack, pose, buffers, light, 0.5F, y, 0.5F, 0.42F, time * (2.0F + 6.0F * act));
            float g = act * (0.25F + 0.65F * prog);
            MachineRender.glow(pose, glow, 0.5F, y + 0.08F, 0.5F, 0.45F + 0.25F * prog * act, 0.35F * g * tint[0], 0.75F * g * tint[1],
                    g * tint[2]);
        }
        if (flash > 0.0F) {
            float rise = 1.0F - flash;
            MachineRender.item(items, be.getLevel(), be.crafted(), pose, buffers, LightTexture.FULL_BRIGHT, 0.5F, surface + 0.2F + rise * 0.5F,
                    0.5F, 0.15F + 0.3F * flash, time * 10.0F);
        }
    }

    /** What is being infused: the craft's items, or the first item in the inputs. */
    private static ItemStack shownItem(ManaInfuserBlockEntity be) {
        MachineBlockEntity.Job job = be.clientJob();
        if (job != null && !job.units().isEmpty()) return job.units().get(0).copyWithCount(1);
        var inputs = MachineRender.inputs(be, 1);
        return inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
