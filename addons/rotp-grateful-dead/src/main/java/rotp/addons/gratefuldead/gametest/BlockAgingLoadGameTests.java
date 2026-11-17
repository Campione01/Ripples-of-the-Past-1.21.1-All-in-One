package rotp.addons.gratefuldead.gametest;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.state.AgingRemovalData;
import rotp.addons.gratefuldead.state.BlockAgingData;
import rotp.addons.gratefuldead.state.LivingAgingState;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModGamerules;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandTuning;

/**
 * Player report of 2026-11-05: once the aging field had aged blocks the game stayed slow, also with the field off.
 * Every test keeps its own BlockAgingData, so the record counts are its own whatever else runs. The scenes stand high
 * in the template's chunk, the one chunk the framework keeps loaded.
 */
@GameTestHolder(GratefulDeadAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockAgingLoadGameTests {
    private static final int SCENE_HEIGHT = 30;
    private static final int RECORDS_IN_A_CHUNK = BlockAgingData.MAX_RECORDS_PER_CHUNK;

    private BlockAgingLoadGameTests() {}

    // One tick past the fade, plus the pass that sees the last records. By visits the 128-a-tick budget makes these
    // 256 records last OWNER_FADE_TICKS * 2 ticks.
    private static final int FADE_DEADLINE = BlockAgingData.OWNER_FADE_TICKS + 6;

    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 100,
            batch = "rotpGdFade")
    public static void aFadeLastsItsTicksHoweverManyRecordsThereAre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockAgingData data = new BlockAgingData();
        UUID nobody = UUID.randomUUID();
        BlockPos corner = chunkCorner(helper);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockPos position = corner.offset(x, 0, z);
                level.setBlockAndUpdate(position, Blocks.GRASS_BLOCK.defaultBlockState());
                helper.assertTrue(data.age(level, position, nobody, 0L, 0.5F),
                        "Setup: a grass block took no aging");
            }
        }
        helper.assertTrue(data.recordCount() == RECORDS_IN_A_CHUNK,
                "Setup: " + data.recordCount() + " records");
        helper.onEachTick(() -> data.tick(level));
        helper.runAfterDelay(FADE_DEADLINE, () -> {
            helper.assertTrue(data.recordCount() == 0,
                    data.recordCount() + " records of " + RECORDS_IN_A_CHUNK + " still fade "
                            + FADE_DEADLINE + " ticks on: a fade of " + BlockAgingData.OWNER_FADE_TICKS
                            + " ticks follows the record budget instead of the clock");
            helper.succeed();
        });
    }

    // Review of 2026-11-05: a record whose chunk had unloaded never faded, kept its old tint and counted against the cap.
    // Its saved form is the old worlds' form too: nothing about a record is new.
    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 20,
            batch = "rotpGdUnloaded")
    public static void aRecordInAnUnloadedChunkDoesNotStayForEver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(1_600_000, 64, 1_600_000);
        helper.assertTrue(!level.hasChunkAt(far), "Setup: the far chunk is loaded");
        CompoundTag record = new CompoundTag();
        record.putLong("Position", far.asLong());
        record.putUUID("Owner", UUID.randomUUID());
        record.putLong("OwnerGeneration", 0L);
        record.putFloat("Progress", 0.5F);
        ListTag records = new ListTag();
        records.add(record);
        CompoundTag saved = new CompoundTag();
        saved.put("Records", records);
        BlockAgingData data = BlockAgingData.load(saved, level.registryAccess());
        helper.assertTrue(data.recordCount() == 1, "Setup: an old world's record did not load");
        data.tick(level);
        helper.assertTrue(data.recordCount() == 0,
                "A record in an unloaded chunk with its owner away stayed: " + data.recordCount());
        helper.assertTrue(!level.hasChunkAt(far), "Ending the record loaded its chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 20,
            batch = "rotpGdQueue")
    public static void aRecordAgedAgainKeepsOnePlaceInTheQueue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockAgingData data = new BlockAgingData();
        UUID nobody = UUID.randomUUID();
        BlockPos position = chunkCorner(helper);
        level.setBlockAndUpdate(position, Blocks.GRASS_BLOCK.defaultBlockState());
        for (int i = 0; i < 1000; i++) {
            data.age(level, position, nobody, 0L, 0.1F);
            data.remove(level, position);
        }
        data.age(level, position, nobody, 0L, 0.1F);
        helper.assertTrue(data.tickQueueSize() == 1,
                "One record holds " + data.tickQueueSize() + " places in the tick queue");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 100,
            batch = "rotpGdFullAge")
    public static void aFullyAgedBlockStaysUntilAPlayerTouchesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockAgingData data = new BlockAgingData();
        ServerPlayer owner = owner(helper);
        BlockPos grass = chunkCorner(helper);
        BlockPos log = grass.offset(4, 0, 4);
        BlockPos fern = grass.offset(8, 1, 8);
        level.setBlockAndUpdate(grass, Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(fern.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(fern, Blocks.FERN.defaultBlockState());
        owner.moveTo(grass.getX() + 0.5D, grass.getY() + 3.0D, grass.getZ() + 0.5D);
        data.age(level, grass, owner.getUUID(), 0L, 1.0F);
        data.age(level, log, owner.getUUID(), 0L, 1.0F);
        data.age(level, fern, owner.getUUID(), 0L, 1.0F);
        helper.onEachTick(() -> data.tick(level));
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(level.getBlockState(grass).is(Blocks.GRASS_BLOCK),
                        "Fully aged grass turned to " + level.getBlockState(grass) + " with nobody on it");
                helper.assertTrue(level.getBlockState(log).is(Blocks.OAK_LOG),
                        "A fully aged log turned to " + level.getBlockState(log));
                helper.assertTrue(level.getBlockState(fern).is(Blocks.FERN),
                        "A fully aged fern turned to " + level.getBlockState(fern) + " with nobody on it");
                helper.assertTrue(data.progress(grass) == 1.0F && data.progress(log) == 1.0F
                                && data.progress(fern) == 1.0F,
                        "A fully aged record left without its owner's removal");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class,
                                new AABB(grass).inflate(24.0D)).isEmpty(),
                        "Aging alone left items on the ground");

                data.crumbleOnContact(level, new AABB(grass.above()));
                helper.assertTrue(level.getBlockState(grass).is(Blocks.DIRT),
                        "Stepped-on aged grass is still " + level.getBlockState(grass));
                helper.assertTrue(data.progress(grass) == 0.0F, "The stepped-on block kept its record");

                data.crumbleOnContact(level, new AABB(log.above()));
                helper.assertTrue(level.getBlockState(log).is(Blocks.OAK_LOG),
                        "1.16 left aged logs alone on contact");

                data.crumbleOnContact(level, new AABB(fern));
                helper.assertTrue(level.getBlockState(fern).isAir(),
                        "A touched aged plant is still " + level.getBlockState(fern));
                helper.succeed();
            }
            finally {
                discard(owner);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 20,
            batch = "rotpGdTerminal")
    public static void flowersAndSaplingsTakeTheir116FormAtFullAge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockAgingData data = new BlockAgingData();
        UUID nobody = UUID.randomUUID();
        BlockPos base = chunkCorner(helper);
        BlockPos flower = base.offset(2, 1, 2);
        BlockPos sapling = base.offset(6, 1, 6);
        for (BlockPos position : new BlockPos[] {flower, sapling}) {
            level.setBlockAndUpdate(position.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        }
        level.setBlockAndUpdate(flower, Blocks.POPPY.defaultBlockState());
        level.setBlockAndUpdate(sapling, Blocks.OAK_SAPLING.defaultBlockState());
        data.age(level, flower, nobody, 0L, 1.0F);
        data.age(level, sapling, nobody, 0L, 1.0F);
        helper.assertTrue(level.getBlockState(flower).is(Blocks.WITHER_ROSE),
                "An aged flower is " + level.getBlockState(flower));
        helper.assertTrue(level.getBlockState(sapling).is(Blocks.DEAD_BUSH),
                "An aged sapling is " + level.getBlockState(sapling));
        helper.assertTrue(data.recordCount() == 0, "A transformed block kept its record");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 20,
            batch = "rotpGdSilent")
    public static void hittingAFullyAgedBlockChangesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockAgingData data = new BlockAgingData();
        UUID nobody = UUID.randomUUID();
        BlockPos position = chunkCorner(helper);
        level.setBlockAndUpdate(position, Blocks.GRASS_BLOCK.defaultBlockState());
        data.age(level, position, nobody, 0L, 1.0F);
        BlockAgingData.Entry first = data.entry(position);
        data.setDirty(false);
        data.age(level, position, nobody, 0L, 0.5F);
        helper.assertTrue(data.entry(position) == first && !data.isDirty(),
                "Another hit on a fully aged block rewrote its record and marked the save dirty");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 20,
            batch = "rotpGdSpread")
    public static void removalEndsTheSpreadingItLeftQueued(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockAgingData data = new BlockAgingData();
        UUID nobody = UUID.randomUUID();
        BlockPos log = chunkCorner(helper).offset(4, 0, 4);
        BlockPos next = log.above();
        BlockPos beside = log.east();
        BlockState oak = Blocks.OAK_LOG.defaultBlockState();
        level.setBlockAndUpdate(log, oak);
        level.setBlockAndUpdate(next, oak);
        level.setBlockAndUpdate(beside, Blocks.GRASS_BLOCK.defaultBlockState());

        data.age(level, log, nobody, 0L, 1.0F);
        data.tick(level);
        helper.assertTrue(data.progress(next) > 0.0F, "A fully aged log did not age the log above it");
        helper.assertTrue(data.progress(beside) == 0.0F, "A tree's aging spread to grass beside it");

        data.remove(level, next);
        BlockAgingData other = new BlockAgingData();
        long generation = AgingRemovalData.get(level.getServer()).generation(nobody);
        other.age(level, log, nobody, generation, 1.0F);
        AgingRemovalData.get(level.getServer()).advance(nobody);
        other.tick(level);
        helper.assertTrue(other.progress(next) == 0.0F,
                "A queued spread still aged a block after the owner's removal");
        helper.succeed();
    }

    /**
     * R772.1 OQ-B01: what aging destroys, a touched plant and a tree part that withers with no log to take it down, is
     * the owner's doing: it happens only with jojoAbilitiesBreakBlocks on and the owner's "breaks blocks" Stand
     * switch on. The rule is set only around each call, so no other test sees it changed.
     */
    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 120,
            batch = "rotpGdBreakRule")
    public static void agingDestroysOnlyWhereTheBlockRuleAndTheOwnersSwitchAllow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean ruleBefore = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
        BlockAgingData data = new BlockAgingData();
        ServerPlayer owner = owner(helper);
        PlayerClientBroadcastedSettings switches =
                owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
        BlockPos grass = chunkCorner(helper);
        BlockPos fern = grass.offset(8, 1, 8);
        BlockPos[] leaves = {grass.offset(3, 2, 6), grass.offset(6, 2, 3), grass.offset(9, 2, 4)};
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        level.setBlockAndUpdate(fern.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(fern, Blocks.FERN.defaultBlockState());
        for (BlockPos position : leaves) {
            level.setBlockAndUpdate(position, leaf);
        }
        owner.moveTo(grass.getX() + 0.5D, grass.getY() + 3.0D, grass.getZ() + 0.5D);
        data.age(level, fern, owner.getUUID(), 0L, 1.0F);
        boolean[] rule = {false};
        helper.onEachTick(() -> underRule(level, rule[0], ruleBefore, () -> data.tick(level)));
        helper.startSequence()
                .thenExecute(() -> guarded(owner, () -> {
                    data.age(level, leaves[0], owner.getUUID(), 0L, 1.0F);
                    underRule(level, false, ruleBefore, () -> data.crumbleOnContact(level, new AABB(fern)));
                }))
                .thenIdle(14)
                .thenExecute(() -> guarded(owner, () -> {
                    helper.assertTrue(level.getBlockState(fern).is(Blocks.FERN)
                                    && level.getBlockState(leaves[0]).is(Blocks.OAK_LEAVES),
                            "Aging destroyed blocks with jojoAbilitiesBreakBlocks off");
                    switches.standBreaksBlocks = false;
                    rule[0] = true;
                    data.age(level, leaves[1], owner.getUUID(), 0L, 1.0F);
                    underRule(level, true, ruleBefore, () -> data.crumbleOnContact(level, new AABB(fern)));
                }))
                .thenIdle(14)
                .thenExecute(() -> guarded(owner, () -> {
                    helper.assertTrue(level.getBlockState(fern).is(Blocks.FERN)
                                    && level.getBlockState(leaves[1]).is(Blocks.OAK_LEAVES),
                            "Aging destroyed blocks with its owner's Stand switch off");
                    switches.standBreaksBlocks = true;
                    data.age(level, leaves[2], owner.getUUID(), 0L, 1.0F);
                    underRule(level, true, ruleBefore, () -> data.crumbleOnContact(level, new AABB(fern)));
                }))
                .thenIdle(14)
                .thenExecute(() -> guarded(owner, () -> {
                    helper.assertTrue(level.getBlockState(fern).isAir() && level.getBlockState(leaves[2]).isAir(),
                            "With the rule and the switch on aging must still destroy a touched plant and a lone leaf: "
                                    + level.getBlockState(fern) + " / " + level.getBlockState(leaves[2]));
                    // what the destroyed plants dropped must not reach a test that counts items on the ground
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(grass).inflate(24.0D)).forEach(Entity::discard);
                    discard(owner);
                }))
                .thenSucceed();
    }

    /**
     * R772.1 EV-STANDCFG item3: old age is dealt from the aged creature's own tick, outside the Stand's code, so the
     * aging says whose work it is: a contact on a fully aged cow takes 1.0 x 1.85 (the aged take x1.85) at the default
     * share and half of that at 50 % damage to mobs.
     */
    @GameTest(template = "empty", templateNamespace = GratefulDeadAddon.MOD_ID, timeoutTicks = 40,
            batch = "rotpGdDamageShare")
    public static void agingDamageTakesTheStandsDamageShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = owner(helper);
        StandTuning tuning = GratefulDeadAddon.THE_GRATEFUL_DEAD.get().getTuning();
        BlockPos spot = chunkCorner(helper).offset(4, 0, 4);
        Cow cow = EntityType.COW.create(level);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.0F, 0.0F);
        owner.moveTo(spot.getX() + 3.5D, spot.getY(), spot.getZ() + 0.5D);
        try {
            helper.assertTrue(level.addFreshEntity(cow), "Fixture: the cow was not added");
            LivingAgingState state = LivingAgingState.get(cow);
            state.age(cow, owner.getUUID(), 0L, 1.0F, false);
            helper.assertTrue(state.progress() >= 1.0F, "Fixture: the cow is not fully aged");
            float whole = contactDamage(cow, state, owner);
            helper.assertTrue(Math.abs(whole - 1.85F) < 1.0E-4F,
                    "At the default share a contact on a fully aged cow deals 1.0 x 1.85 (the aged take x1.85); it took " + whole);
            JsonObject half = new JsonObject();
            half.addProperty("damageVsMobs", 50);
            tuning.applyConfig(half);
            float halved = contactDamage(cow, state, owner);
            helper.assertTrue(Math.abs(halved - 0.925F) < 1.0E-4F,
                    "At 50 % damage to mobs a contact on a fully aged cow deals half of 1.85; it took " + halved);
        }
        finally {
            tuning.restoreDefaults();
            cow.discard();
            discard(owner);
        }
        helper.succeed();
    }

    // one more tick of contact with the owner's aging; what the cow lost
    private static float contactDamage(Cow cow, LivingAgingState state, ServerPlayer owner) {
        cow.setHealth(cow.getMaxHealth());
        float before = cow.getHealth();
        cow.tickCount += 2;
        state.age(cow, owner.getUUID(), 0L, 0.01F, true);
        return before - cow.getHealth();
    }

    private static void underRule(ServerLevel level, boolean rule, boolean ruleBefore, Runnable body) {
        level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(rule, level.getServer());
        try {
            body.run();
        }
        finally {
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(ruleBefore, level.getServer());
        }
    }

    private static void guarded(ServerPlayer owner, Runnable body) {
        try {
            body.run();
        }
        catch (RuntimeException | Error failure) {
            discard(owner);
            throw failure;
        }
    }

    private static BlockPos chunkCorner(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(origin);
        return new BlockPos(chunk.getMinBlockX(), origin.getY() + SCENE_HEIGHT, chunk.getMinBlockZ());
    }

    // As GameTestHelper.makeMockServerPlayerInLevel, with the NeoForge channel set up: a player on the server's
    // list, which is where a record looks for its owner.
    private static ServerPlayer owner(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "gd-aging-owner");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        PowerClass.STAND.attachGet(player).setStand(GratefulDeadAddon.THE_GRATEFUL_DEAD.get());
        return player;
    }

    private static void discard(ServerPlayer player) {
        player.getServer().getPlayerList().remove(player);
    }
}
