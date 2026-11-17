package rotp.addons.spin.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rotp.addons.spin.compat.SpinLoveTrain;

@Pseudo
@Mixin(targets = "rotp.addons.d4c.D4CCorpseService", remap = false)
abstract class SpinLoveTrainProjectileMixin {
    // Both loops skip a projectile that reads as removed, before they count it as intercepted and discard it.
    @Redirect(method = {
            "repelAndIntercept(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;D)V",
            "interceptLightWallProjectiles(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/Projectile;isRemoved()Z"),
            require = 0, expect = 2)
    private static boolean rotp_spin$letQualifiedBallThrough(Projectile projectile) {
        return projectile.isRemoved() || SpinLoveTrain.passesInterception(projectile);
    }
}
