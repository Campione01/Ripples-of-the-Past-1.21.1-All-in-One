package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/**
 * In a window what the client of a participant makes him do is his (EPITAPH-ARCH 3.5): the handlers the forced walk
 * drops are, here, written down under his name. The beginning sits after the hand-off of the packet to the server
 * thread, so that no other thread ever names an actor.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WindowPacketMixin {
    private static final String HANDED_OVER = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
            + "Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
            + "Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    @Inject(method = {"handleMovePlayer", "handleMoveVehicle", "handleInteract", "handleContainerClick", "handleSetCarriedItem",
            "handlePlayerCommand", "handleAnimate", "handlePlayerInput", "handlePickItem", "handlePlaceRecipe",
            "handleContainerButtonClick", "handleSetCreativeModeSlot", "handlePlayerAction", "handleUseItemOn", "handleUseItem"},
            at = @At(value = "INVOKE", target = HANDED_OVER, shift = At.Shift.AFTER), require = 15)
    private void kingCrimson$windowActs(CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.act(player);
    }

    @Inject(method = {"handleMovePlayer", "handleMoveVehicle", "handleInteract", "handleContainerClick", "handleSetCarriedItem",
            "handlePlayerCommand", "handleAnimate", "handlePlayerInput", "handlePickItem", "handlePlaceRecipe",
            "handleContainerButtonClick", "handleSetCreativeModeSlot", "handlePlayerAction", "handleUseItemOn", "handleUseItem"},
            at = @At("RETURN"))
    private void kingCrimson$windowRests(CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.rest();
    }
}
