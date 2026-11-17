package rotp.addons.mandom.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import rotp.addons.mandom.state.MandomJournalHolder;
import rotp.addons.mandom.state.MandomRewindService;
import rotp.addons.mandom.state.MandomRewindService.BlockPreimage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelBlockJournalMixin implements MandomJournalHolder {
    @Unique
    private volatile Object rotpMandom$journal;

    @Override
    public Object rotpMandom$journal() {
        return rotpMandom$journal;
    }

    @Override
    public void rotpMandom$setJournal(Object journal) {
        rotpMandom$journal = journal;
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/"
                    + "BlockState;II)Z",
            at = @At("HEAD"))
    private void rotpMandom$captureBeforeSetBlock(
            BlockPos position,
            BlockState newState,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callback,
            @Share("rotpMandomPreimage")
            LocalRef<BlockPreimage> preimage) {
        if (rotpMandom$journal != null) {
            preimage.set(MandomRewindService.captureBlockPreimage(
                    (Level) (Object) this, position));
        }
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/"
                    + "BlockState;II)Z",
            at = @At("RETURN"))
    private void rotpMandom$recordAfterSetBlock(
            BlockPos position,
            BlockState newState,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callback,
            @Share("rotpMandomPreimage")
            LocalRef<BlockPreimage> preimage) {
        BlockPreimage captured = preimage.get();
        if (captured != null && callback.getReturnValueZ()) {
            MandomRewindService.recordBlockMutation(
                    (Level) (Object) this, captured);
        }
    }
}
