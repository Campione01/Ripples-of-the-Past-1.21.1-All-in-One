package rotp.addons.d4c.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;

public final class D4CDimensionTargets {
    public static final ResourceLocation OVERWORLD =
            ResourceLocation.withDefaultNamespace("overworld");
    public static final ResourceLocation NETHER =
            ResourceLocation.withDefaultNamespace("the_nether");
    public static final ResourceLocation END =
            ResourceLocation.withDefaultNamespace("the_end");
    public static final ResourceLocation PARALLEL_WORLD =
            D4CAddon.id("parallel_world");
    /**
     * Dimensions D4C never travels to: pocket rooms and technical levels.
     * A pack adds its own in data/rotp_d4c/tags/dimension/
     * excluded_from_travel.json with "replace": false.
     */
    public static final TagKey<LevelStem> EXCLUDED_FROM_TRAVEL =
            TagKey.create(
                    Registries.LEVEL_STEM,
                    D4CAddon.id("excluded_from_travel"));

    // the 1.16 menu; it now only names the portal blocks and their labels
    public static final List<ResourceLocation> LEGACY_ORDER = List.of(
            OVERWORLD,
            NETHER,
            END,
            PARALLEL_WORLD,
            ResourceLocation.fromNamespaceAndPath("atum", "atum"),
            ResourceLocation.fromNamespaceAndPath(
                    "dead_guys_untitled_deep_dark_",
                    "deep_darky"),
            ResourceLocation.fromNamespaceAndPath(
                    "lostcities",
                    "lostcity"),
            ResourceLocation.fromNamespaceAndPath(
                    "twilightforest",
                    "skylight_forest"),
            ResourceLocation.fromNamespaceAndPath(
                    "twilightforest",
                    "twilightforest"));

    private static final Map<ResourceLocation, String> LABEL_KEYS = Map.of(
            OVERWORLD, "rotp_d4c.dimension.overworld",
            NETHER, "rotp_d4c.dimension.nether",
            END, "rotp_d4c.dimension.end",
            PARALLEL_WORLD, "rotp_d4c.dimension.parallel_world",
            LEGACY_ORDER.get(4), "rotp_d4c.dimension.atum",
            LEGACY_ORDER.get(5), "rotp_d4c.dimension.deep_dark",
            LEGACY_ORDER.get(6), "rotp_d4c.dimension.lost_city",
            LEGACY_ORDER.get(7), "rotp_d4c.dimension.skylight_forest",
            LEGACY_ORDER.get(8), "rotp_d4c.dimension.twilight_forest");

    private D4CDimensionTargets() {}

    public static ResourceKey<Level> levelKey(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    /** The module's name, else the dimension mod's key, else the id's. */
    public static Component label(ResourceLocation id) {
        String key = LABEL_KEYS.get(id);
        if (key != null) {
            return Component.translatable(key);
        }
        ResourceLocation source = D4CParallelLevels.sourceIdOf(id);
        if (source != null) {
            return Component.translatable(
                    "rotp_d4c.dimension.parallel_of", label(source));
        }
        return Component.translatableWithFallback(
                D4CDimensionRules.translationKey(
                        id.getNamespace(), id.getPath()),
                D4CDimensionRules.fallbackName(id.getPath()));
    }

    /** Has a stem in the dimension registry: no runtime-made level. */
    public static boolean isRegistered(
            MinecraftServer server, ResourceLocation id) {
        return stems(server).containsKey(id);
    }

    public static boolean isExcluded(
            MinecraftServer server, ResourceLocation id) {
        return stems(server)
                .getHolder(ResourceKey.create(Registries.LEVEL_STEM, id))
                .map(holder -> holder.is(EXCLUDED_FROM_TRAVEL))
                .orElse(false);
    }

    public static boolean isCandidate(
            MinecraftServer server, ResourceLocation id) {
        return D4CDimensionRules.isCandidate(
                isRegistered(server, id), isExcluded(server, id));
    }

    /**
     * What the selector offers this player, in menu order: the real
     * dimensions he has unlocked, and his own pair's parallel. The server
     * builds it; the client only shows it.
     */
    public static List<ResourceLocation> offeredTargets(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return List.of();
        }
        ResourceLocation current = player.level().dimension().location();
        List<String> ids = new ArrayList<>();
        for (ResourceKey<Level> key : server.levelKeys()) {
            ResourceLocation id = key.location();
            if (!D4CParallelLevels.isParallel(id)
                    && D4CDimensionRules.isOffered(
                            isRegistered(server, id),
                            isExcluded(server, id),
                            D4CDimensionUnlocks.isUnlocked(player, id),
                            player.isCreative())) {
                ids.add(id.toString());
            }
        }
        ResourceLocation paired =
                D4CParallelLevels.pairedParallel(server, current);
        ResourceLocation source = D4CParallelLevels.sourceIdOf(current);
        for (String id : D4CDimensionRules.pairTargets(
                current.toString(),
                paired != null ? paired.toString() : null,
                source != null
                                && server.getLevel(levelKey(source)) != null
                        ? source.toString()
                        : null)) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        List<ResourceLocation> result = new ArrayList<>();
        for (String id : D4CDimensionRules.order(ids)) {
            result.add(ResourceLocation.parse(id));
        }
        return List.copyOf(result);
    }

    private static Registry<LevelStem> stems(MinecraftServer server) {
        return server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
    }
}
