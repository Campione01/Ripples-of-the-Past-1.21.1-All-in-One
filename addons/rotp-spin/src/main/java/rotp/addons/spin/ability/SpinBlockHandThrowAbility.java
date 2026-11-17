package rotp.addons.spin.ability;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.SpunBlockEntity;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

public final class SpinBlockHandThrowAbility extends SpinActionAbility {
    public SpinBlockHandThrowAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, 3, ThrowAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    private static boolean canThrow(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem
                && stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).equals(ItemContainerContents.EMPTY)
                && !stack.has(DataComponents.BLOCK_ENTITY_DATA);
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
        ItemStack stack = player.getMainHandItem();
        if (!canThrow(stack)) {
            return ConditionCheck.createNegative("rotp_spin.no_block_to_spin");
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return ConditionCheck.NEGATIVE;
        }
        return hasEnergy(player, SpinConfig.BLOCK_SPIN_ENERGY_COST.get()) ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("no_energy_spin");
    }

    public static final class ThrowAction extends EntityActionInstance {
        public ThrowAction(EntityActionType type) {
            super(type);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel server) || !(getPowerUser() instanceof Player player)
                    || !player.isAlive() || player.isSpectator()) {
                return;
            }
            ItemStack held = player.getMainHandItem();
            Item item = held.getItem();
            double cost = SpinConfig.BLOCK_SPIN_ENERGY_COST.get();
            if (!canThrow(held) || player.getCooldowns().isOnCooldown(item) || !hasEnergy(player, cost)) {
                return;
            }
            ItemStack expected = held.copyWithCount(1);
            var state = ((BlockItem) item).getBlock().defaultBlockState();
            SpunBlockEntity projectile = SpunBlockEntity.prepare(server, player, state);
            if (!server.addFreshEntity(projectile)) {
                return;
            }
            if (player.getMainHandItem() != held || !canThrow(held)
                    || !ItemStack.isSameItemSameComponents(held, expected) || !spend(player, cost)) {
                projectile.discard();
                return;
            }
            boolean consumed = !player.hasInfiniteMaterials();
            held.consume(1, player);
            projectile.commitPayload(consumed);
            player.getCooldowns().addCooldown(item, SpinConfig.BLOCK_SPIN_COOLDOWN_TICKS.get());
            SpinBlockThrowAbility.playBlockThrow(server, player);
        }
    }
}
