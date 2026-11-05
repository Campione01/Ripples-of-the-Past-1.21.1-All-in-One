package rotp.addons.echoes.ability;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.echoes.client.EchoesSoundClient;
import rotp.addons.echoes.client.EchoesSoundSelectScreen;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.state.EchoesSoundService;
import rotp.addons.echoes.state.EchoesUserState;
import rotp.addons.echoes.RotpEchoes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * R772 F11: ACT1's sound actions (master InitStands 114-165). All are plain actions that need no Stand out, as the
 * port's ACT2 word actions; the mark and give ones aim with the user's own aim (the master's block and entity targets).
 * The self slot shows its cancel while the self sound plays, the mark slot shows "clear mark" while the user aims at
 * his own mark (the master's replaceAction).
 */
public final class EchoesSoundAbility extends Ability {
    public enum Mode {
        // master SoundSelectAction: the sound picker
        SELECT,
        // master SoundSelfAction / SoundSelfCancelAction
        SELF,
        SELF_CANCEL,
        // master SoundMarkBlockAction / SoundClearMarkAction / SoundClearAllMarksAction
        MARK_BLOCK,
        CLEAR_MARK,
        CLEAR_ALL_MARKS,
        // master SoundGiveAction, staminaCost(50) / SoundGiveCancelAction / SoundGiveCancelAllAction
        GIVE,
        GIVE_CANCEL,
        GIVE_CANCEL_ALL
    }

    private static final float GIVE_STAMINA = 50.0F;

    private final Mode mode;

    public EchoesSoundAbility(AbilityType<?> abilityType, AbilityId abilityId, Mode mode) {
        super(abilityType, abilityId);
        this.mode = mode;
        usageGroup = mode == Mode.GIVE ? AbilityUsageGroup.COMBAT : AbilityUsageGroup.UTILITY;
        if (mode == Mode.GIVE) {
            cooldown(60, 120);
        }
        else if (mode == Mode.GIVE_CANCEL) {
            cooldown(20);
        }
    }

    @Override
    public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
        LivingEntity user = context != null ? context.getUser() : null;
        if (!(user instanceof Player player)) {
            return this;
        }
        String other = null;
        if (mode == Mode.SELF && selfSound(player)) {
            other = "sound_self_cancel";
        }
        else if (mode == Mode.MARK_BLOCK && aimsAtOwnMark(player)) {
            other = "sound_clear_mark";
        }
        Ability replacement = other != null ? context.getMoveset().getAbility(other) : null;
        return replacement != null ? replacement : this;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context instanceof StandPower power) || power.getPowerType() != EchoesStands.ECHOES_ACT1.get()
                || !(power.getUser() instanceof Player player)) {
            return ConditionCheck.NEGATIVE;
        }
        ActionTarget aim = LivingComponentAction.getAimTarget(player);
        return switch (mode) {
            case SELECT, SELF_CANCEL -> ConditionCheck.POSITIVE;
            case SELF -> selfSound(player) || hasSound(player) ? ConditionCheck.POSITIVE : needSound();
            case MARK_BLOCK -> !hasSound(player) ? needSound()
                    : aim.getType() == ActionTarget.TargetType.BLOCK ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
            case CLEAR_MARK -> aimsAtOwnMark(player) ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
            case CLEAR_ALL_MARKS -> markCount(player) > 0
                    ? ConditionCheck.POSITIVE : ConditionCheck.createNegative(Component.translatable("rotp_echoes.message.no_marks"));
            case GIVE -> !hasSound(player) ? needSound()
                    : aimedLiving(player, aim) != null ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
            case GIVE_CANCEL -> {
                LivingEntity target = aimedLiving(player, aim);
                yield target != null && hasGiven(player, target) && !EchoesSoundService.isImmune(target)
                        ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
            }
            case GIVE_CANCEL_ALL -> hasAnyGive(player)
                    ? ConditionCheck.POSITIVE : ConditionCheck.createNegative(Component.translatable("rotp_echoes.message.no_granted_sounds"));
        };
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            if (mode == Mode.SELECT) {
                EchoesSoundSelectScreen.openWindowOnClick(user);
            }
            return;
        }
        if (!(user instanceof ServerPlayer player)) {
            return;
        }
        ActionTarget aim = LivingComponentAction.getAimTarget(player);
        switch (mode) {
            case SELECT -> {}
            case SELF -> EchoesSoundService.toggleSelf(player);
            case SELF_CANCEL -> EchoesSoundService.stopSelf(player);
            case MARK_BLOCK -> {
                if (aim.getType() == ActionTarget.TargetType.BLOCK) {
                    EchoesSoundService.markBlock(player, aim.getBlockPos(), aim.getFace());
                }
            }
            case CLEAR_MARK -> {
                if (aim.getType() == ActionTarget.TargetType.BLOCK) {
                    EchoesSoundService.takeBackMark(player, aim.getBlockPos());
                }
            }
            case CLEAR_ALL_MARKS -> EchoesSoundService.clearMarks(player);
            case GIVE -> {
                LivingEntity target = aimedLiving(player, aim);
                StandPower power = StandPower.get(player);
                if (target != null && power != null && EchoesSoundService.give(player, target)) {
                    StandAbilityStamina.consume(this, power, GIVE_STAMINA, false);
                }
            }
            case GIVE_CANCEL -> {
                LivingEntity target = aimedLiving(player, aim);
                if (target != null) {
                    EchoesSoundService.cancelGive(player, target);
                }
            }
            case GIVE_CANCEL_ALL -> EchoesSoundService.cancelAllGives(player);
        }
    }

    private static ConditionCheck needSound() {
        return ConditionCheck.createNegative(Component.translatable("rotp_echoes.message.need_sound"));
    }

    @Nullable
    private static LivingEntity aimedLiving(Player player, ActionTarget aim) {
        return aim.getType() == ActionTarget.TargetType.ENTITY && aim.getEntity() instanceof LivingEntity living
                && living != player && living.isAlive() ? living : null;
    }

    private static boolean aimsAtOwnMark(Player player) {
        ActionTarget aim = LivingComponentAction.getAimTarget(player);
        return aim.getType() == ActionTarget.TargetType.BLOCK && EchoesSoundService.isOwnMark(player, aim.getBlockPos());
    }

    // the user's client knows its state from EchoesSoundPayloads.Sync and GiveTargets
    private static boolean hasSound(Player player) {
        return player.level().isClientSide() ? EchoesSoundClient.sound() != null : state(player).sound() != null;
    }

    private static boolean selfSound(Player player) {
        return player.level().isClientSide() ? EchoesSoundClient.selfSound() : state(player).selfSound();
    }

    private static int markCount(Player player) {
        return player.level().isClientSide()
                ? EchoesSoundClient.marks()
                : rotp.addons.echoes.block.EchoesSoundMarkBlockEntity.marksOf(player.getUUID()).size();
    }

    private static boolean hasGiven(Player player, LivingEntity target) {
        return player.level().isClientSide()
                ? EchoesSoundClient.gaveTo(target.getUUID())
                : EchoesSoundService.hasGiven(player, target);
    }

    private static boolean hasAnyGive(Player player) {
        return player.level().isClientSide()
                ? EchoesSoundClient.hasGiveTargets()
                : player instanceof ServerPlayer serverPlayer && EchoesSoundService.hasAnyGive(serverPlayer);
    }

    private static EchoesUserState state(Player player) {
        return RotpEchoes.userState(player);
    }
}
