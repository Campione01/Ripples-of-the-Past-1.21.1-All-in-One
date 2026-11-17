package rotp.addons.spin.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rotp.addons.spin.compat.SpinLoveTrain;

@Pseudo
@Mixin(targets = "rotp.addons.d4c.D4CCorpseService", remap = false)
abstract class SpinLoveTrainPurgeMixin {
    @Redirect(method = "tickLoveTrain(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;removeEffect(Lnet/minecraft/core/Holder;)Z"),
            require = 0, expect = 1)
    private static boolean rotp_spin$keepBallBreakerEffect(ServerPlayer player, Holder<MobEffect> effect) {
        if (SpinLoveTrain.retainDuringPurge(player, player.getEffect(effect))) {
            return false;
        }
        return player.removeEffect(effect);
    }
}
