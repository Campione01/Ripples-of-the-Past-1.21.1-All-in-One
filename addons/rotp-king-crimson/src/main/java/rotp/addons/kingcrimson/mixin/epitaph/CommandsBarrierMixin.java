package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.brigadier.ParseResults;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** No command runs from a room. */
@Mixin(value = Commands.class, priority = 900)
public abstract class CommandsBarrierMixin {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomCommand(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code ran a command");
            ci.cancel();
        }
    }
}
