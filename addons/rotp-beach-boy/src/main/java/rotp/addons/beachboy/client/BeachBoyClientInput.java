package rotp.addons.beachboy.client;

import javax.annotation.Nullable;

import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 1.16 BeachBoyClientInput and BeachBoyClientRodTips: the remote hook's keys and the first-person rod tip. */
public final class BeachBoyClientInput {
    private static final double FIRST_PERSON_SIDE = 0.05;
    private static final double FIRST_PERSON_UP = 0.22;
    private static final double FIRST_PERSON_FORWARD = 0.36;
    private static float lastForward;
    private static float lastStrafe;
    private static boolean lastJump;
    private static boolean lastSneak;

    private BeachBoyClientInput() {}

    /** The keys the movement input held this tick, kept before the remote control clears them. */
    public static void capture(Input input) {
        lastForward = input.forwardImpulse;
        lastStrafe = input.leftImpulse;
        lastJump = input.jumping;
        lastSneak = input.shiftKeyDown;
    }

    public static boolean applyIfRemote(BeachBoyHookEntity hook) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || hook.getOwner() != player) {
            return false;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
        if (stand == null || !stand.isManuallyControlled()) {
            return false;
        }
        float[] look = captureViewRotation(player, stand);
        float[] move = captureMove(stand);
        hook.setRemoteInput(move[0], move[1], move[2] > 0.5F, move[3] > 0.5F, look[0], look[1]);
        return true;
    }

    public static float[] captureViewRotation(LocalPlayer player, @Nullable BeachBoyStandEntity stand) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) {
            camera = stand != null ? stand : player;
        }
        return new float[] {camera.getYRot(), camera.getXRot()};
    }

    public static float[] captureMove(@Nullable BeachBoyStandEntity stand) {
        float forward = lastForward;
        float strafe = lastStrafe;
        boolean jump = lastJump;
        boolean sneak = lastSneak;
        if (stand != null) {
            jump = jump || stand.isControlJumping();
            sneak = sneak || stand.isControlSneaking();
        }
        return new float[] {forward, strafe, jump ? 1.0F : 0.0F, sneak ? 1.0F : 0.0F};
    }

    /** 1.16 BeachBoyClientRodTips.tryFirstPerson: the rod tip in the local player's first-person hand. */
    @Nullable
    public static Vec3 tryFirstPersonRodTip(LivingEntity owner, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || owner != player || !mc.options.getCameraType().isFirstPerson()
                || mc.getCameraEntity() != player) {
            return null;
        }
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (BeachBoyStandUtil.isRodItem(player.getOffhandItem()) && !BeachBoyStandUtil.isRodItem(player.getMainHandItem())) {
            side = -side;
        }
        float pitch = Mth.lerp(partialTicks, player.xRotO, player.getXRot());
        float yaw = Mth.lerp(partialTicks, player.yRotO, player.getYRot());
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        if (right.lengthSqr() < 1.0E-6) {
            right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
        }
        right = right.normalize();
        Vec3 up = right.cross(look);
        up = up.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : up.normalize();
        float swing = player.getAttackAnim(partialTicks);
        float swingPow = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        double sideOff = FIRST_PERSON_SIDE * side * (1.0 - 0.25 * swingPow);
        double upOff = FIRST_PERSON_UP - 0.18 * swingPow;
        double forwardOff = FIRST_PERSON_FORWARD - 0.08 * swingPow;
        Vec3 eye = new Vec3(Mth.lerp(partialTicks, player.xo, player.getX()),
                Mth.lerp(partialTicks, player.yo, player.getY()) + player.getEyeHeight(),
                Mth.lerp(partialTicks, player.zo, player.getZ()));
        return eye.add(look.scale(forwardOff)).add(right.scale(sideOff)).add(up.scale(upOff));
    }
}
