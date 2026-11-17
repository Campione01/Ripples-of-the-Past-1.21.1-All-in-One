package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/** In a window a participant earns no advancement: what he really does afterwards earns it at its tick. */
@Mixin(PlayerAdvancements.class)
public abstract class WindowAdvancementMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$windowAward(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (!WindowJournal.active) {
            return;
        }
        if (WindowJournal.holdsBack(player)) {
            cir.setReturnValue(false);
        }
    }
}
