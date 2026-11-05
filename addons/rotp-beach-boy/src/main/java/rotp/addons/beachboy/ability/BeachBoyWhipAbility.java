package rotp.addons.beachboy.ability;

import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 BeachBoyWhipAction: the lash (8.5 blocks across) and, its Shift variation, the overhead slash (11 blocks); 40
 * stamina. With the hook out it needs Resolve level 4 and a creature on the hook, which the lash then swings along.
 */
public class BeachBoyWhipAbility extends BeachBoyAbility {
    private final boolean vertical;

    public BeachBoyWhipAbility(AbilityType<?> abilityType, AbilityId abilityId, boolean vertical) {
        super(abilityType, abilityId, 40.0F);
        this.vertical = vertical;
    }

    @Override
    protected ConditionCheck checkBeachBoyConditions(LivingEntity user, StandPower power) {
        ConditionCheck rod = needsRod(user);
        if (!rod.isPositive()) {
            return rod;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        if (hook != null) {
            if (power.getResolveLevel() < 4) {
                return ConditionCheck.createNegative("beach_boy_reel_first");
            }
            if (BeachBoyCombatUtil.hookedLiving(hook) == null) {
                return ConditionCheck.createNegative("beach_boy_reel_or_hook");
            }
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    protected void perform(Level level, LivingEntity user, StandPower power) {
        BeachBoyCombatUtil.performWhip(level, user, power, vertical);
    }
}
