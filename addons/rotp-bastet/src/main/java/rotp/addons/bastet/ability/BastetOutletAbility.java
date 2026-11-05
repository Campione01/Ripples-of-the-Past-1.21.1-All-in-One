package rotp.addons.bastet.ability;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.bastet.block.BastetOutletBlock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** 1.16 BastetPlaceOutlet and BastetRemoveOutlet, StandEntityActions held to fire. */
public final class BastetOutletAbility extends StandEntityAbility {
    /** 1.16 InitStands: holdToFire(10, false), standRecoveryTicks(20), staminaCost; no cooldown. */
    public static final int HOLD_TO_FIRE_TICKS = 10;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 20;

    public enum Mode {
        PLACE(30.0F),
        REMOVE(0.0F);

        public final float staminaCost;

        Mode(float staminaCost) {
            this.staminaCost = staminaCost;
        }
    }

    private final Mode mode;

    public BastetOutletAbility(AbilityType<?> abilityType, AbilityId abilityId, Mode mode) {
        super(abilityType, abilityId, actionType -> new BastetOutletAction(actionType, mode));
        this.mode = mode;
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        // 1.16 StandEntityAction's default standPerformDuration
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return mode.staminaCost;
    }

    public static final class BastetOutletAction extends EntityActionInstance {
        private final Mode mode;

        public BastetOutletAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        // 1.16 holdToFire(10, false): letting go before the charge completes cancels it
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide() || !(getPerformer() instanceof StandEntity)) {
                return;
            }
            // 1.16 cast the power's user to PlayerEntity
            LivingEntity user = getPowerUser();
            if (!(user instanceof Player player)) {
                return;
            }
            switch (mode) {
                case PLACE -> BastetOutletBlock.placeOutlet(level, player);
                case REMOVE -> BastetOutletBlock.removeOutlets(level, player);
            }
            // 1.16 StandEntityTask paid the staminaCost after standPerform, whatever came of it
            StandAbilityStamina.consume(ability, StandPower.get(user), mode.staminaCost, false);
        }

        // 1.16 StandEntityAction.canClickDuringTask: neither action was chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
