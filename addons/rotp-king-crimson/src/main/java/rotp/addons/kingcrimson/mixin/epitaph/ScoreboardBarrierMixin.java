package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** The scoreboard is read in a room, never written: no score, no team member. */
@Mixin(value = Scoreboard.class, priority = 900)
public abstract class ScoreboardBarrierMixin {
    @Inject(method = "forAllObjectives", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomScores(ObjectiveCriteria criteria, ScoreHolder scoreHolder, Consumer<ScoreAccess> action,
            CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }

    @Inject(method = "addPlayerToTeam", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomJoinTeam(String playerName, PlayerTeam team, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "removePlayerFromTeam(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomLeaveTeam(String playerName, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V", at = @At("HEAD"),
            cancellable = true)
    private void kingCrimson$roomLeaveThisTeam(String username, PlayerTeam team, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }
}
