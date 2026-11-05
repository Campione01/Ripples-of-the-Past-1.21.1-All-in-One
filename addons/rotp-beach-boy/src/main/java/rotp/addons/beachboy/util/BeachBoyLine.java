package rotp.addons.beachboy.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 BeachBoyLine: the line between the rod tip and the hook. With thorns on (always, as 1.16 never turned it
 * off), a hit on the line reaches the hooked creature instead: melee, projectiles, attacks of any kind.
 */
public final class BeachBoyLine {
    public static final float MIN_LENGTH = 3.0F;
    public static final float DEFAULT_LENGTH = 16.0F;
    public static final float ABSOLUTE_MAX_LENGTH = 256.0F;
    public static final float LENGTH_STEP = 1.5F;
    public static final float DEFAULT_THICKNESS = 0.12F;
    public static final float DEFAULT_THORNS_MULTIPLIER = 1.0F;
    private static final ThreadLocal<Boolean> CONDUCTING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Entity> PASSTHROUGH_PROJECTILE = new ThreadLocal<>();
    private final BeachBoyHookEntity hook;

    private BeachBoyLine(BeachBoyHookEntity hook) {
        this.hook = hook;
    }

    public static float standRangeCap(@Nullable BeachBoyStandEntity stand) {
        if (stand == null) {
            return ABSOLUTE_MAX_LENGTH;
        }
        float range = (float) Math.max(3.0, stand.getMaxRange());
        return range * BeachBoyStandUtil.getRangeMultiplier(stand.getUserPower());
    }

    public static float clampLength(float length, @Nullable BeachBoyStandEntity stand) {
        return Mth.clamp(length, MIN_LENGTH, standRangeCap(stand));
    }

    public static Optional<BeachBoyLine> of(@Nullable BeachBoyHookEntity hook) {
        return hook != null && hook.isAlive() ? Optional.of(new BeachBoyLine(hook)) : Optional.empty();
    }

    public static Optional<BeachBoyLine> ofOwner(LivingEntity owner) {
        return of(BeachBoyStandUtil.findHook(owner));
    }

    public static List<BeachBoyLine> allIn(@Nullable Level level, AABB box) {
        List<BeachBoyLine> lines = new ArrayList<>();
        if (level == null) {
            return lines;
        }
        for (BeachBoyHookEntity hook : level.getEntitiesOfClass(BeachBoyHookEntity.class, box, Entity::isAlive)) {
            if (!hook.isTrapArmed()) {
                of(hook).ifPresent(lines::add);
            }
        }
        return lines;
    }

    public BeachBoyHookEntity getHook() {
        return hook;
    }

    @Nullable
    public Entity getOwner() {
        return hook.getOwner();
    }

    public Vec3 getStart(float partialTicks) {
        return hook.getLineStart(partialTicks);
    }

    public List<Vec3> getPath(float partialTicks) {
        return BeachBoyLinePath.buildPath(hook.getLineAnchors(partialTicks), hook.hasLineBlockCollision());
    }

    public float getCurrentLength() {
        return (float) BeachBoyLinePath.polylineLength(getPath(1.0F));
    }

    public boolean isThornsEnabled() {
        return hook.isLineThornsEnabled();
    }

    public float getThornsMultiplier() {
        return hook.getLineThornsMultiplier();
    }

    public float getCombatThickness() {
        return Math.max(0.55F, hook.getLineThickness() * 4.0F);
    }

    public boolean intersectsRay(Vec3 start, Vec3 end, double minDistFromStart) {
        return intersectsRay(start, end, minDistFromStart, 0.0);
    }

