package rotp.addons.spin.breaker;

import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.compat.SpinLoveTrain;
import rotp.addons.spin.config.SpinConfig;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

public final class BallBreakerAging {
    private BallBreakerAging() {}

    static double senescenceScale(boolean chipped) {
        return chipped ? SpinConfig.BALL_BREAKER_CHIPPED_RETENTION.get() : 1;
    }

    static int duration(double scale) {
        return Math.max(1, (int) Math.round(SpinConfig.BALL_BREAKER_SENESCENCE_DURATION.get() * scale));
    }

    static int nextAmplifier(int current, int maxStacks) {
        return Math.min(Math.max(-1, current) + 1, Math.max(1, maxStacks) - 1);
    }

    @Nullable
    static LivingEntity agingTarget(LivingEntity victim) {
        return victim instanceof StandEntity stand ? stand.getUser() : victim;
    }

    @Nullable
    public static LivingEntity apply(LivingEntity victim, double scale) {
        LivingEntity target = agingTarget(victim);
        if (target == null || !target.isAlive() || target.level().isClientSide()) {
            return null;
        }
        MobEffectInstance current = target.getEffect(SpinAddon.SENESCENCE);
        int amplifier = nextAmplifier(current == null ? -1 : current.getAmplifier(),
                SpinConfig.BALL_BREAKER_MAX_STACKS.get());
        SpinLoveTrain.addEffect(target, new MobEffectInstance(SpinAddon.SENESCENCE, duration(scale), amplifier));
        float drain = (float) Math.max(0, SpinConfig.BALL_BREAKER_STAND_STAMINA_DRAIN.get()
                * (amplifier + 1) * Math.max(0, scale));
        if (drain > 0) {
            StandPower.getOptional(target).filter(power -> power.hasPower()
                    && power.usesStamina() && !power.isStaminaInfinite()).ifPresent(power ->
                    power.setStamina(Math.max(0, power.getStamina() - drain)));
        }
        return target;
    }
}
