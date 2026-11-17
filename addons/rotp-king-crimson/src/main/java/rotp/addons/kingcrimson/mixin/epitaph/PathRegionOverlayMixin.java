package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** Path finding reads blocks through a region of chunks, past the level: the overlay of a room answers there too. */
@Mixin(value = PathNavigationRegion.class, priority = 900)
public abstract class PathRegionOverlayMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            BlockState changed = EpitaphSandbox.room().overlay().get(pos);
            if (changed != null) {
                cir.setReturnValue(changed);
            }
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            BlockState changed = EpitaphSandbox.room().overlay().get(pos);
            if (changed != null) {
                cir.setReturnValue(changed.getFluidState());
            }
        }
    }
}
