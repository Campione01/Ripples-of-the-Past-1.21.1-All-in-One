package rotp.addons.kingcrimson.epitaph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxGuards;
import rotp.core.api.perception.PerceptionBlockers;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * Who a press of Epitaph predicts (the owner, 2026-11-07): the creatures that are hunting the user, and the one
 * entity under his crosshair, whatever it is; with each of them what it rides and what rides it, and the projectiles
 * it has in flight. Nothing else. The crosshair entity is always in; the hunters are taken nearest first up to the cap.
 */
public final class EpitaphTargeting {
    private EpitaphTargeting() {}

    /** The entity under the user's crosshair: what the core's aim holds, else the first thing on his line of sight. */
    @Nullable
    public static Entity crosshair(Player user, double reach) {
        ActionTarget aim = LivingComponentAction.getAimTarget(user);
        Entity aimed = aim.getType() == ActionTarget.TargetType.ENTITY ? aim.getEntity() : null;
        if (aimed == null || aimed.isRemoved()) {
            aimed = sighted(user, reach);
        }
        // A Stand stands for its user.
        if (aimed instanceof StandEntity stand) {
            aimed = stand.getUser();
        }
        // What a player rides stands for him too: the two are one target (the owner, 2026-11-11).
        if (aimed != null && !(aimed instanceof Player)) {
            for (Entity rider : aimed.getPassengers()) {
                if (rider instanceof Player && rider != user) {
                    aimed = rider;
                    break;
                }
            }
        }
        return aimed == null || aimed == user || aimed.isRemoved() || SandboxGuards.denied(aimed.getType()) ? null : aimed;
    }

    @Nullable
    private static Entity sighted(Player user, double reach) {
        Level level = user.level();
        Vec3 from = user.getEyePosition();
        Vec3 along = user.getLookAngle().scale(reach);
        HitResult wall = level.clip(new ClipContext(from, from.add(along), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 to = wall.getType() == HitResult.Type.MISS ? from.add(along) : wall.getLocation();
        StandEntity own = ownStand(user);
        Entity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(user, user.getBoundingBox().expandTowards(along).inflate(1.5D),
                entity -> entity != own && !entity.isSpectator() && !PerceptionBlockers.isBlocked(user, entity))) {
            AABB box = entity.getBoundingBox().inflate(Math.max(0.3D, entity.getPickRadius()));
            Optional<Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < nearestSqr) {
                nearestSqr = hit.get().distanceToSqr(from);
                nearest = entity;
            }
        }
        return nearest;
    }

    /**
     * Whether what is under the crosshair is a player a press will foresee: such a press costs more. Not one who is dead
     * or only watches, whom another prediction holds, or the fake player of a mod. One who rides is foreseen with what
     * he rides, as one target at one cost (the owner, 2026-11-11).
     * Asked by the press on the server and, for what the HUD shows, on the client: one check, with what each side knows.
     */
    public static boolean isPlayerTarget(@Nullable Entity crosshair) {
        return crosshair instanceof Player player && !(player instanceof FakePlayer) && player.isAlive()
                && !player.isSpectator() && predictable(player);
    }

    /** The roots of a prediction of this user, the crosshair entity first; the user himself is not among them. */
    public static List<Entity> select(ServerPlayer user, @Nullable Entity crosshair, double radius, int maxTargets) {
        ServerLevel level = user.serverLevel();
        StandEntity own = ownStand(user);
        List<Entity> targets = new ArrayList<>();
        if (crosshair != null && crosshair != user && crosshair != own && predictable(crosshair)
                && (!(crosshair instanceof Player) || isPlayerTarget(crosshair))) {
            targets.add(crosshair);
        }
        List<Mob> hunters = level.getEntitiesOfClass(Mob.class, user.getBoundingBox().inflate(radius),
                mob -> mob != crosshair && mob.isAlive() && predictable(mob) && hunts(mob, user, own)
                        && mob.distanceToSqr(user) <= radius * radius);
        hunters.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(user)));
        for (Mob hunter : hunters) {
            if (targets.size() >= maxTargets) {
                break;
            }
            targets.add(hunter);
        }
        Set<Entity> roots = new LinkedHashSet<>();
        for (Entity target : targets) {
            // What it rides and what rides it, as one group; a player among them is predicted only as a target.
            target.getRootVehicle().getSelfAndPassengers()
                    .filter(member -> member == target || !(member instanceof Player) && predictable(member))
                    .forEach(roots::add);
        }
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, user.getBoundingBox().inflate(radius),
                shot -> predictable(shot) && shot.getOwner() != null && targets.contains(shot.getOwner()))) {
            roots.add(shot);
        }
        roots.remove(user);
        return new ArrayList<>(roots);
    }

    /**
     * A player who rides with all he rides with, as one group: what he rides, what that rides, and whoever else sits
     * there and is no player. Empty when one of them cannot be foreseen: then he is not foreseen either.
     */
    public static List<Entity> groupOf(ServerPlayer rider) {
        List<Entity> group = new ArrayList<>();
        for (Entity member : rider.getRootVehicle().getSelfAndPassengers().toList()) {
            if (member == rider) {
                group.add(member);
            } else if (!(member instanceof Player)) {
                if (!predictable(member)) {
                    return List.of();
                }
                group.add(member);
            }
        }
        return group;
    }

    private static boolean predictable(Entity entity) {
        return !entity.isRemoved() && !(entity instanceof StandEntity) && !SandboxGuards.denied(entity.getType())
                && !EpitaphSessions.holds(entity);
    }

    private static boolean hunts(Mob mob, ServerPlayer user, @Nullable StandEntity own) {
        LivingEntity target = mob.getTarget();
        if (target != null && (target == user || target == own)) {
            return true;
        }
        var memories = mob.getBrain().getMemories();
        Optional<? extends ExpirableValue<?>> attacked = memories.get(MemoryModuleType.ATTACK_TARGET);
        if (attacked != null && attacked.isPresent() && (attacked.get().getValue() == user || attacked.get().getValue() == own)) {
            return true;
        }
        Optional<? extends ExpirableValue<?>> angry = memories.get(MemoryModuleType.ANGRY_AT);
        return angry != null && angry.isPresent() && user.getUUID().equals(angry.get().getValue());
    }

    @Nullable
    private static StandEntity ownStand(Player user) {
        StandPower power = StandPower.get(user);
        return power != null ? power.getSummonedStandEntity() : null;
    }
}
