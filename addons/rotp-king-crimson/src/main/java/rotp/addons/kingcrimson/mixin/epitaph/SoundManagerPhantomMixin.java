package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import rotp.addons.kingcrimson.client.epitaph.EpitaphClientSession;
import rotp.addons.kingcrimson.client.epitaph.PhantomContext;

/** What the phantoms would sound like is not heard, unless the server has their sounds switched on. */
@Mixin(value = SoundManager.class, priority = 900)
public abstract class SoundManagerPhantomMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$phantomSound(SoundInstance sound, CallbackInfo ci) {
        if (!PhantomContext.active) {
            return;
        }
        if (!EpitaphClientSession.hearsPhantoms()) {
            ci.cancel();
        }
    }

    @Inject(method = "playDelayed(Lnet/minecraft/client/resources/sounds/SoundInstance;I)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$phantomSoundDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        if (!PhantomContext.active) {
            return;
        }
        if (!EpitaphClientSession.hearsPhantoms()) {
            ci.cancel();
        }
    }

    // The hum of a minecart or a bee that a packet of a frame makes.
    @Inject(method = "queueTickingSound(Lnet/minecraft/client/resources/sounds/TickableSoundInstance;)V", at = @At("HEAD"),
            cancellable = true)
    private void kingCrimson$phantomSoundQueued(TickableSoundInstance sound, CallbackInfo ci) {
        if (!PhantomContext.active) {
            return;
        }
        if (!EpitaphClientSession.hearsPhantoms()) {
            ci.cancel();
        }
    }
}
