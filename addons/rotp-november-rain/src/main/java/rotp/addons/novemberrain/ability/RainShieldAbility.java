package rotp.addons.novemberrain.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The remake's rain_shield (RainShield): holdType, staminaCostTick(2), cooldown(0), standRecoveryTicks(20), RAIN_ANIM.
 * A curtain of rain 1.5 blocks in front of the Stand removes projectiles and pushes creatures out of it.
 */
public final class RainShieldAbility extends NovemberRainStandAbility {
    public static final double RAIN_RADIUS = 2.0D;
    public static final double FORWARD_OFFSET = 1.5D;
    public static final float KNOCKBACK = 0.8F;
    public static final float STAMINA_COST_TICK = 2.0F;
    public static final int RECOVERY_TICKS = 20;
    public static final int PARTICLES_PER_TICK = 15;

    public RainShieldAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, new Spec()
                .holdType()
                .staminaCostTick(STAMINA_COST_TICK)
                .standRecoveryTicks(RECOVERY_TICKS)
                .pose(RAIN_ANIM));
    }

    @Override
    protected void standTickPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity user) {
        Vec3 look = stand.getLookAngle();
        double centerX = stand.getX() + look.x * FORWARD_OFFSET;
        double centerY = stand.getY();
        double centerZ = stand.getZ() + look.z * FORWARD_OFFSET;
        if (level.isClientSide()) {
            for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                double rx = centerX + (level.random.nextDouble() - 0.5D) * RAIN_RADIUS * 2.0D;
                double rz = centerZ + (level.random.nextDouble() - 0.5D) * RAIN_RADIUS * 2.0D;
                level.addParticle(ParticleTypes.RAIN, rx, centerY + 4.0D, rz, 0.0D, -0.7D, 0.0D);
            }
            return;
        }
        AABB rainArea = new AABB(
                centerX - RAIN_RADIUS, centerY - 1.0D, centerZ - RAIN_RADIUS,
                centerX + RAIN_RADIUS, centerY + 5.0D, centerZ + RAIN_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, rainArea)) {
            if (entity == stand || entity == user) {
                continue;
            }
            if (entity instanceof Projectile) {
                entity.discard();
            }
            else if (entity instanceof LivingEntity living) {
                living.knockback(KNOCKBACK, centerX - living.getX(), centerZ - living.getZ());
            }
        }
    }
}
