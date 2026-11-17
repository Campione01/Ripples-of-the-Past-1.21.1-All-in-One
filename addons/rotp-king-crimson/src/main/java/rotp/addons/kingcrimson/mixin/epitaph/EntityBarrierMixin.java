package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomIdentity;

/**
 * A phantom leaves its level by no portal, and reads team rules under the name of the entity it stands for (its own
 * UUID is in no team and is never put in one). And it equals only itself: the game calls two entities equal when their
 * int ids are, and a phantom has the id of its body.
 */
@Mixin(value = Entity.class, priority = 900)
public abstract class EntityBarrierMixin implements PhantomIdentity.Marked {
    @Unique
    private boolean kingCrimson$phantom;

    @Override
    public void kingCrimson$markPhantom() {
        kingCrimson$phantom = true;
    }

    @Override
    public boolean kingCrimson$isPhantom() {
        return kingCrimson$phantom;
    }

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$phantomEqualsItself(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (!PhantomIdentity.any) {
            return;
        }
        if (kingCrimson$phantom || PhantomIdentity.isMarked(other)) {
            cir.setReturnValue((Object) this == other);
        }
    }

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomChangeDimension(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(null);
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomCanUsePortal(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getScoreboardName", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomScoreboardName(CallbackInfoReturnable<String> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            java.util.UUID real = EpitaphSandbox.room().uuids().realOf(((Entity) (Object) this).getUUID());
            if (real != null) {
                cir.setReturnValue(real.toString());
            }
        }
    }
}
