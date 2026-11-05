package rotp.addons.novemberrain.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The remake's light_rain (Rain): holdType, staminaCostTick(5), cooldown(40), standRecoveryTicks(20), RAIN_ANIM. Its
 * standPerformDuration(70) never ran, as a holdType task performs from the press for as long as it is held.
 */
public final class LightRainAbility extends NovemberRainStandAbility {
    public static final double RAIN_RADIUS = 5.0D;
    public static final float DAMAGE = 1.5F;
    public static final float STAMINA_COST_TICK = 5.0F;
    public static final int COOLDOWN = 40;
    public static final int RECOVERY_TICKS = 20;
    public static final int PARTICLES_PER_TICK = 20;
    public static final int SLOW_DURATION_TICKS = 5;
    public static final int SLOW_AMPLIFIER = 4;
    public static final int SHELTER_CHECK_BLOCKS = 3;

    public LightRainAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, new Spec()
                .holdType()
                .staminaCostTick(STAMINA_COST_TICK)
                .cooldown(COOLDOWN)
                .standRecoveryTicks(RECOVERY_TICKS)
                .pose(RAIN_ANIM));
    }

    @Override
    protected void standTickPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity user) {
        if (level.isClientSide()) {
            for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                double x = stand.getX() + (level.random.nextDouble() - 0.5D) * RAIN_RADIUS * 2.0D;
                double y = stand.getY() + 3.0D;
                double z = stand.getZ() + (level.random.nextDouble() - 0.5D) * RAIN_RADIUS * 2.0D;
                level.addParticle(ParticleTypes.RAIN, x, y, z, 0.0D, -0.5D, 0.0D);
            }
            return;
        }
        AABB rainArea = new AABB(
                stand.getX() - RAIN_RADIUS, stand.getY() - 1.0D, stand.getZ() - RAIN_RADIUS,
                stand.getX() + RAIN_RADIUS, stand.getY() + 4.0D, stand.getZ() + RAIN_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, rainArea)) {
            if (entity == stand || entity == user || isSheltered(level, entity)) {
                continue;
            }
            // the remake's DamageSource.ANVIL
            entity.hurt(level.damageSources().source(DamageTypes.FALLING_ANVIL), DAMAGE);
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION_TICKS, SLOW_AMPLIFIER, false, false, false));
            }
        }
    }

    /** A solid block in the three blocks above the entity's head block keeps the rain off it. */
    public static boolean isSheltered(Level level, Entity entity) {
        BlockPos headPos = entity.blockPosition().above();
        for (int i = 0; i < SHELTER_CHECK_BLOCKS; i++) {
            if (level.getBlockState(headPos.above(i)).isSolid()) {
                return true;
            }
        }
        return false;
    }
}
