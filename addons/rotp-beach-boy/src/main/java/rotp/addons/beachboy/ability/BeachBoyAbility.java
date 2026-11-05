package rotp.addons.beachboy.ability;

import javax.annotation.Nullable;

import rotp.addons.beachboy.util.BeachBoyStaminaPause;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * A 1.16 Beach Boy StandAction: no Stand task, it goes off at the click (autoSummonStand, cooldown 0), takes its
 * stamina cost as it performs (1.16 StandAction.onPerform, whatever the bar holds) and is refused while its performer
 * is stunned.
 */
public abstract class BeachBoyAbility extends Ability {
    private final float staminaCost;

    protected BeachBoyAbility(AbilityType<?> abilityType, AbilityId abilityId, float staminaCost) {
        super(abilityType, abilityId);
        this.staminaCost = staminaCost;
        cooldown(0);
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return staminaCost;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null || !(context instanceof StandPower power)) {
            return ConditionCheck.NEGATIVE;
        }
        return checkBeachBoyConditions(user, power);
    }

    /** 1.16 checkConditions after StandAction's own. */
    protected abstract ConditionCheck checkBeachBoyConditions(LivingEntity user, StandPower power);

    /** 1.16 perform, server side. */
    protected abstract void perform(Level level, LivingEntity user, StandPower power);

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (staminaCost > 0.0F) {
            BeachBoyStaminaPause.pause(user);
        }
        if (level.isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (power == null || !power.hasPower()) {
            return;
        }
        autoSummon(user, power);
        StandAbilityStamina.consume(this, power, staminaCost, false);
        perform(level, user, power);
    }

    static void autoSummon(LivingEntity user, @Nullable StandPower power) {
        if (power != null && !power.isSummoned() && power.getPowerType() instanceof EntityStandType standType) {
            standType.summon(user, power, stand -> {}, true);
        }
    }

    static ConditionCheck needsRod(LivingEntity user) {
        return ConditionCheck.noMessage(BeachBoyStandUtil.isBeachBoyUser(user) && BeachBoyStandUtil.isHoldingRod(user));
    }
}
