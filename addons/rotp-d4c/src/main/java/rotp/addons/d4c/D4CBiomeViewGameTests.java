package rotp.addons.d4c;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import rotp.addons.d4c.travel.D4CDimensionTargets;
import rotp.addons.d4c.travel.D4CParallelLevels;
import rotp.addons.d4c.world.OriginBiomeView;
import rotp.addons.d4c.world.OverworldBiomeView;
import rotp.addons.d4c.world.ParallelOriginBiomeSync;
import rotp.addons.d4c.world.ParallelWorldBiomeSync;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R772.1: the parallel world's biome view merged from the pack's d4c_parallel_sync_compat. The game-test server
 * builds no rotp_d4c:parallel_world (GameTestServer bakes the flat preset with an empty stem registry), so the
 * createLevels hook is not reached here; its bind and refusal rules are run on generators built from the
 * server's own registries, and the opted-in End's parallel shows the generalised view on a real level.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CBiomeViewGameTests {
    private static final ResourceKey<Level> PARALLEL = ResourceKey.create(
            Registries.DIMENSION, ParallelWorldBiomeSync.PARALLEL_WORLD);

    private D4CBiomeViewGameTests() {}

    // the wrap is merged into MinecraftServer; the level it binds is not built on this server
    @GameTest(template = "empty", batch = "d4cViewWrap")
    public static void theCreateLevelsWrapIsApplied(GameTestHelper helper) {
        List<String> merged = new ArrayList<>();
        for (Method method : MinecraftServer.class.getDeclaredMethods()) {
            if (method.getName().contains("rotpD4c$bindParallelWorld")) {
                merged.add(method.getName());
            }
        }
        helper.assertTrue(merged.size() == 1,
                "MinecraftServer carries the D4C createLevels wrap " + merged.size() + " times: " + merged);
        helper.assertTrue(
                helper.getLevel().getServer().getLevel(PARALLEL) == null,
                "This server built rotp_d4c:parallel_world: the bound-level test belongs here then.");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cViewCodec")
    public static void theViewsCodecsAreRuntimeOnly(GameTestHelper helper) {
        ChunkGenerator overworld = overworldNoise(helper);
        List<BiomeSource> views = List.of(
                new OverworldBiomeView(overworld),
                new OriginBiomeView(overworld));
        List<String> types = List.of("rotp_d4c:overworld_view", "rotp_d4c:origin_view");
        List<MapCodec<? extends BiomeSource>> codecs =
                List.of(OverworldBiomeView.CODEC, OriginBiomeView.CODEC);
        for (int i = 0; i < views.size(); i++) {
            BiomeSource view = views.get(i);
            DataResult<JsonElement> registered =
                    BiomeSource.CODEC.encodeStart(JsonOps.INSTANCE, view);
            JsonObject expected = new JsonObject();
            expected.addProperty("type", types.get(i));
            helper.assertTrue(
                    registered.result().map(expected::equals).orElse(false),
                    "Registered encode of " + types.get(i) + ": " + registered);

            MapCodec<? extends BiomeSource> own = codecs.get(i);
            MappedRegistry<MapCodec<? extends BiomeSource>> bare =
                    new MappedRegistry<>(Registries.BIOME_SOURCE, Lifecycle.stable());
            Registry.register(bare, ResourceLocation.withDefaultNamespace("multi_noise"),
                    MultiNoiseBiomeSource.CODEC);
            Codec<BiomeSource> unregistered = bare.byNameCodec()
                    .dispatchStable(source -> own, Function.identity());
            helper.assertTrue(
                    unregistered.encodeStart(JsonOps.INSTANCE, view).isError(),
                    "An encode without the registration did not fail: " + types.get(i));

            DataResult<BiomeSource> decoded;
            try {
                decoded = BiomeSource.CODEC.parse(JsonOps.INSTANCE, expected);
            }
            catch (RuntimeException thrown) {
                throw new AssertionError("Decoding " + types.get(i) + " threw " + thrown);
            }
            helper.assertTrue(
                    decoded.isError(),
                    "Decoding " + types.get(i) + " did not refuse: " + decoded);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cViewRefusal")
    public static void everyRefusalIsGivenWithItsLine(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ChunkGenerator overworld = overworldNoise(helper);
        ChunkGenerator authored = overworldNoise(helper);
        ChunkGenerator flat = helper.getLevel().getChunkSource().getGenerator();
        ChunkGenerator nether = server.getLevel(Level.NETHER).getChunkSource().getGenerator();
        Holder<NoiseGeneratorSettings> owSettings = settings(helper, NoiseGeneratorSettings.OVERWORLD);
        ChunkGenerator subclass = new NoiseBasedChunkGenerator(authored.getBiomeSource(), owSettings) {};
        ChunkGenerator inline = new NoiseBasedChunkGenerator(
                authored.getBiomeSource(), Holder.direct(owSettings.value()));
        ChunkGenerator bound = new NoiseBasedChunkGenerator(new OverworldBiomeView(overworld), owSettings);
        String flatName = flat.getClass().getName();
        String subName = subclass.getClass().getName();
        String[][] rows = {
            {row(authored, null), "no Overworld level exists yet"},
            {row(authored, flat), "the Overworld generator is " + flatName + ", not minecraft:noise"},
            {row(authored, subclass), "the Overworld generator is " + subName + ", not minecraft:noise"},
            {row(authored, nether), "the Overworld noise settings are minecraft:nether, not minecraft:overworld"},
            {row(authored, inline), "the Overworld noise settings are inline, not minecraft:overworld"},
            {row(flat, overworld), "its generator is " + flatName + ", not minecraft:noise"},
            {row(nether, overworld), "its noise settings are minecraft:nether, not minecraft:overworld"},
            {row(bound, overworld), "its biome source is rotp.addons.d4c.world.OverworldBiomeView, not minecraft:multi_noise"},
            {row(authored, overworld), "null"}};
        for (String[] pair : rows) {
            helper.assertTrue(pair[0].equals(pair[1]), "refusal: got '" + pair[0] + "', expected '" + pair[1] + "'");
        }

        List<String> lines = new CopyOnWriteArrayList<>();
        Logger logger = (Logger) LogManager.getLogger(ParallelWorldBiomeSync.class);
        AbstractAppender appender = new AbstractAppender("d4c-view-test", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                lines.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            LevelStem stem = new LevelStem(helper.getLevel().dimensionTypeRegistration(), authored);
            LevelStem left = ParallelWorldBiomeSync.bind(PARALLEL, stem, null);
            LevelStem other = ParallelWorldBiomeSync.bind(Level.NETHER, stem, overworld);
            LevelStem done = ParallelWorldBiomeSync.bind(PARALLEL, stem, overworld);
            helper.assertTrue(left == stem && other == stem && done != stem,
                    "bind: refused " + (left == stem) + ", other dimension " + (other == stem) + ", bound " + (done != stem));
            helper.assertTrue(lines.equals(List.of(
                            "D4C_PARALLEL_SYNC left rotp_d4c:parallel_world unchanged: no Overworld level exists yet",
                            "D4C_PARALLEL_SYNC bound rotp_d4c:parallel_world to the Overworld biome source")),
                    "Log lines: " + lines);
        }
        finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cViewAnswers")
    public static void theBoundViewAnswersAsTheOverworldsSource(GameTestHelper helper) throws Exception {
        ChunkGenerator overworld = overworldNoise(helper);
        ChunkGenerator authored = overworldNoise(helper);
        LevelStem stem = new LevelStem(helper.getLevel().dimensionTypeRegistration(), authored);
        LevelStem bound = ParallelWorldBiomeSync.bind(PARALLEL, stem, overworld);
        helper.assertTrue(
                bound.type() == stem.type()
                        && bound.generator() instanceof NoiseBasedChunkGenerator noise
                        && noise.generatorSettings() == ((NoiseBasedChunkGenerator) authored).generatorSettings()
                        && bound.generator().getBiomeSource() instanceof OverworldBiomeView view
                        && view.overworldSource() == overworld.getBiomeSource()
                        && !MultiNoiseBiomeSource.class.isInstance(view),
                "The bound stem is not a noise generator over a view of the Overworld's source with the authored "
                        + "type and settings: " + bound.generator().getBiomeSource());
        BiomeSource view = bound.generator().getBiomeSource();
        helper.assertTrue(
                view.possibleBiomes().equals(overworld.getBiomeSource().possibleBiomes()),
                "possibleBiomes() is not the Overworld's set.");
        Climate.Sampler sampler = RandomState.create(
                settings(helper, NoiseGeneratorSettings.OVERWORLD).value(),
                helper.getLevel().registryAccess().lookupOrThrow(Registries.NOISE),
                987654321L).sampler();
        int sampled = sameAtQuarts(helper, view, overworld.getBiomeSource(), sampler, "the Overworld view");
        helper.assertTrue(sampled > 200, "Too few quarts compared: " + sampled);
        assertPatchMethods(helper, view, false);
        assertPatchMethods(helper, new OriginBiomeView(overworld), true);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cViewOrigin")
    public static void theEndsParallelReadsTheEndsLiveSource(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel end = server.getLevel(Level.END);
        ServerLevel copy = server.getLevel(D4CDimensionTargets.levelKey(
                D4CParallelLevels.parallelIdOf(D4CDimensionTargets.END)));
        helper.assertTrue(end != null && copy != null, "No End or no made parallel of it.");
        ChunkGenerator generator = copy.getChunkSource().getGenerator();
        helper.assertTrue(
                generator.getClass() == NoiseBasedChunkGenerator.class
                        && generator.getBiomeSource() instanceof OriginBiomeView view
                        && view.originSource() == end.getChunkSource().getGenerator().getBiomeSource()
                        && ((NoiseBasedChunkGenerator) generator).generatorSettings().is(NoiseGeneratorSettings.END),
                "The End's parallel is not built over a view of the End's live source: "
                        + generator.getBiomeSource());
        helper.assertTrue(
                generator.getBiomeSource().possibleBiomes().equals(
                        end.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()),
                "The End's parallel offers other biomes than the End.");
        sameAtQuarts(helper, generator.getBiomeSource(), end.getChunkSource().getGenerator().getBiomeSource(),
                copy.getChunkSource().randomState().sampler(), "the End's parallel");
        ChunkGenerator endGen = end.getChunkSource().getGenerator();
        String flatName = helper.getLevel().getChunkSource().getGenerator().getClass().getName();
        String[][] rows = {
            {String.valueOf(ParallelOriginBiomeSync.refusal(endGen, null)), "its origin level does not exist"},
            {String.valueOf(ParallelOriginBiomeSync.refusal(endGen, helper.getLevel().getChunkSource().getGenerator())),
                    "the origin generator is " + flatName + ", not minecraft:noise"},
            {String.valueOf(ParallelOriginBiomeSync.refusal(
                    server.getLevel(Level.NETHER).getChunkSource().getGenerator(), endGen)),
                    "its noise settings minecraft:nether are not the origin's minecraft:end"},
            {String.valueOf(ParallelOriginBiomeSync.refusal(generator, endGen)),
                    "its biome source is rotp.addons.d4c.world.OriginBiomeView, not minecraft:multi_noise or minecraft:the_end"},
            {String.valueOf(ParallelOriginBiomeSync.refusal(endGen, endGen)), "null"}};
        for (String[] pair : rows) {
            helper.assertTrue(pair[0].equals(pair[1]), "origin refusal: got '" + pair[0] + "', expected '" + pair[1] + "'");
        }
        helper.succeed();
    }

    private static int sameAtQuarts(
            GameTestHelper helper, BiomeSource view, BiomeSource bound, Climate.Sampler sampler, String what) {
        int compared = 0;
        List<String> differ = new ArrayList<>();
        for (int x = -400; x <= 400; x += 37) {
            for (int z = -400; z <= 400; z += 41) {
                for (int y = -16; y <= 80; y += 16) {
                    Holder<Biome> seen = view.getNoiseBiome(x, y, z, sampler);
                    Holder<Biome> expected = bound.getNoiseBiome(x, y, z, sampler);
                    compared++;
                    if (!seen.equals(expected)) {
                        differ.add(x + "," + y + "," + z);
                    }
                }
            }
        }
        helper.assertTrue(differ.isEmpty(), what + " answers otherwise at quarts " + differ);
        return compared;
    }

    private static void assertPatchMethods(GameTestHelper helper, BiomeSource view, boolean notOverworld)
            throws Exception {
        Method set = view.getClass().getMethod("confluence_dimension_patch$setIsNotOverworld");
        Method get = view.getClass().getMethod("confluence_dimension_patch$isNotOverworld");
        set.invoke(view);
        helper.assertTrue(
                Boolean.valueOf(notOverworld).equals(get.invoke(view)),
                view.getClass().getSimpleName() + " answers the patch's isNotOverworld with "
                        + get.invoke(view) + " after the patch's set, expected " + notOverworld);
    }

    private static String row(ChunkGenerator parallel, ChunkGenerator overworld) {
        return String.valueOf(ParallelWorldBiomeSync.refusal(parallel, overworld));
    }

    private static Holder<NoiseGeneratorSettings> settings(
            GameTestHelper helper, ResourceKey<NoiseGeneratorSettings> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
    }

    // a generator as parallel_world.json authors it, from the server's own registries
    private static ChunkGenerator overworldNoise(GameTestHelper helper) {
        var presets = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        return new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                settings(helper, NoiseGeneratorSettings.OVERWORLD));
    }
}
