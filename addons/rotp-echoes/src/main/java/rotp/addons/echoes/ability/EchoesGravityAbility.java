package rotp.addons.echoes.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.state.EchoesGravityService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class EchoesGravityAbility extends StandEntityAbility {
    public enum Mode {
        PROJECTILE(100.0F, 30),
        FREEZE_TARGET(150.0F, 40),
        HAND_FREEZE(150.0F, 40),
        FREEZE_SELF(50.0F, 20),
        INFUSE(75.0F, 20),
        // R772 F11: the master's self hand freeze; the cancel modes run through EchoesGravityCancelAbility
        HAND_FREEZE_SELF(50.0F, 20),
        FREEZE_CANCEL(0.0F, 0),
        HAND_FREEZE_CANCEL(0.0F, 0),
        FREEZE_SELF_CANCEL(0.0F, 0),
        HAND_FREEZE_SELF_CANCEL(0.0F, 0),
        INFUSE_CANCEL(0.0F, 0);

        private final float staminaCost;
        private final int recoveryTicks;

        Mode(float staminaCost, int recoveryTicks) {
            this.staminaCost = staminaCost;
            this.recoveryTicks = recoveryTicks;
        }

        public float staminaCost() {
            return staminaCost;
        }

        public int recoveryTicks() {
            return recoveryTicks;
        }
    }

    // R772 G3: master InitStands FREEZE_POSE and the author's 1.21.1 freeze abilities ("Freeze", renamed freeze)
    private static final ActionAnimIdentifier FREEZE_CLIP = ActionAnimIdentifier.getOrCreate("freeze", false);

    private final Mode mode;

    public EchoesGravityAbility(
            AbilityType<?> abilityType, AbilityId abilityId, Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new GravityAction(type, mode));
        this.mode = mode;
        if (mode == Mode.FREEZE_TARGET || mode == Mode.HAND_FREEZE) {
            // master InitStands rp_echoes_freeze / rp_echoes_hand_freeze: cooldown(60, 120)
            cooldown(60, 120);
        }
        usageGroup = mode == Mode.PROJECTILE
                ? AbilityUsageGroup.COMBAT
                : AbilityUsageGroup.UTILITY;
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, mode.recoveryTicks());
    }

    // 092 GravityProjectileAction has no gravity-item condition: without an
    // item it throws nothing, and StandEntityTask still pays its cost.
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || !RotpEchoes.isEchoesUser(power.getUser())) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        return base;
    }

    // the master gave the gravity projectile no pose, so it keeps the idle
    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return switch (mode) {
            case FREEZE_TARGET, HAND_FREEZE, FREEZE_SELF, HAND_FREEZE_SELF, INFUSE -> FREEZE_CLIP;
            default -> super.getEntityAnim(action);
        };
    }

    // The master's freeze needs an entity target, so a press that catches no one costs no cooldown.
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        if ((mode == Mode.FREEZE_TARGET || mode == Mode.HAND_FREEZE)
                && context != null
                && context.getUser() != null
                && !EchoesGravityService.hasFreezeTarget(context.getUser())) {
            return 0;
        }
        return super.getCooldown(context, ticksHeld);
    }

    public static final class GravityAction extends EntityActionInstance {
        private final Mode mode;

        public GravityAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        // 092 staminaCost: StandEntityTask performs the action, then pays
        // its cost and ignores the result, so a short bar is drained and
        // the action still happens.
        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            if (!(user instanceof ServerPlayer player) || power == null) {
                startRecovery();
                return;
            }
            EchoesGravityService.perform(player, mode);
            StandAbilityStamina.consume(
                    ability, power, mode.staminaCost(), false);
        }
    }
}
