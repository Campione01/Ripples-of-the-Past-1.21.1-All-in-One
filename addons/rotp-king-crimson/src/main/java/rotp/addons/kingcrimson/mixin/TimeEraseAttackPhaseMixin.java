package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

@Mixin(value = EntityActionInstance.class, remap = false)
public abstract class TimeEraseAttackPhaseMixin {
    @Inject(method = "_onTick", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$endEraseBeforeStrike(CallbackInfo ci) {
        EntityActionInstance action = (EntityActionInstance) (Object) this;
        if (!(action.ability instanceof StandEntityPunchAbility
                        || action.ability instanceof StandEntityBarrageAbility)
                || !(action.getPowerUser() instanceof ServerPlayer player)) {
            return;
        }
        // Owner 2026-11-07 (EV1105AF item 12): thrown out of his erase by another time ability, he gets no blow from
        // it. The blow he began inside it ends here, before it strikes.
        if (player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                && KingCrimsonAddon.temporalState(player).callsOffBlowBegunAt(
                        player.level().getGameTime() - (long) Math.ceil(action.getFullTicksPassed()))) {
            action.forceStop();
            action.syncPhaseChanges();
            ci.cancel();
            return;
        }
        if (action.getPhase() == ActionPhase.PERFORM) {
            KingCrimsonTemporalStateService.terminateOnInteraction(player);
        }
    }
}
