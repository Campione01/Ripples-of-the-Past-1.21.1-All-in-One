package rotp.addons.ovatheworld.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands.theworld.TimeStopAbility;

public final class OvaTimeStopAbility extends TimeStopAbility {
    // 1.16 THE_WORLD_OVA_TIME_STOP: staminaCost(250), staminaCostTick(7.5).
    // The start is priced by the OVA time-stop policy and the per-tick cost
    // at PreStart, both scaled from the core's 225 / 9 like the core cost.
    public static final float LEGACY_INITIAL_STAMINA = 250.0F;
    public static final float LEGACY_STAMINA_PER_TICK = 7.5F;

    public OvaTimeStopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 TheWorldTimeStop: a hit during the charge drops it.
        setCancelHeldOnGettingAttacked();
    }

    @Override
    protected int getRequiredResolveLevelToUnlock(
            Power<?> context) {
        return 2;
    }

    // 1.16 TheWorldTimeStop: from Resolve 4 the charge is two thirds of the core's timeStopChargeSeconds setting.
    @Override
    protected boolean shortensChargeAtResolveFour(StandPower power) {
        return true;
    }
}
