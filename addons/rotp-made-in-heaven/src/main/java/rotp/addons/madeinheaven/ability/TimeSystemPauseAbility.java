package rotp.addons.madeinheaven.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;

import rotp.addons.madeinheaven.MadeInHeavenClientConfig;
import rotp.addons.madeinheaven.MadeInHeavenTimeSystemIcons;
import rotp.addons.madeinheaven.state.TimeAccelerationService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class TimeSystemPauseAbility extends Ability {
    public TimeSystemPauseAbility(AbilityType<?> type, AbilityId id) {
        super(type, id);
    }

    @Override
    public boolean endsOtherHoldOnPress() {
        return false;
    }

    // An available variation owns its modifier's hold of the slot, so with no session of the user's the base hold must keep it.
    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context) && TimeAccelerationService.isSessionOwner(context.getUser());
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        return (user instanceof ServerPlayer || user.level().isClientSide())
                && user.isAlive() && TimeAccelerationService.isSessionOwner(user)
                ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (!level.isClientSide() && user instanceof ServerPlayer player) {
            TimeAccelerationService.togglePause(player);
        }
    }

    @Override
    public Component getName(Power<?> context) {
        LivingEntity user = context == null ? null : context.getUser();
        if (TimeAccelerationService.isSessionOwner(user)
                && !TimeAccelerationService.isActiveOwner(user)) {
            return Component.translatableWithFallback(
                    getTranslationKey(".resume"), "Resume Time Acceleration");
        }
        return Component.translatableWithFallback(
                getTranslationKey(), "Pause Time Acceleration");
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return MadeInHeavenTimeSystemIcons.spriteName(
                MadeInHeavenClientConfig.ENABLE_NEW_TIME_SYSTEM_ICONS.get(),
                context != null && TimeAccelerationService.isActiveOwner(context.getUser()));
    }
}
