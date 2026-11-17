package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** One call below the level's block write and block entity read: the belt, should another mod replace those. */
@Mixin(value = LevelChunk.class, priority = 900)
public abstract class LevelChunkBarrierMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSetBlockState(BlockPos pos, BlockState state, boolean isMoving,
            CallbackInfoReturnable<BlockState> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code wrote a block into a chunk");
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)"
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationType,
            CallbackInfoReturnable<BlockEntity> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(null);
        }
    }
}
