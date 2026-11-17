package rotp.addons.spin.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import rotp.addons.spin.config.SpinConfig;

public final class DesiccationEffect extends MobEffect {
    public DesiccationEffect() {
        super(MobEffectCategory.HARMFUL, 12755819);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide()) {
            if (entity instanceof Player player) {
                player.causeFoodExhaustion(SpinConfig.SQUEEZE_DRY_EXHAUSTION_PER_TICK.get().floatValue() * (amplifier + 1));
            }
            if (entity.isOnFire()) entity.clearFire();
        }
        return true;
    }
}
