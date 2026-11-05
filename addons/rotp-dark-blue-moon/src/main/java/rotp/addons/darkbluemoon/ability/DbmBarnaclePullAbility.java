package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.entity.BarnaclePullEntity;
import rotp.addons.darkbluemoon.util.BarnacleItemHelper;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 DarkBlueMoonBarnaclePullAction ("whirlpool" pose, 10 ticks, 100 stamina, cooldown 60, pressed at an entity): on
 * land for 15-20 held barnacles, the Stand shoots a barnacle line that catches an entity and drags it to the Stand.
 */
public class DbmBarnaclePullAbility extends DbmStandAction {
    public DbmBarnaclePullAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "whirlpool", 10, 100.0F, 60, TargetRequirement.ENTITY);
    }

    @Override
    protected ConditionCheck checkDbmConditions(LivingEntity user, @Nullable StandPower power) {
        return ConditionCheck.noMessage(BarnacleItemHelper.canUseBarnacleSkill(user));
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        if (!BarnacleItemHelper.payLandCost(user)) {
            return;
        }
        action.addProjectileWithStandStats(new BarnaclePullEntity(stand.level(), stand));
    }
}
