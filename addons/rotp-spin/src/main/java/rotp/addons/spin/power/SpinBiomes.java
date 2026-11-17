package rotp.addons.spin.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;

/** Maps modern vanilla biome IDs and stable tags to the legacy configuration categories. */
public final class SpinBiomes {
    private SpinBiomes() {}

    public static String category(Level level, BlockPos pos) {
        return category(level.getBiome(pos));
    }

    public static String category(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_NETHER)) return "NETHER";
        if (biome.is(BiomeTags.IS_END)) return "THEEND";
        if (biome.is(BiomeTags.IS_OCEAN)) return "OCEAN";
        if (biome.is(BiomeTags.IS_SAVANNA)) return "SAVANNA";
        if (biome.is(BiomeTags.IS_BADLANDS)) return "MESA";
        if (biome.is(BiomeTags.IS_JUNGLE)) return "JUNGLE";
        if (biome.is(BiomeTags.IS_TAIGA)) return "TAIGA";
        if (biome.is(BiomeTags.IS_FOREST)) return "FOREST";
        if (biome.is(BiomeTags.IS_RIVER)) return "RIVER";
        if (biome.is(BiomeTags.IS_BEACH)) return "BEACH";
        String vanilla = biome.unwrapKey().filter(k -> "minecraft".equals(k.location().getNamespace()))
                .map(k -> switch (k.location().getPath()) {
                    case "plains", "sunflower_plains" -> "PLAINS";
                    case "snowy_plains", "ice_spikes" -> "ICY";
                    case "desert" -> "DESERT";
                    case "swamp", "mangrove_swamp" -> "SWAMP";
                    case "mushroom_fields" -> "MUSHROOM";
                    case "windswept_hills", "windswept_gravelly_hills", "windswept_forest",
                            "meadow", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks" -> "EXTREME_HILLS";
                    case "lush_caves", "dripstone_caves", "deep_dark" -> "UNDERGROUND";
                    case "cherry_grove" -> "FOREST";
                    case "stony_shore" -> "BEACH";
                    case "the_void" -> "NONE";
                    default -> null;
                }).orElse(null);
        if (vanilla != null) return vanilla;
        // Biome.Category gave every 1.16 biome a place, and NONE is in the default Golden dead list: a modded biome must not land on it.
        if (biome.is(Tags.Biomes.IS_PLAINS)) return "PLAINS";
        if (biome.is(Tags.Biomes.IS_DESERT)) return "DESERT";
        if (biome.is(Tags.Biomes.IS_SWAMP)) return "SWAMP";
        if (biome.is(Tags.Biomes.IS_MUSHROOM)) return "MUSHROOM";
        if (biome.is(Tags.Biomes.IS_SNOWY)) return "ICY";
        if (biome.is(Tags.Biomes.IS_MOUNTAIN)) return "EXTREME_HILLS";
        if (biome.is(Tags.Biomes.IS_UNDERGROUND)) return "UNDERGROUND";
        return "PLAINS";
    }
}
