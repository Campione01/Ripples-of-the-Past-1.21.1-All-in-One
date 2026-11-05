package rotp.addons.killerqueen.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.killerqueen.btd.BiteTheDustManager;
import rotp.addons.killerqueen.btd.BitesTheDustService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.6.10 (2026-10-20) com.zeml.rotp_zkq.action.stand.btd.BiteTheDustInsert ('bite_the_dust'): a StandAction of the
 * user (no auto-summon) at the living player under his mouse target, for 100 stamina at Resolve 4 with a 40-tick
 * cooldown; it places Bite the Dust in that player (BiteTheDustManager).
 */
public final class BiteTheDustInsertAbility extends StandEntityAbility {
    public static final float STAMINA_COST = 100.0F;
    public static final int RESOLVE_LEVEL = 4;
    public static final int COOLDOWN_TICKS = 40;

    public BiteTheDustInsertAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, InsertAction::new);
        usageGroup = AbilityUsageGroup.SPECIAL;
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 StandAction.getPerformer: the summoned Stand, else the user; autoSummonStand false
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null ? stand : user;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context.getUser() instanceof Player user)) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck stamina = StandAbilityStamina.check(context, STAMINA_COST);
        if (!stamina.isPositive()) {
            return stamina;
        }
        LivingEntity carrier = BitesTheDustService.aimedLiving(user);
        if (carrier == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (BiteTheDustManager.isOwnerBusy(user)) {
            return ConditionCheck.createNegative("btd_already_active");
        }
        if (user instanceof ServerPlayer owner) {
            String problem = BiteTheDustManager.checkInsert(owner, carrier);
            if (problem != null) {
                return ConditionCheck.createNegative(problem);
            }
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class InsertAction extends EntityActionInstance {
        public InsertAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!level().isClientSide() && getPowerUser() instanceof Player user) {
                setActionTargetSnapshot(BitesTheDustService.aimedTarget(user));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser() instanceof ServerPlayer owner)
                    || !(getActionTargetSnapshot(level()).getMainEntity()
                            instanceof ServerPlayer carrier)) {
                return;
            }
            StandPower power = StandPower.get(owner);
            if (power != null
                    && BiteTheDustManager.activate(owner, carrier, power)) {
                StandAbilityStamina.consume(ability, power, STAMINA_COST, false);
            }
        }
    }
}
