package rotp.addons.diegosworld.ability;

import java.util.Locale;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.impl.stands.theworld.TimeStopAbility;

import net.minecraft.network.chat.Component;

/**
 * The donor's THEWORLDTimeStop had no charge (getHoldDurationToFire 0). The owner's rule for every time stop replaces
 * that: the core's charge setting, nothing on a release before half of it, a quick double press for an urgent stop. To
 * the player it is The World, so from Resolve 4 it keeps two thirds of the charge as The World's does (the coordinator's
 * ruling of R772.1), though the donor built it as a plain TimeStop.
 */
public final class DiegoTimeStopAbility extends TimeStopAbility {
    public DiegoTimeStopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    protected int getRequiredResolveLevelToUnlock(
            Power<?> context) {
        return 1;
    }

    @Override
    protected boolean shortensChargeAtResolveFour(StandPower power) {
        return true;
    }

    @Override
    public Component getName(Power<?> context) {
        StandPower power = PowerClass.STAND.cast(context);
        float seconds =
                TimeStopLearning.getTimeStopTicks(power) / 20.0F;
        return Component.translatable(
                "rotp_theworld.ability.time_stop",
                String.format(Locale.ROOT, "%.2f", seconds));
    }
}
