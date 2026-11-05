package rotp.addons.novemberrain.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.novemberrain.entity.RainDropEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The remake's barrage_rain (BarrageRain): staminaCostTick(10), resolveLevelToUnlock(2), standRecoveryTicks(20), a
 * one-tick perform in the idle pose. Twenty rain drops fly along its user's look, each dealing 2.5 times the stamina
 * share the user had, and the user is thrown back and up.
 */
public final class BarrageRainAbility extends NovemberRainStandAbility {
    public static final float STAMINA_COST_TICK = 10.0F;
    public static final int RESOLVE_LEVEL = 2;
    public static final int RECOVERY_TICKS = 20;
    public static final float MAX_DAMAGE = 2.5F;
    public static final int DROPS = 20;
    public static final double PUSH_STRENGTH = 2.35D;
    public static final double UPWARD_STRENGTH = 0.95D;
    public static final float DROP_VELOCITY = 2.5F;
    public static final float DROP_INACCURACY = 1.0F;

    public BarrageRainAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, new Spec()
                .staminaCostTick(STAMINA_COST_TICK)
                .resolveLevelToUnlock(RESOLVE_LEVEL)
                .standRecoveryTicks(RECOVERY_TICKS));
    }

    @Override
    protected void standTickPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity user) {
        if (level.isClientSide()) {
            return;
        }
        StandPower userPower = StandPower.get(user);
        // read before this tick's staminaCostTick is taken, as 1.16 StandEntityTask did
        float staminaPercent = userPower != null && userPower.getMaxStamina() > 0
                ? userPower.getStamina() / userPower.getMaxStamina() : 0.0F;
        float finalDamage = 0.0F + MAX_DAMAGE * staminaPercent;
        Vec3 look = user.getLookAngle();
        Vec3 backPush = look.normalize().scale(-PUSH_STRENGTH);
        user.setDeltaMovement(user.getDeltaMovement().add(backPush.x, UPWARD_STRENGTH, backPush.z));
        user.hasImpulse = true;
        user.hurtMarked = true;

        for (int i = 0; i < DROPS; i++) {
            RainDropEntity raindrop = new RainDropEntity(level, user);
            raindrop.setDamage(finalDamage);
            double x = stand.getX() + (level.random.nextDouble() - 0.5D) * 1.2D;
            double y = stand.getY() + 1.6D + level.random.nextDouble() * 0.5D;
            double z = stand.getZ() + (level.random.nextDouble() - 0.5D) * 1.2D;
            raindrop.setPos(x, y, z);
            raindrop.shoot(look.x, look.y, look.z, DROP_VELOCITY, DROP_INACCURACY);
            level.addFreshEntity(raindrop);
        }
    }
}
