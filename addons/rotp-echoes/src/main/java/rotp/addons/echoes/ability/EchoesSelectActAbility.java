package rotp.addons.echoes.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.client.EchoesChangeActScreen;
import rotp.addons.echoes.state.EchoesForms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * R772 E1: the master's EchoesSelectActAction. It opens the ACT selector (EchoesChangeActScreen), where any ACT the
 * Resolve level has reached is picked at once; it is a skill bar slot with no key of its own. It works only when
 * there is another ACT to pick, as the master's Resolve level 2 gate did.
 */
public final class EchoesSelectActAbility extends Ability {
    public EchoesSelectActAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        usageGroup = AbilityUsageGroup.UTILITY;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        return context instanceof StandPower power && EchoesForms.highestAct(power) > 1
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            EchoesChangeActScreen.openWindowOnClick(user);
        }
    }
}
