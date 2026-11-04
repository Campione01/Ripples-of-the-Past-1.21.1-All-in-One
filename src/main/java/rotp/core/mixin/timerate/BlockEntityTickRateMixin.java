package rotp.core.mixin.timerate;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.time.LevelTimeRates;
import rotp.core.subsystems.time.LevelTickRateState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/**
 * A server level's ticking block entities at its BLOCK_ENTITIES rate: held back on the ticks the rate gives none, run
 * again after their own tick above 1 within the level's budget. Each extra run is a whole tick of the wrapper, so the
 * stopped-time and validity checks hold for it too.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BlockEntityTickRateMixin {
	@Shadow @Final private BlockEntity blockEntity;
	@Unique private boolean jojo_ripples$extraTick;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
	private void jojo_ripples$holdBackBlockEntity(CallbackInfo ci) {
		if (!jojo_ripples$extraTick && LevelTimeRates.hasProviders() && blockEntity.getLevel() instanceof ServerLevel level
				&& LevelTickRateState.blockEntitySteps(level) <= 0) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"), require = 1)
	private void jojo_ripples$runExtraBlockEntityTicks(CallbackInfo ci) {
		if (jojo_ripples$extraTick || !LevelTimeRates.hasProviders() || !(blockEntity.getLevel() instanceof ServerLevel level)) {
			return;
		}
		int steps = LevelTickRateState.blockEntitySteps(level);
		if (steps <= 1) {
			return;
		}
		int extra = LevelTickRateState.takeExtraBlockEntityTicks(level, steps - 1);
		jojo_ripples$extraTick = true;
		try {
			for (int i = 0; i < extra && !blockEntity.isRemoved(); i++) {
				((TickingBlockEntity) (Object) this).tick();
			}
		}
		finally {
			jojo_ripples$extraTick = false;
		}
	}
}
