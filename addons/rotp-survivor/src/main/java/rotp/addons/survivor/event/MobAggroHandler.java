package rotp.addons.survivor.event;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rotp.addons.survivor.SurvivorAddon;
import rotp.addons.survivor.SurvivorConfig;
import rotp.addons.survivor.util.AngerHelper;
import rotp.addons.survivor.util.WetHelper;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A wet creature near a Survivor user turns on whoever is close: players strike, mobs hunt. */
public final class MobAggroHandler {
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Double> LAST_DISTANCES = new HashMap<>();
    private static final Map<UUID, Integer> STUCK_TIMERS = new HashMap<>();

    private MobAggroHandler() {}

    static void onLivingTick(LivingEntity entity) {
        Level level = entity.level();
        if (!WetHelper.isWet(entity)) {
            return;
        }
        if (entity instanceof Player && !SurvivorConfig.PLAYER_ANGER.get()) {
            return;
        }
        if (SurvivorAddon.isSurvivorUser(entity)) {
            return;
        }
        double survivorRange = SurvivorConfig.SURVIVOR_RANGE.get();
        List<LivingEntity> users = level.getEntitiesOfClass(
                LivingEntity.class, entity.getBoundingBox().inflate(survivorRange), SurvivorAddon::isSurvivorUser);
        if (users.isEmpty()) {
            return;
        }
        if (entity instanceof Player player) {
            handlePlayer(player, users);
        } else if (entity instanceof Mob mob) {
            if (!AngerHelper.isAngry(entity)) {
                return;
            }
            handleMob(mob, level, users);
        }
    }

    private static void handlePlayer(Player player, List<LivingEntity> users) {
        if (isOnCooldown(player)) {
            return;
        }
        double playerHitRange = SurvivorConfig.PLAYER_HIT_RANGE.get();
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(playerHitRange), e -> isValidTarget(player, e));
        if (!targets.isEmpty()) {
            LivingEntity target = targets.get(player.level().random.nextInt(targets.size()));
            player.swing(InteractionHand.MAIN_HAND, true);
            target.hurt(byTheStand(player.damageSources().playerAttack(player), player, users),
                    SurvivorConfig.HIT_DAMAGE.get().floatValue());
            setCooldown(player);
        }
    }

    private static void handleMob(Mob mob, Level level, List<LivingEntity> users) {
        boolean hasAttackAI = mob instanceof Monster;
        double targetRange = SurvivorConfig.TARGET_RANGE.get();
        double mobHitRange = SurvivorConfig.MOB_HIT_RANGE.get();
        double moveSpeed = SurvivorConfig.MOVE_SPEED.get();
        double forcedSpeedAttribute = SurvivorConfig.FORCED_SPEED_ATTRIBUTE.get();
        int stuckTime = SurvivorConfig.STUCK_TIME.get();
        LivingEntity target = mob.getTarget();
        if (target != null && !isValidTarget(mob, target)) {
            mob.setTarget(null);
            target = null;
        }

        if (target == null || !target.isAlive() || mob.distanceTo(target) > targetRange) {
            List<LivingEntity> targets = level.getEntitiesOfClass(
                    LivingEntity.class, mob.getBoundingBox().inflate(targetRange), e -> isValidTarget(mob, e));
            if (targets.isEmpty()) {
                return;
            }
            target = targets.get(level.random.nextInt(targets.size()));
            mob.setTarget(target);
        }

        // Monsters fight with their own goals; other mobs are walked to the target and strike directly.
        if (!hasAttackAI) {
            AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(forcedSpeedAttribute);
            }

            UUID id = mob.getUUID();
            double currentDistance = mob.distanceTo(target);
            double lastDistance = LAST_DISTANCES.getOrDefault(id, currentDistance);
            LAST_DISTANCES.put(id, currentDistance);
            if (currentDistance < lastDistance - 0.01) {
                STUCK_TIMERS.put(id, 0);
            } else {
                STUCK_TIMERS.put(id, STUCK_TIMERS.getOrDefault(id, 0) + 1);
            }

            Vec3 direction = target.position().subtract(mob.position()).normalize().scale(moveSpeed);
            if (STUCK_TIMERS.getOrDefault(id, 0) > stuckTime) {
                mob.setDeltaMovement(direction.x, mob.getDeltaMovement().y, direction.z);
                mob.hurtMarked = true;
                STUCK_TIMERS.put(id, 0);
            } else {
                mob.getNavigation().moveTo(target, moveSpeed);
            }

            if (mob.distanceTo(target) < mobHitRange && !isOnCooldown(mob)) {
                target.hurt(byTheStand(mob.damageSources().mobAttack(mob), mob, users),
                        SurvivorConfig.HIT_DAMAGE.get().floatValue());
                setCooldown(mob);
            }
        }
    }

    // the creature strikes because of the Survivor user nearest to it, so the hit is that Stand's work and takes its damage share
    private static DamageSource byTheStand(DamageSource source, LivingEntity attacker, List<LivingEntity> users) {
        LivingEntity nearest = users.stream().min(Comparator.comparingDouble(attacker::distanceToSqr)).orElse(null);
        if (nearest != null && source instanceof DamageSourceModified modified) {
            modified.jojo_ripples$setStandPower(StandPower.get(nearest));
        }
        return source;
    }

    private static boolean isValidTarget(LivingEntity attacker, LivingEntity target) {
        if (target == null || target == attacker || !target.isAlive()) {
            return false;
        }
        return !target.isSpectator() && !SurvivorAddon.isSurvivorUser(target);
    }

    private static boolean isOnCooldown(LivingEntity entity) {
        return COOLDOWNS.getOrDefault(entity.getUUID(), 0) > 0;
    }

    private static void setCooldown(LivingEntity entity) {
        COOLDOWNS.put(entity.getUUID(), SurvivorConfig.ATTACK_COOLDOWN.get());
    }

    /** The entries of a creature that left the level; 1.16 kept one per creature that ever chased. */
    static void forget(UUID id) {
        COOLDOWNS.remove(id);
        LAST_DISTANCES.remove(id);
        STUCK_TIMERS.remove(id);
    }

    static void clear() {
        COOLDOWNS.clear();
        LAST_DISTANCES.clear();
        STUCK_TIMERS.clear();
    }

    static void onServerTickEnd() {
        COOLDOWNS.entrySet().removeIf(entry -> {
            int value = entry.getValue() - 1;
            if (value <= 0) {
                return true;
            }
            entry.setValue(value);
            return false;
        });
    }
}
