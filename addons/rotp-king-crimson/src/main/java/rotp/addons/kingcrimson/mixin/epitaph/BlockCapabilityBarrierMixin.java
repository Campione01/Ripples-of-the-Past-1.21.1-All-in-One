package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** Block capabilities (item handlers, tanks, energy) are not there for a phantom, as block entities are not. */
@Mixin(value = BlockCapability.class, remap = false, priority = 900)
public abstract class BlockCapabilityBarrierMixin {
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingCrimson$roomCapability(Level level, BlockPos pos, @Nullable BlockState state,
            @Nullable BlockEntity blockEntity, Object context, CallbackInfoReturnable<Object> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(null);
        }
    }
}
