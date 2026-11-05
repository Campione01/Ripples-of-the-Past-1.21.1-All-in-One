package rotp.addons.sexpistols.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.config.SexPistolsClientConfig;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.item.RevolverStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Next Album 0.1.13 RevolverClientAnim/RevolverFirstPersonHandler: the
// first-person look sway and the visual gun kick, laid over the port's own
// first-person revolver poses.
@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT)
public final class RevolverFirstPersonMotion {
    private static float aimProgress;
    private static float gunKick;
    private static float prevGunKick;
    private static float gunLift;
    private static float prevGunLift;
    private static float gunSway;
    private static float swayPitch;
    private static float prevSwayPitch;
    private static float swayYaw;
    private static float prevSwayYaw;

    private RevolverFirstPersonMotion() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            reset();
            return;
        }
        ItemStack stack = player.getMainHandItem();
        boolean holding = stack.getItem() instanceof RevolverItem;
        prevGunKick = gunKick;
        prevGunLift = gunLift;
        prevSwayPitch = swayPitch;
        prevSwayYaw = swayYaw;
        boolean aiming = holding
                && player.isUsingItem()
                && RevolverItem.state(stack).aiming()
                && !RevolverItem.state(stack).reloadMode();
        float targetAim = aiming ? 1.0F : 0.0F;
        aimProgress += (targetAim - aimProgress)
                * (targetAim > aimProgress ? 0.28F : 0.34F);
        if (Math.abs(aimProgress - targetAim) < 0.004F) {
            aimProgress = targetAim;
        }
        gunKick *= 0.72F;
        gunLift *= 0.68F;
        gunSway *= 0.78F;
        if (Math.abs(gunKick) < 8.0E-4F) {
            gunKick = 0.0F;
        }
        if (Math.abs(gunLift) < 0.02F) {
            gunLift = 0.0F;
        }
        if (holding && SexPistolsClientConfig.gunSway()) {
            float pitchDelta = Mth.wrapDegrees(
                    player.getXRot() - player.xBob);
            float yawDelta = Mth.wrapDegrees(
                    player.getYRot() - player.yBob);
            float sensitivity = (float) SexPistolsClientConfig
                    .gunSwaySensitivity()
                    * RevolverStats.of(stack).swaySensitivity();
            float adsMul = 1.0F - 0.62F * aimProgress;
            float targetPitch = Mth.clamp(
                    pitchDelta * sensitivity * adsMul, -18.0F, 18.0F);
            float targetYaw = Mth.clamp(
                    yawDelta * sensitivity * adsMul, -22.0F, 22.0F);
            swayPitch += (targetPitch - swayPitch) * 0.42F;
            swayYaw += (targetYaw - swayYaw) * 0.42F;
        }
        else {
            swayPitch *= 0.6F;
            swayYaw *= 0.6F;
        }
    }

    public static void addGunKick(RevolverStats stats) {
        LocalPlayer player = Minecraft.getInstance().player;
        float recoil = (float) SexPistolsClientConfigSnapshot.snapshot()
                .recoilMultiplier();
        float adsMul = 1.0F - 0.45F * aimProgress;
        gunKick = Mth.clamp(
                gunKick + stats.visualRecoilKick() * recoil * adsMul,
                0.0F, 0.28F);
        gunLift = Mth.clamp(
                gunLift + stats.visualRecoilLift() * recoil * adsMul,
                0.0F, 18.0F);
        float sign = player != null && player.getRandom().nextBoolean()
                ? 1.0F
                : -1.0F;
        gunSway = Mth.clamp(
                gunSway + sign * (1.8F + 1.4F * (1.0F - aimProgress)),
                -8.0F, 8.0F);
    }

    // 0.1.13 applyLookSway and applyVisualRecoil, in the item's
    // first-person space.
    public static void apply(PoseStack poseStack, float handSide) {
        float partialTick = Minecraft.getInstance().getTimer()
                .getGameTimeDeltaPartialTick(true);
        if (SexPistolsClientConfig.gunSway()) {
            float pitch = Mth.lerp(partialTick, prevSwayPitch, swayPitch);
            float yaw = Mth.lerp(partialTick, prevSwayYaw, swayYaw);
            poseStack.translate(0.0D, -0.22D, 0.18D);
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw * handSide));
            poseStack.mulPose(Axis.ZP.rotationDegrees(yaw * 0.22F));
            poseStack.translate(0.0D, 0.22D, -0.18D);
        }
        float kick = Mth.lerp(partialTick, prevGunKick, gunKick);
        float lift = Mth.lerp(partialTick, prevGunLift, gunLift);
        if (kick > 5.0E-4F
                || Math.abs(lift) >= 0.05F
                || Math.abs(gunSway) >= 0.05F) {
            poseStack.translate(0.0D, 0.0D, kick);
            poseStack.translate(0.0D, 0.12D, 0.12D);
            poseStack.mulPose(Axis.YP.rotationDegrees(gunSway * handSide));
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    gunSway * 0.65F * handSide));
            poseStack.mulPose(Axis.XP.rotationDegrees(lift));
            poseStack.translate(0.0D, -0.12D, -0.12D);
        }
    }

    private static void reset() {
        aimProgress = 0.0F;
        gunKick = 0.0F;
        prevGunKick = 0.0F;
        gunLift = 0.0F;
        prevGunLift = 0.0F;
        gunSway = 0.0F;
        swayPitch = 0.0F;
        prevSwayPitch = 0.0F;
        swayYaw = 0.0F;
        prevSwayYaw = 0.0F;
    }
}
