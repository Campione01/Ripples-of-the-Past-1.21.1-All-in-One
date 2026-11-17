package rotp.addons.darkbluemoon.ability;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 DarkBlueMoonChopAction ("chop" pose, 20 ticks, 50 stamina, cooldown 400): a chop sound at once, then after the
 * configured delay (10 ticks) one hit on the entity the Stand aims at, else on the first living entity in the cone in
 * front within the radius: 30 damage within 3 blocks in water (knocked up 0.5 and back 2.5, Vulnerability and Slowness
 * for 200 ticks), 10 within 1.5 on land (0.2 up, 0.5 back). With no hit it cuts the blocks in front instead.
 */
public class DbmChopAbility extends DbmStandAction {
    public DbmChopAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "chop", 20, 50.0F, 400, TargetRequirement.NONE);
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        action.flag = false;
        // 1.16 set the aimed target as the task's, so the Stand faced it
        Entity aimed = DbmTargets.aimedEntity(stand, user);
        if (aimed != null) {
            action.keepStandAimedAtTarget(new ActionTarget(aimed));
        }
        boolean inWater = DbmWater.isInWater(user);
        stand.playSound(inWater ? DbmWater.randomWaterUseSound(stand.getRandom())
                : DbmWater.randomLandUseSound(stand.getRandom()), 1.0F, 1.0F);
    }

    @Override
    protected void standTickPerform(DbmAction action, StandEntity stand, LivingEntity user, int tick) {
        if (!action.flag && tick >= DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.CHOP_DAMAGE_DELAY_TICKS)) {
            action.flag = true;
            dealChopDamage(stand.level(), stand, user);
        }
    }

    // public static for the game test of the block rule
    public static void dealChopDamage(Level level, StandEntity stand, LivingEntity user) {
        boolean inWater = DbmWater.isInWater(user);
        float damage = (float) DarkBlueMoonConfig.getDouble(inWater
                ? DarkBlueMoonConfig.CHOP_WATER_DAMAGE : DarkBlueMoonConfig.CHOP_LAND_DAMAGE);
        double radius = DarkBlueMoonConfig.getDouble(inWater
                ? DarkBlueMoonConfig.CHOP_WATER_RADIUS : DarkBlueMoonConfig.CHOP_LAND_RADIUS);
        double knockback = DarkBlueMoonConfig.getDouble(inWater
                ? DarkBlueMoonConfig.CHOP_WATER_KNOCKBACK : DarkBlueMoonConfig.CHOP_LAND_KNOCKBACK);
        Vec3 look = user.getLookAngle();
        Vec3 start = stand.position().add(0.0, stand.getBbHeight() * 0.5, 0.0);
        boolean hitAny = false;

        // 1.16 aimWithThisOrUser: what the Stand or its user aims at
        Entity aimedEntity = DbmTargets.aimedEntity(stand, user);
        if (aimedEntity instanceof LivingEntity target && target != user && target != stand && target.isAlive()) {
            Vec3 toTarget = target.position().subtract(start);
            if (toTarget.length() <= radius && toTarget.normalize().dot(look) >= 0.3) {
                hitAny = hitTarget(target, user, look, damage, knockback, inWater);
            }
        }
        if (!hitAny) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, stand.getBoundingBox().inflate(radius),
                    e -> e != user && e != stand && e.isAlive())) {
                Vec3 toTarget = target.position().subtract(start);
                if (toTarget.length() <= radius && toTarget.normalize().dot(look) >= 0.3) {
                    hitAny = hitTarget(target, user, look, damage, knockback, inWater);
                    break;
                }
            }
        }
        if (!hitAny && DarkBlueMoonConfig.getBool(DarkBlueMoonConfig.CHOP_BREAK_BLOCKS)
                && JojoModUtil.breakingBlocksEnabled(level, stand)
                && breakBlocksInFront(level, stand, look, radius)) {
            hitAny = true;
        }
        if (hitAny) {
            stand.playSound(inWater ? DarkBlueMoonAddon.CHOP_HIT_WATER.get() : DarkBlueMoonAddon.CHOP_HIT_LAND.get(),
                    1.0F, 1.0F);
        }
    }

    private static boolean hitTarget(LivingEntity target, LivingEntity user, Vec3 look, float damage, double knockback,
            boolean inWater) {
        DamageSource source = user.damageSources().mobAttack(user);
        boolean hurt = target.hurt(source, damage);
        if (hurt) {
            target.setDeltaMovement(target.getDeltaMovement().add(look.x * knockback, inWater ? 0.5 : 0.2, look.z * knockback));
            target.hurtMarked = true;
            if (inWater) {
                target.addEffect(new MobEffectInstance(DarkBlueMoonAddon.VULNERABILITY, 200, 0, false, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0, false, false, false));
            }
        }
        return hurt;
    }

    private static boolean breakBlocksInFront(Level level, StandEntity stand, Vec3 look, double radius) {
        boolean broke = false;
        BlockPos center = stand.blockPosition();
        int r = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.CHOP_BREAK_BLOCK_RADIUS);
        int maxY = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.CHOP_BLOCK_BREAK_MAX_Y);
        double dotThreshold = DarkBlueMoonConfig.getDouble(DarkBlueMoonConfig.CHOP_BLOCK_BREAK_DOT);
        boolean breakHard = DarkBlueMoonConfig.getBool(DarkBlueMoonConfig.CHOP_BREAK_HARD_BLOCKS);
        for (int x = -r; x <= r; x++) {
            for (int y = -maxY; y <= maxY; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !breakHard && state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }
                    Vec3 toBlock = new Vec3(pos.getX() + 0.5 - stand.getX(), pos.getY() + 0.5 - stand.getY(),
                            pos.getZ() + 0.5 - stand.getZ());
                    if (toBlock.length() <= radius && toBlock.normalize().dot(look) >= dotThreshold) {
                        level.destroyBlock(pos, false);
                        broke = true;
                    }
                }
            }
        }
        return broke;
    }
}
