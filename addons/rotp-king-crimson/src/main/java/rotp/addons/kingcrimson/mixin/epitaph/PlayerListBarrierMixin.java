package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.function.Function;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxPackets;

/** Broadcasts to the players of the server: from a room, one to those near a place is kept, the others are dropped. */
@Mixin(value = PlayerList.class, priority = 900)
public abstract class PlayerListBarrierMixin {
    @Inject(method = "broadcast", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBroadcast(@Nullable Player except, double x, double y, double z, double radius,
            ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            SandboxPackets.area(except, x, y, z, radius, packet);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            if (!SandboxPackets.ofTheCore(packet)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBroadcastAll(Packet<?> packet, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            SandboxPackets.drop(packet);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            if (!SandboxPackets.ofTheCore(packet)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBroadcastAllIn(Packet<?> packet, ResourceKey<Level> dimension, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            SandboxPackets.drop(packet);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            if (!SandboxPackets.ofTheCore(packet)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSystemMessage(Component serverMessage, Function<ServerPlayer, Component> playerMessageFactory,
            boolean bypassHiddenChat, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }
}
