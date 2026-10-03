package com.reya.starfall.client;

import com.reya.starfall.Skill;
import com.reya.starfall.Starfall;
import com.reya.starfall.network.Net;
import com.reya.starfall.network.SelectSkillPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Starfall.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean aimHeld;
    private static float heldYaw, heldPitch;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        holdAim();
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ClientStrikes.tick();
        for (int i = 0; i < Keys.SKILLS.length; i++) {
            while (Keys.SKILLS[i].consumeClick()) select(mc, Skill.byIndex(i));
        }
        while (Keys.MENU.consumeClick()) {
            if (mc.screen == null) SkillScreen.open();
        }
        while (Keys.FILM.consumeClick()) {
            if (Film.active()) {
                Film.stop();
                mc.player.displayClientMessage(Component.translatable("message.starfall.film_skipped"), true);
            } else {
                boolean on = !ClientConfig.films();
                ClientConfig.setFilms(on);
                mc.player.displayClientMessage(Component.translatable(on ? "message.starfall.films_on" : "message.starfall.films_off"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        holdAim();
    }

    /** While the film plays the view doesn't turn: the aim stays where the strike was marked. */
    private static void holdAim() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !Film.active()) {
            aimHeld = false;
            return;
        }
        if (!aimHeld) {
            heldYaw = p.getYRot();
            heldPitch = p.getXRot();
            aimHeld = true;
        }
        p.setYRot(heldYaw);
        p.setXRot(heldPitch);
        p.yRotO = heldYaw;
        p.xRotO = heldPitch;
        p.yHeadRot = heldYaw;
        p.yHeadRotO = heldYaw;
    }

    private static void select(Minecraft mc, Skill skill) {
        if (mc.player == null) return;
        if (RemoteItems.held(mc.player).isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("message.starfall.hold_remote"), true);
            return;
        }
        Net.CHANNEL.sendToServer(new SelectSkillPacket(skill.ordinal()));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) ClientStrikes.render(event);
    }

    /** A remote held by someone else animates their press too: remember whose hand is being drawn. */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        RemoteRenderer.holder = event.getEntity();
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        RemoteRenderer.holder = null;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float amp = ClientStrikes.shake((float) event.getPartialTick());
        if (amp <= 0.0F) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + amp * (Mth.sin(t * 1.9F) * 0.6F + Mth.sin(t * 4.3F) * 0.4F));
        event.setPitch(event.getPitch() + amp * (Mth.cos(t * 2.3F) * 0.6F + Mth.sin(t * 5.1F) * 0.4F));
        event.setRoll(event.getRoll() + amp * 0.5F * Mth.sin(t * 3.7F));
    }

    /** You stand still while the film plays. */
    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!Film.active()) return;
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStrikes.clear();
        RemoteAnimation.clear();
    }

    private ClientEvents() {
    }
}
