package rotp.addons.spin.breaker;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.compat.SpinLoveTrain;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.item.SteelBallItem;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.DamageUtil;

public final class BallBreakerSenescence extends StandEntityAbility {
    public static final ResourceKey<DamageType> TOUCH_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, SpinAddon.id("ball_breaker_touch"));

    public BallBreakerSenescence(AbilityType<?> type, AbilityId id) {
        super(type, id, Touch::new);
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return SpinConfig.BALL_BREAKER_TOUCH_STAMINA.get().floatValue();
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(StandPower power, StandEntity stand) {
        return power.getStandInstance().filter(instance -> instance.hasPart(StandPart.ARMS)).isPresent()
                ? super.checkStandEntityConditions(power, stand) : ConditionCheck.NEGATIVE;
    }

    static final class Touch extends EntityActionInstance {
        Touch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(getPerformer() instanceof BallBreakerEntity stand) || stand.level().isClientSide()
                    || stand.getUser() == null || stand.getUserPower() == null) {
                return;
            }
            LivingEntity user = stand.getUser();
            StandAbilityStamina.consume(ability, stand.getUserPower(),
                    SpinConfig.BALL_BREAKER_TOUCH_STAMINA.get().floatValue(), false);
            boolean chipped = false;
            for (var stack : user.getHandSlots()) {
                chipped |= stack.is(SpinAddon.STEEL_BALL.get()) && SteelBallItem.isChipped(stack);
            }
            double scale = BallBreakerAging.senescenceScale(chipped);
            float damage = (float) (SpinConfig.BALL_BREAKER_TOUCH_DAMAGE.get() * scale);
            double range = SpinConfig.BALL_BREAKER_TOUCH_RANGE.get();
            DamageSource source = DamageUtil.make(stand.level(), TOUCH_DAMAGE, stand);
            Set<LivingEntity> aged = new HashSet<>();
            for (LivingEntity victim : stand.level().getEntitiesOfClass(LivingEntity.class,
                    stand.getBoundingBox().inflate(range), entity -> entity.isAlive() && entity != user && entity != stand)) {
                SpinLoveTrain.hurt(victim, source, damage);
                LivingEntity target = BallBreakerAging.agingTarget(victim);
                if (target != null && target != user && !aged.contains(target)
                        && BallBreakerAging.apply(victim, scale) != null) {
                    aged.add(target);
                    SpinLoveTrain.addEffect(target, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            BallBreakerAging.duration(scale), 1));
                }
            }
        }
    }
}
