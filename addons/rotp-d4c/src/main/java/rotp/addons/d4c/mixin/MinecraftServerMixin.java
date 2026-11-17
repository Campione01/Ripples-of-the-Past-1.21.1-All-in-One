package rotp.addons.d4c.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rotp.addons.d4c.world.ParallelWorldBiomeSync;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    // A wrap, not a redirect, so it chains with Biolith's wrap of the same constructor call.
    @WrapOperation(method = "createLevels", at = @At(value = "NEW", target = "Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel rotpD4c$bindParallelWorld(MinecraftServer server, Executor executor,
            LevelStorageSource.LevelStorageAccess storage, ServerLevelData data, ResourceKey<Level> dimension, LevelStem stem,
            ChunkProgressListener progress, boolean debug, long seed, List<CustomSpawner> spawners, boolean tickTime,
            @Nullable RandomSequences sequences, Operation<ServerLevel> original) {
        if (ParallelWorldBiomeSync.isParallelWorld(dimension)) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            stem = ParallelWorldBiomeSync.bind(dimension, stem, overworld == null ? null : overworld.getChunkSource().getGenerator());
        }
        return original.call(server, executor, storage, data, dimension, stem, progress, debug, seed, spawners, tickTime, sequences);
    }
}
