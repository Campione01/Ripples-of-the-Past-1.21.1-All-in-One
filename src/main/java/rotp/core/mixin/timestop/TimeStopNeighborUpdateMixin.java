package rotp.core.mixin.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Every neighbour update of the level's updater reaches its block here, whoever sent it. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class TimeStopNeighborUpdateMixin {
	@Inject(method = "handleNeighborChanged", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$holdNeighborUpdateInTimeStop(Level level, BlockPos pos, Block source, BlockPos sourcePos,
			boolean movedByPiston, CallbackInfo ci) {
		if (TimeStopState.holdNeighborUpdate(level, (BlockState) (Object) this, pos, source, sourcePos, movedByPiston)) {
			ci.cancel();
		}
	}
}
