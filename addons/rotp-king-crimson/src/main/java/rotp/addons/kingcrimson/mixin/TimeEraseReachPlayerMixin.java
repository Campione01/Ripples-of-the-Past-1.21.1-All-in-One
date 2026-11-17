package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import rotp.addons.kingcrimson.entity.KingCrimsonDecoyRules;
import rotp.addons.kingcrimson.state.KingCrimsonEraseReach;

/** A Time Erase's user as a player: he touches nothing (items, orbs, arrows), no current moves him, his steps are unheard. */
@Mixin(Player.class)
public abstract class TimeEraseReachPlayerMixin {
    @Inject(method = "touch", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$touchesNothing(Entity entity, CallbackInfo ci) {
        // Nor does the player the core wraps round his decoy: what a decoy took would vanish with it.
        if (KingCrimsonEraseReach.isOutOfReach((Player) (Object) this)
                || KingCrimsonDecoyRules.decoyOf((Player) (Object) this) != null) {
            ci.cancel();
        }
    }

    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noCurrent(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Player) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    // The server plays a player's steps to everyone else; his own client keeps its own.
    @Inject(method = "getMovementEmission", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$silentSteps(CallbackInfoReturnable<Entity.MovementEmission> cir) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide() && KingCrimsonEraseReach.isOutOfReach(self)) {
            cir.setReturnValue(Entity.MovementEmission.NONE);
        }
    }
}
