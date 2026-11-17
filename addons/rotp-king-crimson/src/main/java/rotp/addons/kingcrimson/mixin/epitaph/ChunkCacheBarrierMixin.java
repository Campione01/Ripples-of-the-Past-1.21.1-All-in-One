package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxPackets;

/**
 * A room loads and generates no chunk and holds none: where a chunk is not loaded a phantom gets an empty one that is
 * never stored, and stops there. What its code tells its trackers is kept for the bodies' trackers; the tracker of the
 * level is keyed by the int id a phantom shares with its body.
 */
@Mixin(value = ServerChunkCache.class, priority = 900)
public abstract class ChunkCacheBarrierMixin {
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk,
            CallbackInfoReturnable<ChunkAccess> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            LevelChunk loaded = ((ServerChunkCache) (Object) this).getChunkNow(x, z);
            cir.setReturnValue(loaded != null ? loaded : requireChunk ? EpitaphSandbox.room().edgeChunk(x, z) : null);
        }
    }

    @Inject(method = "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;"
            + "ILjava/lang/Object;Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void kingCrimson$roomAddTicket(TicketType<T> type, ChunkPos pos, int distance, T value, boolean forceTicks,
            CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }

    @Inject(method = "removeRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;"
            + "ILjava/lang/Object;Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void kingCrimson$roomRemoveTicket(TicketType<T> type, ChunkPos pos, int distance, T value, boolean forceTicks,
            CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }

    @Inject(method = "broadcast", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBroadcast(Entity entity, Packet<?> packet, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            SandboxPackets.tracker(entity, packet, false);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            if (!SandboxPackets.ofTheCore(packet)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "broadcastAndSend", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBroadcastAndSend(Entity entity, Packet<?> packet, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            SandboxPackets.tracker(entity, packet, true);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            if (!SandboxPackets.ofTheCore(packet)) {
                ci.cancel();
            }
        }
    }
}
