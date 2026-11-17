package rotp.addons.d4c.world;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.slf4j.Logger;

/**
 * Lets {@code rotp_d4c:parallel_world} write the Overworld's biomes.
 *
 * <p>Only the stem handed to the new level changes. The registry and {@code level.dat} keep the authored stem,
 * so removing this leaves no trace in the save.
 * <p>Merged from the pack's d4c_parallel_sync_compat 0.1.0 (owner's code, R772.1); the log lines are the pack
 * checklist's and stay word for word.
 */
public final class ParallelWorldBiomeSync {
    public static final ResourceLocation PARALLEL_WORLD = ResourceLocation.fromNamespaceAndPath("rotp_d4c", "parallel_world");
    private static final Logger LOGGER = LogUtils.getLogger();

    private ParallelWorldBiomeSync() {
    }

    public static boolean isParallelWorld(ResourceKey<Level> dimension) {
        return PARALLEL_WORLD.equals(dimension.location());
    }

    /**
     * Returns the stem to build {@code dimension} from: for the parallel world, the same dimension type and a
     * noise generator over a read-only view of the Overworld's biome source with the same settings holder;
     * otherwise {@code stem} itself.
     *
     * @param overworld the generator of the live Overworld level, or null when there is none
     */
    public static LevelStem bind(ResourceKey<Level> dimension, LevelStem stem, @Nullable ChunkGenerator overworld) {
        if (!isParallelWorld(dimension)) {
            return stem;
        }
        String refusal = refusal(stem.generator(), overworld);
        if (refusal != null) {
            LOGGER.info("D4C_PARALLEL_SYNC left {} unchanged: {}", dimension.location(), refusal);
            return stem;
        }
        var own = (NoiseBasedChunkGenerator) stem.generator();
        var generator = new NoiseBasedChunkGenerator(new OverworldBiomeView(overworld), own.generatorSettings());
        LOGGER.info("D4C_PARALLEL_SYNC bound {} to the Overworld biome source", dimension.location());
        return new LevelStem(stem.type(), generator);
    }

    /** Null when the parallel world may be bound, otherwise the reason it is left as authored. */
    @Nullable
    public static String refusal(ChunkGenerator parallel, @Nullable ChunkGenerator overworld) {
        if (overworld == null) {
            return "no Overworld level exists yet";
        }
        String reason = noiseOverworldSettings("the Overworld", overworld);
        if (reason != null) {
            return reason;
        }
        reason = noiseOverworldSettings("its", parallel);
        if (reason != null) {
            return reason;
        }
        // A source that is no longer multi-noise was already replaced, by D4C itself or by another mod.
        BiomeSource source = parallel.getBiomeSource();
        if (!(source instanceof MultiNoiseBiomeSource)) {
            return "its biome source is " + source.getClass().getName() + ", not minecraft:multi_noise";
        }
        return null;
    }

    @Nullable
    private static String noiseOverworldSettings(String owner, ChunkGenerator generator) {
        if (generator.getClass() != NoiseBasedChunkGenerator.class) {
            return owner + " generator is " + generator.getClass().getName() + ", not minecraft:noise";
        }
        var settings = ((NoiseBasedChunkGenerator) generator).generatorSettings();
        if (!settings.is(NoiseGeneratorSettings.OVERWORLD)) {
            return owner + " noise settings are " + settings.unwrapKey().map(key -> key.location().toString()).orElse("inline")
                + ", not minecraft:overworld";
        }
        return null;
    }
}
