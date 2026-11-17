package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.human.ForcedWalk;

/**
 * What the client of a walked player sends is dropped (EPITAPH-ARCH 3.4): his moves, his hands, his inventory. Each
 * hook sits after the hand-off of the packet to the server thread. Dropped at the packet and not through an event: an
 * item toss that is cancelled as an event loses the item. A click that the client took for done is answered, so that
 * it does not keep a block it only imagined.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 900)
public abstract class GamePacketGateMixin {
    private static final String HANDED_OVER = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
            + "Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
            + "Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    @Inject(method = {"handleMovePlayer", "handleMoveVehicle", "handleInteract", "handleContainerClick", "handleSetCarriedItem",
            "handlePlayerCommand", "handleAnimate", "handlePlayerInput", "handlePickItem", "handlePlaceRecipe",
            "handleContainerButtonClick", "handleSetCreativeModeSlot"},
            at = @At(value = "INVOKE", target = HANDED_OVER, shift = At.Shift.AFTER), cancellable = true, require = 12)
    private void kingCrimson$dropForWalked(CallbackInfo ci) {
        if (!EpitaphSessions.any) {
            return;
        }
        if (ForcedWalk.drops(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = HANDED_OVER, shift = At.Shift.AFTER),
            cancellable = true)
    private void kingCrimson$dropActionForWalked(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!EpitaphSessions.any) {
            return;
        }
        if (ForcedWalk.drops(player)) {
            ForcedWalk.answerDroppedClick(player, packet.getSequence(), packet.getPos(), null);
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = HANDED_OVER, shift = At.Shift.AFTER),
            cancellable = true)
    private void kingCrimson$dropUseOnForWalked(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (!EpitaphSessions.any) {
            return;
        }
        if (ForcedWalk.drops(player)) {
            ForcedWalk.answerDroppedClick(player, packet.getSequence(), packet.getHitResult().getBlockPos(),
                    packet.getHitResult().getDirection());
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = HANDED_OVER, shift = At.Shift.AFTER),
            cancellable = true)
    private void kingCrimson$dropUseForWalked(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (!EpitaphSessions.any) {
            return;
        }
        if (ForcedWalk.drops(player)) {
            ForcedWalk.answerDroppedClick(player, packet.getSequence(), null, null);
            ci.cancel();
        }
    }
}
