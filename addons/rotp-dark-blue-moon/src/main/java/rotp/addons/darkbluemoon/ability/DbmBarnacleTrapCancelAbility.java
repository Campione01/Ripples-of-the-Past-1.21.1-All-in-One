package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.entity.DarkBlueMoonEntity;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.entity.LivingEntity;

/** 1.16 DarkBlueMoonBarnacleTrapCancelAction ("whirlpool" pose, 10 ticks, cooldown 20): clears the summoned Stand's trap. */
public class DbmBarnacleTrapCancelAbility extends DbmStandAction {
    public DbmBarnacleTrapCancelAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "whirlpool", 10, 0.0F, 20, TargetRequirement.NONE);
    }

    @Override
    protected ConditionCheck checkDbmConditions(LivingEntity user, @Nullable StandPower power) {
        return ConditionCheck.noMessage(power != null
                && power.getSummonedStandEntity() instanceof DarkBlueMoonEntity dbm && dbm.hasBarnacleTrap());
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        if (stand instanceof DarkBlueMoonEntity dbm) {
            dbm.clearBarnacleTrap();
        }
    }
}
