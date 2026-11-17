package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** The belt under statistics: a human phantom awards none, and nothing in a room may write a real player's. */
@Mixin(value = ServerStatsCounter.class, priority = 900)
public abstract class StatsBarrierMixin {
    @Inject(method = "setValue", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSetValue(Player player, Stat<?> stat, int value, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code wrote a statistic");
            ci.cancel();
        }
    }
}
