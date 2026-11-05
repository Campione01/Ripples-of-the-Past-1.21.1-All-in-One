package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.addons.darkbluemoon.util.BarnacleItemHelper;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 DarkBlueMoonBarnacleSelfAction ("whirlpool" pose, 20 ticks, 200 stamina, cooldown 40): the user binds itself
 * (Barnacle Bind, 200 ticks), on land for 15-20 held barnacles.
 */
public class DbmBarnacleSelfAbility extends DbmStandAction {
    public DbmBarnacleSelfAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "whirlpool", 20, 200.0F, 40, TargetRequirement.NONE);
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
        int duration = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.BARNACLE_BIND_DURATION);
        user.addEffect(new MobEffectInstance(DarkBlueMoonAddon.BARNACLE_BIND, duration, 0, false, false, false));
    }
}
