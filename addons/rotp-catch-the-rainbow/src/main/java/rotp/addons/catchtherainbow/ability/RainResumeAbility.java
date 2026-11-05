package rotp.addons.catchtherainbow.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

/**
 * Compatibility registry alias for 1.16's time-resume variation. Rain
 * Stop turns off by a second press of its own key.
 */
public final class RainResumeAbility extends Ability {
    public RainResumeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return false;
    }

    @Override
    public boolean addToControlSchemeEditing() {
        return false;
    }
}
