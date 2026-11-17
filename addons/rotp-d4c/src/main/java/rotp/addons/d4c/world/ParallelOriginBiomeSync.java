package rotp.addons.d4c.world;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.slf4j.Logger;

/**
 * {@link ParallelWorldBiomeSync}'s rule for the made parallel of an opted-in dimension other than the Overworld.
 * The level is built by D4C itself (D4CParallelLevels), so this is called there, not from createLevels. The
 * Overworld's case is not widened: its own class, its own log prefix.
 */
public final class ParallelOriginBiomeSync {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ParallelOriginBiomeSync() {
    }

    /**
     * The generator the parallel is built with: a noise generator over a view of the origin's live biome source
     * with the copy's own settings holder, or {@code copy} itself when refused.
     *
     * @param copy the generator decoded from the origin's stem
     * @param origin the live origin level's generator
     */
    public static ChunkGenerator bind(
            ResourceLocation parallel, ResourceLocation originId, ChunkGenerator copy, @Nullable ChunkGenerator origin) {
        String refusal = refusal(copy, origin);
        if (refusal != null) {
            LOGGER.info("D4C_PARALLEL_ORIGIN left {} on its decoded copy: {}", parallel, refusal);
            return copy;
        }
        var own = (NoiseBasedChunkGenerator) copy;
        LOGGER.info("D4C_PARALLEL_ORIGIN bound {} to the {} biome source", parallel, originId);
        return new NoiseBasedChunkGenerator(new OriginBiomeView(origin), own.generatorSettings());
    }

    /**
     * Null when bound may go ahead: both generators exactly minecraft:noise on one and the same settings key, and
     * the copy's source still vanilla multi-noise or the End's.
     */
    @Nullable
    public static String refusal(ChunkGenerator copy, @Nullable ChunkGenerator origin) {
        if (origin == null) {
            return "its origin level does not exist";
        }
        if (origin.getClass() != NoiseBasedChunkGenerator.class) {
            return "the origin generator is " + origin.getClass().getName() + ", not minecraft:noise";
        }
        if (copy.getClass() != NoiseBasedChunkGenerator.class) {
            return "its generator is " + copy.getClass().getName() + ", not minecraft:noise";
        }
        var originSettings = ((NoiseBasedChunkGenerator) origin).generatorSettings().unwrapKey();
        var copySettings = ((NoiseBasedChunkGenerator) copy).generatorSettings().unwrapKey();
        if (originSettings.isEmpty() || !originSettings.equals(copySettings)) {
            return "its noise settings " + copySettings.map(key -> key.location().toString()).orElse("inline")
                    + " are not the origin's " + originSettings.map(key -> key.location().toString()).orElse("inline");
        }
        BiomeSource source = copy.getBiomeSource();
        if (!(source instanceof MultiNoiseBiomeSource) && !(source instanceof TheEndBiomeSource)) {
            return "its biome source is " + source.getClass().getName() + ", not minecraft:multi_noise or minecraft:the_end";
        }
        return null;
    }
}