    public boolean intersectsRay(Vec3 start, Vec3 end, double minDistFromStart, double extraThickness) {
        double thickness = getCombatThickness() + extraThickness;
        double limit = thickness * thickness;
        double minSqr = minDistFromStart * minDistFromStart;
        Vec3 origin = getStart(1.0F);
        List<Vec3> path = getPath(1.0F);
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1);
            Vec3 b = path.get(i);
            if (distanceToSegmentSqr(start, end, a, b) <= limit) {
                Vec3 hit = closestPointOnSecondSegment(start, end, a, b);
                if (!(minSqr > 0.0) || !(hit.distanceToSqr(origin) < minSqr)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean intersectsEntity(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        AABB box = entity.getBoundingBox().inflate(getCombatThickness());
        List<Vec3> path = getPath(1.0F);
        for (int i = 1; i < path.size(); i++) {
            Vec3 start = path.get(i - 1);
            Vec3 end = path.get(i);
            if (box.clip(start, end).isPresent() || box.contains(start) || box.contains(end)) {
                return true;
            }
        }
        return false;
    }

    public boolean isOwnedBy(@Nullable Entity entity) {
        Entity owner = getOwner();
        return owner != null && entity != null && (owner == entity || owner.is(entity));
    }

    public boolean isFromOwnerSide(@Nullable Entity attacker) {
        if (attacker == null) {
            return false;
        }
        if (isOwnedBy(attacker)) {
            return true;
        }
        if (attacker instanceof StandEntity stand) {
            return isOwnedBy(stand.getUser());
        }
        return attacker instanceof Projectile projectile && isFromOwnerSide(projectile.getOwner());
    }

    public static boolean isConducting() {
        return Boolean.TRUE.equals(CONDUCTING.get());
    }

    public static boolean shouldSkipProjectileRemove(@Nullable Entity entity) {
        return entity != null && entity == PASSTHROUGH_PROJECTILE.get();
    }

    @Nullable
    public LivingEntity getHookedLiving() {
        return hook.getHookedEntity() instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    public boolean crossesProjectilePath(@Nullable Projectile projectile) {
        if (projectile == null) {
            return false;
        }
        Vec3 prev = new Vec3(projectile.xo, projectile.yo, projectile.zo);
        Vec3 now = projectile.position();
        return prev.distanceToSqr(now) > 1.0E-8 && intersectsRay(prev, now, 0.0, 0.15) || intersectsEntity(projectile);
    }

    public boolean crossesImpact(@Nullable Entity projectile, @Nullable HitResult result) {
        if (projectile == null || result == null) {
            return false;
        }
        Vec3 prev = new Vec3(projectile.xo, projectile.yo, projectile.zo);
        return intersectsRay(prev, result.getLocation(), 0.0, 0.15);
    }

    /** 1.16 getMeleeReach: the Stand's aim distance, else the player's block reach less 1.5 (3 and 5 in 1.16). */
    public static double getMeleeReach(@Nullable Entity attacker) {
        if (attacker instanceof StandEntity stand) {
            return Math.max(1.0, stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
        }
        if (attacker instanceof LivingEntity living) {
            if (living.getAttributes().hasAttribute(Attributes.BLOCK_INTERACTION_RANGE)) {
                double blockReach = living.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
                return Math.max(3.0, blockReach - 1.5) + 0.35;
            }
            return living instanceof Player player && player.getAbilities().instabuild ? 5.35 : 3.35;
        }
        return 3.0;
    }

    @Nullable
    public static ActionTarget redirectHamonAttack(@Nullable LivingEntity attacker, @Nullable Entity currentTarget) {
        if (attacker == null || attacker.level() == null || isConducting()) {
            return null;
        }
        double reach = getMeleeReach(attacker);
        BeachBoyLine hit = findHitByAim(attacker, reach);
        if (hit == null || !hit.isThornsEnabled()) {
            return null;
        }
        LivingEntity hooked = hit.getHookedLiving();
        if (hooked == null || hooked == attacker) {
            return null;
        }
        if (currentTarget == hooked) {
            return new ActionTarget(hooked);
        }
        return currentTarget != null && currentTarget != attacker && currentTarget.isAlive()
                && attacker.distanceTo(currentTarget) <= reach + 0.5 ? null : new ActionTarget(hooked);
    }

    public static boolean hasHamonPower(@Nullable LivingEntity entity) {
        return entity != null && PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON).isPresent();
    }

    public static boolean isHoldingHamonAction(@Nullable LivingEntity entity) {
        if (entity == null || !hasHamonPower(entity)) {
            return false;
        }
        EntityActionInstance held = AbilityInput.getHeldAction(entity, PowerClass.PLAYER_POWER);
        return held != null && !held.isOver();
    }

    @Nullable
    public static BeachBoyLine findHitByAim(@Nullable Entity aimer, double reach) {
        if (aimer == null || aimer.level() == null) {
            return null;
        }
        Vec3 start = aimer.getEyePosition(1.0F);
        Vec3 end = start.add(aimer.getLookAngle().scale(Math.max(reach, 1.0)));
        return findHitByRay(aimer.level(), start, end, aimer, 1.75, 0.35);
    }

    @Nullable
    public static BeachBoyLine findHitByRay(Level level, Vec3 start, Vec3 end, @Nullable Entity attacker,
            double minDistFromStart, double extraThickness) {
        AABB box = new AABB(start, end).inflate(2.0);
        for (BeachBoyLine line : allIn(level, box)) {
            if (line.isThornsEnabled() && line.getHookedLiving() != null && line.getHookedLiving() != attacker) {
                double minDist = line.isFromOwnerSide(attacker) ? minDistFromStart : 0.0;
                if (line.intersectsRay(start, end, minDist, extraThickness)) {
                    return line;
                }
            }
        }
        return null;
    }

    /** 1.16 IndirectStandEntityDamageSource("beach_boy_crush", hook, owner, power).setDamageBypassesArmor(). */
    public static DamageSource createCrushDamage(BeachBoyHookEntity hook) {
        Entity owner = hook.getOwner();
        DamageSource source = DamageUtil.make(hook.level(), BeachBoyAddon.CRUSH_DAMAGE_TYPE, hook,
                owner != null ? owner : hook);
        if (owner instanceof LivingEntity living && source instanceof DamageSourceModified modified) {
            StandPower power = StandPower.get(living);
            if (power != null && power.hasPower()) {
                modified.jojo_ripples$setStandPower(power);
            }
        }
        return source;
    }

    public boolean tryConductAttack(@Nullable DamageSource source, float amount, @Nullable Entity projectile) {
        if (!isThornsEnabled() || source == null || isConducting()
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        LivingEntity hooked = getHookedLiving();
        if (hooked == null) {
            return false;
        }
        Entity attacker = source.getEntity();
        Entity direct = projectile != null ? projectile : source.getDirectEntity();
        if (attacker == hook || attacker == hooked || direct == hooked || !hook.markConductedThisTick()) {
            return false;
        }
        LivingEntity sourceLiving = attacker instanceof LivingEntity living ? living : null;
        Entity projectileHit = projectile;
        if (projectile == null && direct instanceof Projectile) {
            projectileHit = direct;
        }
        CONDUCTING.set(Boolean.TRUE);
        try {
            applyConductedHit(sourceLiving, hooked, source, amount * getThornsMultiplier(), projectileHit);
            return true;
        }
        finally {
            CONDUCTING.set(Boolean.FALSE);
        }
    }

    public boolean tryConductMelee(@Nullable LivingEntity attacker) {
        if (!isThornsEnabled() || attacker == null || isConducting()) {
            return false;
        }
        LivingEntity hooked = getHookedLiving();
        if (hooked == null || hooked == attacker || !hook.markConductedThisTick()) {
            return false;
        }
        CONDUCTING.set(Boolean.TRUE);
        try {
            float amount = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
            DamageSource source;
            if (attacker instanceof Player player) {
                amount *= player.getAttackStrengthScale(0.5F);
                source = player.damageSources().playerAttack(player);
                player.resetAttackStrengthTicker();
            }
            else {
                source = attacker.damageSources().mobAttack(attacker);
            }
            applyConductedHit(attacker, hooked, source, Math.max(0.1F, amount * getThornsMultiplier()), null);
            if (hasHamonPower(attacker) && !isHoldingHamonAction(attacker)) {
                HamonAbilityHelpers.hamonHurt(hooked, attacker, Math.max(1.0F, amount * getThornsMultiplier()));
            }
            return true;
        }
        finally {
            CONDUCTING.set(Boolean.FALSE);
        }
    }

    public boolean tryConductProjectile(@Nullable Projectile projectile) {
        if (projectile == null || !projectile.isAlive() || projectile instanceof BeachBoyHookEntity
                || !isThornsEnabled() || isConducting()) {
            return false;
        }
        LivingEntity hooked = getHookedLiving();
        if (hooked == null || projectile.getOwner() == hooked || !hook.markProjectileConducted(projectile.getId())) {
            return false;
        }
        CONDUCTING.set(Boolean.TRUE);
        PASSTHROUGH_PROJECTILE.set(projectile);
        try {
            applyProjectileHit(projectile, hooked);
            return true;
        }
        finally {
            PASSTHROUGH_PROJECTILE.set(null);
            CONDUCTING.set(Boolean.FALSE);
        }
    }

    // 1.16 ran a RotP projectile's own entity hit through an invoker mixin; here every projectile takes the generic
    // damage of its kind, as 1.16 did for non-RotP ones
    private void applyProjectileHit(Projectile projectile, LivingEntity target) {
        Entity shooter = projectile.getOwner();
        DamageSource source;
        float damage;
        var sources = projectile.damageSources();
        if (projectile instanceof AbstractArrow arrow) {
            source = sources.arrow(arrow, shooter);
            damage = (float) Math.max(1.0, arrow.getBaseDamage() * arrow.getDeltaMovement().length());
        }
        else if (projectile instanceof SmallFireball fireball) {
            source = sources.fireball(fireball, shooter);
            damage = 5.0F;
        }
        else if (projectile instanceof LargeFireball fireball) {
            source = sources.fireball(fireball, shooter);
            damage = 6.0F;
        }
        else if (projectile instanceof AbstractHurtingProjectile) {
            source = sources.indirectMagic(projectile, shooter);
            damage = (float) Math.max(4.0, projectile.getDeltaMovement().length() * 6.0);
        }
        else if (projectile instanceof ThrownPotion) {
            source = sources.thrown(projectile, shooter);
            damage = 0.0F;
        }
        else {
            source = sources.thrown(projectile, shooter);
            damage = (float) Math.max(1.0, projectile.getDeltaMovement().length() * 2.0);
        }
        LivingEntity sourceLiving = shooter instanceof LivingEntity living ? living : null;
        if (damage > 0.0F) {
            applyConductedHit(sourceLiving, target, source, damage * getThornsMultiplier(), projectile);
        }
        else {
            applyProjectileExtras(projectile, target, source);
        }
    }

    private static void applyConductedHit(@Nullable LivingEntity attacker, LivingEntity target, DamageSource source,
            float amount, @Nullable Entity projectile) {
        target.hurt(source, amount);
        if (projectile != null) {
            applyProjectileExtras(projectile, target, source);
        }
        else if (attacker != null && attacker != target) {
            copyMeleeExtras(attacker, target, source);
        }
    }

    // 1.16 copyMeleeExtras: fire aspect, knockback (sprinting adds one), arthropod and thorns enchantments
    private static void copyMeleeExtras(LivingEntity attacker, LivingEntity target, DamageSource source) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        float knockback = EnchantmentHelper.modifyKnockback(level, attacker.getMainHandItem(), target, source, 0.0F);
        if (attacker.isSprinting()) {
            knockback++;
        }
        if (knockback > 0) {
            float yaw = attacker.getYRot() * Mth.DEG_TO_RAD;
            target.knockback(knockback * 0.5F, Mth.sin(yaw), -Mth.cos(yaw));
            attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(0.6, 1.0, 0.6));
        }
        EnchantmentHelper.doPostAttackEffects(level, target, source);
    }

    private static void applyProjectileExtras(Entity projectile, LivingEntity target, DamageSource source) {
        if (projectile.isOnFire()) {
            target.igniteForSeconds(5.0F);
        }
        ItemStack potionItem = ItemStack.EMPTY;
        if (projectile instanceof ThrownPotion potion) {
            potionItem = potion.getItem();
        }
        else if (projectile instanceof AbstractArrow arrow) {
            potionItem = arrow.getPickupItemStackOrigin();
        }
        PotionContents contents = potionItem.get(DataComponents.POTION_CONTENTS);
        if (contents != null) {
            for (MobEffectInstance effect : contents.getAllEffects()) {
                target.addEffect(new MobEffectInstance(effect));
            }
        }
        if (projectile instanceof WitherSkull) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
        }
        if (projectile instanceof AbstractArrow && target.level() instanceof ServerLevel level) {
            EnchantmentHelper.doPostAttackEffects(level, target, source);
        }
    }

    public static boolean tryReflectAttack(BeachBoyHookEntity hook, DamageSource source, float amount) {
        return of(hook).map(line -> line.tryConductAttack(source, amount, null)).orElse(false);
    }

    public static boolean tryConductMelee(BeachBoyHookEntity hook, LivingEntity attacker) {
        return of(hook).map(line -> line.tryConductMelee(attacker)).orElse(false);
    }

    public static double distanceToSegmentSqr(Vec3 pointA, Vec3 pointB, Vec3 segStart, Vec3 segEnd) {
        Vec3 hit = closestPointOnSecondSegment(pointA, pointB, segStart, segEnd);
        Vec3 u = pointB.subtract(pointA);
        Vec3 v = segEnd.subtract(segStart);
        Vec3 w = pointA.subtract(segStart);
        double a = u.dot(u);
        double b = u.dot(v);
        double c = v.dot(v);
        double d = u.dot(w);
        double e = v.dot(w);
        double denom = a * c - b * b;
        double sc = denom <= 1.0E-8 ? 0.0 : (b * e - c * d) / denom;
        sc = Math.max(0.0, Math.min(1.0, sc));
        Vec3 p = pointA.add(u.scale(sc));
        return p.distanceToSqr(hit);
    }

    private static Vec3 closestPointOnSecondSegment(Vec3 pointA, Vec3 pointB, Vec3 segStart, Vec3 segEnd) {
        Vec3 u = pointB.subtract(pointA);
        Vec3 v = segEnd.subtract(segStart);
        Vec3 w = pointA.subtract(segStart);
        double a = u.dot(u);
        double b = u.dot(v);
        double c = v.dot(v);
        double d = u.dot(w);
        double e = v.dot(w);
        double denom = a * c - b * b;
        double tc = denom <= 1.0E-8 ? e / Math.max(c, 1.0E-8) : (a * e - b * d) / denom;
        tc = Math.max(0.0, Math.min(1.0, tc));
        return segStart.add(v.scale(tc));
    }
}
