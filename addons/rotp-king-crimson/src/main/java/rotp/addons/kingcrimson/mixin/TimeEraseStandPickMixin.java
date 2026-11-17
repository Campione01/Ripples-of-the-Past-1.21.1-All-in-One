package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.state.KingCrimsonEraseReach;
import rotp.core.powersystem.standpower.entity.StandEntity;

@Mixin(value = StandEntity.class, remap = false)
public abstract class TimeEraseStandPickMixin {
    @Shadow public abstract LivingEntity getUser();

    // The core lets the void and /kill through to a Stand before it asks anything: an eraser's Stand is asked here.
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$outOfReach(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((StandEntity) (Object) this) && !KingCrimsonEraseReach.pierces(source)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$ignoreLocalProjection(CallbackInfoReturnable<Boolean> cir) {
        if (getUser() instanceof KingCrimsonTimeEraseDecoyEntity decoy
                && decoy.getOwner() instanceof Player player && player.isLocalPlayer()
                || KingCrimsonEraseReach.isOutOfReach((StandEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
