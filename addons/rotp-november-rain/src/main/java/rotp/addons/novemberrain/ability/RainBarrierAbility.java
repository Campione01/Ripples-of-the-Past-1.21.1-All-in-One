package rotp.addons.novemberrain.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The remake's rain_barrier (RainBarrier, "Large Rain Shield"), the Shift variation of rain_shield: staminaCostTick(5),
 * holdType, resolveLevelToUnlock(1), standRecoveryTicks(20), RAIN_ANIM. A ring of rain between 3 and 6 blocks from the
 * Stand removes projectiles and pushes creatures away.
 */
public final class RainBarrierAbility extends NovemberRainStandAbility {
    public static final double SAFE_RADIUS = 3.0D;
    public static final double REPEL_RADIUS = 6.0D;
    public static final float KNOCKBACK = 0.5F;
    public static final float STAMINA_COST_TICK = 5.0F;
    public static final int RESOLVE_LEVEL = 1;
    public static final int RECOVERY_TICKS = 20;

    public RainBarrierAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, new Spec()
                .staminaCostTick(STAMINA_COST_TICK)
                .holdType()
                .resolveLevelToUnlock(RESOLVE_LEVEL)
                .standRecoveryTicks(RECOVERY_TICKS)
                .pose(RAIN_ANIM));
    }

    @Override
    protected void standTickPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity user) {
        double x = stand.getX();
        double y = stand.getY();
        double z = stand.getZ();
        if (level.isClientSide()) {
            RainParticles.ring(level, x, y, z, SAFE_RADIUS);
            return;
        }
        AABB checkArea = new AABB(
                x - REPEL_RADIUS, y - 1.0D, z - REPEL_RADIUS, x + REPEL_RADIUS, y + 5.0D, z + REPEL_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, checkArea)) {
            if (entity == stand || entity == user) {
                continue;
            }
            double distanceSq = entity.distanceToSqr(x, y, z);
            if (distanceSq > SAFE_RADIUS * SAFE_RADIUS && distanceSq <= REPEL_RADIUS * REPEL_RADIUS) {
                if (entity instanceof Projectile) {
                    entity.discard();
                }
                else if (entity instanceof LivingEntity living) {
                    living.knockback(KNOCKBACK, -(living.getX() - x), -(living.getZ() - z));
                }
            }
        }
    }
}
