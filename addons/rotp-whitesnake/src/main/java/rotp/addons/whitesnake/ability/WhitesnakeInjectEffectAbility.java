package rotp.addons.whitesnake.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.buffdisc.DiscItem;
import rotp.addons.whitesnake.network.OpenInjectEffectPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * PATCH1 InjectEffect (staminaCost 10, resolveLevelToUnlock 0, arms): with a blank Buff disc in either hand the
 * user picks an effect on the inject screen, and the server writes it on the disc.
 */
public final class WhitesnakeInjectEffectAbility
        extends NoPoseStandEntityAbility {
    public static final float STAMINA_COST = 10.0F;
    // how long the opened screen may still inject
    public static final int SELECTION_WINDOW_TICKS = 1200;
    private static final String ABILITY_NAME = "inject_effect";

    public WhitesnakeInjectEffectAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, InjectEffectAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public boolean canContinueAction(EntityActionInstance action) {
        return WhitesnakeAddon.isActiveWhitesnakeUser(
                action.getPowerUser(), ABILITY_NAME);
    }

    // PATCH1 checkStandConditions: a player holding a blank disc, refused silently otherwise
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        return context.getUser() instanceof Player player
                        && (DiscItem.isBlank(player.getMainHandItem())
                                || DiscItem.isBlank(
                                        player.getOffhandItem()))
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    public static final class InjectEffectAction
            extends EntityActionInstance {
        public InjectEffectAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user instanceof ServerPlayer player
                    && WhitesnakeAddon.isActiveWhitesnakeUser(
                            player, ABILITY_NAME)) {
                WhitesnakeAddon.buffDiscState(player).grantInjectPermit(
                        player.level().getGameTime()
                                + SELECTION_WINDOW_TICKS);
                PacketDistributor.sendToPlayer(
                        player, new OpenInjectEffectPayload());
            }
            // 1.16 StandEntityTask paid the cost after the perform
            StandPower power = user != null ? StandPower.get(user) : null;
            if (power != null) {
                StandAbilityStamina.consume(
                        ability, power, STAMINA_COST, false);
            }
        }
    }
}
