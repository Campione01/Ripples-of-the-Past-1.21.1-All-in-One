package rotp.addons.beachboy.util;

import javax.annotation.Nullable;

import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 1.16 BeachBoyVanillaMotion: vanilla-like walking, swimming and bobbing for the remote hook and its host. */
public final class BeachBoyVanillaMotion {
    private static final ThreadLocal<Boolean> APPLYING_CONTROL = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private BeachBoyVanillaMotion() {}

    public static boolean isApplyingControl() {
        return Boolean.TRUE.equals(APPLYING_CONTROL.get());
    }

    public static Vec3 inputVector(float strafe, float forward, float yaw, float speed) {
        double lengthSq = (double) strafe * strafe + (double) forward * forward;
        if (lengthSq < 1.0E-7) {
            return Vec3.ZERO;
        }
        Vec3 relative = lengthSq > 1.0 ? new Vec3(strafe, 0.0, forward).normalize() : new Vec3(strafe, 0.0, forward);
        relative = relative.scale(speed);
        float sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        float cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(relative.x * cos - relative.z * sin, 0.0, relative.z * cos + relative.x * sin);
    }

    public static float frictionInfluencedSpeed(boolean onGround, float walkingSpeed, float slipperiness) {
        if (!onGround) {
            return 0.02F;
        }
        float friction = Math.max(slipperiness * slipperiness * slipperiness, 1.0E-6F);
        return walkingSpeed * (0.21600002F / friction);
    }

    public record BuoyancyVolume(double restY, double floorY) {
        public BuoyancyVolume {
            floorY = Math.min(restY, floorY);
        }
    }

    @Nullable
    public static BuoyancyVolume findBuoyancyVolume(BeachBoyHookEntity hook) {
        Level level = hook.level();
        BlockPos origin = BlockPos.containing(hook.getX(), hook.getY(), hook.getZ());
        if (!hook.isFluidLikeAt(origin)) {
            return null;
        }
        int top = origin.getY();
        while (top < level.getMaxBuildHeight() - 1 && hook.isFluidLikeAt(new BlockPos(origin.getX(), top + 1, origin.getZ()))) {
            top++;
        }
        int bottom = origin.getY();
        while (bottom > level.getMinBuildHeight() && hook.isFluidLikeAt(new BlockPos(origin.getX(), bottom - 1, origin.getZ()))) {
            bottom--;
        }
        BlockPos topPos = new BlockPos(origin.getX(), top, origin.getZ());
        BlockState topState = level.getBlockState(topPos);
        double restY = top + 1.0 - 0.32;
        if (!topState.getFluidState().isEmpty()) {
            restY = top + topState.getFluidState().getHeight(level, topPos) - 0.32;
        }
        double floorY = Math.max(bottom + 0.12, restY - 5.0);
        return new BuoyancyVolume(restY, floorY);
    }

    public static Vec3 applyImmersedBuoyancy(BeachBoyHookEntity hook, Vec3 motion, boolean sneak) {
        BuoyancyVolume volume = findBuoyancyVolume(hook);
        if (sneak) {
            if (volume == null) {
                return motion;
            }
            double vy = Math.max(motion.y * 0.82 - 0.05, -0.16);
            return new Vec3(motion.x * 0.9, vy, motion.z * 0.9);
        }
        double rest = volume != null ? volume.restY() : localSurfaceY(hook, hook.blockPosition());
        double d1 = hook.getY() - rest;
        if (Math.abs(d1) < 0.02) {
            return new Vec3(motion.x * 0.86, 0.0, motion.z * 0.86);
        }
        double vy = motion.y * 0.16 - d1 * 0.42;
        return new Vec3(motion.x * 0.9, vy, motion.z * 0.9);
    }

    private static double localSurfaceY(BeachBoyHookEntity hook, BlockPos pos) {
        FluidState fluid = hook.level().getBlockState(pos).getFluidState();
        double surface = pos.getY() + 1.0;
        if (!fluid.isEmpty()) {
            surface = pos.getY() + fluid.getHeight(hook.level(), pos);
        }
        return surface - 0.32;
    }

    public static double restYFromSurface(double surfaceY) {
        return surfaceY - 0.32;
    }

    public static Vec3 applyBobberDrag(Vec3 motion) {
        return motion.scale(0.92);
    }

    public static double clampImmersedY(BeachBoyHookEntity hook, double y, boolean sneak) {
        BuoyancyVolume volume = findBuoyancyVolume(hook);
        if (volume == null) {
            return y;
        }
        if (sneak && y < volume.floorY()) {
            return volume.floorY();
        }
        if (!sneak) {
            double rest = volume.restY();
            if (y > rest || Math.abs(y - rest) < 0.02) {
                return rest;
            }
        }
        return y;
    }

