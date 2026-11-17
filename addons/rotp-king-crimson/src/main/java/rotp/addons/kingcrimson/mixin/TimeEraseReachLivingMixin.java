package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import rotp.addons.kingcrimson.state.KingCrimsonEraseReach;

/** A Time Erase's user and his Stand among the living: not pushed, not pushing, not seen, not picked, not drowning. */
@Mixin(LivingEntity.class)
public abstract class TimeEraseReachLivingMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$notPushable(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$pushesNobody(CallbackInfo ci) {
        if (KingCrimsonEraseReach.isOutOfReach((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    // Mob targeting and sensors go through this, as for a spectator.
    @Inject(method = "canBeSeenByAnyone", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$unseen(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$notPickable(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "decreaseAirSupply", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$keepsHisAir(int currentAir, CallbackInfoReturnable<Integer> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((LivingEntity) (Object) this)) {
            cir.setReturnValue(currentAir);
        }
    }
}
