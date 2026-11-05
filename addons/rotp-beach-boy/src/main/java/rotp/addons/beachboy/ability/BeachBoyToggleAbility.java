package rotp.addons.beachboy.ability;

import rotp.addons.beachboy.util.BeachBoySettings;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 BeachBoyLineCollisionAction, BeachBoyCrushDamageAction and BeachBoySoundSenseAction: a switch of the user's
 * Beach Boy settings, named and drawn by its state ("Path Line: On", the _on/_off icon, a green frame when on).
 */
public class BeachBoyToggleAbility extends BeachBoyAbility {
    private final BeachBoySettings.Toggle toggle;

    public BeachBoyToggleAbility(AbilityType<?> abilityType, AbilityId abilityId, BeachBoySettings.Toggle toggle) {
        super(abilityType, abilityId, 0.0F);
        this.toggle = toggle;
    }

    public boolean isOn(Power<?> context) {
        return context instanceof StandPower power && BeachBoySettings.get(power, toggle);
    }

    @Override
    protected ConditionCheck checkBeachBoyConditions(LivingEntity user, StandPower power) {
        return ConditionCheck.noMessage(BeachBoyStandUtil.isBeachBoyUser(user));
    }

    @Override
    protected void perform(Level level, LivingEntity user, StandPower power) {
        BeachBoySettings.toggle(user, toggle);
    }

    @Override
    public Component getName(Power<?> context) {
        return abilityName(context, "", Component.translatable(getTranslationKey(isOn(context) ? ".on" : ".off")));
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return super.getSpriteName(context) + (isOn(context) ? "_on" : "_off");
    }
}
