package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.util.BarnacleBinds;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.entity.LivingEntity;

/** 1.16 DarkBlueMoonBarnacleCancelAction ("whirlpool" pose, 10 ticks, cooldown 20): frees the bound target. */
public class DbmBarnacleCancelAbility extends DbmStandAction {
    public DbmBarnacleCancelAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "whirlpool", 10, 0.0F, 20, TargetRequirement.NONE);
    }

    @Override
    protected ConditionCheck checkDbmConditions(LivingEntity user, @Nullable StandPower power) {
        return ConditionCheck.noMessage(BarnacleBinds.isBinding(user));
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        LivingEntity target = BarnacleBinds.getTarget(user, stand.level());
        if (target != null) {
            BarnacleBinds.removeAllBarnacles(stand.level(), target);
        }
        BarnacleBinds.unbind(user);
    }
}
