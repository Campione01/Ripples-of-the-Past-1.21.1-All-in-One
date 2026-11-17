package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import rotp.addons.kingcrimson.client.epitaph.PhantomContext;
import rotp.addons.kingcrimson.client.epitaph.PhantomLook;

/** A particle made while the phantoms are handled is a particle of the foreseen future: it takes its look. */
@Mixin(value = ParticleEngine.class, priority = 900)
public abstract class ParticleEnginePhantomMixin {
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"))
    private void kingCrimson$phantomParticle(Particle effect, CallbackInfo ci) {
        if (!PhantomContext.active) {
            return;
        }
        PhantomLook.EPITAPH_FUTURE.tint(effect);
    }
}
