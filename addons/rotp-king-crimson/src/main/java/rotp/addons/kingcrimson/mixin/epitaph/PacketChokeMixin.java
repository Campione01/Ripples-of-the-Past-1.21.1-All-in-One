package rotp.addons.kingcrimson.mixin.epitaph;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxPackets;

/** The one method every packet to a client passes: in a room nothing leaves by it. */
@Mixin(value = ServerCommonPacketListenerImpl.class, priority = 900)
public abstract class PacketChokeMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSend(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            // While a phantom is being shown to the one who foresees, what is sent to him is of that phantom.
            if (EpitaphSandbox.viewer() != null && (Object) this instanceof ServerGamePacketListenerImpl game
                    && game.player == EpitaphSandbox.viewer()) {
                SandboxPackets.toViewer(packet);
            } else {
                SandboxPackets.straight((Object) this instanceof ServerGamePacketListenerImpl game ? game.player : null, packet);
            }
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            if (!SandboxPackets.ofTheCore(packet)) {
                ci.cancel();
            }
        }
    }
}
