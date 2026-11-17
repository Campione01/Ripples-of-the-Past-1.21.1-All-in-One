package rotp.addons.kingcrimson.ability;

import rotp.addons.kingcrimson.state.KingCrimsonReflexErase;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

/**
 * A passive skill of King Crimson's: the reflex erase and the last-chance erase (KingCrimsonReflexErase). It is in
 * the moveset for its name, its Resolve level and its cooldown, which the Stand keeps and sends to the client; no key
 * uses it. It carries the core's passive mark: the Stand skills screen lists it under the passive skills with its
 * description, and no control scheme offers it.
 */
public final class KingCrimsonPassiveEraseAbility extends Ability {
    public KingCrimsonPassiveEraseAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    // The owner, 2026-11-11 and 12: the reflex is a small practical skill for the core's fatigue. The last chance
    // is none: it keeps its own wait in the mode with no cooldowns, so it has no repeat to price.
    @Override
    public boolean isPracticalSkill(Power<?> context) {
        return KingCrimsonReflexErase.REFLEX.equals(name());
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return false;
    }
}
