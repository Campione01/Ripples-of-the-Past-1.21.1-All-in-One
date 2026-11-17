package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxPackets;

/**
 * The barrier on the server level: in a room, an added entity becomes a child phantom, lookups answer the phantoms,
 * and sounds, particles, level and game events, block events and neighbour updates are recorded, not done. Outside a
 * room no hook of it acts. In a muted tick (an action of the record ticking on its real performer) the same calls are
 * absorbed without a record, and lookups answer the bodies on their tracks.
 */
@Mixin(value = ServerLevel.class, priority = 900)
public abstract class ServerLevelBarrierMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomAddEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().adopt(entity));
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getEntities()Lnet/minecraft/world/level/entity/LevelEntityGetter;", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomEntities(CallbackInfoReturnable<LevelEntityGetter<Entity>> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().getter());
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(EpitaphSandbox.mirror().bodies());
        }
    }

    @Inject(method = "players", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomPlayers(CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().players());
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(EpitaphSandbox.mirror().players());
        }
    }

    @Inject(method = "getPlayers(Ljava/util/function/Predicate;I)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomPlayersMatching(Predicate<? super ServerPlayer> predicate, int maxResults,
            CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            List<ServerPlayer> found = new ArrayList<>();
            for (ServerPlayer human : EpitaphSandbox.room().players()) {
                if (predicate.test(human)) {
                    found.add(human);
                    if (found.size() >= maxResults) {
                        break;
                    }
                }
            }
            cir.setReturnValue(found);
        } else if (EpitaphSandbox.mirroring()) {
            List<ServerPlayer> found = new ArrayList<>();
            for (ServerPlayer human : EpitaphSandbox.mirror().players()) {
                if (found.size() < maxResults && predicate.test(human)) {
                    found.add(human);
                }
            }
            cir.setReturnValue(found);
        }
    }

    @Inject(method = "getPartEntities", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingCrimson$roomParts(CallbackInfoReturnable<Collection<PartEntity<?>>> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(List.of());
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(List.of());
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;"
            + "Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSoundAt(@Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource category, float volume, float pitch, long seed, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.Sound(EpitaphSandbox.actorKey(), ActorKey.of(player), ActorKey.NONE, x, y, z,
                    sound, category, volume, pitch, seed));
            SandboxPackets.viewSound(null, x, y, z, sound, category, volume, pitch, seed);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSoundOn(@Nullable Player player, Entity entity, Holder<SoundEvent> sound,
            SoundSource category, float volume, float pitch, long seed, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.Sound(EpitaphSandbox.actorKey(), ActorKey.of(player), ActorKey.of(entity),
                    entity.getX(), entity.getY(), entity.getZ(), sound, category, volume, pitch, seed));
            SandboxPackets.viewSound(entity, entity.getX(), entity.getY(), entity.getZ(), sound, category, volume, pitch, seed);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"),
            cancellable = true)
    private void kingCrimson$roomParticles(ParticleOptions type, double posX, double posY, double posZ, int particleCount,
            double xOffset, double yOffset, double zOffset, double speed, CallbackInfoReturnable<Integer> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.Particles(EpitaphSandbox.actorKey(), ActorKey.NONE, type, false, posX, posY,
                    posZ, particleCount, xOffset, yOffset, zOffset, speed));
            SandboxPackets.viewParticles(type, false, posX, posY, posZ, particleCount, xOffset, yOffset, zOffset, speed);
            cir.setReturnValue(0);
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;"
            + "ZDDDIDDDD)Z", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomParticlesTo(ServerPlayer player, ParticleOptions type, boolean longDistance, double posX,
            double posY, double posZ, int particleCount, double xOffset, double yOffset, double zOffset, double speed,
            CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.Particles(EpitaphSandbox.actorKey(), ActorKey.of(player), type, longDistance,
                    posX, posY, posZ, particleCount, xOffset, yOffset, zOffset, speed));
            cir.setReturnValue(false);
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomLevelEvent(@Nullable Player player, int type, BlockPos pos, int data, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.LevelEvent(EpitaphSandbox.actorKey(), ActorKey.of(player), type,
                    pos.immutable(), data, false));
            SandboxPackets.viewLevelEvent(type, pos.immutable(), data, false);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "globalLevelEvent", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomGlobalLevelEvent(int id, BlockPos pos, int data, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.LevelEvent(EpitaphSandbox.actorKey(), ActorKey.NONE, id, pos.immutable(), data,
                    true));
            SandboxPackets.viewLevelEvent(id, pos.immutable(), data, true);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomGameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            // The blast of a room explosion is posted by the real one.
            if (!(EpitaphSandbox.room().exploding() && gameEvent.value() == GameEvent.EXPLODE.value())) {
                EpitaphSandbox.record(new RecordedOp.GameEventOp(EpitaphSandbox.actorKey(), gameEvent, pos,
                        ActorKey.of(context.sourceEntity()), context.affectedState()));
            }
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBlockEvent(BlockPos pos, Block block, int eventId, int eventParam, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.BlockEvent(EpitaphSandbox.actorKey(), pos.immutable(), block, eventId,
                    eventParam));
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateNeighborsAt", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomNeighbours(BlockPos pos, Block block, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.Neighbours(EpitaphSandbox.actorKey(), pos.immutable(), block, null));
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomNeighboursBut(BlockPos pos, Block block, Direction skipSide, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.record(new RecordedOp.Neighbours(EpitaphSandbox.actorKey(), pos.immutable(), block, skipSide));
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "destroyBlockProgress", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBreakProgress(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "getRandomSequence", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomRandomSequence(ResourceLocation location, CallbackInfoReturnable<RandomSource> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().sequence(location));
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(EpitaphSandbox.room().sequence(location));
        }
    }

    @Inject(method = "getPathTypeCache", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomPathTypes(CallbackInfoReturnable<PathTypeCache> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().pathTypes());
        }
    }

    @Inject(method = "getRaidAt", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomRaid(BlockPos pos, CallbackInfoReturnable<Raid> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "setMapData", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomMapData(MapId mapId, MapItemSavedData mapData, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code set map data");
            ci.cancel();
        }
    }
}
