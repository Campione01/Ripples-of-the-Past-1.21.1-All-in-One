package rotp.addons.darkbluemoon.ability;

import rotp.addons.darkbluemoon.entity.WhirlpoolEntity;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.entity.LivingEntity;

/** 1.16 DarkBlueMoonWhirlpoolCancelAction ("whirlpoolCancel" pose, 10 ticks, cooldown 20): the user's whirlpools within 64 blocks end. */
public class DbmWhirlpoolCancelAbility extends DbmStandAction {
    public DbmWhirlpoolCancelAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "whirlpoolCancel", 10, 0.0F, 20, TargetRequirement.NONE);
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        for (WhirlpoolEntity whirlpool : stand.level().getEntitiesOfClass(WhirlpoolEntity.class,
                user.getBoundingBox().inflate(64.0))) {
            if (whirlpool.getOwner() == user) {
                whirlpool.discard();
            }
        }
    }
}
