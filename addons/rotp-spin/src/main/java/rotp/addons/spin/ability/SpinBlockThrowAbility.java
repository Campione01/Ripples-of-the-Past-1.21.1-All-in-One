package rotp.addons.spin.ability;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.SpinBlock;
import rotp.addons.spin.entity.SpunBlockEntity;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;

public final class SpinBlockThrowAbility extends SpinActionAbility {
    public SpinBlockThrowAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, 3, ThrowAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
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
        if (findBlock(player, LivingComponentAction.getAimTarget(player)) == null) {
            return ConditionCheck.createNegative("rotp_spin.no_block_to_spin");
        }
        return hasEnergy(player, SpinConfig.BLOCK_SPIN_ENERGY_COST.get()) ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("no_energy_spin");
    }

    @Nullable
    private static BlockHitResult findBlock(Player player, @Nullable ActionTarget target) {
        if (target == null || target.getType() != ActionTarget.TargetType.BLOCK) {
            return null;
        }
        Level level = player.level();
        BlockPos pos = target.getBlockPos();
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !canRip(level, pos, level.getBlockState(pos))) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(SpinConfig.BLOCK_SPIN_REACH.get()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
    }

    private static boolean canRip(Level level, BlockPos pos, BlockState state) {
        return SpinBlock.canSpin(state.isAir(), state.getDestroySpeed(level, pos), state.getFluidState().isEmpty(),
                state.hasBlockEntity() || level.getBlockEntity(pos) != null);
    }

    public static final class ThrowAction extends EntityActionInstance {
        public ThrowAction(EntityActionType type) {
            super(type);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPowerUser() instanceof Player player) {
                captureActionTargetFromAim(player);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel server) || !(getPowerUser() instanceof ServerPlayer player)
                    || !(ability instanceof SpinBlockThrowAbility throwAbility) || !player.isAlive() || player.isSpectator()) {
                return;
            }
            BlockHitResult hit = findBlock(player, getActionTargetSnapshot(server));
            double cost = SpinConfig.BLOCK_SPIN_ENERGY_COST.get();
            if (hit == null || !hasEnergy(player, cost)) {
                return;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = server.getBlockState(pos);
            if (!server.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), ItemStack.EMPTY)
                    || CommonHooks.fireBlockBreak(server, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()
                    || server.getBlockState(pos) != state) {
                return;
            }
            SpunBlockEntity projectile = SpunBlockEntity.prepare(server, player, state);
            if (!server.addFreshEntity(projectile)) {
                return;
            }
            if (server.getBlockState(pos) != state || !canRip(server, pos, state) || !spend(player, cost)
                    || !server.removeBlock(pos, false)) {
                projectile.discard();
                return;
            }
            projectile.commitPayload(true);
            throwAbility.beginCooldown(player, SpinConfig.BLOCK_SPIN_COOLDOWN_TICKS.get());
            playBlockThrow(server, player);
        }
    }

    static void playBlockThrow(ServerLevel level, Player player) {
        level.playSound(null, player, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }
}
