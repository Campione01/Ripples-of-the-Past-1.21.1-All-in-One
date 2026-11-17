package rotp.core.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.s2c.TrDirectEntityDataPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the owner's "連拉桿紅石也等時間恢復" asks of stopped time, through real level calls: random ticks stop, block events
 * wait, and neighbour updates to redstone wait for the level's last stop to end while building goes on as it did.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopWorldGameTests {
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_stop_world_gametest"));
	private static int nextStopId = -7_340_000;
	private static final int[] START_END_MOBS = { 1000, 3000 };

	private TimeStopWorldGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.WORLD_DIRTY_DATA)
	public static void entityDataChangesWaitForTheFrozenViewer(GameTestHelper helper) {
		Scene s = start(helper);
		ArmorStand actor = helper.spawn(EntityType.ARMOR_STAND, 3, 2, 2);
		FakePlayer viewer = new FakePlayer(s.level, new GameProfile(UUID.randomUUID(), "FrozenDataViewer"));
		List<TrDirectEntityDataPacket> received = new ArrayList<>();
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		var tracked = s.level.getChunkSource().chunkMap.entityMap.get(actor.getId());
		boolean addedTracker = false;
		try {
			viewer.connection = new ServerGamePacketListenerImpl(s.level.getServer(), connection, viewer,
					CommonListenerCookie.createInitial(viewer.getGameProfile(), false)) {
				@Override
				public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
					if (packet instanceof ClientboundCustomPayloadPacket payload
							&& payload.payload() instanceof TrDirectEntityDataPacket data
							&& data.entityId() == actor.getId()) {
						received.add(data);
					}
				}
			};
			Vec3 at = Vec3.atBottomCenterOf(s.abs(2, 2, 2));
			viewer.moveTo(at.x, at.y, at.z);
			s.level.addNewPlayer(viewer);
			helper.assertTrue(tracked != null && s.level.players().contains(viewer), "Expected native entity and player tracking");
			addedTracker = tracked.seenBy.add(viewer.connection);
			actor.setCustomName(Component.literal("before time stopped"));
			int stop = s.stop(200, 0);
			helper.assertTrue(!s.state.canPlayerSeeAndMove(viewer).canSee(), "The observer did not enter frozen vision");
			s.state.tickLifecycle();
			helper.assertTrue(!actor.getEntityData().isDirty() && received.isEmpty(),
					"Already dirty data must be queued when the first viewer freezes, without sending yet");

			actor.setCustomName(Component.literal("during stopped time"));
			s.state.tickLifecycle();
			helper.assertTrue(!actor.getEntityData().isDirty() && received.isEmpty(),
					"A real dirty write during stopped time must be queued without sending to the frozen viewer");
			s.state.removeInstance(stop);
			helper.assertTrue(received.size() == 1 && received.getFirst().packedItems().stream().anyMatch(item ->
					item.value() instanceof Optional<?> name && name.orElse(null) instanceof Component text
							&& text.getString().equals("during stopped time")),
					"Resuming must send the latest held value once");
		}
		finally {
			if (addedTracker) {
				tracked.seenBy.remove(viewer.connection);
			}
			s.close();
			viewer.discard();
			actor.discard();
			channel.finishAndReleaseAll();
		}
		helper.succeed();
	}

	/**
	 * A frozen viewer logs out on the last tick of the last stop, so neither the awareness pass nor the data sync meets it
	 * again: its name must not stay behind and keep the index taking every entity's data writes with no stop running.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.WORLD_VIEWER_LEFT)
	public static void aViewerGoneBeforeTheLastStopEndedLeavesNoDirtyIndex(GameTestHelper helper) {
		Scene s = start(helper);
		ArmorStand actor = helper.spawn(EntityType.ARMOR_STAND, 3, 2, 2);
		Vec3 at = Vec3.atBottomCenterOf(s.abs(2, 2, 2));
		for (boolean expires : List.of(true, false)) {
			FakePlayer viewer = new FakePlayer(s.level, new GameProfile(UUID.randomUUID(), "GoneDataViewer"));
			try {
				viewer.moveTo(at.x, at.y, at.z);
				s.level.addNewPlayer(viewer);
				int stop = s.stop(expires ? 1 : 200, 0);
				helper.assertTrue(!s.state.canPlayerSeeAndMove(viewer).canSee(), "The observer did not enter frozen vision");
				s.level.removePlayerImmediately(viewer, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
				if (expires) {
					s.state.tickLifecycle();
				}
				else {
					s.state.removeInstance(stop);
				}
				helper.assertTrue(s.state.getInstance(stop).isEmpty(), "Expected the last stop to be over");
				actor.setCustomName(Component.literal("after the last stop, expired " + expires));
				helper.assertTrue(s.state.heldEntityDataCount() == 0,
						"With no stop running a gone viewer kept the dirty index growing, expired " + expires + ": "
								+ s.state.heldEntityDataCount());
			}
			finally {
				viewer.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 450, batch = GameTestBatches.WORLD_RANGE_COST)
	public static void timeStopRangeCostInLoadedWorld(GameTestHelper helper) {
		Scene s = start(helper);
		GameTestChunkTickets tickets = new GameTestChunkTickets(helper);
		List<ChunkPos> chunks = new ArrayList<>();
		List<ArmorStand> actors = new ArrayList<>();
		FakePlayer viewer = new FakePlayer(s.level, new GameProfile(UUID.randomUUID(), "RangeCostViewer"));
		helper.testInfo.addListener(new GameTestListener() {
			private void close() {
				viewer.discard();
				actors.forEach(Entity::discard);
			}

			@Override public void testStructureLoaded(GameTestInfo test) {}
			@Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
			@Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
			@Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
		});
		ChunkPos center = new ChunkPos(s.abs(2, 2, 2));
		Vec3 at = Vec3.atBottomCenterOf(s.abs(3, 5, 3));
		viewer.moveTo(at.x, at.y, at.z);
		viewer.setNoGravity(true);
		s.level.addNewPlayer(viewer);
		for (int side : new int[] { 0, 16 }) {
			ChunkPos first = new ChunkPos(center.x + side, center.z);
			ChunkPos last = new ChunkPos(first.x + 5, first.z + 3);
			tickets.hold(new Vec3(first.getMinBlockX() + 4, at.y, first.getMinBlockZ() + 4),
					new Vec3(last.getMinBlockX() + 11, at.y, last.getMinBlockZ() + 11));
			for (int x = 0; x < 6; x++) {
				for (int z = 0; z < 4; z++) {
					chunks.add(new ChunkPos(first.x + x, first.z + z));
				}
			}
		}
		helper.runAfterDelay(5, () -> s.guarded(() -> {
			for (ChunkPos chunk : chunks) {
				for (int i = 0; i < 10; i++) {
					ArmorStand actor = EntityType.ARMOR_STAND.create(s.level);
					actor.setNoGravity(true);
					actor.setPos(chunk.getMinBlockX() + 2.5D + 3 * (i % 4), at.y,
							chunk.getMinBlockZ() + 2.5D + 4 * (i / 4));
					actors.add(actor);
					helper.assertTrue(s.level.addFreshEntity(actor), "Range-cost fixture could not add an actor");
				}
			}
			int finite = s.stop(400, 12);
			assertRangeCostFixture(helper, s, viewer, actors, 240);
			helper.runAfterDelay(105, () -> s.guarded(() -> {
				logRangeCost(s, 12, finite, actors.size());
				s.state.removeInstance(finite);
				int whole = s.stop(400, 0);
				assertRangeCostFixture(helper, s, viewer, actors, 480);
				helper.runAfterDelay(105, () -> s.guarded(() -> {
					logRangeCost(s, 0, whole, actors.size());
					helper.succeed();
				}));
			}));
		}));
	}

	private static void assertRangeCostFixture(GameTestHelper helper, Scene s, FakePlayer viewer,
			List<ArmorStand> actors, long expectedCovered) {
		long loaded = actors.stream().filter(actor -> s.level.getEntity(actor.getId()) == actor).count();
		long covered = actors.stream().filter(s.state::isTimeStopped).count();
		helper.assertTrue(loaded == 480 && covered == expectedCovered && s.level.players().contains(viewer)
						&& !s.state.canPlayerSeeAndMove(viewer).canSee(),
				"Range-cost premises: loaded=" + loaded + " covered=" + covered + " frozen observer="
						+ !s.state.canPlayerSeeAndMove(viewer).canSee());
	}

	private static void logRangeCost(Scene s, int range, int stop, int actorCount) {
		var server = s.level.getServer();
		long[] ring = server.getTickTimesNanos();
		long[] elapsed = new long[80];
		for (int i = 0; i < elapsed.length; i++) {
			elapsed[i] = ring[Math.floorMod(server.getTickCount() - 1 - i, ring.length)];
		}
		Arrays.sort(elapsed);
		long lifecycleStart = System.nanoTime();
		for (int i = 0; i < 100; i++) {
			s.state.tickLifecycle();
		}
		long lifecycle = System.nanoTime() - lifecycleStart;
		s.helper.assertTrue(s.state.getInstance(stop).isPresent(), "Range-cost stop expired during measurement");
		JojoMod.getLogger().info("TIME-STOP-RANGE-COST range={} fixtureActors={} loadedChunks={} "
						+ "serverSamples={} meanMs={} p95Ms={} lifecycleSamples=100 lifecycleMeanUs={}",
				range, actorCount, s.level.getChunkSource().getLoadedChunksCount(), elapsed.length,
				Arrays.stream(elapsed).average().orElseThrow() / 1e6, elapsed[75] / 1e6, lifecycle / 1e5);
	}

	/**
	 * What a whole-dimension stop costs at its two moments of whole-level work, with real mobs ticking: its first tick holds
	 * the first-freeze seed and the pass over every entity, its last the pass over the frozen ones and the data flush. A
	 * frozen observer tracks every mob and every mob holds a data change for it, the largest flush a viewer can cause.
	 * Numbers for the log, in TIME-STOP-START-END lines; nothing here limits them.
	 */
	@GameTest(template = "empty", timeoutTicks = 200, batch = GameTestBatches.WORLD_START_END_COST)
	public static void wholeDimensionStopStartAndEndTicksWithRealMobs(GameTestHelper helper) {
		Scene s = start(helper);
		GameTestChunkTickets tickets = new GameTestChunkTickets(helper);
		List<Mob> mobs = new ArrayList<>();
		FakePlayer viewer = new FakePlayer(s.level, new GameProfile(UUID.randomUUID(), "StartEndViewer"));
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		int[] flushed = new int[1];
		helper.testInfo.addListener(new GameTestListener() {
			private void close() {
				mobs.forEach(Entity::discard);
				viewer.discard();
				channel.finishAndReleaseAll();
			}

			@Override public void testStructureLoaded(GameTestInfo test) {}
			@Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
			@Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
			@Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
		});
		viewer.connection = new ServerGamePacketListenerImpl(s.level.getServer(), connection, viewer,
				CommonListenerCookie.createInitial(viewer.getGameProfile(), false)) {
			@Override
			public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
				if (packet instanceof ClientboundCustomPayloadPacket payload && payload.payload() instanceof TrDirectEntityDataPacket) {
					flushed[0]++;
				}
			}
		};
		Vec3 at = Vec3.atBottomCenterOf(s.abs(3, 5, 3));
		viewer.moveTo(at.x, at.y, at.z);
		viewer.setNoGravity(true);
		s.level.addNewPlayer(viewer);
		ChunkPos first = new ChunkPos(s.abs(2, 2, 2));
		ChunkPos last = new ChunkPos(first.x + 8, first.z + 7);
		tickets.hold(new Vec3(first.getMinBlockX() + 4, at.y, first.getMinBlockZ() + 4),
				new Vec3(last.getMinBlockX() + 11, at.y, last.getMinBlockZ() + 11));
		List<ChunkPos> chunks = new ArrayList<>();
		for (int x = first.x; x <= last.x; x++) {
			for (int z = first.z; z <= last.z; z++) {
				chunks.add(new ChunkPos(x, z));
			}
		}
		startEndRound(s, viewer, mobs, chunks, flushed, 0);
	}

	private static void startEndRound(Scene s, FakePlayer viewer, List<Mob> mobs, List<ChunkPos> chunks, int[] flushed, int round) {
		GameTestHelper helper = s.helper;
		var server = s.level.getServer();
		int y = s.abs(0, 2, 0).getY();
		for (int i = mobs.size(); i < START_END_MOBS[round]; i++) {
			ChunkPos chunk = chunks.get(i % chunks.size());
			int spot = i / chunks.size();
			Mob mob = i % 2 == 0 ? EntityType.ZOMBIE.create(s.level) : EntityType.COW.create(s.level);
			mob.moveTo(chunk.getMinBlockX() + 1.5D + 2.5D * (spot % 6), y, chunk.getMinBlockZ() + 1.5D + 2.0D * (spot / 6), 0.0F, 0.0F);
			mob.setPersistenceRequired();
			mobs.add(mob);
			helper.assertTrue(s.level.addFreshEntity(mob), "Start and end fixture could not add a mob");
		}
		helper.runAfterDelay(8, () -> s.guarded(() -> {
			long baseline = medianOfLastTicks(server, 5);
			for (Mob mob : mobs) {
				s.level.getChunkSource().chunkMap.entityMap.get(mob.getId()).seenBy.add(viewer.connection);
			}
			long before = System.nanoTime();
			int stop = s.stop(400, 0);
			long startCall = System.nanoTime() - before;
			int startTick = server.getTickCount();
			helper.assertTrue(mobs.stream().allMatch(mob -> s.level.getEntity(mob.getId()) == mob && s.state.shouldFreeze(mob))
							&& !s.state.canPlayerSeeAndMove(viewer).canSee(),
					"Start and end premises: every mob loaded and frozen, the observer frozen");
			mobs.forEach(mob -> mob.setCustomName(Component.literal("held for the frozen viewer")));
			helper.runAfterDelay(6, () -> s.guarded(() -> {
				long frozen = medianOfLastTicks(server, 4);
				flushed[0] = 0;
				long beforeEnd = System.nanoTime();
				s.state.removeInstance(stop);
				long endCall = System.nanoTime() - beforeEnd;
				int endTick = server.getTickCount();
				helper.assertTrue(flushed[0] == mobs.size() && mobs.stream().noneMatch(s.state::shouldFreeze),
						"Start and end premises: held data packets " + flushed[0] + " for " + mobs.size() + " mobs, every mob free");
				helper.runAfterDelay(4, () -> s.guarded(() -> {
					long[] ring = server.getTickTimesNanos();
					JojoMod.getLogger().info("TIME-STOP-START-END mobs={} zombiesAndCowsEven=true loadedChunks={} baselineTickMs={} "
									+ "startCallMs={} startTickMs={} frozenTickMs={} endCallMs={} endTickMs={} nextTickMs={} afterTickMs={} "
									+ "heldDataPackets={}",
							mobs.size(), s.level.getChunkSource().getLoadedChunksCount(), baseline / 1e6, startCall / 1e6,
							ring[startTick % ring.length] / 1e6, frozen / 1e6, endCall / 1e6, ring[endTick % ring.length] / 1e6,
							ring[(endTick + 1) % ring.length] / 1e6, medianOfLastTicks(server, 3) / 1e6, flushed[0]);
					if (round + 1 < START_END_MOBS.length) {
						startEndRound(s, viewer, mobs, chunks, flushed, round + 1);
					}
					else {
						helper.succeed();
					}
				}));
			}));
		}));
	}

	private static long medianOfLastTicks(MinecraftServer server, int count) {
		long[] ring = server.getTickTimesNanos();
		long[] last = new long[count];
		for (int i = 0; i < count; i++) {
			last[i] = ring[Math.floorMod(server.getTickCount() - 1 - i, ring.length)];
		}
		Arrays.sort(last);
		return last[count / 2];
	}

	/** Leaves with no log decay on any random tick, so 4096 ticks of a section hit one of 16 for certain. */
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.WORLD_RANDOM_TICKS)
	public static void randomTicksWaitInStoppedTime(GameTestHelper helper) {
		Scene s = start(helper);
		List<BlockPos> leaves = new ArrayList<>();
		BlockState decaying = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
				.setValue(LeavesBlock.DISTANCE, 7);
		for (int y = 2; y < 18; y++) {
			BlockPos pos = s.abs(1, y, 1);
			s.level.setBlock(pos, decaying, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			leaves.add(pos);
		}
		var chunk = s.level.getChunkAt(leaves.get(0));
		int stop = s.stop(200);
		for (int round = 0; round < 8; round++) {
			s.level.tickChunk(chunk, 4096);
		}
		helper.assertTrue(leaves.stream().allMatch(pos -> s.level.getBlockState(pos).is(Blocks.OAK_LEAVES)),
				"a leaf decayed on a random tick in stopped time");
		s.state.removeInstance(stop);
		s.level.tickChunk(chunk, 4096);
		s.level.tickChunk(chunk, 4096);
		helper.assertTrue(leaves.stream().anyMatch(pos -> !s.level.getBlockState(pos).is(Blocks.OAK_LEAVES)),
				"setup: random ticks do not reach these leaves in normal time");
		helper.succeed();
	}

	/**
	 * A piston caught moving stays as it is; the update that would retract it and the event of one placed in the stop
	 * wait; both run at resume against the world as it then is, with no block lost or doubled.
	 */
	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.WORLD_PISTONS)
	public static void blockEventsWaitForResumeAndAMovingPistonSurvivesTheStop(GameTestHelper helper) {
		Scene s = start(helper);
		s.floor();
		BlockPos piston = s.abs(2, 2, 1);
		BlockPos front = s.abs(3, 2, 1);
		BlockPos pushedTo = s.abs(4, 2, 1);
		BlockPos power = s.abs(1, 2, 1);
		s.level.setBlock(front, Blocks.STONE.defaultBlockState(), 3);
		s.level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 3);
		s.level.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
		BlockPos second = s.abs(2, 2, 3);
		BlockPos secondHead = s.abs(3, 2, 3);
		float[] progress = new float[1];
		helper.runAfterDelay(1, () -> s.guarded(() -> {
			helper.assertTrue(s.level.getBlockEntity(pushedTo) instanceof PistonMovingBlockEntity,
					"setup: the stone is not moving when the stop begins: " + s.level.getBlockState(pushedTo));
			progress[0] = ((PistonMovingBlockEntity) s.level.getBlockEntity(pushedTo)).getProgress(0.0F);
			s.stop(6);
			s.level.setBlock(power, Blocks.AIR.defaultBlockState(), 3);
			s.level.setBlock(s.abs(1, 2, 3), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
			s.level.setBlock(second, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 3);
		}));
		helper.runAfterDelay(4, () -> s.guarded(() -> {
			helper.assertTrue(s.level.getBlockEntity(pushedTo) instanceof PistonMovingBlockEntity moving
					&& moving.getProgress(0.0F) == progress[0], "a moving piston's block went on in stopped time");
			helper.assertTrue(!s.level.getBlockState(second).getValue(PistonBaseBlock.EXTENDED),
					"a block event ran in stopped time: the piston placed by the redstone block extended");
			helper.assertTrue(s.level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
					"the moving piston retracted in stopped time");
		}));
		helper.runAfterDelay(16, () -> s.guarded(() -> {
			helper.assertTrue(!s.level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
					"the update that lost the piston its power never came: " + s.level.getBlockState(piston));
			helper.assertTrue(s.level.getBlockState(front).isAir() && s.level.getBlockState(pushedTo).is(Blocks.STONE),
					"the pushed stone did not end up one further on, once: " + s.level.getBlockState(front) + ", "
							+ s.level.getBlockState(pushedTo));
			helper.assertTrue(s.level.getBlockState(second).getValue(PistonBaseBlock.EXTENDED)
							&& s.level.getBlockState(secondHead).is(Blocks.PISTON_HEAD),
					"the block event that waited did not run at resume: " + s.level.getBlockState(second) + ", "
							+ s.level.getBlockState(secondHead));
			helper.succeed();
		}));
	}

	/** The lever flips; dust, lamp, door and piston react when the stop runs out. A command block keeps running. */
	/*
	 * OQ-C03, owner M0157 (redstone waits for time to resume, then runs as normal): a stone button pressed in a long stop
	 * stays pressed its 20 ticks after it, and one pressed 5 ticks before the stop keeps the 15 it had left. Before, every
	 * frozen tick fired at the resume, and a button gave a pulse.
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.WORLD_BUTTON)
	public static void aButtonPressedInStoppedTimeStaysPressedItsFullTimeAfterIt(GameTestHelper helper) {
		Scene s = start(helper);
		s.floor();
		BlockPos before = s.abs(4, 2, 2);
		BlockPos inStop = s.abs(2, 2, 2);
		s.button(before);
		s.button(inStop);
		s.press(before);
		int[] stop = new int[1];
		helper.runAfterDelay(5, () -> s.guarded(() -> {
			stop[0] = s.stop(40);
			s.press(inStop);
		}));
		Runnable bothPressed = () -> helper.assertTrue(s.pressed(before) && s.pressed(inStop), "a button let go in stopped time"
				+ " or at once after it: pressed before the stop " + s.pressed(before) + ", in it " + s.pressed(inStop));
		helper.runAfterDelay(30, () -> s.guarded(bothPressed));
		helper.runAfterDelay(52, () -> s.guarded(() -> {
			helper.assertTrue(!s.state.getInstance(stop[0]).map(TimeStopState.Instance::isActive).orElse(false),
					"setup: the stop is still running");
			bothPressed.run();
		}));
		helper.runAfterDelay(80, () -> s.guarded(() -> {
			helper.assertTrue(!s.pressed(before) && !s.pressed(inStop), "a button stayed pressed long after the stop: before "
					+ s.pressed(before) + ", in it " + s.pressed(inStop));
			s.close();
			helper.succeed();
		}));
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.WORLD_LEVER)
	public static void aLeverInStoppedTimeChangesNothingUntilTimeResumes(GameTestHelper helper) {
		Scene s = start(helper);
		s.floor();
		// with no stop a lever changes the wire at once
		BlockPos control = s.abs(0, 2, 4);
		BlockPos controlDust = s.abs(1, 2, 4);
		s.lever(control);
		s.dust(controlDust);
		s.pull(control);
		helper.assertTrue(s.power(controlDust) > 0, "normal time held a lever's update");

		BlockPos lever = s.abs(2, 2, 2);
		BlockPos dust = s.abs(3, 2, 2);
		BlockPos lamp = s.abs(5, 2, 2);
		BlockPos door = s.abs(2, 2, 3);
		BlockPos piston = s.abs(1, 2, 2);
		BlockPos command = s.abs(2, 2, 1);
		s.lever(lever);
		s.dust(dust);
		s.dust(s.abs(4, 2, 2));
		s.level.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
		s.door(door);
		s.level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST), 3);
		s.level.setBlock(command, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
		s.stop(6);
		s.pull(lever);
		Runnable nothingYet = () -> {
			helper.assertTrue(s.level.getBlockState(lever).getValue(LeverBlock.POWERED), "the lever did not flip");
			helper.assertTrue(s.power(dust) == 0, "the wire reacted in stopped time: " + s.power(dust));
			helper.assertTrue(!s.level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the lamp reacted in stopped time");
			helper.assertTrue(!s.level.getBlockState(door).getValue(DoorBlock.OPEN), "the door reacted in stopped time");
			helper.assertTrue(!s.level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED), "the piston reacted in stopped time");
		};
		s.guarded(nothingYet);
		s.guarded(() -> helper.assertTrue(s.level.getBlockEntity(command) instanceof CommandBlockEntity block && block.isPowered(),
				"a command block's neighbour update waited: it keeps running in stopped time, as in 1.16"));
		// past the stop's reach, in the same level, a lever goes on as ever
		BlockPos farLever = s.abs(50, 2, 2);
		BlockPos farDust = s.abs(51, 2, 2);
		s.guarded(() -> {
			s.level.setBlock(farLever.below(), Blocks.STONE.defaultBlockState(), 3);
			s.level.setBlock(farDust.below(), Blocks.STONE.defaultBlockState(), 3);
			s.lever(farLever);
			s.dust(farDust);
			helper.assertTrue(!s.state.isTimeStopped(new ChunkPos(farLever)), "setup: the far lever is in the stop's reach");
			s.pull(farLever);
			helper.assertTrue(s.power(farDust) > 0, "a lever past the stop's reach was held");
			s.level.setBlock(farLever, Blocks.AIR.defaultBlockState(), 3);
			s.level.setBlock(farDust, Blocks.AIR.defaultBlockState(), 3);
			s.level.setBlock(farLever.below(), Blocks.AIR.defaultBlockState(), 3);
			s.level.setBlock(farDust.below(), Blocks.AIR.defaultBlockState(), 3);
		});
		helper.runAfterDelay(4, () -> s.guarded(nothingYet));
		helper.runAfterDelay(12, () -> s.guarded(() -> {
			helper.assertTrue(s.power(dust) > 0, "the wire did not react when time resumed");
			helper.assertTrue(s.level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the lamp did not react when time resumed");
			helper.assertTrue(s.level.getBlockState(door).getValue(DoorBlock.OPEN), "the door did not react when time resumed");
			helper.assertTrue(s.level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED), "the piston did not react when time resumed");
			helper.assertTrue(s.state.heldNeighborUpdates().isEmpty(), "updates are still held: " + s.state.heldNeighborUpdates());
			helper.succeed();
		}));
	}

	/**
	 * What the stopper places and breaks shows at once: a torch and a repeater come off with their block, dust joins the
	 * dust beside it. Sand and water stay as the frozen ticks keep them, and move when time resumes.
	 */
	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.WORLD_BUILDING)
	public static void buildingInStoppedTimeShowsAndOnlyRedstoneWaits(GameTestHelper helper) {
		Scene s = start(helper);
		s.floor();
		BlockPos torchBase = s.abs(0, 2, 0);
		BlockPos torch = s.abs(0, 3, 0);
		BlockPos repeaterBase = s.abs(1, 1, 1);
		BlockPos repeater = s.abs(1, 2, 1);
		BlockPos dust = s.abs(3, 2, 0);
		BlockPos nextDust = s.abs(4, 2, 0);
		BlockPos placed = s.abs(5, 2, 0);
		BlockPos sand = s.abs(5, 4, 3);
		BlockPos water = s.abs(2, 2, 3);
		s.level.setBlock(torchBase, Blocks.STONE.defaultBlockState(), 3);
		s.level.setBlock(torch, Blocks.TORCH.defaultBlockState(), 3);
		s.level.setBlock(repeater, Blocks.REPEATER.defaultBlockState(), 3);
		BlockPos railBase = s.abs(6, 1, 4);
		BlockPos rail = s.abs(6, 2, 4);
		s.level.setBlock(rail, Blocks.RAIL.defaultBlockState(), 3);
		s.dust(dust);
		s.stop(6);

		s.level.setBlock(placed, Blocks.STONE.defaultBlockState(), 3);
		helper.assertTrue(s.level.getBlockState(placed).is(Blocks.STONE), "the placed block does not show");
		s.level.destroyBlock(torchBase, false);
		helper.assertTrue(s.level.getBlockState(torchBase).isAir() && s.level.getBlockState(torch).isAir(),
				"a torch stayed on the block broken under it: " + s.level.getBlockState(torch));
		s.level.destroyBlock(repeaterBase, false);
		helper.assertTrue(s.level.getBlockState(repeater).isAir(),
				"a repeater stayed on the block broken under it: " + s.level.getBlockState(repeater));
		// a rail comes off through its neighbour update alone, which a component with its block gone must not wait for
		s.level.destroyBlock(railBase, false);
		helper.assertTrue(s.level.getBlockState(rail).isAir(),
				"a rail stayed on the block broken under it: " + s.level.getBlockState(rail));
		s.dust(nextDust);
		helper.assertTrue(s.level.getBlockState(dust).getValue(RedStoneWireBlock.EAST) != RedstoneSide.NONE,
				"dust does not show its joining the dust placed beside it");
		s.level.setBlock(sand, Blocks.SAND.defaultBlockState(), 3);
		s.level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
		helper.runAfterDelay(5, () -> s.guarded(() -> {
			helper.assertTrue(s.level.getBlockState(sand).is(Blocks.SAND), "sand fell in stopped time");
			helper.assertTrue(!s.flowedFrom(water), "water spread in stopped time");
		}));
		helper.runAfterDelay(20, () -> s.guarded(() -> {
			helper.assertTrue(!s.level.getBlockState(sand).is(Blocks.SAND), "the sand did not fall when time resumed");
			helper.assertTrue(s.flowedFrom(water), "the water did not spread when time resumed");
			helper.succeed();
		}));
	}

	/** Each row a stop over a flipped lever and its dust, ended as the game ends one, within one game tick. */
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.WORLD_ENDS)
	public static void everyEndOfAStopDrainsWhatItHeld(GameTestHelper helper) {
		Scene s = start(helper);
		s.floor();
		s.row("a stop that runs out", 1, id -> s.state.tickOnLevelTick());
		s.row("a manual resume", 200, id -> {
			s.state.requestManualResume(id);
			s.state.requestManualResume(id);
			s.state.tickOnLevelTick();
		});
		s.row("the user's death", 200, id -> {
			s.user.setHealth(0.0F);
			s.state.tickOnLevelTick();
			s.user.setHealth(s.user.getMaxHealth());
		});
		s.row("a logout", 200, id -> {
			s.state.removeInstance(id);
			s.state.tickOnLevelTick();
		});
		s.row("the last player's logout", 200, id -> {
			s.state.reset();
			s.state.tickOnLevelTick();
		});
		s.row("the last of two stops ending", 200, id -> {
			int other = s.stop(200);
			s.state.removeInstance(id);
			s.state.tickOnLevelTick();
			helper.assertTrue(s.power(s.rowDust()) == 0 && !s.state.heldNeighborUpdates().isEmpty(),
					"the first of two stops ending let go of what the level holds");
			s.state.removeInstance(other);
			s.state.tickOnLevelTick();
		});
		// the stop goes on where its user left, as the owner chose (T3-09), and the update stays in its own level
		ServerLevel nether = s.level.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "setup: no second dimension");
		TimeStopState netherState = nether.getData(ModDataAttachmentTypes.TIME_STOP.get());
		s.row("a level change", 2, id -> {
			s.level.removePlayerImmediately(s.user, Entity.RemovalReason.CHANGED_DIMENSION);
			s.user.revive();
			s.user.setServerLevel(nether);
			nether.addDuringTeleport(s.user);
			helper.assertTrue(netherState.heldNeighborUpdates().isEmpty(), "held updates are not their own level's");
			netherState.tickOnLevelTick();
			s.state.tickOnLevelTick();
			helper.assertTrue(s.state.getInstance(id).isPresent() && s.power(s.rowDust()) == 0
					&& !s.state.heldNeighborUpdates().isEmpty(), "the stop its user left did not go on holding");
			s.state.tickOnLevelTick();
			helper.assertTrue(s.state.getInstance(id).isEmpty(), "the stop its user left did not run out");
		});
		s.user.discard();
		s.bringUserBack();
		BlockPos stoppingPiston = s.abs(1, 2, 2);
		s.level.setBlock(stoppingPiston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH), 3);
		s.row("the server stopping", 200, id -> {
			TimeStopState.onServerStopping(new ServerStoppingEvent(s.level.getServer()));
			helper.assertTrue(s.state.getInstance(id).isEmpty(), "the server stopping left the stop running");
			helper.assertTrue(s.level.getBlockState(stoppingPiston).getValue(PistonBaseBlock.EXTENDED),
					"the server stopping lost the block event of an update it drained: a level's block events are not saved");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.WORLD_QUEUE)
	public static void theHeldQueueKeepsOrderDropsDuplicatesAndFlushesWhenFull(GameTestHelper helper) {
		Scene s = start(helper);
		s.floor();
		BlockPos firstLever = s.abs(1, 2, 1);
		BlockPos firstDoor = s.abs(2, 2, 1);
		BlockPos lastLever = s.abs(5, 2, 4);
		BlockPos lastDoor = s.abs(6, 2, 4);
		s.lever(firstLever);
		s.door(firstDoor);
		s.lever(lastLever);
		s.door(lastDoor);
		List<BlockPos> heard = new ArrayList<>();
		Consumer<PlayLevelSoundEvent.AtPosition> sound = event -> {
			if (event.getLevel() == s.level) {
				heard.add(BlockPos.containing(event.getPosition()));
			}
		};
		NeoForge.EVENT_BUS.addListener(sound);
		try {
			int stop = s.stop(200);
			s.pull(firstLever);
			int afterOneFlip = s.state.heldNeighborUpdates().size();
			s.pull(firstLever);
			s.pull(firstLever);
			helper.assertTrue(s.state.heldNeighborUpdates().size() == afterOneFlip,
					"flipping a lever on, off and on held the same updates again: " + afterOneFlip + ", then "
							+ s.state.heldNeighborUpdates().size());
			s.pull(lastLever);
			heard.clear();
			s.state.removeInstance(stop);
			s.state.tickOnLevelTick();
			List<BlockPos> doors = heard.stream().filter(pos -> pos.equals(firstDoor) || pos.equals(lastDoor)).toList();
			helper.assertTrue(doors.equals(List.of(firstDoor, lastDoor)),
					"the held updates did not run in the order they came: doors heard " + doors);
		}
		finally {
			NeoForge.EVENT_BUS.unregister(sound);
		}

		// flushing early is better than losing an update: past the cap all of it runs, in stopped time, and the rest of
		// the stop stays live; the next stop holds again
		BlockPos lever = s.abs(3, 2, 2);
		BlockPos lamp = s.abs(4, 2, 2);
		BlockPos liveLever = s.abs(3, 2, 3);
		BlockPos liveDust = s.abs(4, 2, 3);
		BlockPos heldDust = s.abs(3, 2, 1);
		s.lever(lever);
		s.dust(heldDust);
		s.level.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
		s.lever(liveLever);
		s.dust(liveDust);
		int stop = s.stop(200);
		s.pull(lever);
		helper.assertTrue(!s.level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the lamp reacted before the queue was full");
		for (int at = s.state.heldNeighborUpdates().size(); at < 4096; at++) {
			helper.assertTrue(!s.level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the queue overflowed at " + at);
			s.level.neighborChanged(lamp, Blocks.LEVER, s.abs(0, 200, at));
		}
		helper.assertTrue(s.state.heldNeighborUpdates().size() == 4096, "setup: the queue holds "
				+ s.state.heldNeighborUpdates().size());
		s.level.neighborChanged(lamp, Blocks.LEVER, s.abs(1, 200, 0));
		helper.assertTrue(s.state.heldNeighborUpdates().isEmpty() && s.level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)
						&& s.power(heldDust) > 0,
				"an overflow lost updates or kept them: " + s.state.heldNeighborUpdates().size() + " held, lamp "
						+ s.level.getBlockState(lamp) + ", dust " + s.power(heldDust));
		s.pull(liveLever);
		helper.assertTrue(s.power(liveDust) > 0, "the rest of an overflowed stop did not stay live");
		s.state.removeInstance(stop);
		s.state.tickOnLevelTick();
		s.pull(liveLever);
		s.pull(liveLever);
		int next = s.stop(200);
		s.pull(liveLever);
		helper.assertTrue(!s.state.heldNeighborUpdates().isEmpty(), "the next stop does not hold after an overflow");
		s.state.removeInstance(next);
		s.state.tickOnLevelTick();
		helper.succeed();
	}

	private static Scene start(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		helper.testInfo.addListener(scene);
		scene.setUp();
		return scene;
	}

	/** A Stand user who keeps a real stop running over the test cell, and a cell of blocks to clear after it. */
	private static final class Scene implements GameTestListener {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final TimeStopState state;
		private final List<Integer> stops = new ArrayList<>();
		private FakePlayer user;
		private BlockPos rowDust;
		private boolean closed;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			level = helper.getLevel();
			state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
		}

		private void setUp() {
			bringUserBack();
		}

		private void bringUserBack() {
			user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "TimeStopWorld"));
			Vec3 at = Vec3.atBottomCenterOf(abs(7, 6, 5));
			user.moveTo(at.x, at.y, at.z);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "setup: could not add the user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not grant Star Platinum");
			power.setResolveLevel(power.getMaxResolveLevel());
			TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
		}

		private BlockPos abs(int x, int y, int z) {
			return helper.absolutePos(new BlockPos(x, y, z));
		}

		private void floor() {
			for (int x = 0; x <= 7; x++) {
				for (int z = 0; z <= 5; z++) {
					level.setBlock(abs(x, 1, z), Blocks.STONE.defaultBlockState(), 3);
				}
			}
		}

		private int stop(int ticks) {
			return stop(ticks, 2);
		}

		private int stop(int ticks, int range) {
			int id = nextStopId--;
			stops.add(id);
			helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(id, ticks, ticks, new ChunkPos(abs(2, 2, 2)), range,
					user.getId(), "time_stop_world_test")), "setup: could not stop time");
			helper.assertTrue(state.isTimeStopped(new ChunkPos(abs(2, 2, 2))), "setup: the cell's chunk is not stopped");
			return id;
		}

		private void lever(BlockPos pos) {
			level.setBlock(pos, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR), 3);
		}

		private void button(BlockPos pos) {
			level.setBlock(pos, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR), 3);
		}

		private void press(BlockPos pos) {
			BlockState button = level.getBlockState(pos);
			((ButtonBlock) button.getBlock()).press(button, level, pos, null);
		}

		private boolean pressed(BlockPos pos) {
			BlockState button = level.getBlockState(pos);
			return button.getBlock() instanceof ButtonBlock && button.getValue(ButtonBlock.POWERED);
		}

		private void pull(BlockPos pos) {
			BlockState lever = level.getBlockState(pos);
			((LeverBlock) lever.getBlock()).pull(lever, level, pos, null);
		}

		private void dust(BlockPos pos) {
			level.setBlock(pos, Blocks.REDSTONE_WIRE.defaultBlockState(), 3);
		}

		private void door(BlockPos lower) {
			BlockState door = Blocks.OAK_DOOR.defaultBlockState();
			level.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}

		// water runs toward the floor's nearest edge only
		private boolean flowedFrom(BlockPos source) {
			return Direction.Plane.HORIZONTAL.stream().anyMatch(
					side -> level.getFluidState(source.relative(side)).is(Fluids.FLOWING_WATER));
		}

		private int power(BlockPos dust) {
			return level.getBlockState(dust).getValue(RedStoneWireBlock.POWER);
		}

		private BlockPos rowDust() {
			return rowDust;
		}

		private void row(String name, int ticks, Consumer<Integer> end) {
			BlockPos lever = abs(1, 2, 1);
			rowDust = abs(2, 2, 1);
			lever(lever);
			dust(rowDust);
			int id = stop(ticks);
			pull(lever);
			helper.assertTrue(power(rowDust) == 0 && !state.heldNeighborUpdates().isEmpty(), name + ": the update was not held");
			end.accept(id);
			helper.assertTrue(power(rowDust) > 0, name + ": the wire did not react, power " + power(rowDust));
			helper.assertTrue(state.heldNeighborUpdates().isEmpty(), name + ": updates are still held: " + state.heldNeighborUpdates());
			state.removeInstance(id);
			level.setBlock(lever, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(rowDust, Blocks.AIR.defaultBlockState(), 3);
		}

		private void guarded(Runnable step) {
			try {
				step.run();
			}
			catch (RuntimeException | Error failure) {
				close();
				throw failure;
			}
		}

		private void close() {
			if (closed) {
				return;
			}
			closed = true;
			for (int id : stops) {
				state.removeInstance(id);
			}
			state.tickOnLevelTick();
			// from y 1: the test's structure block is at y 0
			for (int x = 0; x <= 7; x++) {
				for (int z = 0; z <= 5; z++) {
					for (int y = 1; y <= 19; y++) {
						BlockPos pos = abs(x, y, z);
						if (!level.getBlockState(pos).isAir()) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						}
					}
				}
			}
			BoundingBox cell = BoundingBox.fromCorners(abs(0, 1, 0), abs(7, 19, 5));
			level.clearBlockEvents(cell);
			level.getEntitiesOfClass(ItemEntity.class, AABB.of(cell)).forEach(Entity::discard);
			level.getEntitiesOfClass(FallingBlockEntity.class, AABB.of(cell)).forEach(Entity::discard);
			if (user != null) {
				user.discard();
			}
		}

		@Override
		public void testStructureLoaded(GameTestInfo test) {}

		@Override
		public void testPassed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testFailed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
			close();
		}
	}
}
