package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/** In a window the statistics of a participant stand still: what is undone is counted nowhere. */
@Mixin(ServerStatsCounter.class)
public abstract class WindowStatsMixin {
    @Inject(method = "setValue", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$windowSetValue(Player player, Stat<?> stat, int value, CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        if (WindowJournal.holdsBack(player)) {
            ci.cancel();
        }
    }
}