    public static float groundSlipperiness(BeachBoyHookEntity hook) {
        Level level = hook.level();
        BlockPos below = BlockPos.containing(hook.getX(), hook.getBoundingBox().minY - 0.5000001, hook.getZ());
        return level.getBlockState(below).getFriction(level, below, hook);
    }

    /** 1.16 travelLiving: the host walks by the remote input as if it pressed those keys, looking where the user looks. */
    public static void travelLiving(LivingEntity host, float strafe, float forward, float viewYaw, float viewPitch,
            boolean jump, boolean sneak) {
        float savedYaw = host.getYRot();
        float savedYawO = host.yRotO;
        float savedPitch = host.getXRot();
        float savedPitchO = host.xRotO;
        float savedHead = host.yHeadRot;
        float savedHeadO = host.yHeadRotO;
        float savedBody = host.yBodyRot;
        float savedBodyO = host.yBodyRotO;
        boolean savedShift = host.isShiftKeyDown();
        float savedXxa = host.xxa;
        float savedYya = host.yya;
        float savedZza = host.zza;
        APPLYING_CONTROL.set(Boolean.TRUE);
        try {
            host.setYRot(viewYaw);
            host.yRotO = viewYaw;
            host.setXRot(viewPitch);
            host.xRotO = viewPitch;
            float walkStrafe = Mth.clamp(strafe, -1.0F, 1.0F);
            float walkForward = Mth.clamp(forward, -1.0F, 1.0F);
            if (sneak) {
                walkStrafe *= 0.3F;
                walkForward *= 0.3F;
                host.setShiftKeyDown(true);
            }
            else if (host instanceof Player) {
                host.setShiftKeyDown(false);
            }
            host.xxa = walkStrafe;
            host.yya = 0.0F;
            host.zza = walkForward;
            boolean inFluid = host.isInWater() || host.isInLava();
            if (jump && host.onGround() && !inFluid) {
                // 1.16 jumpFromGround through an accessor mixin: the jump impulse with the Jump Boost bonus
                Vec3 motion = host.getDeltaMovement();
                host.setDeltaMovement(motion.x, 0.42 * blockJumpFactor(host) + host.getJumpBoostPower(), motion.z);
                host.hasImpulse = true;
            }
            else if (jump && inFluid) {
                host.setDeltaMovement(host.getDeltaMovement().add(0.0, 0.04, 0.0));
            }
            host.travel(new Vec3(host.xxa, host.yya, host.zza));
        }
        finally {
            APPLYING_CONTROL.set(Boolean.FALSE);
            host.setYRot(savedYaw);
            host.yRotO = savedYawO;
            host.setXRot(savedPitch);
            host.xRotO = savedPitchO;
            host.yHeadRot = savedHead;
            host.yHeadRotO = savedHeadO;
            host.yBodyRot = savedBody;
            host.yBodyRotO = savedBodyO;
            host.setShiftKeyDown(savedShift);
            host.xxa = savedXxa;
            host.yya = savedYya;
            host.zza = savedZza;
        }
        host.hurtMarked = true;
        host.hasImpulse = true;
    }

    private static float blockJumpFactor(LivingEntity host) {
        float here = host.level().getBlockState(host.blockPosition()).getBlock().getJumpFactor();
        float below = host.level().getBlockState(host.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();
        return here == 1.0F ? below : here;
    }

    public static void travelHook(BeachBoyHookEntity hook, float strafe, float forward, float yaw, boolean jump,
            boolean sneak) {
        float walkSpeed = hook.getRemoteMoveSpeed();
        float swimSpeed = walkSpeed * 0.85F;
        float walkStrafe = Mth.clamp(strafe, -1.0F, 1.0F);
        float walkForward = Mth.clamp(forward, -1.0F, 1.0F);
        boolean immersed = hook.isImmersedAt(hook.position());
        boolean onSurface = !immersed && hook.isOnWalkSurface();
        if (sneak && !immersed) {
            walkStrafe *= 0.3F;
            walkForward *= 0.3F;
        }
        Vec3 motion = hook.getDeltaMovement();
        if (immersed) {
            motion = motion.add(inputVector(walkStrafe, walkForward, yaw, swimSpeed));
            if (jump) {
                motion = motion.add(0.0, 0.12, 0.0);
            }
            motion = applyImmersedBuoyancy(hook, motion, sneak);
            motion = applyBobberDrag(motion);
        }
        else {
            if (jump && onSurface) {
                motion = new Vec3(motion.x, 0.42, motion.z);
                onSurface = false;
            }
            else if (sneak && onSurface) {
                motion = motion.add(0.0, -0.08, 0.0);
            }
            float slipperiness = onSurface ? groundSlipperiness(hook) : 0.6F;
            float speed = frictionInfluencedSpeed(onSurface, walkSpeed, slipperiness);
            motion = motion.add(inputVector(walkStrafe, walkForward, yaw, speed));
            if (onSurface) {
                motion = new Vec3(motion.x * (slipperiness * 0.91F), motion.y, motion.z * (slipperiness * 0.91F));
            }
            else {
                motion = new Vec3(motion.x * 0.91F, motion.y - 0.08, motion.z * 0.91F);
            }
        }
        Vec3 start = hook.position();
        Vec3 next = hook.limitMoveByPath(start, start.add(motion));
        if (!immersed && hook.isImmersedAt(next) && motion.y < -0.02) {
            Vec3 horiz = new Vec3(motion.x, 0.0, motion.z);
            boolean fromSide = sneak || horiz.lengthSqr() > motion.y * motion.y * 0.35;
            if (!fromSide) {
                BlockHitResult hit = hook.level().clip(new ClipContext(start, next, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, hook));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
                    next = new Vec3(next.x, hit.getLocation().y + 0.06, next.z);
                    motion = new Vec3(motion.x * 0.6, 0.0, motion.z * 0.6);
                }
            }
        }
        if (hook.isImmersedAt(next)) {
            double clampedY = clampImmersedY(hook, next.y, sneak);
            if (clampedY != next.y) {
                double vy = clampedY < next.y ? Math.min(0.0, motion.y) : Math.max(0.0, motion.y);
                next = new Vec3(next.x, clampedY, next.z);
                motion = new Vec3(motion.x, vy, motion.z);
            }
        }
        hook.setPos(next.x, next.y, next.z);
        hook.setDeltaMovement(motion);
        hook.hasImpulse = true;
        hook.sampleSurfaceFx(start.y, next.y, motion);
    }

