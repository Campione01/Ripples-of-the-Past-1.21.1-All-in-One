package rotp.addons.beachboy.client;

import java.util.OptionalInt;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.client.render.BeachBoyHookRenderer;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.item.BeachBoyRodItem;
import rotp.addons.beachboy.network.BeachBoyPayloads;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.entityglow.EntityGlowChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 1.16 BeachBoyClientEvents.ForgeBus with the hooks of BeachBoyRemoteSenseClient. */
@EventBusSubscriber(modid = BeachBoyAddon.MOD_ID, value = Dist.CLIENT)
public final class BeachBoyClientEvents {
    private static final float REEL_TUG_DEADZONE = 0.65F;
    private static final float REEL_TUG_FULL_DEG = 10.0F;
    private static float lastReelYaw;
    private static float lastReelPitch;
    private static float smoothReelRight;
    private static float smoothReelUp;
    private static boolean reelLookTracking;
    private static boolean sentReelTugStop;
    private static int lastLifeDetectId = -1;

    private BeachBoyClientEvents() {}

    // The wheel over the rod pays out or reels in the line, unless the player is picking an ability (1.16 isJojoActionScrollActive).
    // Below the core's HIGH handler: this class registers first, so at one priority it took the wheel from the selection.
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || !isStandHudActive() || isPickingAbility()
                || !BeachBoyStandUtil.isHoldingRod(player) || !BeachBoyStandUtil.isBeachBoySummoned(player)) {
            return;
        }
        boolean lengthen = event.getScrollDeltaY() > 0.0;
        float step = BeachBoyStandUtil.getReelStep(player);
        PacketDistributor.sendToServer(new BeachBoyPayloads.LineLength(lengthen));
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
        if (!lengthen) {
            BeachBoyClientReel.notifyReelIn();
        }
        if (BeachBoyStandUtil.isPathLineEnabled(StandPower.get(player))) {
            BeachBoyClientReel.spin(lengthen);
            if (!lengthen && hook != null) {
                hook.reelPathBy(step);
            }
        }
        else if (stand != null) {
            stand.adjustMaxLineLength(lengthen ? step : -step);
        }
        event.setCanceled(true);
    }

    private static boolean isPickingAbility() {
        InputHandler input = InputHandler.getInstance();
        ClientControlScheme scheme = input != null ? input.getActiveControlScheme() : null;
        if (scheme == null) {
            return false;
        }
        for (ClientControlScheme.Hotbar hotbar : scheme.getCurGroup().hotbars) {
            if (input.isSelectingAbility(hotbar)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStandHudActive() {
        InputHandler input = InputHandler.getInstance();
        return input == null || input.curPowerClassToggle == null || input.curPowerClassToggle == PowerClass.STAND;
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        PacketDistributor.sendToServer(new BeachBoyPayloads.HitLine());
    }

    @SubscribeEvent
    public static void onAttackClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && Minecraft.getInstance().player != null) {
            PacketDistributor.sendToServer(new BeachBoyPayloads.HitLine());
        }
    }

    @SubscribeEvent
    public static void onChargeFov(ComputeFovModifierEvent event) {
        if (event.getPlayer().isUsingItem() && event.getPlayer().getUseItem().getItem() instanceof BeachBoyRodItem) {
            float charge = BeachBoyRodItem.getChargeStrength(event.getPlayer().getTicksUsingItem());
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F - charge * 0.15F));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BeachBoyRemoteSenseClient.tick();
        if (mc.player == null) {
            resetReelLookTug();
            BeachBoyClientReel.reset();
            return;
        }
        BeachBoyClientReel.tick();
        tickReelLookTug();
        tickHookedLifeDetect(mc);
        if (mc.getCameraEntity() instanceof BeachBoyHookEntity) {
            mc.setCameraEntity(mc.player);
        }
    }

    // between the core's manual control (HIGHEST), which moves the Stand, and its clearing of the keys (LOWEST)
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player) {
            return;
        }
        BeachBoyClientInput.capture(event.getInput());
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
        if (stand != null && stand.isManuallyControlled()) {
            float[] look = BeachBoyClientInput.captureViewRotation(player, stand);
            float[] move = BeachBoyClientInput.captureMove(stand);
            PacketDistributor.sendToServer(new BeachBoyPayloads.HookControl(move[0], move[1], move[2] > 0.5F,
                    move[3] > 0.5F, look[0], look[1]));
        }
    }

    private static void tickReelLookTug() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!canSendReelLookTug(mc, player)) {
            resetReelLookTug();
            return;
        }
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (!reelLookTracking) {
            lastReelYaw = yaw;
            lastReelPitch = pitch;
            reelLookTracking = true;
            sentReelTugStop = true;
            return;
        }
        float dyaw = Mth.wrapDegrees(yaw - lastReelYaw);
        float dpitch = pitch - lastReelPitch;
        lastReelYaw = yaw;
        lastReelPitch = pitch;
        float right = analogFromLookDelta(dyaw);
        float up = analogFromLookDelta(-dpitch);
        smoothReelRight = smoothReelRight * 0.4F + right * 0.6F;
        smoothReelUp = smoothReelUp * 0.4F + up * 0.6F;
        if (Math.abs(right) < 1.0E-4F) {
            smoothReelRight *= 0.58F;
        }
        if (Math.abs(up) < 1.0E-4F) {
            smoothReelUp *= 0.58F;
        }
        if (Math.abs(smoothReelRight) < 0.025F) {
            smoothReelRight = 0.0F;
        }
        if (Math.abs(smoothReelUp) < 0.025F) {
            smoothReelUp = 0.0F;
        }
        if (smoothReelRight == 0.0F && smoothReelUp == 0.0F) {
            if (!sentReelTugStop) {
                PacketDistributor.sendToServer(new BeachBoyPayloads.ReelTug(0.0F, 0.0F, yaw));
                sentReelTugStop = true;
            }
        }
        else {
            sentReelTugStop = false;
            PacketDistributor.sendToServer(new BeachBoyPayloads.ReelTug(Mth.clamp(smoothReelRight, -1.0F, 1.0F),
                    Mth.clamp(smoothReelUp, -1.0F, 1.0F), yaw));
        }
    }

    private static boolean canSendReelLookTug(Minecraft mc, LocalPlayer player) {
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null
                || !mc.options.getCameraType().isFirstPerson()
                || mc.getCameraEntity() != null && mc.getCameraEntity() != player) {
            return false;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
        if (stand != null && stand.isManuallyControlled()) {
            return false;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
        if (hook == null || !hook.isAlive()) {
            return false;
        }
        Entity hooked = hook.getHookedEntity();
        return hooked != null && hooked.isAlive() && hooked != player
                && (hook.isRetracting() || BeachBoyClientReel.isScrollReeling());
    }

    private static float analogFromLookDelta(float degrees) {
        float mag = Math.abs(degrees);
        if (mag <= REEL_TUG_DEADZONE) {
            return 0.0F;
        }
        float analog = Mth.clamp((mag - REEL_TUG_DEADZONE) / (REEL_TUG_FULL_DEG - REEL_TUG_DEADZONE), 0.0F, 1.0F);
        analog *= 0.35F + 0.65F * analog;
        return Math.copySign(analog, degrees);
    }

    private static void resetReelLookTug() {
        if (reelLookTracking && !sentReelTugStop) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                PacketDistributor.sendToServer(new BeachBoyPayloads.ReelTug(0.0F, 0.0F, player.getYRot()));
            }
        }
        reelLookTracking = false;
        smoothReelRight = 0.0F;
        smoothReelUp = 0.0F;
        sentReelTugStop = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera instanceof BeachBoyStandEntity || camera instanceof BeachBoyHookEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        BeachBoyRemoteSenseClient.renderOverlays(poseStack, buffer, camera, partialTicks);
        Entity cameraEntity = mc.getCameraEntity();
        BeachBoyHookEntity hook = null;
        if (cameraEntity instanceof BeachBoyStandEntity stand) {
            hook = BeachBoyStandUtil.findHook(stand.getUser());
        }
        else if (cameraEntity instanceof BeachBoyHookEntity cameraHook) {
            hook = cameraHook;
        }
        if (hook == null && BeachBoyRemoteSenseClient.isRemoteVision() && mc.player != null) {
            hook = BeachBoyStandUtil.findHook(mc.player);
        }
        if (hook != null && hook.isAlive()) {
            Entity owner = hook.getOwner();
            if (BeachBoyRemoteSenseClient.isRemoteVision() && !mc.options.getCameraType().isFirstPerson()) {
                BeachBoyHookRenderer.renderHookFromWorld(hook, partialTicks, poseStack, buffer, camera);
            }
            if (owner != null) {
                BeachBoyHookRenderer.renderLineFromWorld(hook, owner, partialTicks, poseStack, buffer, camera);
            }
        }
        if (mc.player != null) {
            BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(mc.player);
            if (stand != null && stand.getWhipTicks() > 0) {
                BeachBoyHookRenderer.renderWhipFromWorld(mc.player, stand, partialTicks, poseStack, buffer, camera);
            }
        }
        buffer.endBatch();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFogColors(ViewportEvent.ComputeFogColor event) {
        if (BeachBoyRemoteSenseClient.isRemoteVision()) {
            event.setRed(0.0F);
            event.setGreen(0.0F);
            event.setBlue(0.0F);
        }
    }

    // 1.16 FogDensity 2.0: the remote hook sees almost nothing but what the senses show
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (BeachBoyRemoteSenseClient.isRemoteVision()) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(2.0F);
            event.setFogShape(FogShape.SPHERE);
            event.setCanceled(true);
        }
    }

    // This event can come off the client thread; the labels and the hook lookup belong to that thread.
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft.getInstance().execute(() -> BeachBoyRemoteSenseClient.onSound(sound));
    }

    private static void tickHookedLifeDetect(Minecraft mc) {
        if (mc.level == null || !BeachBoyStandUtil.isSoundSenseEnabled(StandPower.get(mc.player))) {
            clearLifeDetect(mc);
            return;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(mc.player);
        Entity hooked = hook != null ? hook.getHookedEntity() : null;
        if (hooked instanceof LivingEntity && hooked.isAlive()) {
            if (lastLifeDetectId >= 0 && lastLifeDetectId != hooked.getId()) {
                clearLifeDetect(mc);
            }
            lastLifeDetectId = hooked.getId();
            EntityGlowChannel.GE_LIFE_DETECTOR.apply(hooked,
                    OptionalInt.of(BeachBoyStandUtil.beachBoyColor(StandPower.get(mc.player))), 80);
        }
        else {
            clearLifeDetect(mc);
        }
    }

    private static void clearLifeDetect(Minecraft mc) {
        if (lastLifeDetectId >= 0 && mc.level != null) {
            Entity prev = mc.level.getEntity(lastLifeDetectId);
            if (prev != null) {
                EntityGlowChannel.GE_LIFE_DETECTOR.clear(prev);
            }
        }
        lastLifeDetectId = -1;
    }
}
