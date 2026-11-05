package rotp.addons.sexpistols.content.sexpistols;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.sexpistols.registry.SexPistolsEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 0.1.13 SexPistolsBulletRedirectUtil.findForcedAttackPath and the AIM
// branch of getRedirect: a kicked shot goes to a forced target first, then
// to the aimed target, straight when it is seen, else through other fixed
// members, else straight at it anyway. The 0.1.13 wall detours and planned
// wall bounces are not ported: the port's own ricochet decides bounces.
public final class SexPistolsGuidance {
    private static final double TURN_PENALTY = 8.0D;
    private static final double HOP_PENALTY = 18.0D;
    private static final double BEST_EFFORT_PENALTY = 2500.0D;
    private static final double RELAY_RANGE = 128.0D;

    public record Plan(
            Vec3 aimPoint,
            @Nullable LivingEntity target,
            @Nullable SexPistolsEntity relay) {}

    private record Aim(
            @Nullable LivingEntity target,
            List<Vec3> points,
            @Nullable BlockPos block) {}

    private SexPistolsGuidance() {}

    @Nullable
    public static Plan plan(
            ServerLevel level,
            LivingEntity owner,
            SexPistolsCoordinator coordinator,
            Entity context,
            @Nullable SexPistolsEntity source,
            Vec3 origin,
            Vec3 incoming,
            int usedMemberMask,
            @Nullable LivingEntity aimLock) {
        Vec3 incomingDir = incoming.lengthSqr() > 1.0E-6D
                ? incoming.normalize()
                : owner.getLookAngle();
        List<Aim> aims = new ArrayList<>();
        for (SexPistolsForcedTarget forced :
                coordinator.forcedTargets()) {
            if (!forced.isValid(level)) {
                continue;
            }
            if (forced.isEntity()) {
                LivingEntity living = forced.living(level);
                if (living != null && living != owner) {
                    aims.add(new Aim(
                            living,
                            entityAimPoints(origin, incomingDir, living),
                            null));
                }
            }
            else {
                aims.add(new Aim(
                        null,
                        blockAimPoints(origin, forced.blockPos()),
                        forced.blockPos()));
            }
        }
        if (aims.isEmpty()
                && coordinator.targetMode() == SexPistolsTargetMode.AIM
                && aimLock != null
                && aimLock.isAlive()
                && aimLock.distanceToSqr(origin)
                        <= SexPistolsAim.AIM_MAX_DISTANCE
                                * SexPistolsAim.AIM_MAX_DISTANCE) {
            aims.add(new Aim(
                    aimLock,
                    List.of(aimLock.getBoundingBox().getCenter()),
                    null));
        }
        if (aims.isEmpty()) {
            return null;
        }

        List<SexPistolsEntity> relays = coordinator
                .loadedMembers(level).stream()
                .filter(member -> member != source)
                .filter(SexPistolsEntity::canRedirectBullet)
                .filter(member -> {
                    int slot = SexPistolsEntities.slotForNumber(
                            member.getMemberNumber());
                    return slot >= 0
                            && (usedMemberMask & 1 << slot) == 0;
                })
                .filter(member -> member.distanceToSqr(origin)
                        <= RELAY_RANGE * RELAY_RANGE)
                .toList();
        Entity ignored = source != null ? source : context;

        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        for (Aim aim : aims) {
            for (Vec3 point : aim.points()) {
                if (point.distanceToSqr(origin) <= 1.0E-6D) {
                    continue;
                }
                if (clear(level, ignored, origin, point, aim.block())) {
                    double score = score(origin, point, incomingDir, 0);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Plan(point, aim.target(), null);
                    }
                    continue;
                }
                for (SexPistolsEntity first : relays) {
                    Vec3 firstPos = first.getBoundingBox().getCenter();
                    if (!clear(level, ignored, origin, firstPos, null)
                            || !reaches(
                                    level,
                                    first,
                                    firstPos,
                                    point,
                                    aim.block(),
                                    relays,
                                    relays.size() - 1)) {
                        continue;
                    }
                    double score = score(origin, firstPos, incomingDir, 1)
                            + firstPos.distanceTo(point);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Plan(point, aim.target(), first);
                    }
                }
            }
            Vec3 direct = aim.points().get(0);
            if (direct.distanceToSqr(origin) > 1.0E-6D) {
                double score = BEST_EFFORT_PENALTY
                        + score(origin, direct, incomingDir, 0);
                if (score < bestScore) {
                    bestScore = score;
                    best = new Plan(direct, aim.target(), null);
                }
            }
        }
        return best;
    }

    // 0.1.13 canReachAim: a breadth-first search over the other fixed
    // members, each hop needing a clear line.
    private static boolean reaches(
            ServerLevel level,
            SexPistolsEntity from,
            Vec3 fromPos,
            Vec3 point,
            @Nullable BlockPos allowBlock,
            List<SexPistolsEntity> relays,
            int remainingHops) {
        if (clear(level, from, fromPos, point, allowBlock)) {
            return true;
        }
        if (remainingHops <= 0) {
            return false;
        }
        record Hop(SexPistolsEntity stand, Vec3 pos, int remaining) {}
        ArrayDeque<Hop> queue = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(from.getUUID());
        queue.add(new Hop(from, fromPos, remainingHops));
        while (!queue.isEmpty()) {
            Hop hop = queue.poll();
            if (clear(level, hop.stand(), hop.pos(), point, allowBlock)) {
                return true;
            }
            if (hop.remaining() <= 0) {
                continue;
            }
            for (SexPistolsEntity next : relays) {
                if (visited.contains(next.getUUID())) {
                    continue;
                }
                Vec3 nextPos = next.getBoundingBox().getCenter();
                if (clear(level, hop.stand(), hop.pos(), nextPos, null)) {
                    visited.add(next.getUUID());
                    queue.add(new Hop(
                            next, nextPos, hop.remaining() - 1));
                }
            }
        }
        return false;
    }

    private static double score(
            Vec3 origin, Vec3 next, Vec3 incomingDir, int hops) {
        Vec3 direction = next.subtract(origin);
        double distance = direction.length();
        double turn = 0.0D;
        if (distance > 1.0E-6D) {
            turn = (1.0D - Math.max(-1.0D, Math.min(
                            1.0D,
                            direction.scale(1.0D / distance)
                                    .dot(incomingDir))))
                    * TURN_PENALTY;
        }
        return distance + turn + hops * HOP_PENALTY;
    }

    // A forced block counts as seen when the line ends on that block.
    public static boolean clear(
            ServerLevel level,
            Entity context,
            Vec3 from,
            Vec3 to,
            @Nullable BlockPos allowBlock) {
        BlockHitResult hit = level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                context));
        return hit.getType() == HitResult.Type.MISS
                || allowBlock != null
                        && hit.getType() == HitResult.Type.BLOCK
                        && allowBlock.equals(hit.getBlockPos());
    }

    private static List<Vec3> entityAimPoints(
            Vec3 origin, Vec3 incomingDir, LivingEntity target) {
        List<Vec3> points = new ArrayList<>(5);
        Vec3 center = target.getBoundingBox().getCenter();
        points.add(center);
        Vec3 toTarget = center.subtract(origin);
        Vec3 horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            horizontal = new Vec3(incomingDir.x, 0.0D, incomingDir.z);
        }
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        }
        horizontal = horizontal.normalize();
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        double sideOffset = Math.max(
                0.18D, Math.min(0.55D, target.getBbWidth() * 0.42D));
        double verticalOffset = Math.max(
                0.08D, Math.min(0.35D, target.getBbHeight() * 0.18D));
        points.add(center.add(side.scale(sideOffset)));
        points.add(center.subtract(side.scale(sideOffset)));
        points.add(center.add(side.scale(sideOffset * 0.65D))
                .add(0.0D, verticalOffset, 0.0D));
        points.add(center.subtract(side.scale(sideOffset * 0.65D))
                .add(0.0D, verticalOffset, 0.0D));
        return points;
    }

    private static List<Vec3> blockAimPoints(Vec3 origin, BlockPos pos) {
        List<Vec3> points = new ArrayList<>(2);
        Vec3 center = Vec3.atCenterOf(pos);
        points.add(center);
        Vec3 toOrigin = origin.subtract(center);
        if (toOrigin.lengthSqr() > 1.0E-6D) {
            points.add(center.add(toOrigin.normalize().scale(0.49D)));
        }
        return points;
    }
}
