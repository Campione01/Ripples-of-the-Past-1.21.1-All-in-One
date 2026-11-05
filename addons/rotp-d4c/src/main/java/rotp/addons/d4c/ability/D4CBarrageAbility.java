package rotp.addons.d4c.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.type.EntityActionType;

/**
 * D4C barrage: the core barrage plus the 1.16 heavy-attack follow-up.
 * 1.16 ai/ah.java:70 also gave it shout(valentine_barrage), but the 1.16
 * sounds.json had no valentine_barrage entry, so the barrage said nothing;
 * R772 F27 keeps it silent instead of a 6-14 s Valentine monologue over
 * the rush.
 */
public final class D4CBarrageAbility extends StandEntityBarrageAbility {

    public D4CBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, D4CBarrage::new);
    }

    public static final class D4CBarrage extends StandEntityBarrage {

        public D4CBarrage(EntityActionType ability) {
            super(ability);
        }

        // 1.16 StandEntityMeleeBarrage.isCancelable: a heavy attack
        // (canFollowUpBarrage) followed the barrage up in its recovery; the
        // hand chop was one (StandEntityHeavyAttack.Builder, ai/ah.java:72)
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return super.canBeCancelledInto(cancellingAbility)
                    || getPhase() == ActionPhase.RECOVERY
                            && cancellingAbility instanceof D4CPhase2BAbility chop
                            && chop.mode() == D4CPhase2BAbility.Mode.HAND_CHOP;
        }
    }
}
