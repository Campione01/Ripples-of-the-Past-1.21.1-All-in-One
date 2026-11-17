package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/** In a window what the own tick of a participant does is his: it is written down under his name. */
@Mixin(ServerPlayer.class)
public abstract class WindowPlayerTickMixin {
    @Inject(method = "doTick", at = @At("HEAD"))
    private void kingCrimson$windowActs(CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.act((ServerPlayer) (Object) this);
    }

    @Inject(method = "doTick", at = @At("RETURN"))
    private void kingCrimson$windowRests(CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.rest();
    }
}
