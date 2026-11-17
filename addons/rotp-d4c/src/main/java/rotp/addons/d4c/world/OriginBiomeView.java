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
 * {@link OverworldBiomeView} for the parallel of an opted-in dimension other than the Overworld: a read-only view
 * of that dimension's live biome source, answered with the parallel's own sampler, neither shared nor cloned.
 * Its origin is never the Overworld, so it answers the Confluence patch's question as the origin's own source
 * is marked: not the Overworld.
 */
public final class OriginBiomeView extends BiomeSource {
    /** Runtime-only, as the Overworld view's: encodes to an empty object and refuses to decode. */
    public static final MapCodec<OriginBiomeView> CODEC = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<OriginBiomeView> decode(DynamicOps<T> ops, MapLike<T> input) {
            return DataResult.error(() -> "A D4C parallel world's origin biome view exists only at runtime");
        }

        @Override
        public <T> RecordBuilder<T> encode(OriginBiomeView input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return prefix;
        }
    };

    private final ChunkGenerator origin;

    public OriginBiomeView(ChunkGenerator origin) {
        this.origin = origin;
    }

    public BiomeSource originSource() {
        return this.origin.getBiomeSource();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.originSource().possibleBiomes().stream();
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        return this.originSource().possibleBiomes();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return this.originSource().getNoiseBiome(x, y, z, sampler);
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
        this.originSource().addDebugInfo(info, pos, sampler);
    }

    // the Confluence patch's accessor names: the origin is not the Overworld, and the flag is never written here
    public void confluence_dimension_patch$setIsNotOverworld() {
    }

    public boolean confluence_dimension_patch$isNotOverworld() {
        return true;
    }
}
