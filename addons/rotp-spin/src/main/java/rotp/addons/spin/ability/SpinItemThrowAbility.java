package rotp.addons.spin.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.SpunItemEntity;
import rotp.addons.spin.item.SteelBallItem;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

public final class SpinItemThrowAbility extends SpinActionAbility {
    public SpinItemThrowAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, 3, ThrowAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    private static boolean canThrow(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof SteelBallItem);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player) || player.isSpectator()) {
            return ConditionCheck.NEGATIVE;
        }
        if (!canThrow(player.getMainHandItem())) {
            return ConditionCheck.createNegative("rotp_spin.no_item_to_spin");
        }
        return hasEnergy(player, SpinConfig.ITEM_SPIN_ENERGY_COST.get()) ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("no_energy_spin");
    }

    public static final class ThrowAction extends EntityActionInstance {
        public ThrowAction(EntityActionType type) {
            super(type);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel server) || !(getPowerUser() instanceof Player player)
                    || !(ability instanceof SpinItemThrowAbility throwAbility) || !player.isAlive() || player.isSpectator()) {
                return;
            }
            ItemStack held = player.getMainHandItem();
            double cost = SpinConfig.ITEM_SPIN_ENERGY_COST.get();
            if (!canThrow(held) || !hasEnergy(player, cost)) {
                return;
            }
            ItemStack payload = held.copyWithCount(1);
            SpunItemEntity projectile = SpunItemEntity.prepare(server, player, payload);
            if (!server.addFreshEntity(projectile)) {
                return;
            }
            if (player.getMainHandItem() != held || !canThrow(held)
                    || !ItemStack.isSameItemSameComponents(held, payload) || !spend(player, cost)) {
                projectile.discard();
                return;
            }
            held.consume(1, player);
            throwAbility.beginCooldown(player, SpinConfig.ITEM_SPIN_COOLDOWN_TICKS.get());
            server.playSound(null, player, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }
}
