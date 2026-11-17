package rotp.addons.d4c;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.config.editor.AddonConfigKeys;
import rotp.core.config.editor.ConfigChoices;
import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditorPackets;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.ConfigEditorPackets.Reply;
import rotp.core.config.editor.ConfigEditorPackets.Request;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ConfigWidgetRules;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.d4c.travel.D4CDimensionTargets;
import rotp.addons.d4c.travel.D4CDimensionTravelService;
import rotp.addons.d4c.travel.D4CDimensionUnlocks;
import rotp.addons.d4c.travel.D4CParallelLevels;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R772.1 D4CDIM: the selector lists the server's own dimensions, a real
 * one only once the player has been there (owner 2026-11-10), parallels
 * by their pair only; arrivals are safe or refused; ticked dimensions get
 * a made parallel at start. The game-test server is the flat preset with
 * the Overworld, the Nether and the End only (GameTestServer.create bakes
 * an empty stem registry, so no data-defined dimension exists here, the
 * Overworld's parallel included); runD4cGameTest ticks the Nether and the
 * End for these tests.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CDimensionMenuGameTests {
    private static final String SESSION_ID =
            "rotp_d4c.phase2a.selector_session";
    private static final String SESSION_EXPIRY =
            "rotp_d4c.phase2a.selector_expiry";
    private static final String SESSION_DIMENSION =
            "rotp_d4c.phase2a.selector_dimension";
    private static final String SELECTED =
            "rotp_d4c.phase2a.selected_dimension";
    private static final String PORTAL_COOLDOWN = "rotp_d4c.portal_cooldown";
    private static final ResourceLocation OVERWORLD =
            D4CDimensionTargets.OVERWORLD;
    private static final ResourceLocation NETHER = D4CDimensionTargets.NETHER;
    private static final ResourceLocation END = D4CDimensionTargets.END;

    private D4CDimensionMenuGameTests() {}

    @GameTest(template = "empty", batch = "d4cDimMenuList")
    public static void menuOffersOnlyUnlockedRealDimensions(
            GameTestHelper helper) {
        ServerPlayer player = player(helper, "d4c-dim-list");
        try {
            assertList(helper, player, List.of(OVERWORLD),
                    "a fresh survival player");
            D4CDimensionUnlocks.record(player, NETHER);
            assertList(helper, player, List.of(OVERWORLD, NETHER),
                    "after the Nether was recorded");
            player.setGameMode(GameType.CREATIVE);
            assertList(helper, player, List.of(OVERWORLD, NETHER, END),
                    "in creative");
        }
        finally {
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimVisit", timeoutTicks = 200)
    public static void aRealDimensionChangeUnlocksIt(GameTestHelper helper) {
        ServerPlayer player = player(helper, "d4c-dim-visit");
        try {
            helper.assertTrue(
                    !D4CDimensionUnlocks.isUnlocked(player, NETHER),
                    "The Nether is unlocked before any visit.");
            ServerLevel nether = level(helper, NETHER);
            player.teleportTo(
                    nether, 2100.5D, 80.0D, 2100.5D, Set.of(), 0.0F, 0.0F);
            helper.assertTrue(
                    player.level() == nether,
                    "The fixture player did not reach the Nether.");
            helper.assertTrue(
                    D4CDimensionUnlocks.isUnlocked(player, NETHER),
                    "A real dimension change into the Nether did not "
                            + "unlock it: " + D4CDimensionUnlocks.visited(
                                    player));
            helper.assertTrue(
                    !D4CDimensionUnlocks.isUnlocked(player, END),
                    "The End was unlocked by a visit to the Nether.");
        }
        finally {
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimKeep")
    public static void unlocksSurviveDeathAndRelog(GameTestHelper helper) {
        ServerPlayer player = player(helper, "d4c-dim-keep");
        try {
            D4CDimensionUnlocks.record(player, END);
            MinecraftServer server = helper.getLevel().getServer();
            ServerPlayer respawned = new ServerPlayer(
                    server,
                    helper.getLevel(),
                    player.getGameProfile(),
                    ClientInformation.createDefault());
            respawned.copyAttachmentsFrom(player, true);
            helper.assertTrue(
                    D4CDimensionUnlocks.isUnlocked(respawned, END),
                    "The record was lost through death.");

            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            ServerPlayer relogged = new ServerPlayer(
                    server,
                    helper.getLevel(),
                    player.getGameProfile(),
                    ClientInformation.createDefault());
            relogged.load(saved);
            helper.assertTrue(
                    relogged.getData(D4CDimensionUnlocks.VISITED).seeded()
                            && D4CDimensionUnlocks.isUnlocked(relogged, END),
                    "The record was lost through a save and load: "
                            + relogged.getData(D4CDimensionUnlocks.VISITED));
        }
        finally {
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimOldSave")
    public static void firstRecordFollowsTheEnteringAdvancements(
            GameTestHelper helper) {
        ServerPlayer player = player(helper, "d4c-dim-old-save");
        try {
            MinecraftServer server = helper.getLevel().getServer();
            AdvancementHolder netherRoot = server.getAdvancements().get(
                    ResourceLocation.withDefaultNamespace("nether/root"));
            helper.assertTrue(
                    netherRoot != null, "No nether/root advancement.");
            for (String criterion : netherRoot.value().criteria().keySet()) {
                player.getAdvancements().award(netherRoot, criterion);
            }
            Set<ResourceLocation> first =
                    D4CDimensionUnlocks.visited(player);
            helper.assertTrue(
                    first.contains(OVERWORLD)
                            && first.contains(NETHER)
                            && !first.contains(END),
                    "First record with nether/root alone: " + first
                            + ", expected the Overworld and the Nether.");
        }
        finally {
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimPacket")
    public static void aCraftedSelectionOfALockedDimensionIsRefused(
            GameTestHelper helper) {
        ServerPlayer player = player(helper, "d4c-dim-packet");
        try {
            giveD4C(helper, player);
            UUID first = openSession(player);
            helper.assertTrue(
                    !D4CDimensionTravelService.consumeSelection(
                            player, first, END),
                    "A packet choosing the locked End was accepted.");
            helper.assertTrue(
                    !player.getPersistentData().getString(SELECTED)
                            .equals(END.toString()),
                    "The locked End became the selection.");
            D4CDimensionUnlocks.record(player, END);
            UUID second = openSession(player);
            helper.assertTrue(
                    D4CDimensionTravelService.consumeSelection(
                            player, second, END),
                    "A packet choosing the unlocked End was refused.");
        }
        finally {
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimExclude")
    public static void exclusionTagIsLoadedAndMadeLevelsAreNoTargets(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(
                server.registryAccess()
                        .registryOrThrow(Registries.LEVEL_STEM)
                        .getTag(D4CDimensionTargets.EXCLUDED_FROM_TRAVEL)
                        .isPresent(),
                "The exclusion tag "
                        + D4CDimensionTargets.EXCLUDED_FROM_TRAVEL.location()
                        + " was not loaded into the dimension registry.");
        ResourceLocation madeEnd = D4CParallelLevels.parallelIdOf(END);
        helper.assertTrue(
                D4CDimensionTargets.isCandidate(server, NETHER)
                        && !D4CDimensionTargets.isCandidate(server, madeEnd),
                "Candidates: the Nether "
                        + D4CDimensionTargets.isCandidate(server, NETHER)
                        + ", a level made at run time "
                        + D4CDimensionTargets.isCandidate(server, madeEnd));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimLava")
    public static void theArrivalSkipsLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "d4c-dim-lava");
        BlockPos feet = helper.absolutePos(new BlockPos(4, 4, 4));
        List<BlockPos> touched = new ArrayList<>();
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(level, feet.offset(dx, -1, dz), Blocks.STONE, touched);
                    set(level, feet.offset(dx, 1, dz), Blocks.AIR, touched);
                    set(level, feet.offset(dx, 2, dz), Blocks.AIR, touched);
                    if (dx != 0 || dz != 0) {
                        set(level, feet.offset(dx, 0, dz), Blocks.AIR, touched);
                    }
                }
            }
            set(level, feet, Blocks.LAVA, touched);
            player.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
            BlockPos arrival =
                    D4CDimensionTravelService.safeArrival(level, player);
            helper.assertTrue(
                    arrival != null
                            && level.getFluidState(arrival).isEmpty()
                            && level.getFluidState(arrival.above()).isEmpty(),
                    "The arrival was chosen in lava: " + arrival
                            + " (lava at " + feet + ").");
        }
        finally {
            restore(level, touched);
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimRefuse", timeoutTicks = 200)
    public static void travelWithNoSafeSpotIsRefusedWithAMessage(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level(helper, NETHER);
        ServerPlayer player = player(helper, "d4c-dim-refuse");
        BlockPos feet = helper.absolutePos(new BlockPos(2, 3, 2));
        List<BlockPos> here = new ArrayList<>();
        List<BlockPos> there = new ArrayList<>();
        List<BlockState> thereStates = new ArrayList<>();
        try {
            giveD4C(helper, player);
            set(level, feet.below(), Blocks.STONE, here);
            for (int y = 0; y <= 1; y++) {
                set(level, feet.offset(1, y, 0), Blocks.STONE, here);
                set(level, feet.offset(-1, y, 0), Blocks.STONE, here);
            }
            set(level, feet, Blocks.AIR, here);
            set(level, feet.above(), Blocks.AIR, here);
            player.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
            helper.assertTrue(
                    D4CDimensionTravelService.isCaughtBetween(level, player),
                    "fixture: the player is not caught between the stones.");
            D4CDimensionUnlocks.record(player, NETHER);
            player.getPersistentData().putString(SELECTED, NETHER.toString());

            // every block the search could stand on is stone
            int cx = (int) Math.floor(player.getX() / 8.0D);
            int cz = (int) Math.floor(player.getZ() / 8.0D);
            int radius = D4CDimensionTravelService.SAFE_SEARCH_RADIUS;
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    for (int y = nether.getMinBuildHeight();
                            y < nether.getMinBuildHeight()
                                    + nether.getLogicalHeight();
                            y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        there.add(pos);
                        thereStates.add(nether.getBlockState(pos));
                        nether.setBlock(pos, Blocks.STONE.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
            D4CDimensionTravelService.TravelPlanResult result =
                    D4CDimensionTravelService.planTravel(player);
            Component failure = result.failure();
            helper.assertTrue(
                    !result.successful()
                            && failure != null
                            && failure.getContents()
                                    instanceof TranslatableContents contents
                            && contents.getKey().equals(
                                    "rotp_d4c.message.no_safe_destination"),
                    "Travel with no safe spot was not refused with its "
                            + "message: plan=" + result.plan()
                            + ", failure=" + failure);
            helper.assertTrue(
                    player.isAlive() && player.level() == level,
                    "The refused traveller moved or died.");
        }
        finally {
            for (int i = 0; i < there.size(); i++) {
                nether.setBlock(there.get(i), thereStates.get(i),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            restore(level, here);
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimMade")
    public static void onlyTickedDimensionsGetAMadeParallel(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(
                D4CParallelLevels.plan(server, List.of("minecraft:overworld"))
                        .isEmpty(),
                "The default setting would make a level.");
        List<D4CParallelLevels.Planned> rows = D4CParallelLevels.plan(
                server,
                List.of(
                        "minecraft:overworld",
                        "minecraft:the_nether",
                        "rotp_d4c_test:missing",
                        D4CParallelLevels.parallelIdOf(END).toString()));
        helper.assertTrue(
                rows.size() == 3
                        && rows.get(0).source().equals(NETHER)
                        && rows.get(0).refusal() == null
                        && rows.get(1).refusal() != null
                        && rows.get(2).refusal() != null,
                "Plan rows: " + rows);
        for (ResourceLocation source : List.of(END)) {
            ResourceKey<Level> key = D4CDimensionTargets.levelKey(
                    D4CParallelLevels.parallelIdOf(source));
            ServerLevel made = server.getLevel(key);
            helper.assertTrue(
                    made != null && D4CParallelLevels.isCreated(key),
                    "The ticked " + source + " has no made parallel "
                            + key.location());
            helper.assertTrue(
                    made.dimensionTypeRegistration().equals(
                            server.getLevel(D4CDimensionTargets.levelKey(
                                    source)).dimensionTypeRegistration()),
                    "The parallel of " + source
                            + " does not use its dimension type.");
        }
        helper.assertTrue(
                server.getLevel(D4CDimensionTargets.levelKey(
                        D4CAddon.id("parallel/minecraft/overworld"))) == null
                        && server.getLevel(D4CDimensionTargets.levelKey(
                                D4CParallelLevels.parallelIdOf(NETHER))) == null
                        && server.getLevel(D4CDimensionTargets.levelKey(
                                D4CAddon.id("parallel/rotp_d4c_test/missing")))
                                == null,
                "A level was made for the Overworld, the unticked Nether "
                        + "or a missing id.");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimDragon")
    public static void theEndsParallelHasNoDragonFight(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel end = level(helper, END);
        ServerLevel copy = server.getLevel(D4CDimensionTargets.levelKey(
                D4CParallelLevels.parallelIdOf(END)));
        helper.assertTrue(copy != null, "The End has no made parallel.");
        helper.assertTrue(
                end.getDragonFight() != null && copy.getDragonFight() == null,
                "Dragon fights: the End " + end.getDragonFight()
                        + ", its parallel " + copy.getDragonFight());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimPair", timeoutTicks = 200)
    public static void aParallelIsOfferedOnlyInsideItsPair(
            GameTestHelper helper) {
        ServerPlayer player = player(helper, "d4c-dim-pair");
        ResourceLocation endCopy = D4CParallelLevels.parallelIdOf(END);
        try {
            List<ResourceLocation> fromOverworld =
                    D4CDimensionTargets.offeredTargets(player);
            helper.assertTrue(
                    !fromOverworld.contains(endCopy),
                    "From the Overworld: " + fromOverworld);
            ServerLevel nether = level(helper, NETHER);
            player.teleportTo(
                    nether, 2200.5D, 80.0D, 2200.5D, Set.of(), 0.0F, 0.0F);
            List<ResourceLocation> fromNether =
                    D4CDimensionTargets.offeredTargets(player);
            helper.assertTrue(
                    !fromNether.contains(endCopy),
                    "From the Nether: " + fromNether);
            ServerLevel end = level(helper, END);
            player.teleportTo(
                    end, 2200.5D, 80.0D, 2200.5D, Set.of(), 0.0F, 0.0F);
            List<ResourceLocation> fromEnd =
                    D4CDimensionTargets.offeredTargets(player);
            helper.assertTrue(
                    fromEnd.contains(endCopy),
                    "From the End: " + fromEnd);
            ServerLevel copy = level(helper, endCopy);
            player.teleportTo(
                    copy, 2200.5D, 80.0D, 2200.5D, Set.of(), 0.0F, 0.0F);
            List<ResourceLocation> fromCopy =
                    D4CDimensionTargets.offeredTargets(player);
            helper.assertTrue(
                    fromCopy.contains(endCopy)
                            && fromCopy.contains(END),
                    "From the End's parallel: " + fromCopy);
        }
        finally {
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimCross", timeoutTicks = 300)
    public static void crossingToAMadeParallelCopiesAndComesBack(
            GameTestHelper helper) {
        ServerPlayer player = player(helper, "d4c-dim-cross");
        ServerLevel end = level(helper, END);
        ResourceLocation copyId = D4CParallelLevels.parallelIdOf(END);
        ServerLevel copy = level(helper, copyId);
        BlockPos feet = new BlockPos(2300, 70, 2300);
        BlockPos marker = feet.offset(2, 0, 0);
        try {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    end.setBlockAndUpdate(
                            feet.offset(dx, -1, dz),
                            Blocks.STONE.defaultBlockState());
                    for (int dy = 0; dy <= 2; dy++) {
                        end.setBlockAndUpdate(
                                feet.offset(dx, dy, dz),
                                Blocks.AIR.defaultBlockState());
                    }
                }
            }
            end.setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
            copy.setBlockAndUpdate(marker, Blocks.AIR.defaultBlockState());
            player.teleportTo(
                    end, feet.getX() + 0.5D, feet.getY(),
                    feet.getZ() + 0.5D, Set.of(), 0.0F, 0.0F);
            helper.assertTrue(
                    player.level() == end,
                    "fixture: the player is not in the End.");

            // the End's own portal inside the End leads to its parallel
            boolean crossed = D4CDimensionTravelService.teleportThroughPortal(
                    end, feet, player, END);
            helper.assertTrue(
                    crossed && player.level() == copy,
                    "The crossing into the End's parallel failed: "
                            + crossed + ", in " + player.level().dimension());
            helper.assertTrue(
                    copy.getBlockState(marker).is(Blocks.GOLD_BLOCK),
                    "The crossing did not copy the departure area: "
                            + copy.getBlockState(marker));

            player.getPersistentData().remove(PORTAL_COOLDOWN);
            boolean back = D4CDimensionTravelService.teleportThroughPortal(
                    copy, player.blockPosition(), player, END);
            helper.assertTrue(
                    back && player.level() == end,
                    "The way back to the End failed: " + back + ", in "
                            + player.level().dimension());
        }
        finally {
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cDimOverworldPair")
    public static void theOverworldPairKeepsItsRows(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceKey<Level> parallel = D4CDimensionTargets.levelKey(
                D4CDimensionTargets.PARALLEL_WORLD);
        for (ResourceKey<Level> from : server.levelKeys()) {
            for (ResourceKey<Level> to : server.levelKeys()) {
                helper.assertTrue(
                        !(from == Level.OVERWORLD || to.equals(parallel))
                                || !D4CParallelLevels.isCreatedPair(from, to),
                        "A made pair claims the Overworld's crossing: "
                                + from.location() + " -> " + to.location());
            }
        }
        helper.assertTrue(
                D4CDimensionTravelService.pulledDestination(
                                server, OVERWORLD, OVERWORLD)
                        .equals(D4CDimensionTargets.PARALLEL_WORLD)
                        && D4CDimensionTravelService.pulledDestination(
                                        server,
                                        D4CDimensionTargets.PARALLEL_WORLD,
                                        D4CDimensionTargets.PARALLEL_WORLD)
                                .equals(OVERWORLD),
                "The Overworld pair's pull-in rows changed.");
        helper.succeed();
    }

    // R772.1 follow-up: the setting is the Stands page's tick list, through
    // the settings editor's own server path (read, write, read back)
    @GameTest(template = "empty", batch = "d4cDimSettings")
    public static void theParallelSettingIsATickListOnTheStandsPage(
            GameTestHelper helper) throws IOException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = player(helper, "d4c-dim-settings");
        String key = AddonConfigKeys.key(
                D4CAddon.MOD_ID, D4CConfig.PARALLEL_DIMENSIONS.getPath());
        List<String> before = List.copyOf(D4CConfig.PARALLEL_DIMENSIONS.getRaw());
        server.getPlayerList().getOps().add(
                new ServerOpListEntry(player.getGameProfile(), 2, false));
        try {
            List<ConfigEntry> entries = read(player);
            ConfigEntry row = entries.stream()
                    .filter(entry -> entry.key().equals(key))
                    .findFirst()
                    .orElse(null);
            helper.assertTrue(row != null, "No row " + key + " on the page.");
            helper.assertTrue(
                    row.listChoices() != null
                            && ConfigWidgetRules.widgetKind(row)
                                    == ConfigWidgetRules.WidgetKind.CHECKLIST
                            && ConfigWidgetRules.isOnStandsPage(
                                    row, ConfigWidgetRules.standNamespaces(entries)),
                    "The setting is not a tick list in D4C's Stand block: "
                            + row.listChoices() + ", "
                            + ConfigWidgetRules.widgetKind(row));
            List<String> offered = row.listChoices().stream()
                    .map(ConfigChoices.Choice::id)
                    .toList();
            List<String> expected = new ArrayList<>();
            for (ResourceKey<Level> level : server.levelKeys()) {
                if (!D4CParallelLevels.isParallel(level.location())) {
                    expected.add(level.location().toString());
                }
            }
            helper.assertTrue(
                    offered.size() == expected.size()
                            && offered.containsAll(expected)
                            && offered.getFirst().equals(OVERWORLD.toString()),
                    "Offered " + offered + ", expected every dimension but "
                            + "D4C's parallels, the Overworld first: " + expected);
            helper.assertTrue(
                    row.defaultValue().getAsJsonArray().size() == 1
                            && row.defaultValue().getAsJsonArray().get(0)
                                    .getAsString().equals(OVERWORLD.toString())
                            && row.restart() == ConfigEntry.Restart.WORLD
                            && "rotp_d4c.configuration.parallelDimensions"
                                    .equals(row.translationKey()),
                    "Default, restart hint or name: " + row.defaultValue()
                            + ", " + row.restart() + ", " + row.translationKey());

            Reply written = write(player, key, OVERWORLD.toString(), NETHER.toString());
            helper.assertTrue(
                    written.result().status() == ConfigEditResult.Status.OK
                            && written.result().changes().getFirst().restart()
                                    == ConfigEntry.Restart.WORLD
                            && D4CConfig.PARALLEL_DIMENSIONS.getRaw().equals(
                                    List.of(OVERWORLD.toString(), NETHER.toString())),
                    "Ticking the Nether: " + written.result() + ", stored "
                            + D4CConfig.PARALLEL_DIMENSIONS.getRaw());
            String file = Files.readString(
                    FMLPaths.CONFIGDIR.get().resolve("rotp_d4c-common.toml"),
                    StandardCharsets.UTF_8);
            helper.assertTrue(
                    file.contains("\"minecraft:the_nether\""),
                    "The COMMON file does not hold the change: " + file);
            ConfigEntry again = read(player).stream()
                    .filter(entry -> entry.key().equals(key))
                    .findFirst()
                    .orElseThrow();
            helper.assertTrue(
                    again.value().getAsJsonArray().size() == 2
                            && again.value().getAsJsonArray().get(1).getAsString()
                                    .equals(NETHER.toString()),
                    "Read back: " + again.value());
            helper.assertTrue(
                    write(player, key, OVERWORLD.toString(),
                            D4CParallelLevels.parallelIdOf(END).toString())
                            .result().status()
                            == ConfigEditResult.Status.INVALID_VALUE,
                    "A parallel world itself could be ticked.");
        }
        finally {
            // the fixture's list holds a missing id the page would refuse
            D4CConfig.PARALLEL_DIMENSIONS.set(before);
            D4CConfig.SPEC.save();
            server.getPlayerList().getOps().remove(player.getGameProfile());
            remove(player);
        }
        helper.succeed();
    }

    // R772.1 follow-up (coordinator's ruling): items follow "a parallel
    // world of D4C", so the End's made parallel behaves as parallel_world
    @GameTest(
            template = "empty",
            batch = "d4cDimItems",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void itemsInAMadeParallelFollowTheParallelRules(
            GameTestHelper helper) {
        BlockPos at = new BlockPos(2400, 70, 2400);
        ServerLevel end = level(helper, END);
        ServerLevel copy = level(helper, D4CParallelLevels.parallelIdOf(END));
        // item entities are seen only where the chunk ticks entities
        new GameTestChunkTickets(helper, copy).cover(at);
        new GameTestChunkTickets(helper, end).cover(at)
                .whenTicking(() -> {}, h -> itemsBody(h, end, copy, at));
    }

    private static void itemsBody(
            GameTestHelper helper, ServerLevel end, ServerLevel copy, BlockPos at) {
        MinecraftServer server = helper.getLevel().getServer();
        copy.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
        end.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
        ItemEntity born = new ItemEntity(copy, at.getX() + 0.5D, at.getY(),
                at.getZ() + 0.5D, new ItemStack(Items.COBBLESTONE));
        copy.addFreshEntity(born);
        helper.assertTrue(
                D4CParallelItemService.isParallel(born.getItem()),
                "An item that appeared in the End's parallel is not a "
                        + "parallel item.");
        born.discard();

        ItemStack parallelStack = new ItemStack(Items.COBBLESTONE);
        D4CParallelItemService.markParallel(parallelStack);
        ItemEntity parallelItem = new ItemEntity(end, at.getX() + 0.5D,
                at.getY(), at.getZ() + 0.5D, parallelStack);
        ItemEntity nativeItem = new ItemEntity(end, at.getX() + 0.6D,
                at.getY(), at.getZ() + 0.5D, new ItemStack(Items.COBBLESTONE));
        parallelItem.setNoGravity(true);
        nativeItem.setNoGravity(true);
        end.addFreshEntity(parallelItem);
        end.addFreshEntity(nativeItem);
        try {
            helper.assertTrue(
                    !D4CParallelItemService.isParallel(nativeItem.getItem()),
                    "An item that appeared in the End itself was marked.");
            D4CParallelItemService.tick(server);
            helper.assertTrue(
                    parallelItem.isRemoved() && nativeItem.isRemoved(),
                    "A parallel and a native item touching in the End were "
                            + "not annihilated: parallel removed="
                            + parallelItem.isRemoved() + ", native removed="
                            + nativeItem.isRemoved());
        }
        finally {
            parallelItem.discard();
            nativeItem.discard();
        }
        helper.assertTrue(
                D4CParallelLevels.isParallel(D4CDimensionTargets.PARALLEL_WORLD)
                        && D4CParallelLevels.isParallel(copy.dimension().location())
                        && !D4CParallelLevels.isParallel(END),
                "The fog's test for a parallel world is wrong.");
        helper.succeed();
    }

    private static List<ConfigEntry> read(ServerPlayer player) {
        Exchange exchange = new Exchange(player);
        ConfigEditorPackets.handleRequest(
                new Request(1, Operation.READ, "", null), exchange.context);
        return exchange.replies.getFirst().entries();
    }

    private static Reply write(ServerPlayer player, String key, String... items) {
        JsonArray array = new JsonArray();
        for (String item : items) {
            array.add(item);
        }
        Exchange exchange = new Exchange(player);
        ConfigEditorPackets.handleRequest(
                new Request(2, Operation.SET, key, array), exchange.context);
        return exchange.replies.getFirst();
    }

    // the editor's replies captured as a connection would send them
    private static final class Exchange {
        final List<Reply> replies = new ArrayList<>();
        final IPayloadContext context;

        Exchange(ServerPlayer player) {
            ICommonPacketListener listener = (ICommonPacketListener) Proxy.newProxyInstance(
                    ICommonPacketListener.class.getClassLoader(),
                    new Class<?>[] {ICommonPacketListener.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasChannel" -> Reply.TYPE.equals(args[0]);
                        case "send" -> {
                            replies.add((Reply) args[0]);
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            context = (IPayloadContext) Proxy.newProxyInstance(
                    IPayloadContext.class.getClassLoader(),
                    new Class<?>[] {IPayloadContext.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "player" -> player;
                        case "listener" -> listener;
                        case "reply" -> InvocationHandler.invokeDefault(proxy, method, args);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static void assertList(
            GameTestHelper helper,
            ServerPlayer player,
            List<ResourceLocation> expected,
            String when) {
        List<ResourceLocation> actual =
                D4CDimensionTargets.offeredTargets(player);
        helper.assertTrue(
                actual.equals(expected),
                "Offered " + when + ": " + actual + ", expected " + expected);
    }

    private static ServerLevel level(GameTestHelper helper, ResourceLocation id) {
        ServerLevel level = helper.getLevel().getServer().getLevel(
                D4CDimensionTargets.levelKey(id));
        helper.assertTrue(level != null, "No level " + id + " here.");
        return level;
    }

    private static void giveD4C(GameTestHelper helper, ServerPlayer player) {
        StandPower power = StandPower.get(player);
        helper.assertTrue(power != null, "fixture: no Stand power.");
        power.setStand(D4CAddon.D4C.get());
        power.skipProgression();
    }

    private static UUID openSession(ServerPlayer player) {
        UUID session = UUID.randomUUID();
        CompoundTag data = player.getPersistentData();
        data.putString(SESSION_ID, session.toString());
        data.putLong(SESSION_EXPIRY, player.serverLevel().getGameTime() + 100L);
        data.putString(
                SESSION_DIMENSION,
                player.level().dimension().location().toString());
        return session;
    }

    private static void set(
            ServerLevel level, BlockPos pos, Block block, List<BlockPos> touched) {
        touched.add(pos.immutable());
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void restore(ServerLevel level, List<BlockPos> touched) {
        for (BlockPos pos : touched) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(
                server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentListener(
                server,
                connection,
                player,
                CommonListenerCookie.createInitial(profile, false));
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 1, 1));
        player.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
        player.setGameMode(GameType.SURVIVAL);
        level.addNewPlayer(player);
        return player;
    }

    private static void remove(ServerPlayer player) {
        player.serverLevel().removePlayerImmediately(
                player, Entity.RemovalReason.DISCARDED);
    }

    private static final class SilentListener
            extends ServerGamePacketListenerImpl {
        private SilentListener(
                MinecraftServer server,
                Connection connection,
                ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
        }
    }
}
