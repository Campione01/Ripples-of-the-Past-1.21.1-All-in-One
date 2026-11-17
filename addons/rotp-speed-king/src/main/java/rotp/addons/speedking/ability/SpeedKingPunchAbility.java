package rotp.addons.speedking.ability;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.speedking.SpeedKingAddon;

/**
 * 1.16 099 InitStands :61: example_stand_punch was the plain core StandEntityLightAttack (punchSound
 * STAND_PUNCH_LIGHT, the default): the core light punch, its damage from the Stand's strength, its finisher meter,
 * timing, cancel rules and arm-only summon.
 */
public final class SpeedKingPunchAbility extends StandEntityPunchAbility {
    public SpeedKingPunchAbility(AbilityType<?> type, AbilityId id) {
        super(type, id);
        // Speed King's own clips: punch for the right arm, punch2 for the left; the core picks the index
        anim = ActionAnimIdentifier.getOrCreate("punch", false);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (base.isPositive() && !(context instanceof StandPower power
                && SpeedKingAddon.isUser(power.getUser()))) {
            return ConditionCheck.NEGATIVE;
        }
        return base;
    }
}
