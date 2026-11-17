package rotp.addons.shadowtheworld.ability;

import java.util.Locale;

import rotp.core.JojoModConfig;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.impl.stands.theworld.TimeStopAbility;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class ShadowTimeStopAbility extends TimeStopAbility {
    // 1.16 SHADOW_WORLD_TIME_STOP charged holdToFire(40) times the user's
    // health share. The owner's rule for every time stop puts the core's
    // charge setting in place of the 40, and nothing charges for less than
    // that setting's least. To the player it is The World, so from Resolve 4
    // it keeps two thirds of the charge as The World's does (the
    // coordinator's ruling of R772.1); 1.16's own 20-tick helper for that,
    // shortedHoldDuration, was private and never called.
    private static final int LEAST_CHARGE_TICKS = Math.round(
            (float) (JojoModConfig.TIME_STOP_CHARGE_SECONDS_MIN * 20.0D));

    public ShadowTimeStopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 cancelHeldOnGettingAttacked: a hit during the charge
        // drops it.
        setCancelHeldOnGettingAttacked();
        // 1.16 SHADOW_WORLD_TIME_STOP builder: ignoresPerformerStun(), so a
        // stunned Stand keeps its charge.
        setIgnoresPerformerStun();
    }

    @Override
    protected int getRequiredResolveLevelToUnlock(
            Power<?> context) {
        return 2;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 getHoldDurationToFire: TimeStop's charge is 0 in stopped
        // time, and the health ratio keeps it 0.
        float charge = action.phasesLength.getFloat(
                ActionPhase.BUTTON_CHARGE);
        if (charge <= 0.0F) {
            return;
        }
        StandPower power = powerUser != null
                ? PowerClass.STAND.get(powerUser)
                : null;
        int chargeTicks = Mth.ceil(charge);
        if (powerUser != null
                && powerUser.getMaxHealth() > 0.0F
                && (power == null || !power.isUserCreative())) {
            chargeTicks = Mth.ceil(
                    charge
                            * powerUser.getHealth()
                            / powerUser.getMaxHealth());
        }
        action.phasesLength.put(
                ActionPhase.BUTTON_CHARGE,
                Math.max(chargeTicks, LEAST_CHARGE_TICKS));
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
                "rotp_shadowtheworld.ability.time_stop",
                String.format(Locale.ROOT, "%.2f", seconds));
    }
}
