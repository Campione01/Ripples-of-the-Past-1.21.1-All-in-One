package rotp.addons.beachboy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.addons.beachboy.util.BeachBoyVanillaMotion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 1.16 PuppetedLivingTravelMixin: a creature steered on the remote hook moves only by the user's keys. */
@Mixin(LivingEntity.class)
public abstract class PuppetedLivingTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void rotpBeachBoy$skipUnauthoredPuppetTravel(Vec3 travelVector, CallbackInfo ci) {
        if (!BeachBoyVanillaMotion.isApplyingControl() && BeachBoyStandUtil.isRemotePuppeted((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
