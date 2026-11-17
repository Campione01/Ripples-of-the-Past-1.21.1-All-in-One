package rotp.addons.spin.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import rotp.addons.spin.entity.WreckingBall;

public final class HemispatialNeglectEffect extends MobEffect {
    public HemispatialNeglectEffect() {
        super(MobEffectCategory.HARMFUL, 9075672);
    }

    public static boolean onLeft(LivingEntity viewer, LivingEntity target) {
        return WreckingBall.isOnLeftSide(viewer.getLookAngle(), target.position().subtract(viewer.position()));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive() && onLeft(mob, target)) mob.setTarget(null);
        }
        return true;
    }
}
