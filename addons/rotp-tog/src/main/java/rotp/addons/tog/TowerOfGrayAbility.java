package rotp.addons.tog;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

final class TowerOfGrayAbility extends StandEntityAbility {
    private static final int PURSUIT_RELEASE_TICKS = 20;
    private final Move move;

    enum Move {
        DASH("dash", 10, 40, 0),
        NEEDLE_PURSUIT("needle_pursuit", 30, 80, 2),
        GRAY_STINGER("gray_stinger", 50, 0, 4),
        FLY("fly", 5, 0, 0),
        QUICK_REPOSITION("quick_reposition", 0, 20, 0),
        DODGE("dodge", 35, 0, 3),
        GRAY_SWARM("gray_swarm", 40, 0, 3);

        final String id;
        final float stamina;
        final int cooldown;
        final int resolve;

        Move(String id, float stamina, int cooldown, int resolve) {
            this.id = id;
            this.stamina = stamina;
            this.cooldown = cooldown;
            this.resolve = resolve;
        }

        boolean hold() {
            return this == NEEDLE_PURSUIT || this == DODGE || this == GRAY_SWARM;
        }
    }

    TowerOfGrayAbility(AbilityType<?> type, AbilityId id, Move move) {
        super(type, id, ability -> new Action((TowerOfGrayAbility) ability));
        this.move = move;
        resolveLevelToUnlock(move.resolve);
        cooldown(0, move.cooldown, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 10);
        if (move == Move.NEEDLE_PURSUIT) {
            // The phase counter holds one tick before its end. Tick 20 arms release; it never fires on its own.
            setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, PURSUIT_RELEASE_TICKS + 1);
        }
        else if (move.hold()) {
            setButtonHoldPhase(ActionPhase.PERFORM);
        }
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return move.stamina;
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return move != Move.NEEDLE_PURSUIT;
    }

    @Override
    public boolean noAdheringToUserOffset(StandPower standPower, StandEntity stand) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(StandEntity stand) {
        return true;
    }

    @Override
    public boolean lockStandManualMovement(StandPower standPower, StandEntity stand) {
        return true;
    }

    @Override
    public boolean retractsStandAfterAction(StandPower standPower, StandEntity stand, EntityActionInstance action) {
        return move != Move.NEEDLE_PURSUIT;
    }

    @Override
    protected boolean keepsTaskTargetType(StandEntity stand, ActionTarget target) {
        return move == Move.NEEDLE_PURSUIT && target.getType() == ActionTarget.TargetType.ENTITY;
    }

    @Override
    protected ConditionCheck checkStandEntityConditions(StandPower power, StandEntity stand) {
        if (move == Move.NEEDLE_PURSUIT && stand.getUser() != null && !stand.level().isClientSide()) {
            ActionTarget target = pursuitAim(stand.getUser());
            if (!keepsTaskTarget(stand, target)) {
                return ConditionCheck.NEGATIVE;
            }
        }
        return super.checkStandEntityConditions(power, stand);
    }

    private ActionTarget pursuitAim(LivingEntity user) {
        return overridePressedTarget(user, LivingComponentAction.getAimTarget(user)).resolveEntityId(user.level());
    }

    private static final class Action extends EntityActionInstance {
        private final TowerOfGrayAbility moveAbility;
        private ActionTarget pressTarget = ActionTarget.EMPTY;

        Action(TowerOfGrayAbility ability) {
            super(ability);
            this.moveAbility = ability;
        }

        @Override
        public void onActionSet(@Nullable EntityActionInstance previous) {
            if (!isRestoringInputLifecycle() && moveAbility.move == Move.NEEDLE_PURSUIT
                    && getPerformer() instanceof TowerOfGrayEntity stand && !stand.level().isClientSide()
                    && getPowerUser() != null) {
                ActionTarget target = moveAbility.pursuitAim(getPowerUser());
                Entity entity = null;
                if (moveAbility.keepsTaskTarget(stand, target)) {
                    pressTarget = target.copy();
                    entity = target.getEntity();
                }
                stand.pursue(entity instanceof LivingEntity living ? living : null);
            }
        }

        @Override
        protected ActionTarget pressedTaskTarget() {
            return pressTarget;
        }

        // The donor built Dodge and Gray Swarm with holdType(): another move replaces them at once.
        @Override
        public boolean isHoldTypeStandAction() {
            return moveAbility.move == Move.DODGE || moveAbility.move == Move.GRAY_SWARM;
        }

        // 1.16 canClickDuringTask: no donor move is chainable, so its own press is refused until it ends.
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }

        @Override
        public void onSetPhase(ActionPhase next) {
            if (!isRestoringInputLifecycle() && next == ActionPhase.PERFORM
                    && moveAbility.move != Move.NEEDLE_PURSUIT
                    && getPerformer() instanceof TowerOfGrayEntity stand && !stand.level().isClientSide()) {
                stand.beginMove(moveAbility.move);
            }
        }

        @Override
        public void actionPerformStart() {
            if (getPerformer() instanceof TowerOfGrayEntity stand && !stand.level().isClientSide()) {
                StandAbilityStamina.consume(ability, stand.getUserPower(), moveAbility.move.stamina, false);
            }
        }

        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return moveAbility.move == Move.NEEDLE_PURSUIT && getPhase() == ActionPhase.BUTTON_CHARGE
                    || moveAbility.move.hold() && getPhase() == ActionPhase.PERFORM;
        }

        @Override
        public void onButtonStopHold() {
            if (!moveAbility.move.hold() || getPerformer().level().isClientSide()) {
                return;
            }
            if (moveAbility.move == Move.NEEDLE_PURSUIT && getPhase() == ActionPhase.BUTTON_CHARGE
                    && getPhaseTick() >= PURSUIT_RELEASE_TICKS && moveAbility.canFireReleasedHold(this)) {
                setPhaseStart(ActionPhase.PERFORM);
                moveAbility.setCooldownOnUse(StandPower.get(getPowerUser()));
            }
            else if (getPhase() != ActionPhase.RECOVERY) {
                forceStop();
            }
            syncPhaseChanges();
        }
    }
}
