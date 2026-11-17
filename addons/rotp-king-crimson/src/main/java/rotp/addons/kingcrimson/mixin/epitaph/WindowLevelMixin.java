package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/**
 * The journal of a window on the blocks (EPITAPH-ARCH 3.5): a block that changes while a participant acts is kept as it
 * was, once, and as he left it; a block entity that changes under him where he changed no block cannot be put back.
 * Nothing is held back here: the window is real play.
 */
@Mixin(Level.class)
public abstract class WindowLevelMixin {
    private static final String SET_BLOCK = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z";

    @Inject(method = SET_BLOCK, at = @At("HEAD"))
    private void kingCrimson$windowBlockChanging(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.blockChanging((Level) (Object) this, pos);
    }

    @Inject(method = SET_BLOCK, at = @At("RETURN"))
    private void kingCrimson$windowBlockChanged(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.blockChanged((Level) (Object) this, pos);
    }

    @Inject(method = "blockEntityChanged", at = @At("HEAD"))
    private void kingCrimson$windowBlockEntityChanged(BlockPos pos, CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.blockEntityChanged((Level) (Object) this, pos);
    }
}
