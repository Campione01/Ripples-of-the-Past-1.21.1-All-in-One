package rotp.addons.beachboy.ability;

import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 BeachBoyRetractHookAction: "Detach Target" lets the hooked creature go; "Quick Reel" and "Retract Trap Hook"
 * (5 stamina) reel the hook in, a second press stops the reel. Needs only a hook out.
 */
public class BeachBoyRetractAbility extends BeachBoyAbility {
    private final boolean detachOnly;

    public BeachBoyRetractAbility(AbilityType<?> abilityType, AbilityId abilityId, boolean detachOnly,
            float staminaCost) {
        super(abilityType, abilityId, staminaCost);
        this.detachOnly = detachOnly;
    }

    @Override
    protected ConditionCheck checkBeachBoyConditions(LivingEntity user, StandPower power) {
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        if (hook == null) {
            return ConditionCheck.NEGATIVE;
        }
        return detachOnly && hook.getHookedEntity() == null
                ? ConditionCheck.createNegative("beach_boy_need_hooked") : ConditionCheck.POSITIVE;
    }

    @Override
    protected void perform(Level level, LivingEntity user, StandPower power) {
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        if (hook != null) {
            if (detachOnly) {
                hook.detachFromHost();
            }
            else {
                hook.beginRetract();
            }
        }
    }
}