    /** With the path line on, the remote hook flies where the user looks (1.16 travelHookFree). */
    public static void travelHookFree(BeachBoyHookEntity hook, float strafe, float forward, float yaw, float pitch,
            boolean jump, boolean sneak) {
        float speed = hook.getRemoteMoveSpeed() * 1.12F;
        float walkStrafe = Mth.clamp(strafe, -1.0F, 1.0F);
        float walkForward = Mth.clamp(forward, -1.0F, 1.0F);
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        float yawRad = yaw * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yawRad), 0.0, Mth.sin(yawRad));
        Vec3 input = look.scale(walkForward).add(left.scale(walkStrafe));
        if (jump) {
            input = input.add(0.0, 1.0, 0.0);
        }
        if (sneak) {
            input = input.add(0.0, -1.0, 0.0);
        }
        if (input.lengthSqr() > 1.0) {
            input = input.normalize();
        }
        Vec3 motion = hook.getDeltaMovement().scale(0.7).add(input.scale(speed));
        Vec3 start = hook.position();
        Vec3 next = hook.limitMoveByPath(start, start.add(motion));
        hook.setPos(next.x, next.y, next.z);
        hook.setDeltaMovement(next.subtract(start));
        hook.hasImpulse = true;
    }

    /** 1.16 applyReelTug: looking aside while reeling drags the hooked creature that way. */
    public static void applyReelTug(@Nullable Entity hooked, float right, float up, float viewYaw, float strengthScale) {
        if (hooked == null) {
            return;
        }
        float analogRight = Mth.clamp(right, -1.0F, 1.0F);
        float analogUp = Mth.clamp(up, -1.0F, 1.0F);
        if (Math.abs(analogRight) < 1.0E-4F && Math.abs(analogUp) < 1.0E-4F) {
            return;
        }
        float scale = Mth.clamp(strengthScale, 0.0F, 1.0F);
        if (hooked instanceof LivingEntity living) {
            AttributeInstance knockback = living.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (knockback != null) {
                scale *= Mth.clamp(1.0F - (float) knockback.getValue() * 0.5F, 0.42F, 1.0F);
            }
        }
        if (scale <= 1.0E-4F) {
            return;
        }
        Vec3 horiz = inputVector(-analogRight, 0.0F, viewYaw, (float) (0.145 * scale));
        double vy = analogUp * 0.105 * scale;
        Vec3 force = new Vec3(horiz.x, vy, horiz.z);
        hooked.move(MoverType.SELF, force);
        Vec3 motion = hooked.getDeltaMovement();
        hooked.setDeltaMovement(motion.x * 0.7 + force.x * 0.52, Mth.clamp(motion.y * 0.82 + force.y, -0.24, 0.28),
                motion.z * 0.7 + force.z * 0.52);
        if (force.y > 0.015) {
            hooked.fallDistance = 0.0F;
        }
        hooked.hurtMarked = true;
        hooked.hasImpulse = true;
        if (hooked instanceof ServerPlayer player) {
            BeachBoyPlayerSync.sendPositionKeepLook(player);
        }
    }
}
