package rotp.addons.tog;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rotp.addons.tog.TowerOfGrayAbility.Move;
import rotp.core.JojoModConfig;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

public final class TowerOfGrayEntity extends StandEntity {
    private static final double TRAVEL_SPEED = 1.8;
    private static final int TRAVEL_TICKS = 10;
    private static final double CONTACT_DISTANCE_SQ = 2.5;
    private static final double ORBIT_RADIUS = 2.5;
    private static final int ORBIT_TICKS = 30;
    private static final double ORBIT_STEP = Math.PI * 2 / ORBIT_TICKS;

    @Nullable private LivingEntity pursuitTarget;
    private int flyTicks;
    private Vec3 flyDirection = Vec3.ZERO;
    private int orbitTicks;
    private double orbitAngle;
    private int dashTicks;
    private Vec3 dashDirection = Vec3.ZERO;
    private float dashDamage;

    public TowerOfGrayEntity(EntityType<? extends TowerOfGrayEntity> type, Level level) {
        super(type, level);
    }

    void beginMove(Move move) {
        LivingEntity user = getUser();
        if (user == null) {
            return;
        }
        switch (move) {
            case DASH -> aimedDash(user, 20, TRAVEL_SPEED);
            case GRAY_STINGER -> {
                dashDamage = 8;
                aimedDash(user, 30, 3.2);
            }
            case QUICK_REPOSITION -> startDash(directionTo(user), TRAVEL_SPEED);
            case FLY -> {
                dashTicks = 0;
                pursuitTarget = null;
                flyDirection = user.getLookAngle().normalize();
                flyTicks = TRAVEL_TICKS;
                velocity(flyDirection.scale(TRAVEL_SPEED));
            }
            case DODGE -> {
                float yaw = getRandom().nextFloat() * ((float) Math.PI * 2);
                velocity(new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).normalize()
                        .add(0, 0.1, 0).normalize().scale(0.0375));
            }
            case GRAY_SWARM -> startSwarm(user);
            case NEEDLE_PURSUIT -> { }
        }
    }

    void pursue(@Nullable LivingEntity target) {
        pursuitTarget = target != this && target != getUser() ? target : null;
        if (pursuitTarget != null) {
            velocity(directionTo(pursuitTarget).scale(TRAVEL_SPEED));
        }
    }

    private void aimedDash(LivingEntity user, int reach, double speed) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle().normalize();
        Vec3 ray = look.scale(reach);
        Vec3 end = eye.add(ray);
        AABB search = user.getBoundingBox().expandTowards(ray).inflate(1);
        double closestDistance = (double) reach * reach;
        LivingEntity closest = null;
        // The donor chooses by hit position on this eye ray, not by entity-centre distance or the HUD target.
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, search,
                living -> living != user && living.isAlive())) {
            var hit = target.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < closestDistance) {
                closestDistance = eye.distanceToSqr(hit.get());
                closest = target;
            }
        }
        startDash(closest == null ? look : directionTo(closest), speed);
    }

    private void startDash(Vec3 direction, double speed) {
        flyTicks = 0;
        pursuitTarget = null;
        dashDirection = direction;
        dashTicks = TRAVEL_TICKS;
        velocity(direction.scale(speed));
    }

    private void startSwarm(LivingEntity user) {
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
                user.getBoundingBox().inflate(ORBIT_RADIUS), living -> living != user && living != this)) {
            target.hurt(damageSources().generic(), 4);
        }
        Vec3 rel = position().subtract(user.position());
        orbitAngle = Math.atan2(rel.z, rel.x);
        orbitTicks = ORBIT_TICKS;
        Vec3 horizontal = new Vec3(rel.x, 0, rel.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            horizontal = new Vec3(1, 0, 0);
        }
        velocity(new Vec3(-horizontal.z, 0, horizontal.x).normalize().scale(0.3));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        if (pursuitTarget != null) {
            if (!pursuitTarget.isAlive() || pursuitTarget.level() != server) {
                pursuitTarget = null;
                setDeltaMovement(Vec3.ZERO);
            }
            else if (getCurStandAction() != null && getCurStandAction().getPhase() == ActionPhase.PERFORM
                    && distanceToSqr(pursuitTarget) <= CONTACT_DISTANCE_SQ) {
                hitWithNeedle(server, pursuitTarget, (float) Math.max(1, getAttackDamage()));
            }
            else {
                velocity(directionTo(pursuitTarget).scale(TRAVEL_SPEED));
            }
            return;
        }
        if (flyTicks > 0) {
            --flyTicks;
            velocity(flyDirection.scale(TRAVEL_SPEED));
            return;
        }
        if (orbitTicks > 0) {
            --orbitTicks;
            tickSwarm();
            return;
        }
        if (dashTicks > 0) {
            --dashTicks;
            velocity(dashDirection.scale(TRAVEL_SPEED));
            for (LivingEntity target : nearbyTargets()) {
                if (distanceToSqr(target) <= CONTACT_DISTANCE_SQ) {
                    hitWithNeedle(server, target, (float) Math.max(1, dashDamage > 0 ? dashDamage : getAttackDamage()));
                    dashTicks = 0;
                    // Stinger's override survives a miss and is consumed by the first later dash collision.
                    dashDamage = 0;
                    return;
                }
            }
        }
    }

    private void tickSwarm() {
        LivingEntity user = getUser();
        if (user == null) {
            return;
        }
        orbitAngle += ORBIT_STEP;
        Vec3 point = centre(user).add(Math.cos(orbitAngle) * ORBIT_RADIUS, 0, Math.sin(orbitAngle) * ORBIT_RADIUS);
        velocity(point.subtract(position()).normalize().scale(Math.max(0.01, ORBIT_RADIUS * ORBIT_STEP)));
        for (LivingEntity target : nearbyTargets()) {
            if (distanceToSqr(target) <= (ORBIT_RADIUS + 1.5) * (ORBIT_RADIUS + 1.5)) {
                target.hurt(damageSources().generic(), 1);
            }
        }
    }

    private Iterable<LivingEntity> nearbyTargets() {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.15),
                living -> living != this && living != getUser() && living.isAlive());
    }

    private void hitWithNeedle(ServerLevel server, LivingEntity target, float amount) {
        DamageSource source = DamageUtil.make(server, ModDamageTypes.STAND_ATTACK, this);
        if (source instanceof DamageSourceModified modified) {
            modified.jojo_ripples$setStandPower(getUserPower());
        }
        // The legacy attack called hurtTarget directly, not the main-punch shield or Stand-combo cooldown path.
        float multiplier = JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get().floatValue();
        if (DamageUtil.hurtThroughInvulTicks(target, source, amount * multiplier)) {
            LivingEntity user = getUser();
            if (user != null) {
                if (user instanceof Player player) {
                    target.setLastHurtByPlayer(player);
                }
                LivingEntity aggro = isFollowingUser() || target.hasLineOfSight(user) ? user
                        : StandUtil.isEntityStandUser(target) ? this : null;
                if (aggro != null) {
                    target.setLastHurtByMob(aggro);
                }
            }
            EnchantmentHelper.doPostAttackEffects(server, target, source);
        }
    }

    private Vec3 directionTo(LivingEntity target) {
        return centre(target).subtract(position()).normalize();
    }

    private static Vec3 centre(LivingEntity entity) {
        return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
    }

    private void velocity(Vec3 delta) {
        setDeltaMovement(delta);
        hasImpulse = true;
        hurtMarked = true;
    }
}
