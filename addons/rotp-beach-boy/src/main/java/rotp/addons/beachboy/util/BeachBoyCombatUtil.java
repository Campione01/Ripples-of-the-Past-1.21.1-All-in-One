package rotp.addons.beachboy.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.network.s2c.BloodParticlesPacket;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** 1.16 BeachBoyCombatUtil: lash and overhead slash, tear and heart crush, trap hook and the strike effects. */
public final class BeachBoyCombatUtil {
    public static final int WHIP_ANIM_TICKS = 18;
    public static final float WHIP_OUT_RATIO = 0.62F;
    public static final double WHIP_RANGE = 8.5;
    public static final double WHIP_SLASH_RANGE = 11.0;
    public static final double TRAP_PLACE_RANGE = 50.0;
    public static final int BLEED_TICKS = 200;
    public static final int HEART_CRUSH_DAMAGE = 18;
    public static final int HEART_CRUSH_HOLD_TICKS = 100;
    public static final int RIP_SLOW_TICKS = 80;
    public static final int COLLAPSE_TICKS = 160;

    private BeachBoyCombatUtil() {}

    public static double whipRange(boolean vertical) {
        return vertical ? WHIP_SLASH_RANGE : WHIP_RANGE;
    }

    public static LivingEntity getSkillLookSource(LivingEntity user) {
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(user);
        return stand != null && stand.isManuallyControlled() ? stand : user;
    }

    public static ActionTarget resolveTrapTarget(LivingEntity user) {
        ActionTarget look = resolveLookTarget(user, TRAP_PLACE_RANGE);
        ItemEntity item = findLookedAtItem(user, TRAP_PLACE_RANGE);
        if (item == null) {
            return look;
        }
        Vec3 origin = getSkillLookSource(user).getEyePosition(1.0F);
        double itemDist = origin.distanceToSqr(item.position());
        if (look.getType() == ActionTarget.TargetType.EMPTY) {
            return new ActionTarget(item);
        }
        Vec3 lookPoint = look.getType() == ActionTarget.TargetType.ENTITY && look.getEntity() != null
                ? look.getEntity().position()
                : look.getType() == ActionTarget.TargetType.BLOCK && look.getBlockPos() != null
                        ? Vec3.atCenterOf(look.getBlockPos()) : origin;
        return itemDist <= origin.distanceToSqr(lookPoint) + 0.05 ? new ActionTarget(item) : look;
    }

