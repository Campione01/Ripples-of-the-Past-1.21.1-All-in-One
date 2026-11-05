package rotp.addons.novemberrain.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The remake's heavy_rain (HeavyRain), the Shift variation of light_rain: staminaCostTick(15), cooldown(400),
 * resolveLevelToUnlock(2), holdType, standRecoveryTicks(20), RAIN_ANIM (its standPerformDuration(50) never ran).
 */
public final class HeavyRainAbility extends NovemberRainStandAbility {
    public static final double RAIN_RADIUS = 2.0D;
    public static final float DAMAGE = 10.0F;
    public static final float STAMINA_COST_TICK = 15.0F;
    public static final int COOLDOWN = 400;
    public static final int RESOLVE_LEVEL = 2;
    public static final int RECOVERY_TICKS = 20;

    public HeavyRainAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, new Spec()
                .staminaCostTick(STAMINA_COST_TICK)
                .cooldown(COOLDOWN)
                .resolveLevelToUnlock(RESOLVE_LEVEL)
                .holdType()
                .standRecoveryTicks(RECOVERY_TICKS)
                .pose(RAIN_ANIM));
    }

    @Override
    protected void standTickPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity user) {
        if (level.isClientSide()) {
            RainParticles.ring(level, stand.getX(), stand.getY(), stand.getZ(), RAIN_RADIUS);
            return;
        }
        AABB rainArea = new AABB(
                stand.getX() - RAIN_RADIUS, stand.getY() - 1.0D, stand.getZ() - RAIN_RADIUS,
                stand.getX() + RAIN_RADIUS, stand.getY() + 4.0D, stand.getZ() + RAIN_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, rainArea)) {
            if (entity != stand && entity != user) {
                entity.hurt(level.damageSources().drown(), DAMAGE);
            }
        }
    }
}
