package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/**
 * The events that are not posted for a phantom, kept back where NeoForge posts them. The tick events: whatever ticks
 * while a room is lived is a phantom, and the game posts them itself in the middle of a tick (for a rider in
 * Entity.rideTick, for a player in Player.tick); the caller that asks whether the tick was cancelled gets an event
 * nobody saw. Every other event of an entity is posted for a phantom as for any entity: its listeners answer through
 * the event or act on its subject (the impact of a projectile among them, since ruling R7).
 */
@Mixin(value = EventHooks.class, remap = false, priority = 900)
public abstract class RoomEventGateMixin {
    @Inject(method = "fireEntityTickPre", at = @At("HEAD"), cancellable = true, remap = false)
    private static void kingCrimson$roomEntityTickPre(Entity entity, CallbackInfoReturnable<EntityTickEvent.Pre> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(new EntityTickEvent.Pre(entity));
        }
    }

    @Inject(method = "fireEntityTickPost", at = @At("HEAD"), cancellable = true, remap = false)
    private static void kingCrimson$roomEntityTickPost(Entity entity, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }

    @Inject(method = "firePlayerTickPre", at = @At("HEAD"), cancellable = true, remap = false)
    private static void kingCrimson$roomPlayerTickPre(Player player, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }

    @Inject(method = "firePlayerTickPost", at = @At("HEAD"), cancellable = true, remap = false)
    private static void kingCrimson$roomPlayerTickPost(Player player, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }
}
