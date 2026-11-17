package rotp.addons.kingcrimson.ability;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.HeldInput;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseStepService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Time Erase step (時刪快速移動, owner 2026-11-07), a regular skill with a slot of its own, one Resolve level under
 * Time Erase: a click moves its user to where he aims through erased time (KingCrimsonTimeEraseStepService). Its
 * cooldown and its cost come with the trip, as The World's time stop blink takes them.
 */
public final class KingCrimsonTimeEraseStepAbility extends Ability {
    public KingCrimsonTimeEraseStepAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    // The service sets it by the ticks the trip implies.
    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        // Not in an erase of his own; another's is nothing to him.
        boolean busy = context.getUser() instanceof ServerPlayer onServer
                ? !KingCrimsonTemporalStateService.canStartTimeErase(onServer)
                : context.getUser() instanceof Player onClient && onClient.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                        && !KingCrimsonAddon.temporalState(onClient).isIdle();
        if (busy) {
            return ConditionCheck.createNegative(Component.translatable("rotp_kingcrimson.message.temporal_state_active"));
        }
        return StandAbilityStamina.check(context, KingCrimsonTimeEraseStepService.cost(0));
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        bufferingState.isActionSuccess = !level.isClientSide()
                && user instanceof ServerPlayer player
                && KingCrimsonTimeEraseStepService.step(player);
        return null;
    }

    /** What the shortest step costs; the trip's ticks add to it. */
    @Override
    public float getStaminaCost(Power<?> context) {
        return KingCrimsonTimeEraseStepService.cost(0);
    }
}
