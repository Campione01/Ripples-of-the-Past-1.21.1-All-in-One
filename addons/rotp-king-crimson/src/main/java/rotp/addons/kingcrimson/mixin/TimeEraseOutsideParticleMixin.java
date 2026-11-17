package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.kingcrimson.client.KingCrimsonEraseOutsideView;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;

/**
 * The erasing user's client draws the skills at work outside his erase and not the world's own dust (EV1105AC
 * section 7): every particle is asked for at the one door it enters the engine by. Outside an erase of the local
 * player the answer is one boolean read.
 */
@Mixin(ParticleEngine.class)
public abstract class TimeEraseOutsideParticleMixin {
    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)"
            + "Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"))
    private void kingCrimson$noteKind(ParticleOptions kind, double x, double y, double z, double xSpeed, double ySpeed,
            double zSpeed, CallbackInfoReturnable<Particle> callback) {
        KingCrimsonEraseOutsideView.creating(kind);
    }

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)"
            + "Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"))
    private void kingCrimson$kindDone(ParticleOptions kind, double x, double y, double z, double xSpeed, double ySpeed,
            double zSpeed, CallbackInfoReturnable<Particle> callback) {
        KingCrimsonEraseOutsideView.creating(null);
    }

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$outsideWorldOnly(Particle particle, CallbackInfo callback) {
        if (!KingCrimsonEraseOutsideView.admits(particle)) {
            callback.cancel();
        }
    }
}
