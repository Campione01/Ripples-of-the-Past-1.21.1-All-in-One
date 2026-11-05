package rotp.addons.echoes.ability;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
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

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.client.EchoesOnomatopoeiaScreen;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.state.EchoesOnomatopoeiaService;
import rotp.addons.echoes.state.EchoesUserState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * R772 F11: ACT2's onomatopoeia actions (master InitStands 192-249). The tail's attach is a Stand action, the rest are
 * plain actions that need no Stand out.
 */
public final class EchoesOnomatopoeiaAbility extends Ability {
    // master InitStands.LAUNCH_POSE; the clip holds the pose EchoesACT2Model.forceLaunchPose forced
    private static final ActionAnimIdentifier LAUNCH_ANIM = ActionAnimIdentifier.getOrCreate("launch", false);

    public enum Mode {
        // master OnomatopoeiaSelectAction: the word picker
        SELECT(0.0F),
        // master OnomatopoeiaCancelAction: no word chosen
        CANCEL(0.0F),
        // master EchoesOnomatopoeiaAttachCancelAction: the words come off the others and the tail returns
        ATTACH_CANCEL(0.0F),
        // master EchoesOnomatopoeiaSelfAction, staminaCost(20)
        SELF(20.0F),
        // master EchoesOnomatopoeiaSelfCancelAction
        SELF_CANCEL(0.0F),
        // master EchoesTailNeedleRecallAction
        TAIL_NEEDLE_RECALL(0.0F),
        // master EchoesOnomatopoeiaEntityToggleAction: a word put on a block becomes a thing, or a mark again
        ENTITY_TOGGLE(0.0F);

        private final float staminaCost;

        Mode(float staminaCost) {
            this.staminaCost = staminaCost;
        }
    }

    private final Mode mode;

    public EchoesOnomatopoeiaAbility(AbilityType<?> abilityType, AbilityId abilityId, Mode mode) {
        super(abilityType, abilityId);
        this.mode = mode;
        usageGroup = AbilityUsageGroup.UTILITY;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        return isAct2User(context) ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            if (mode == Mode.SELECT) {
                EchoesOnomatopoeiaScreen.openWindowOnClick(user);
            }
            return;
        }
        if (!(user instanceof ServerPlayer player)) {
            return;
        }
        switch (mode) {
            case SELECT -> {}
            case CANCEL -> EchoesOnomatopoeiaService.select(player, null);
            case ATTACH_CANCEL -> EchoesOnomatopoeiaService.takeBackMarks(player);
            case SELF -> {
                StandPower power = StandPower.get(player);
                if (power != null && EchoesOnomatopoeiaService.attachToSelf(player)) {
                    StandAbilityStamina.consume(this, power, mode.staminaCost, false);
                }
            }
            case SELF_CANCEL -> EchoesOnomatopoeiaService.takeBackSelfMark(player);
            case TAIL_NEEDLE_RECALL -> EchoesOnomatopoeiaService.recall(player);
            case ENTITY_TOGGLE -> {
                EchoesUserState state = RotpEchoes.userState(player);
                state.setEntityForm(!state.entityForm());
                player.displayClientMessage(Component.translatable(state.entityForm()
                        ? "rotp_echoes.message.entity_form_on"
                        : "rotp_echoes.message.entity_form_off"), true);
            }
        }
    }

    static boolean isAct2User(Power<?> context) {
        return context instanceof StandPower power && power.getPowerType() == EchoesStands.ECHOES_ACT2.get();
    }

    /**
     * master EchoesOnomatopoeiaAttachAction: staminaCost(30), cooldown(10, 20), the arms, LAUNCH_POSE (ACT2's launch
     * clip); the chosen word lands on the first living target in the user's aim within 5 blocks.
     */
    public static final class Attach extends StandEntityAbility {
        public Attach(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, AttachAction::new);
            usageGroup = AbilityUsageGroup.UTILITY;
            cooldown(10, 20);
            standAutoSummonMode(AutoSummonMode.FULL);
            partsRequired(StandPart.ARMS);
            setDefaultPhaseLength(ActionPhase.WINDUP, 4);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 10);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck base = super.checkSpecificConditions(context);
            if (!base.isPositive()) {
                return base;
            }
            return isAct2User(context) ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
        }

        @Override
        public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
            return LAUNCH_ANIM;
        }
    }

    /**
     * master EchoesTailNeedleAction: staminaCost(50), cooldown(20, 40), the arms, LAUNCH_POSE (ACT2's launch clip);
     * ACT2 shoots its tail with the chosen word (EchoesTailNeedleEntity).
     */
    public static final class TailNeedle extends StandEntityAbility {
        public TailNeedle(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, TailNeedleAction::new);
            usageGroup = AbilityUsageGroup.COMBAT;
            cooldown(20, 40);
            standAutoSummonMode(AutoSummonMode.FULL);
            partsRequired(StandPart.ARMS);
            setDefaultPhaseLength(ActionPhase.WINDUP, 4);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 15);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            ConditionCheck base = super.checkSpecificConditions(context);
            if (!base.isPositive()) {
                return base;
            }
            return isAct2User(context) ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
        }

        @Override
        public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
            return LAUNCH_ANIM;
        }
    }

    private static final class TailNeedleAction extends EntityActionInstance {
        private TailNeedleAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide() || !(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power != null && EchoesOnomatopoeiaService.shootNeedle(player)) {
                StandAbilityStamina.consume(ability, power, 50.0F, false);
            }
        }
    }

    private static final class AttachAction extends EntityActionInstance {
        private AttachAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide() || !(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power != null && EchoesOnomatopoeiaService.attachInAim(player)) {
                StandAbilityStamina.consume(ability, power, 30.0F, false);
            }
        }
    }
}
