package rotp.addons.sexpistols.content.sexpistols;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 0.1.13 SexPistolsAimUtil and the look helpers of SexPistolsStandUtil.
public final class SexPistolsAim {
    public static final double AIM_MAX_DISTANCE = 128.0D;
    public static final double AIM_FOV_HALF_ANGLE_DEG = 50.0D;
    private static final double AIM_FOV_MIN_DOT =
            Math.cos(Math.toRadians(AIM_FOV_HALF_ANGLE_DEG));
    public static final double FORCED_TARGET_RANGE = 128.0D;

    private SexPistolsAim() {}

    // 0.1.13 getAimLookSource: the fixed member with the lowest number,
    // else the user. The port's fixed members do not turn with the user's
    // view, so the cone keeps the user's look direction and only starts
    // at that member.
    public static Vec3 aimEye(LivingEntity owner) {
        SexPistolsEntity member = lowestFixedMember(owner);
        return member != null
                ? member.getEyePosition()
                : owner.getEyePosition();
    }

    @Nullable
    public static SexPistolsEntity lowestFixedMember(LivingEntity owner) {
        if (owner == null
                || !owner.hasData(
                        SexPistolsAttachments.SEX_PISTOLS_COORDINATOR)) {
            return null;
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        Level level = owner.level();
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            Entity entity = level instanceof ServerLevel serverLevel
                    ? coordinator.member(serverLevel, number)
                    : level.getEntity(coordinator.childEntityId(number));
            if (entity instanceof SexPistolsEntity member
                    && member.isAlive()
                    && member.getMemberState()
                            == SexPistolsMemberState.SCOUTING
                    && !member.isRetrievingItem()) {
                return member;
            }
        }
        return null;
    }

    @Nullable
    public static LivingEntity findAimTarget(LivingEntity owner) {
        return owner != null
                ? findAimTarget(
                        owner, aimEye(owner), owner.getLookAngle())
                : null;
    }

    // The living entity closest to the look line within a 50-degree cone
    // and 128 blocks, seen from the eye; ties go to the nearer one.
    @Nullable
    public static LivingEntity findAimTarget(
            LivingEntity owner, Vec3 eye, Vec3 look) {
        if (owner == null || look.lengthSqr() <= 1.0E-6D) {
            return null;
        }
        Level level = owner.level();
        Vec3 direction = look.normalize();
        AABB box = new AABB(eye, eye).inflate(AIM_MAX_DISTANCE);
        LivingEntity best = null;
        double bestDot = AIM_FOV_MIN_DOT;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                candidate -> isAimCandidate(owner, candidate))) {
            Vec3 center = entity.getBoundingBox().getCenter();
            Vec3 toEntity = center.subtract(eye);
            double distance = toEntity.length();
            if (distance < 0.4D || distance > AIM_MAX_DISTANCE) {
                continue;
            }
            double dot = toEntity.scale(1.0D / distance).dot(direction);
            if (dot < AIM_FOV_MIN_DOT
                    || !seen(level, owner, eye, center)) {
                continue;
            }
            if (dot > bestDot + 1.0E-4D
                    || Math.abs(dot - bestDot) <= 1.0E-4D
                            && distance < bestDistance) {
                bestDot = dot;
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private static boolean isAimCandidate(
            LivingEntity owner, LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && entity != owner
                && !(entity instanceof StandEntity)
                && !(entity instanceof SexPistolsEntity)
                && !(entity instanceof Player player
                        && player.isSpectator());
    }

    // Unlike the aim cone, other users' Stands can be marked.
    private static boolean isForcedCandidate(
            LivingEntity owner, LivingEntity entity) {
        if (entity == null
                || !entity.isAlive()
                || entity == owner
                || entity instanceof Player player
                        && player.isSpectator()) {
            return false;
        }
        if (entity instanceof SexPistolsEntity member) {
            return member.getSwarmOwner() != owner;
        }
        return !(entity instanceof StandEntity stand)
                || stand.getUser() != owner;
    }

    private static boolean seen(
            Level level, Entity context, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(
                        from,
                        to,
                        ClipContext.Block.VISUAL,
                        ClipContext.Fluid.NONE,
                        context))
                .getType() == HitResult.Type.MISS;
    }

    // 0.1.13 resolveForcedLookTarget: the entity or block on the user's
    // look ray within 128 blocks; with no entity on the ray, the aim-cone
    // target counts as looked at.
    public static HitResult forcedLookTarget(LivingEntity owner) {
        Level level = owner.level();
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(
                owner.getLookAngle().scale(FORCED_TARGET_RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                owner));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS
                ? end
                : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level,
                owner,
                eye,
                entityEnd,
                new AABB(eye, entityEnd).inflate(1.0D),
                entity -> entity instanceof LivingEntity living
                        && isForcedCandidate(owner, living),
                0.15F);
        if (entityHit != null) {
            return entityHit;
        }
        LivingEntity aim = findAimTarget(
                owner, eye, owner.getLookAngle());
        if (aim != null) {
            return new EntityHitResult(aim);
        }
        return blockHit;
    }
}
