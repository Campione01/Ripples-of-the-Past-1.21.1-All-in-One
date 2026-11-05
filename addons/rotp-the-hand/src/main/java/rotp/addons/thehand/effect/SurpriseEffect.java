package rotp.addons.thehand.effect;

import rotp.core.mechanics.StunEffect;
import rotp.core.util.reflection.CommonReflection;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

public final class SurpriseEffect extends StunEffect {
    public SurpriseEffect() {
        super(0xDD0F0F);
    }

    // Extra Stands 1.1.0d replaced the stun's tick: the vertical motion is
    // kept as it is (the stun only lets an entity fall)
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            player.getAbilities().flying = false;
        }
        if (resetsDeltaMovement()) {
            entity.setDeltaMovement(
                    0.0D, entity.getDeltaMovement().y, 0.0D);
        }
        if (entity instanceof Creeper creeper) {
            CommonReflection.setCreeperSwell(creeper, -1);
        }
        return true;
    }

    @Override
    public boolean isApplicable(LivingEntity entity) {
        return !(entity instanceof Player) && super.isApplicable(entity);
    }
}
