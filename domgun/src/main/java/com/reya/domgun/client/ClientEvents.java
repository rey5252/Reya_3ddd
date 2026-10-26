package com.reya.domgun.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reya.domgun.Config;
import com.reya.domgun.DomGun;
import com.reya.domgun.DomGunItem;
import com.reya.domgun.network.Net;
import com.reya.domgun.network.ReloadPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client side of the gun: reload key, ADS zoom, recoil, crosshair, hit marker and ammo HUD. */
@Mod.EventBusSubscriber(modid = DomGun.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping RELOAD = new KeyMapping("key.domgun.reload", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.domgun");

    private static final int HIT_MARKER_TICKS = 6;

    private static float ads;
    private static float prevAds;
    private static int hitTicks;
    private static int hitKind;

    private ClientEvents() {
    }

    private static boolean holdingGun(Player player) {
        return player != null && player.getMainHandItem().getItem() instanceof DomGunItem;
    }

    /** Called by {@link com.reya.domgun.network.ShotPacket} for every shot this player fires. */
    public static void onShot(float kick, float sway, int result) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.setXRot(Mth.clamp(player.getXRot() - kick, -90.0F, 90.0F));
        player.xRotO = Mth.clamp(player.xRotO - kick, -90.0F, 90.0F);
        player.setYRot(player.getYRot() + sway);
        player.yRotO += sway;
        if (result != DomGunItem.MISS) {
            hitTicks = HIT_MARKER_TICKS;
            hitKind = result;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean holding = holdingGun(player);
        float target = holding && player.isShiftKeyDown() && mc.screen == null ? 1.0F : 0.0F;
        prevAds = ads;
        ads += (target - ads) * 0.4F;
        if (hitTicks > 0) hitTicks--;
        while (RELOAD.consumeClick()) {
            if (holding && mc.screen == null) Net.CHANNEL.sendToServer(new ReloadPacket());
        }
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        float t = Mth.lerp(Minecraft.getInstance().getFrameTime(), prevAds, ads);
        if (t > 0.001F) event.setNewFovModifier(event.getNewFovModifier() * (1.0F - 0.4F * t));
    }

    /** The vanilla crosshair is replaced by the gun's own. */
    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type() && holdingGun(Minecraft.getInstance().player)) {
            event.setCanceled(true);
        }
    }

    public static final IGuiOverlay HUD = ClientEvents::renderHud;

    private static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = gui.getMinecraft();
        LocalPlayer player = mc.player;
        if (!holdingGun(player) || mc.options.hideGui) return;
        ItemStack stack = player.getMainHandItem();
        Font font = mc.font;
        int cx = w / 2;
        int cy = h / 2;

        // crosshair: four ticks that open up when sprinting or jumping, a single dot while aiming
        float aim = Mth.lerp(partialTick, prevAds, ads);
        if (aim > 0.6F) {
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        } else {
            int gap = 3 + Math.round((1.0F - aim) * 3.0F);
            if (player.isSprinting()) gap += 3;
            if (!player.onGround()) gap += 4;
            int c = 0xDDFFFFFF;
            g.fill(cx - gap - 4, cy, cx - gap, cy + 1, c);
            g.fill(cx + gap, cy, cx + gap + 4, cy + 1, c);
            g.fill(cx, cy - gap - 4, cx + 1, cy - gap, c);
            g.fill(cx, cy + gap, cx + 1, cy + gap + 4, c);
        }

        // hit marker: four short diagonals, red for headshots and kills
        if (hitTicks > 0) {
            int c = hitKind >= DomGunItem.HEADSHOT ? 0xFFFF3030 : 0xFFFFFFFF;
            for (int d = 4; d < 9; d++) {
                g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, c);
                g.fill(cx - d - 1, cy + d, cx - d, cy + d + 1, c);
                g.fill(cx + d, cy - d - 1, cx + d + 1, cy - d, c);
                g.fill(cx - d - 1, cy - d - 1, cx - d, cy - d, c);
            }
        }

        // ammo counter, bottom right
        int mag = Config.MAGAZINE_SIZE.get();
        int ammo = DomGunItem.getAmmo(stack);
        boolean creative = player.getAbilities().instabuild;
        String reserve = creative ? "∞" : String.valueOf(DomGunItem.countAmmo(player));
        int color = ammo == 0 ? 0xFFFF4040 : ammo * 4 <= mag ? 0xFFFFA030 : 0xFFFFFFFF;
        String cur = String.valueOf(ammo);
        int right = w - 66;
        int y = h - 40;
        g.pose().pushPose();
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font, cur, (right - font.width(cur) * 2) / 2, y / 2, color, true);
        g.pose().popPose();
        g.drawString(font, "/ " + reserve, right + 4, y + 7, 0xFFB0B0B0, true);

        long left = DomGunItem.reloadEnd(stack) - mc.level.getGameTime();
        if (left > 0) {
            Component text = Component.translatable("domgun.hud.reloading");
            int total = Math.max(1, Config.RELOAD_TICKS.get());
            float frac = Mth.clamp(1.0F - (left - partialTick) / total, 0.0F, 1.0F);
            int bw = 60;
            int bx = cx - bw / 2;
            int by = cy + 24;
            g.drawString(font, text, cx - font.width(text) / 2, by - 11, 0xFFFFE070, true);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 3, 0xAA000000);
            g.fill(bx, by, bx + Math.round(bw * frac), by + 2, 0xFFFFC030);
        } else if (ammo * 4 <= mag && (creative || DomGunItem.countAmmo(player) > 0)) {
            Component hint = Component.translatable("domgun.hud.reload_hint", RELOAD.getTranslatedKeyMessage());
            g.drawString(font, hint, cx - font.width(hint) / 2, cy + 24, 0xFFFFA030, true);
        } else if (ammo == 0) {
            Component none = Component.translatable("domgun.hud.no_ammo");
            g.drawString(font, none, cx - font.width(none) / 2, cy + 24, 0xFFFF4040, true);
        }
    }
}
