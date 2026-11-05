package rotp.addons.beachboy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.addons.beachboy.util.BeachBoyStandUtil;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** 1.16 HookedPlayerMovePacketMixin: a player steered on the remote hook only turns its head by its own packets. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HookedPlayerMovePacketMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void rotpBeachBoy$keepHookedLookOnly(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer hooked = player;
        if (hooked != null && hooked.getServer() != null && hooked.getServer().isSameThread()
                && BeachBoyStandUtil.isRemotePuppeted(hooked)) {
            hooked.setYRot(packet.getYRot(hooked.getYRot()));
            hooked.setXRot(packet.getXRot(hooked.getXRot()));
            hooked.setYHeadRot(hooked.getYRot());
            ci.cancel();
        }
    }
}
