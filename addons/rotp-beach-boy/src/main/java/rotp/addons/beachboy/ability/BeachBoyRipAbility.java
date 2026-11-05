package rotp.addons.beachboy.ability;

import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** 1.16 BeachBoyRipAction: tears the hook through the hooked creature (bleeding, Slowness II for 4 s); 55 stamina. */
public class BeachBoyRipAbility extends BeachBoyAbility {
    public BeachBoyRipAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, 55.0F);
    }

    @Override
    protected ConditionCheck checkBeachBoyConditions(LivingEntity user, StandPower power) {
        ConditionCheck rod = needsRod(user);
        if (!rod.isPositive()) {
            return rod;
        }
        return BeachBoyCombatUtil.hookedLiving(BeachBoyStandUtil.findHook(user)) == null
                ? ConditionCheck.createNegative("beach_boy_need_hooked") : ConditionCheck.POSITIVE;
    }

    @Override
    protected void perform(Level level, LivingEntity user, StandPower power) {
        BeachBoyCombatUtil.performRip(level, user, power, false);
    }
}
