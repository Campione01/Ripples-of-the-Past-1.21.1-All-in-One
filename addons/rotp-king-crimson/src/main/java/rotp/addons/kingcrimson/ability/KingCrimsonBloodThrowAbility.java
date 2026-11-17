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

import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.state.KingCrimsonBloodThrowService;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Blood Throw (以血蒙眼), the variation of Time Erase's slot (owner 2026-11-07: "在時刪時可以通過時刪結束這個鍵的變體操作來...釋放以血矇眼").
 * Time Erase is held, so its key is down for as long as the erase lasts: letting it go with the variation key down
 * throws and leaves the erase running with its key up (KingCrimsonTimeEraseAbility), and from then on the erase key
 * with the variation key throws again through this ability, while the plain key ends the erase. Outside an erase it
 * does not exist: the slot is Time Erase alone, whatever the variation key does.
 */
public final class KingCrimsonBloodThrowAbility extends Ability {
    public KingCrimsonBloodThrowAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        cooldown(KingCrimsonPhase5Spec.BLOOD_THROW_COOLDOWN_SECONDS * 20);
    }

    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld), KingCrimsonConfig.bloodThrowCooldownTicks());
    }

    // The service sets it with the rest of the price, and only for a throw that left his hand.
    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    // On a key of its own it is thrown with the erase key still held, and must not end that hold.
    @Override
    public boolean endsOtherHoldOnPress() {
        return false;
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context)
                && context.getUser() instanceof Player player
                && KingCrimsonTemporalStateService.isErasing(player);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck stamina = StandAbilityStamina.check(context, KingCrimsonConfig.bloodThrowStamina());
        if (!stamina.isPositive()) {
            return stamina;
        }
        // The server alone knows where the eraser sees the other players.
        if (context.getUser() instanceof ServerPlayer player && !KingCrimsonBloodThrowService.hasTarget(player)) {
            return ConditionCheck.createNegative(
                    Component.translatable("rotp_kingcrimson.message.bloodthrow_no_target"));
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        bufferingState.isActionSuccess = !level.isClientSide()
                && user instanceof ServerPlayer player
                && KingCrimsonBloodThrowService.throwAtAim(player);
        return null;
    }
}
