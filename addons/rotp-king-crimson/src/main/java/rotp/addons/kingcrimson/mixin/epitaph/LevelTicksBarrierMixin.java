package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxPackets;

/** A tick a phantom schedules (the release of the pressure plate it stepped on) is recorded, not scheduled. */
@Mixin(value = LevelTicks.class, priority = 900)
public abstract class LevelTicksBarrierMixin {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSchedule(ScheduledTick<?> tick, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            SandboxPackets.schedule(tick);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }
}
