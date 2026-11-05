package rotp.addons.catchtherainbow.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Rain Stop as the manga has it: the raindrops around the user stop in
 * mid-air and cut whoever moves through them. 1.16 registered a
 * TheWorldTimeStop under this ID but left it off both hotbars, so its
 * players never had it; the port had put that time stop on the wheel.
 */
public final class RainStopAbility extends Ability {
    public static final float ACTIVATION_STAMINA_COST = 100.0F;
    public static final int COOLDOWN_TICKS = 10;

    public RainStopAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        resolveLevelToUnlock(1);
        cooldown(COOLDOWN_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user =
                context != null ? context.getUser() : null;
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        if (user == null
                || controller == null
                        && !CatchTheRainbowAddon.summonsOnPress(user)) {
            return ConditionCheck.createNegative(
                    "catch_the_rainbow_not_summoned");
        }
        if (controller != null && controller.isRainStopActive()) {
            return ConditionCheck.POSITIVE;
        }
        if (!CatchTheRainbowAddon.isCreative(user)
                && !RainExposure.isExposedToRain(user)) {
            return ConditionCheck.createNegative("needs_rain");
        }
        StandPower power = StandPower.get(user);
        if (!CatchTheRainbowAddon.isCreative(user)
                && (power == null
                        || power.getStamina()
                                <= ACTIVATION_STAMINA_COST)) {
            return ConditionCheck.createNegative(
                    "not_enough_stamina");
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (power == null) {
            return;
        }
        autoSummonStand(user, power);
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        if (controller == null) {
            return;
        }
        if (controller.isRainStopActive()) {
            controller.setRainStopActive(false);
            return;
        }
        boolean creative = CatchTheRainbowAddon.isCreative(user);
        if (!creative
                && (!RainExposure.isExposedToRain(user)
                        || power.getStamina()
                                <= ACTIVATION_STAMINA_COST)) {
            return;
        }
        StandAbilityStamina.consume(
                this,
                power,
                creative ? 0.0F : ACTIVATION_STAMINA_COST,
                false);
        controller.setRainStopActive(true);
        level.playSound(
                null,
                user.blockPosition(),
                CatchTheRainbowAddon.RAIN_STEP_SOUND.get(),
                user.getSoundSource(),
                1.0F,
                0.6F);
    }

    private static void autoSummonStand(
            LivingEntity user, StandPower power) {
        if (!(power.getPowerType()
                instanceof EntityStandType entityStandType)) {
            return;
        }
        StandEntity standEntity = power.getSummonedStandEntity();
        if (standEntity == null) {
            entityStandType.summon(user, power, entity -> {}, true);
        }
        else if (standEntity.isArmsOnlyMode()) {
            standEntity.fullSummonFromArms();
            entityStandType.triggerFullSummonAdvancement(
                    user, standEntity);
        }
    }
}
