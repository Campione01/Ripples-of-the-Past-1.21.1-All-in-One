package rotp.addons.d4c.world;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * Read-only view of the Overworld generator's live biome source.
 *
 * <p>It is deliberately not a {@code MultiNoiseBiomeSource}: hooks that write per-dimension state into
 * multi-noise sources (Alex's Caves, TerraBlender's fill clone) then act on this view, never on the
 * Overworld's object. Queries are answered by the Overworld's source with the caller's own sampler.
 * <p>Merged from the pack's d4c_parallel_sync_compat 0.1.0 (owner's code, R772.1).
 */
public final class OverworldBiomeView extends BiomeSource {
    /** Runtime-only: encodes to an empty object and refuses to decode, so an accidental encode cannot throw. */
    public static final MapCodec<OverworldBiomeView> CODEC = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<OverworldBiomeView> decode(DynamicOps<T> ops, MapLike<T> input) {
            return DataResult.error(() -> "The parallel world's Overworld biome view exists only at runtime");
        }

        @Override
        public <T> RecordBuilder<T> encode(OverworldBiomeView input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return prefix;
        }
    };

    private final ChunkGenerator overworld;

    public OverworldBiomeView(ChunkGenerator overworld) {
        this.overworld = overworld;
    }

    /** The Overworld biome source as it is now; Lithostitched may replace it before the server starts. */
    public BiomeSource overworldSource() {
        return this.overworld.getBiomeSource();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.overworldSource().possibleBiomes().stream();
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        return this.overworldSource().possibleBiomes();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return this.overworldSource().getNoiseBiome(x, y, z, sampler);
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
        this.overworldSource().addDebugInfo(info, pos, sampler);
    }

    // Same names as the Confluence dimension patch's BiomeSource accessor, matched without a class reference.
    // The patch marks every non-Overworld source; staying unmarked keeps otherworld-only content off, as in the Overworld.
    public void confluence_dimension_patch$setIsNotOverworld() {
    }

    public boolean confluence_dimension_patch$isNotOverworld() {
        return false;
    }
}