    @Nullable
    public static ItemEntity findLookedAtItem(@Nullable LivingEntity looker, double range) {
        if (looker == null) {
            return null;
        }
        Vec3 eyePos = looker.getEyePosition(1.0F);
        Vec3 endPos = eyePos.add(looker.getViewVector(1.0F).scale(range));
        BlockHitResult blockHit = looker.level().clip(new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, looker));
        double bestDistanceSqr = blockHit.getType() == HitResult.Type.BLOCK
                ? eyePos.distanceToSqr(blockHit.getLocation()) : eyePos.distanceToSqr(endPos);
        AABB searchBox = new AABB(eyePos, endPos).inflate(0.85);
        ItemEntity bestTarget = null;
        for (ItemEntity itemEntity : looker.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> item.isAlive() && !item.getItem().isEmpty())) {
            AABB itemBox = itemEntity.getBoundingBox().inflate(0.35);
            Vec3 hitPos = itemBox.clip(eyePos, endPos).orElse(itemBox.contains(eyePos) ? eyePos : null);
            if (hitPos != null) {
                double hitDistanceSqr = eyePos.distanceToSqr(hitPos);
                if (hitDistanceSqr <= bestDistanceSqr) {
                    bestDistanceSqr = hitDistanceSqr;
                    bestTarget = itemEntity;
                }
            }
        }
        return bestTarget;
    }

    /** 1.16 JojoModUtil.rayTrace from the skill's look source: the nearest block or entity (0.15 wider) in range. */
    public static ActionTarget resolveLookTarget(LivingEntity user, double range) {
        LivingEntity look = getSkillLookSource(user);
        double reach = Math.max(range, look instanceof Player player ? player.blockInteractionRange() : 4.5);
        Vec3 start = look.getEyePosition(1.0F);
        Vec3 end = start.add(look.getViewVector(1.0F).scale(reach));
        Level level = look.level();
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, look));
        double maxSqr = blockHit.getType() == HitResult.Type.BLOCK ? start.distanceToSqr(blockHit.getLocation())
                : reach * reach;
        Entity best = null;
        double bestSqr = maxSqr;
        for (Entity entity : level.getEntities(look, new AABB(start, end).inflate(1.0),
                entity -> entity.isAlive() && !entity.isSpectator() && entity.isPickable() && !entity.is(look)
                        && !entity.is(user) && !(entity instanceof BeachBoyHookEntity) && !isOwnStand(entity, user))) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius() + 0.15);
            Optional<Vec3> hit = box.clip(start, end);
            Vec3 at = box.contains(start) ? start : hit.orElse(null);
            if (at != null) {
                double sqr = start.distanceToSqr(at);
                if (sqr < bestSqr) {
                    bestSqr = sqr;
                    best = entity;
                }
            }
        }
        if (best != null) {
            return new ActionTarget(best);
        }
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            return new ActionTarget(blockHit.getBlockPos(), blockHit.getDirection());
        }
        return ActionTarget.EMPTY;
    }

    public static float getMaxLineLength(LivingEntity user) {
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        if (hook != null) {
            return hook.getMaxLineLength();
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(user);
        return stand != null ? stand.getMaxLineLength() : BeachBoyLine.DEFAULT_LENGTH;
    }

    public static boolean isWithinTrapRange(LivingEntity user, Vec3 point) {
        return getSkillLookSource(user).distanceToSqr(point) <= TRAP_PLACE_RANGE * TRAP_PLACE_RANGE;
    }

    public static boolean performWhip(Level level, LivingEntity user, @Nullable StandPower power, boolean vertical) {
        if (level.isClientSide() || user == null) {
            return false;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(user);
        if (stand != null) {
            stand.playWhip(vertical);
        }
        user.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, vertical ? 0.7F : 1.0F);
        LivingEntity grabbed = hookedLiving(BeachBoyStandUtil.findHook(user));
        float damage = whipDamage(power, stand);
        Vec3 origin = user.getEyePosition(1.0F);
        Vec3 look = user.getLookAngle();
        double range = whipRange(vertical);
        AABB box = user.getBoundingBox().inflate(range + 1.25);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> isWhipEnemy(user, candidate) && candidate != grabbed)) {
            if (isInWhipArc(origin, look, target, vertical, range)) {
                hurtStand(user, power, stand, target, damage, null);
                tryApplyStrikeEffects(user, target);
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            Vec3 tip = origin.add(look.scale(range * 0.55));
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, tip.x, tip.y, tip.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        return true;
    }

    /** 1.16 tickWhippedHost: a lash with a creature on the hook swings that creature along the arc. */
    public static void tickWhippedHost(BeachBoyStandEntity stand) {
        if (stand == null || stand.level().isClientSide()) {
            return;
        }
        LivingEntity user = stand.getUser();
        LivingEntity grabbed = hookedLiving(BeachBoyStandUtil.findHook(user));
        if (user == null || grabbed == null || !grabbed.isAlive()) {
            return;
        }
        int ticks = stand.getWhipTicks();
        float progress = 1.0F - Mth.clamp((float) ticks / WHIP_ANIM_TICKS, 0.0F, 1.0F);
        Vec3 origin = BeachBoyHookEntity.getRodTip(user, 1.0F);
        Vec3 look = user.getLookAngle();
        boolean vertical = stand.isWhipVertical();
        List<Vec3> path = buildWhipArc(origin, look, vertical, stand.isWhipLeftToRight(), stand.isWhipUpward(),
                progress, whipRange(vertical));
        if (path.size() < 2) {
            return;
        }
        Vec3 tip = path.get(path.size() - 1);
        Vec3 prev = path.get(path.size() - 2);
        Vec3 dest = tip.subtract(0.0, grabbed.getBbHeight() * 0.45, 0.0);
        Vec3 before = grabbed.position();
        Vec3 delta = dest.subtract(before);
        grabbed.move(MoverType.SELF, delta);
        Vec3 after = grabbed.position();
        boolean hitWall = grabbed.horizontalCollision;
        boolean hitVert = grabbed.verticalCollision;
        boolean blockedMove = delta.lengthSqr() > 0.04 && after.distanceToSqr(before) < delta.lengthSqr() * 0.36;
        boolean downwardSlam = vertical && !stand.isWhipUpward() && delta.y < -0.05 && (hitVert || grabbed.onGround());
        if (downwardSlam) {
            applyWhipBlockImpact(stand, grabbed, before, dest, delta);
            applyWhipSlamDamage(stand, grabbed, delta);
            BeachBoyPathSwim.slam(grabbed, 40);
            stopWhipSwing(stand, grabbed);
        }
        else if (!hitWall && (!hitVert || !(Math.abs(delta.y) > 0.05)) && !blockedMove) {
            Vec3 motion = dest.subtract(after).add(tip.subtract(prev).scale(0.85));
            if (progress >= WHIP_OUT_RATIO) {
                motion = tip.subtract(prev).scale(1.35);
            }
            grabbed.setDeltaMovement(motion);
            grabbed.hurtMarked = true;
            grabbed.hasImpulse = true;
            grabbed.fallDistance = 0.0F;
            BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
            if (hook != null) {
                hook.snapToHost();
            }
        }
        else {
            applyWhipBlockImpact(stand, grabbed, before, dest, delta);
            stopWhipSwing(stand, grabbed);
        }
    }

    private static void applyWhipSlamDamage(BeachBoyStandEntity stand, LivingEntity grabbed, Vec3 delta) {
        float speed = (float) Math.max(Math.abs(delta.y), delta.length() * 0.45);
        LivingEntity user = stand.getUser();
        StandPower power = stand.getUserPower();
        float scale = BeachBoyStandUtil.getPowerRatio(power);
        float damage = Mth.clamp(speed * 1.05F, 0.35F, 2.4F) * Mth.clamp(scale, 0.75F, 1.35F);
        // 1.16 standDamage(...): with the Stand out, its own damage source
        grabbed.hurt(standSource(user != null ? user : grabbed, power, stand, null), damage);
    }

    private static void applyWhipBlockImpact(BeachBoyStandEntity stand, LivingEntity grabbed, Vec3 before, Vec3 dest,
            Vec3 delta) {
        Vec3 kb = delta.lengthSqr() > 1.0E-8 ? delta : dest.subtract(before);
        if (kb.lengthSqr() < 1.0E-8) {
            kb = grabbed.getLookAngle().scale(0.35);
        }
        KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(grabbed);
        if (impact != null) {
            impact.onPunchSetKnockbackImpact(kb, stand)
                    .withImpactExplosion(Math.max(StandEntityHeavyPunchAbility.calcExplosionRadius(stand) - 0.5F, 0.0F),
                            null, 0.0F);
            impact.collideBreakBlocks(before, dest, grabbed.level());
        }
    }

    private static void stopWhipSwing(BeachBoyStandEntity stand, LivingEntity grabbed) {
        grabbed.setDeltaMovement(grabbed.getDeltaMovement().scale(0.15));
        grabbed.hurtMarked = true;
        stand.stopWhip();
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(stand.getUser());
        if (hook != null) {
            hook.snapToHost();
        }
    }

    public static boolean performRip(Level level, LivingEntity user, @Nullable StandPower power, boolean heartCrush) {
        if (level.isClientSide() || user == null) {
            return false;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        LivingEntity target = hookedLiving(hook);
        if (hook == null || target == null) {
            return false;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(user);
        float amount = heartCrush ? HEART_CRUSH_DAMAGE : ripDamage(power, stand);
        hurtStand(user, power, stand, target, amount,
                heartCrush ? BeachBoyAddon.HEART_CRUSH_DAMAGE_TYPE : BeachBoyAddon.RIP_DAMAGE_TYPE);
        applyBleed(target);
        double yaw = user.getRandom().nextDouble() * (Math.PI * 2);
        float knock = heartCrush ? 1.05F : 0.85F;
        target.knockback(knock, Math.sin(yaw), Math.cos(yaw));
        Vec3 flyDir = new Vec3(-Math.sin(yaw), heartCrush ? 0.28 : 0.18, -Math.cos(yaw));
        spawnBloodSplash(target, flyDir, heartCrush ? 22 : 14, heartCrush ? 0.55F : 0.4F);
        if (heartCrush) {
            BeachBoyCollapse.apply(target, COLLAPSE_TICKS);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0F, 0.55F);
            hook.ejectFromHost(flyDir.normalize().scale(1.35));
        }
        else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, RIP_SLOW_TICKS, 1, false, false, true));
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        target.hurtMarked = true;
        return true;
    }

    public static boolean performTrap(Level level, LivingEntity user, ActionTarget target) {
        if (level.isClientSide() || user == null || target == null) {
            return false;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(user);
        if (stand != null && stand.isManuallyControlled()) {
            stand.leaveRemoteControl();
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        if (hook == null) {
            hook = new BeachBoyHookEntity(level, user, 0.0F);
            level.addFreshEntity(hook);
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() instanceof ItemEntity item) {
            hook.plantTrapOnItem(item);
            return true;
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() instanceof LivingEntity living) {
            hook.plantTrap(living, null);
            return true;
        }
        if (target.getType() == ActionTarget.TargetType.BLOCK && target.getBlockPos() != null) {
            BlockPos pos = target.getBlockPos();
            if (level.getBlockState(pos).isAir()) {
                return false;
            }
            hook.plantTrap(null, pos);
            return true;
        }
        return false;
    }

    public static void tryTriggerWalk(LivingEntity walker) {
        if (walker == null || walker.level().isClientSide()) {
            return;
        }
        BlockPos feet = walker.blockPosition();
        BlockPos below = feet.below();
        AABB box = walker.getBoundingBox().inflate(0.05);
        for (BeachBoyHookEntity hook : walker.level().getEntitiesOfClass(BeachBoyHookEntity.class,
                walker.getBoundingBox().inflate(1.6), BeachBoyHookEntity::isTrapArmed)) {
            if (hook.getOwner() != walker) {
                Optional<BlockPos> trap = hook.getTrapBlockPos();
                if (trap.isPresent()) {
                    BlockPos pos = trap.get();
                    if (pos.equals(feet) || pos.equals(below) || box.intersects(new AABB(pos))) {
                        hook.triggerTrap(walker);
                    }
                }
            }
        }
    }

    public static boolean tryTriggerBlockTrap(@Nullable Entity triggerer, @Nullable BlockPos pos) {
        if (triggerer == null || triggerer.level().isClientSide() || pos == null) {
            return false;
        }
        boolean triggered = false;
        for (BeachBoyHookEntity hook : triggerer.level().getEntitiesOfClass(BeachBoyHookEntity.class,
                new AABB(pos).inflate(1.25), BeachBoyHookEntity::isTrapArmed)) {
            if (hook.getOwner() != triggerer) {
                Optional<BlockPos> trap = hook.getTrapBlockPos();
                if (trap.isPresent() && trap.get().equals(pos)) {
                    triggered |= hook.triggerTrap(triggerer);
                }
            }
        }
        return triggered;
    }

    public static boolean tryTriggerEntityTrap(@Nullable Entity host, @Nullable Entity triggerer) {
        if (host == null || triggerer == null || host.level().isClientSide()
                || triggerer == host || triggerer == host.getVehicle()) {
            return false;
        }
        boolean triggered = false;
        for (BeachBoyHookEntity hook : host.level().getEntitiesOfClass(BeachBoyHookEntity.class,
                host.getBoundingBox().inflate(2.0), BeachBoyHookEntity::isTrapArmed)) {
            if (hook.getOwner() != triggerer && hook.getHookedEntity() == host) {
                triggered |= hook.triggerTrap(triggerer);
            }
        }
        return triggered;
    }

    public static List<Vec3> buildWhipArc(Vec3 origin, Vec3 look, boolean vertical, boolean leftToRight,
            boolean upward, float progress, double range) {
        List<Vec3> points = new ArrayList<>();
        Vec3 dir = look.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
        Vec3 right = dir.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 axis = vertical ? right : new Vec3(0.0, 1.0, 0.0);
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        float swingT;
        float extend;
        if (clamped < WHIP_OUT_RATIO) {
            swingT = easeOutCubic(clamped / WHIP_OUT_RATIO);
            extend = swingT;
        }
        else {
            swingT = 1.0F;
            extend = 1.0F - easeInCubic((clamped - WHIP_OUT_RATIO) / (1.0F - WHIP_OUT_RATIO));
        }
        float startAngle;
        float endAngle;
        if (vertical) {
            startAngle = upward ? -52.0F : 108.0F;
            endAngle = upward ? 108.0F : -52.0F;
        }
        else {
            float sign = leftToRight ? 1.0F : -1.0F;
            startAngle = 82.0F * sign;
            endAngle = -82.0F * sign;
        }
        float angle = Mth.lerp(swingT, startAngle, endAngle);
        Vec3 lash = rotateAround(dir, axis, angle);
        Vec3 ahead = rotateAround(dir, axis, angle + (vertical ? (upward ? 8.0F : -8.0F) : (leftToRight ? -8.0F : 8.0F)));
        Vec3 swingVel = ahead.subtract(lash);
        swingVel = swingVel.lengthSqr() > 1.0E-8 ? swingVel.normalize() : Vec3.ZERO;
        double trail = 0.62 * extend * (clamped < WHIP_OUT_RATIO ? 1.0 : 0.28);
        int segments = 20;
        double length = range * Math.max(extend, 0.02);
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            Vec3 along = origin.add(lash.scale(length * t));
            double lag = Math.sin(t * Math.PI) * trail;
            points.add(along.subtract(swingVel.scale(lag)));
        }
        return points;
    }

    @Nullable
    public static LivingEntity hookedLiving(@Nullable BeachBoyHookEntity hook) {
        return hook != null && hook.getHookedEntity() instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** 1.16 standDamage: with the Stand out its own source (as the core's Stand punch), else one of the user. */
    private static DamageSource standSource(LivingEntity user, @Nullable StandPower power,
            @Nullable BeachBoyStandEntity stand, @Nullable ResourceKey<DamageType> armorPiercingType) {
        Entity attacker = stand != null ? stand : user;
        ResourceKey<DamageType> type = armorPiercingType != null ? armorPiercingType : ModDamageTypes.STAND_ATTACK;
        DamageSource source = DamageUtil.make(user.level(), type, attacker, attacker);
        if (power != null && power.hasPower() && source instanceof DamageSourceModified modified) {
            modified.jojo_ripples$setStandPower(power);
        }
        return source;
    }

    private static void hurtStand(LivingEntity user, @Nullable StandPower power, @Nullable BeachBoyStandEntity stand,
            LivingEntity target, float amount, @Nullable ResourceKey<DamageType> armorPiercingType) {
        DamageSource source = standSource(user, power, stand, armorPiercingType);
        if (stand != null) {
            EntityActionInstance.standEntityAttack(stand, target, source, amount);
        }
        else {
            DamageUtil.hurtThroughInvulTicks(target, source, amount);
        }
    }

    private static float ripDamage(@Nullable StandPower power, @Nullable BeachBoyStandEntity stand) {
        float attack = stand != null ? (float) stand.getAttackDamage() : BeachBoyStandUtil.standPowerStat(power);
        return Math.max(2.0F, attack * 0.42F);
    }

    public static float whipDamage(@Nullable StandPower power, @Nullable BeachBoyStandEntity stand) {
        return stand != null ? (float) Math.max(4.0, stand.getAttackDamage())
                : Math.max(4.0F, BeachBoyStandUtil.standPowerStat(power) * 0.65F);
    }

    private static boolean isWhipEnemy(LivingEntity user, Entity entity) {
        if (!(entity instanceof LivingEntity target) || !entity.isAlive() || entity == user
                || target instanceof ArmorStand || isOwnStand(target, user)) {
            return false;
        }
        if (user instanceof Player player && target instanceof Player other && !player.canHarmPlayer(other)) {
            return false;
        }
        return user.canAttack(target);
    }

    private static boolean isInWhipArc(Vec3 origin, Vec3 look, LivingEntity target, boolean vertical, double range) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(origin);
        double distSq = to.lengthSqr();
        if (distSq < 1.0E-6 || distSq > range * range) {
            return false;
        }
        Vec3 lookH = new Vec3(look.x, 0.0, look.z);
        lookH = lookH.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : lookH.normalize();
        Vec3 toH = new Vec3(to.x, 0.0, to.z);
        if (vertical) {
            return (toH.lengthSqr() <= 1.0E-8 || lookH.dot(toH.normalize()) >= 0.72)
                    && look.normalize().dot(to.normalize()) > -0.05;
        }
        if (Math.abs(to.y) > 3.1) {
            return false;
        }
        return toH.lengthSqr() < 1.0E-8 || lookH.dot(toH.normalize()) > 0.12;
    }

    public static void tryResolveAutoRip(LivingEntity user, LivingEntity target) {
        if (user != null && target != null && hasResolve(user)) {
            performRip(user.level(), user, StandPower.get(user), false);
        }
    }

    public static void tryApplyStrikeEffects(@Nullable LivingEntity user, @Nullable LivingEntity target) {
        if (user == null || target == null || !target.isAlive()) {
            return;
        }
        boolean resolve = hasResolve(user);
        if (user.getRandom().nextFloat() < (resolve ? 0.45F : 0.2F)) {
            applyBleed(target);
        }
        if (resolve && user.getRandom().nextFloat() < 0.35F) {
            List<Holder<MobEffect>> pool = List.of(MobEffects.BLINDNESS, MobEffects.MOVEMENT_SLOWDOWN,
                    MobEffects.WEAKNESS, MobEffects.DIG_SLOWDOWN);
            target.addEffect(new MobEffectInstance(pool.get(user.getRandom().nextInt(pool.size())), 80, 0, false, true, true));
        }
    }

    public static boolean hasResolve(@Nullable LivingEntity entity) {
        return entity != null && entity.hasEffect(ModStatusEffects.RESOLVE);
    }

    private static void applyBleed(LivingEntity target) {
        target.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, BLEED_TICKS, 0, false, true, true));
    }

    private static boolean isOwnStand(Entity entity, LivingEntity user) {
        return entity instanceof StandEntity stand && user.equals(stand.getUser());
    }

    private static void spawnBloodSplash(LivingEntity target, Vec3 flyDir, int count, float speed) {
        Vec3 source = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        Vec3 dest = source.add(flyDir.normalize().scale(1.8));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                new BloodParticlesPacket(source, dest, speed, count, target.getId()));
    }

    private static float easeOutCubic(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    private static float easeInCubic(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * c;
    }

    private static Vec3 rotateAround(Vec3 vec, Vec3 axis, float degrees) {
        Vec3 k = axis.normalize();
        double rad = Math.toRadians(degrees);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        Vec3 cross = k.cross(vec);
        double dot = k.dot(vec);
        return vec.scale(c).add(cross.scale(s)).add(k.scale(dot * (1.0 - c)));
    }
}
