package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxDamage;

/**
 * A human phantom picks nothing up: no item, no experience, no arrow from the ground. A hit on it is noted as it was
 * dealt, before the player scales it by the difficulty: the real hit, dealt later, is scaled by the real player.
 */
@Mixin(value = Player.class, priority = 900)
public abstract class PlayerBarrierMixin {
    @Inject(method = "hurt", at = @At("HEAD"))
    private void kingCrimson$roomHurtAsDealt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            SandboxDamage.dealt((Player) (Object) this, amount);
        }
    }

    @Inject(method = "touch", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomTouch(Entity entity, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()
                && (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof AbstractArrow)) {
            ci.cancel();
        }
    }
}
