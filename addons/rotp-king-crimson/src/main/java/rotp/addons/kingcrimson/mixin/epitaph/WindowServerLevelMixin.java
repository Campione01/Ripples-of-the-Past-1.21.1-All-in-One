package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/**
 * The journal of a window on what comes into the level: an entity a participant made is his, and goes at the pull-back.
 * And what a block he changed does by itself a tick or two later (sand falls, water runs, fire eats) is his as well.
 */
@Mixin(ServerLevel.class)
public abstract class WindowServerLevelMixin {
    @Inject(method = "addEntity", at = @At("RETURN"))
    private void kingCrimson$windowEntityAdded(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!WindowJournal.active) {
            return;
        }
        if (cir.getReturnValue()) {
            WindowJournal.entityAdded(entity);
        }
    }

    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void kingCrimson$windowBlockTickActs(BlockPos pos, Block block, CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.actAt((ServerLevel) (Object) this, pos);
    }

    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void kingCrimson$windowFluidTickActs(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.actAt((ServerLevel) (Object) this, pos);
    }

    @Inject(method = {"tickBlock", "tickFluid"}, at = @At("RETURN"))
    private void kingCrimson$windowTickRests(CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.rest();
    }
}
