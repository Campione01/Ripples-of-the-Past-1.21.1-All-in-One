package rotp.addons.spin.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.item.SteelBallItem;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

public final class SpinBallThrowAbility extends SpinActionAbility {
    private static HolsterThrow holster = new HolsterThrow() {
        @Override public boolean hasBall(Player player) { return false; }
        @Override public boolean throwBall(ServerPlayer player) { return false; }
    };

    public SpinBallThrowAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, 1, ThrowAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    public interface HolsterThrow {
        boolean hasBall(Player player);
        boolean throwBall(ServerPlayer player);
    }

    public static void setHolsterThrow(HolsterThrow handler) {
        holster = handler;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive() || !(context.getUser() instanceof Player player) || player.isSpectator()) {
            return check.isPositive() ? ConditionCheck.NEGATIVE : check;
        }
        if (player.getCooldowns().isOnCooldown(SpinAddon.STEEL_BALL.get())) {
            return ConditionCheck.NEGATIVE;
        }
        InteractionHand hand = SteelBallItem.handWithBall(player, false);
        if (hand != null) {
            return player.getCooldowns().isOnCooldown(player.getItemInHand(hand).getItem())
                    ? ConditionCheck.NEGATIVE : ConditionCheck.POSITIVE;
        }
        return holster.hasBall(player) ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("rotp_spin.no_steel_ball");
    }

    public static final class ThrowAction extends EntityActionInstance {
        public ThrowAction(EntityActionType type) {
            super(type);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel server) || !(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            InteractionHand hand = SteelBallItem.handWithBall(player, false);
            if (hand == null) {
                holster.throwBall(player);
            }
            else if (SteelBallItem.throwFromHand(server, player, hand, 0)) {
                player.swing(hand, true);
            }
        }
    }
}
