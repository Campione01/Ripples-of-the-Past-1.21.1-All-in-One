package rotp.addons.highwaystar.ability;

import java.util.ArrayList;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarEntity;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import rotp.addons.highwaystar.network.HighwayStarPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 HighwayStarTrackingAction, HighwayStarTrackCancelAction, HighwayStarArmReturnAction and
 * HighwayStarFormSwitchAction: one-tick Stand actions with no target.
 */
public final class HighwayStarUtilityAbility extends StandEntityAbility {
    public static final ActionAnimIdentifier SPLIT_ANIM = ActionAnimIdentifier.getOrCreate("division", false);
    public static final ActionAnimIdentifier RECOVERY_ANIM = ActionAnimIdentifier.getOrCreate("recovery", false);

    public enum Mode {
        TRACKING(15.0F),
        TRACK_CANCEL(0.0F),
        ARM_RETURN(5.0F),
        FORM_SWITCH(10.0F);

        public final float staminaCost;

        Mode(float staminaCost) {
            this.staminaCost = staminaCost;
        }
    }

    private final Mode mode;

    public HighwayStarUtilityAbility(AbilityType<?> abilityType, AbilityId abilityId, Mode mode) {
        super(abilityType, abilityId, type -> new UtilityAction(type, mode));
        this.mode = mode;
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        switch (mode) {
            case TRACKING -> cooldown(60, 0);
            case ARM_RETURN, FORM_SWITCH -> cooldown(20, 0);
            case TRACK_CANCEL -> {}
        }
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        if (mode == Mode.ARM_RETURN && context instanceof StandPower power
                && power.getSummonedStandEntity() instanceof HighwayStarEntity hs && !hs.isArmMissing()) {
            return ConditionCheck.createNegative(Component.translatable("action.highwaystar.condition.arm_not_missing"));
        }
        return super.checkSpecificConditions(context);
    }

    // 1.16 TargetRequirement NONE
    @Override
    public boolean takesPressedTarget(StandEntity standEntity, ActionTarget target) {
        return false;
    }

    // 1.16 HighwayStarFormSwitchAction.getStandActionTicks: 10 to split, 1 to come back
    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (mode == Mode.FORM_SWITCH && performer instanceof HighwayStarEntity hs) {
            action.phasesLength.put(ActionPhase.PERFORM, !hs.isSplit() && !hs.isPendingSplit() ? 10 : 1);
        }
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        if (mode == Mode.FORM_SWITCH && action instanceof UtilityAction utility && utility.formAnim != null) {
            return utility.formAnim;
        }
        return super.getEntityAnim(action);
    }

    public static final class UtilityAction extends EntityActionInstance {
        private final Mode mode;
        @Nullable private ActionAnimIdentifier formAnim;

        public UtilityAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onActionSet(@Nullable EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            // 1.16 HighwayStarFormSwitchAction.getStandPose: "division" from the human form, else "recovery"
            if (mode == Mode.FORM_SWITCH && performer instanceof HighwayStarEntity hs) {
                formAnim = !hs.isSplit() && !hs.isPendingSplit() ? SPLIT_ANIM : RECOVERY_ANIM;
            }
        }

        public boolean isSplitting() {
            return formAnim == SPLIT_ANIM;
        }

        public boolean isComingBack() {
            return formAnim == RECOVERY_ANIM;
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            LivingEntity user = getPowerUser();
            if (level.isClientSide() || user == null || !(performer instanceof HighwayStarEntity hs)) {
                return;
            }
            switch (mode) {
                case TRACKING -> {
                    // 1.16 sent C2SRequestTrackTargetsPacket from the client's perform; the server answered with the list
                    if (user instanceof ServerPlayer player) {
                        HighwayStarPayloads.openTrackingScreen(player,
                                new ArrayList<>(player.getData(HighwayStarAddon.SMELL_TARGETS).getTargets()));
                    }
                }
                case TRACK_CANCEL -> {
                    hs.setTrackingTarget(null);
                    HighwayStarSoleEntity remote = hs.getRemoteSole();
                    if (remote != null && !remote.isRemoved()) {
                        remote.detach();
                    }
                    for (HighwayStarSoleEntity sole : HighwayStarEntity.ownedSoles(level, user, 64.0)) {
                        sole.detach();
                    }
                }
                case ARM_RETURN -> {
                    hs.forceReturnArm();
                    hs.setTrackingTarget(null);
                    HighwayStarSoleEntity remote = hs.getRemoteSole();
                    if (remote != null && !remote.isRemoved()) {
                        remote.detach();
                    }
                    for (HighwayStarSoleEntity sole : HighwayStarEntity.ownedSoles(level, user, 64.0)) {
                        if (!sole.isRemoteControlled()) {
                            sole.discard();
                        }
                    }
                }
                case FORM_SWITCH -> {
                    level.playSound(null, hs.getX(), hs.getY(), hs.getZ(),
                            HighwayStarAddon.FORM_SWITCH_SOUND.get(), hs.getSoundSource(), 1.0F, 1.0F);
                    if (!hs.isSplit()) {
                        hs.setPendingSplit(true);
                        hs.setSplitSwitchTicks(10);
                    }
                    else {
                        hs.setPendingSplit(false);
                        hs.setSplitSwitchTicks(1);
                        hs.performSplitSwitch();
                    }
                }
            }
            if (mode.staminaCost > 0) {
                StandAbilityStamina.consume(ability, StandPower.get(user), mode.staminaCost, false);
            }
        }

        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return true;
        }
    }
}
