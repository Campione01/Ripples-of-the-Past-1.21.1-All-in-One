package rotp.addons.spin.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.compat.SpinLoveTrain;
import rotp.addons.spin.config.SpinConfig;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;

public final class SenescenceEffect extends MobEffect {
    public SenescenceEffect() {
        super(MobEffectCategory.HARMFUL, 8026762);
        addAttributeModifier(Attributes.MAX_HEALTH, SpinAddon.id("senescence_health"), AttributeModifier.Operation.ADD_VALUE,
                level -> penalty(SpinConfig.BALL_BREAKER_HEALTH_PER_STACK, level));
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SpinAddon.id("senescence_speed"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                level -> penalty(SpinConfig.BALL_BREAKER_SPEED_PER_STACK, level));
        addAttributeModifier(Attributes.ATTACK_DAMAGE, SpinAddon.id("senescence_attack"), AttributeModifier.Operation.ADD_VALUE,
                level -> penalty(SpinConfig.BALL_BREAKER_ATTACK_PER_STACK, level));
    }

    private static double penalty(DoubleValue value, int amplifier) {
        return -(SpinConfig.SPEC.isLoaded() ? value.get() : value.getDefault()) * (amplifier + 1);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % SpinConfig.BALL_BREAKER_SENESCENCE_INTERVAL.get() == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide()) {
            if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
            SpinLoveTrain.hurt(entity, entity.damageSources().wither(),
                    SpinConfig.BALL_BREAKER_SENESCENCE_DAMAGE.get().floatValue() * (amplifier + 1));
        }
        return true;
    }
}
