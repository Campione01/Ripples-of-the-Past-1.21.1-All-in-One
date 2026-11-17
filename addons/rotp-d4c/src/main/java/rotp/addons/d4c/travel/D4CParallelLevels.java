package rotp.addons.d4c.travel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CConfig;
import rotp.addons.d4c.world.ParallelOriginBiomeSync;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import rotp.core.config.editor.ConfigChoices;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Parallel worlds of dimensions other than the Overworld (owner
 * 2026-11-10: opted in one by one in the settings, the Overworld alone by
 * default). The Overworld's stays the data-defined rotp_d4c:parallel_world.
 * Every other ticked dimension gets one level, made when the server starts
 * and before anyone can join, named rotp_d4c:parallel/namespace/path: the
 * source's id kept whole, so two mods' dimensions of one name never meet,
 * and its folder sits under this module's in the save.
 */
@EventBusSubscriber(
        modid = D4CAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class D4CParallelLevels {
    public static final String PREFIX = "parallel/";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceKey<Level>> CREATED =
            ConcurrentHashMap.newKeySet();
    private static volatile boolean overworldOpen = true;

    private D4CParallelLevels() {}

    public static ResourceLocation parallelIdOf(ResourceLocation source) {
        return D4CDimensionTargets.OVERWORLD.equals(source)
                ? D4CDimensionTargets.PARALLEL_WORLD
                : D4CAddon.id(PREFIX + source.getNamespace()
                        + "/" + source.getPath());
    }

    /** The dimension a parallel copies, or null for any other id. */
    @Nullable
    public static ResourceLocation sourceIdOf(ResourceLocation id) {
        if (D4CDimensionTargets.PARALLEL_WORLD.equals(id)) {
            return D4CDimensionTargets.OVERWORLD;
        }
        if (!D4CAddon.MOD_ID.equals(id.getNamespace())
                || !id.getPath().startsWith(PREFIX)) {
            return null;
        }
        String rest = id.getPath().substring(PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return null;
        }
        return ResourceLocation.tryBuild(
                rest.substring(0, slash), rest.substring(slash + 1));
    }

    public static boolean isParallel(ResourceLocation id) {
        return sourceIdOf(id) != null;
    }

    public static boolean isCreated(ResourceKey<Level> key) {
        return CREATED.contains(key);
    }

    public static Set<ResourceKey<Level>> created() {
        return Set.copyOf(CREATED);
    }

    /** A made level and the dimension it copies, in that direction. */
    public static boolean isCreatedPair(
            ResourceKey<Level> from, ResourceKey<Level> to) {
        return CREATED.contains(to)
                && parallelIdOf(from.location()).equals(to.location());
    }

    /**
     * Whether a crossing into this parallel is allowed now. The
     * Overworld's exists by data; unticking it only closes the crossing.
     */
    public static boolean isOpen(MinecraftServer server, ResourceLocation id) {
        if (D4CDimensionTargets.PARALLEL_WORLD.equals(id)) {
            return overworldOpen
                    && server.getLevel(D4CDimensionTargets.levelKey(id))
                            != null;
        }
        return CREATED.contains(D4CDimensionTargets.levelKey(id))
                && server.getLevel(D4CDimensionTargets.levelKey(id)) != null;
    }

    /** The parallel of the dimension a player stands in, if it has one. */
    @Nullable
    public static ResourceLocation pairedParallel(
            MinecraftServer server, ResourceLocation current) {
        if (isParallel(current)) {
            return null;
        }
        ResourceLocation parallel = parallelIdOf(current);
        return isOpen(server, parallel) ? parallel : null;
    }

    /**
     * The settings page's tick list: the core's list of the server's
     * dimensions, its order and names, minus D4C's own parallels and the
     * pocket and technical levels that cannot get one.
     */
    public static List<ConfigChoices.Choice> choices(MinecraftServer server) {
        List<ConfigChoices.Choice> choices = new ArrayList<>();
        for (ConfigChoices.Choice choice
                : ConfigChoices.dimensions().choices(server)) {
            ResourceLocation id = ResourceLocation.tryParse(choice.id());
            if (id != null
                    && !isParallel(id)
                    && D4CDimensionTargets.isCandidate(server, id)) {
                choices.add(choice);
            }
        }
        return List.copyOf(choices);
    }

    // The common settings are loaded with the mod, long before; the levels
    // exist from loadLevel on; players join only after this event
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerStartingEvent event) {
        createTicked(event.getServer(), D4CConfig.parallelDimensions());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CREATED.clear();
        overworldOpen = true;
    }

    static void createTicked(MinecraftServer server, List<String> ticked) {
        CREATED.clear();
        overworldOpen = ticked.contains(D4CConfig.OVERWORLD);
        for (Planned planned : plan(server, ticked)) {
            ResourceLocation source = planned.source();
            String refused = planned.refusal() != null
                    ? planned.refusal()
                    : server.getLevel(D4CDimensionTargets.levelKey(
                                    parallelIdOf(source))) != null
                            ? "its parallel id is taken"
                            : null;
            if (refused != null) {
                LOGGER.info(
                        "D4C parallel world for {} skipped: {}",
                        source,
                        refused);
                continue;
            }
            try {
                create(server, source);
            }
            catch (RuntimeException | ReflectiveOperationException failure) {
                LOGGER.error(
                        "D4C parallel world for {} could not be made",
                        source,
                        failure);
            }
        }
    }

    /**
     * What a start with these ticked ids makes, without making it: one
     * row per ticked dimension other than the Overworld, whose parallel is
     * data-defined. A row with a refusal is skipped.
     */
    public static List<Planned> plan(
            MinecraftServer server, List<String> ticked) {
        List<Planned> rows = new ArrayList<>();
        for (String text : new LinkedHashSet<>(ticked)) {
            ResourceLocation source = ResourceLocation.tryParse(text);
            if (source == null) {
                continue;
            }
            if (D4CDimensionTargets.OVERWORLD.equals(source)) {
                continue;
            }
            rows.add(new Planned(source, refusal(server, source)));
        }
        return List.copyOf(rows);
    }

    public record Planned(
            ResourceLocation source, @Nullable String refusal) {}

    @Nullable
    private static String refusal(
            MinecraftServer server, ResourceLocation source) {
        if (isParallel(source)) {
            return "it is a parallel world itself";
        }
        if (server.getLevel(D4CDimensionTargets.levelKey(source)) == null) {
            return "no such dimension on this server";
        }
        if (!D4CDimensionTargets.isCandidate(server, source)) {
            return "a pocket, technical or instanced dimension";
        }
        return null;
    }

    private static void create(
            MinecraftServer server, ResourceLocation sourceId)
            throws ReflectiveOperationException {
        LevelStem sourceStem = server.registryAccess()
                .registryOrThrow(Registries.LEVEL_STEM)
                .get(sourceId);
        if (sourceStem == null) {
            LOGGER.info(
                    "D4C parallel world for {} skipped: no stem",
                    sourceId);
            return;
        }
        // the settings are copied through the codec, so the new level owns
        // its generator and builds its own noise and structure state
        RegistryOps<Tag> ops =
                RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        Tag encoded = ChunkGenerator.CODEC
                .encodeStart(ops, sourceStem.generator())
                .getOrThrow();
        ChunkGenerator generator =
                ChunkGenerator.CODEC.parse(ops, encoded).getOrThrow();
        ResourceKey<Level> key =
                D4CDimensionTargets.levelKey(parallelIdOf(sourceId));
        // the decoded copy keeps the settings; its biomes come from the
        // origin's live source, which a copy would miss (mods inject there)
        ServerLevel originLevel =
                server.getLevel(D4CDimensionTargets.levelKey(sourceId));
        generator = ParallelOriginBiomeSync.bind(
                key.location(),
                sourceId,
                generator,
                originLevel != null
                        ? originLevel.getChunkSource().getGenerator()
                        : null);
        LevelStem stem = new LevelStem(sourceStem.type(), generator);

        WorldData worldData = server.getWorldData();
        ServerLevel overworld = server.overworld();
        // as MinecraftServer.createLevels makes every non-Overworld level:
        // time, weather and rules from the Overworld's data, no spawners
        ServerLevel level = new ServerLevel(
                server,
                Util.backgroundExecutor(),
                storage(server),
                new DerivedLevelData(worldData, worldData.overworldData()),
                key,
                stem,
                QUIET,
                worldData.isDebugWorld(),
                BiomeManager.obfuscateSeed(
                        worldData.worldGenOptions().seed()),
                List.of(),
                false,
                overworld.getRandomSequences());
        overworld.getWorldBorder().addListener(
                new BorderChangeListener.DelegateBorderChangeListener(
                        level.getWorldBorder()));
        level.getWorldBorder().applySettings(
                overworld.getWorldBorder().createSettings());
        Map<ResourceKey<Level>, ServerLevel> levels =
                server.forgeGetWorldMap();
        levels.put(key, level);
        server.markWorldsDirty();
        CREATED.add(key);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        LOGGER.info("D4C parallel world {} made for {}", key.location(),
                sourceId);
    }

    private static LevelStorageSource.LevelStorageAccess storage(
            MinecraftServer server) throws ReflectiveOperationException {
        Field field = MinecraftServer.class.getDeclaredField("storageSource");
        field.setAccessible(true);
        return (LevelStorageSource.LevelStorageAccess) field.get(server);
    }

    private static final ChunkProgressListener QUIET =
            new ChunkProgressListener() {
                @Override
                public void updateSpawnPos(ChunkPos center) {}

                @Override
                public void onStatusChange(
                        ChunkPos chunkPos, @Nullable ChunkStatus status) {}

                @Override
                public void start() {}

                @Override
                public void stop() {}
            };
}
