package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.EpitaphTargeting;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.RandomState;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxGuards;
import rotp.core.api.simulation.EntitySimulations;

/**
 * Epitaph, the room and what it must not disturb (EPITAPH-ARCH 1.4, 1.5, 1.8): the random streams of the world, the
 * chunks that are not loaded, the rest of a prediction when one phantom breaks, and the things of which a server has
 * only one, which a copy of an entity could reach while it is made.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphRoomGameTests {
    private KingCrimsonEpitaphRoomGameTests() {}

    // A12
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_random")
    public static void randomStreamsAreUntouched(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-random", 3, 16, 10);
        ServerLevel level = fixture.level;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        Cow cow = fixture.mob(EntityType.COW, 4.5D, 8.5D);
        Creeper creeper = fixture.mob(EntityType.CREEPER, 10.5D, 7.5D);
        creeper.setNoAi(true);
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            creeper.ignite();
            RandomSource[] streams = { level.random, cow.getRandom(), zombie.getRandom() };
            String[] names = { "the level", "a creature that is not predicted", "the body of a predicted creature" };
            // Without a prediction: the next values of each stream, which is then put back where it was.
            long[][] without = new long[streams.length][];
            for (int i = 0; i < streams.length; i++) {
                RandomState state = RandomState.of(streams[i]);
                helper.assertTrue(state != null, "Setup: the random stream of " + names[i] + " is of an unknown kind");
                without[i] = next(streams[i]);
                state.writeTo(streams[i]);
            }
            EpitaphSession session = fixture.press(creeper);
            session.foreseeAll();
            // The blast of the creeper draws the strength of every ray from the stream of the level.
            helper.assertTrue(KingCrimsonEpitaphFixture.ops(session, RecordedOp.Explode.class).size() == 1
                    && session.record().track(zombie.getId()) != null, "Setup: no blast in the room, or the zombie is not predicted");
            session.end(EpitaphEnd.CANCELLED);
            for (int i = 0; i < streams.length; i++) {
                fixture.rule(Arrays.equals(without[i], next(streams[i])),
                        "after a prediction the random stream of " + names[i] + " gives other values than without one");
            }
            fixture.finish("A prediction leaves the random streams untouched");
        }));
    }

    private static long[] next(RandomSource stream) {
        long[] values = new long[8];
        for (int i = 0; i < values.length; i++) {
            values[i] = stream.nextLong();
        }
        return values;
    }

    // A13
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_edge")
    public static void noChunkIsLoadedOrGenerated(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-edge", 4, 12, 8);
        ServerLevel level = fixture.level;
        helper.runAfterDelay(20, () -> fixture.guarded(() -> {
            Vec3 spot = fixture.spot(8.5D, 14.0D, 4.5D);
            ChunkPos here = new ChunkPos(BlockPos.containing(spot));
            int edge = Integer.MIN_VALUE;
            for (int ahead = 1; ahead <= 16 && edge == Integer.MIN_VALUE; ahead++) {
                if (level.getChunkSource().getChunkNow(here.x + ahead, here.z) == null) {
                    edge = here.x + ahead;
                }
            }
            helper.assertTrue(edge != Integer.MIN_VALUE, "Setup: every chunk for 16 chunks east of the test is loaded");
            // An arrow that flies level and fast along the middle of its row of chunks, at the first that is not loaded.
            Arrow arrow = new Arrow(level, spot.x, spot.y, here.getMiddleBlockZ() + 0.5D, new ItemStack(Items.ARROW), null);
            arrow.setNoGravity(true);
            arrow.shoot(1.0D, 0.0D, 0.0D, 6.0F, 0.0F);
            fixture.thing(arrow);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            EpitaphSession session = fixture.press(arrow);
            session.foreseeAll();
            EntityTrack track = session.record().track(arrow.getId());
            helper.assertTrue(track != null, "Setup: the arrow under the crosshair is not predicted");
            double reached = track.frame(track.lastTick()).position().x;
            int border = new ChunkPos(edge, here.z).getMinBlockX();
            fixture.rule(track.end() == EntityTrack.End.EDGE, "the track of an arrow that flew at a chunk that is not loaded ended as "
                    + track.end() + " at tick " + track.lastTick() + ", x " + reached + ", with the chunk beginning at x " + border);
            fixture.rule(reached > border - 8.0D && reached < border + 16.0D, "the track ends at x " + reached
                    + ", not at the edge of the loaded chunks at x " + border);
            fixture.rule(level.getChunkSource().getChunkNow(edge, here.z) == null, "the chunk the arrow flew at is loaded now");
            fixture.rule(level.getChunkSource().getLoadedChunksCount() == loaded, "a prediction changed the count of loaded chunks from "
                    + loaded + " to " + level.getChunkSource().getLoadedChunksCount());
            fixture.rule(EpitaphSandbox.leaks() == 0, "something reached past the room: " + EpitaphSandbox.firstLeak());
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A prediction loads no chunk");
        }));
    }

    // A14
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_fault")
    public static void aThrowingPhantomEndsAlone(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-fault", 2, 16, 8);
        fixture.budget(0);
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        Husk husk = fixture.hunter(EntityType.HUSK, 12.5D, 5.5D);
        fixture.base.onClose(SandboxGuards::reset);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] ticks = new int[1];
        int[] faults = new int[1];
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            faults[0] = SandboxGuards.faults(EntityType.HUSK);
            EpitaphSession session = fixture.press(null);
            helper.assertTrue(session.room().tick() == 0 && EpitaphSessions.isOnTrack(husk) && EpitaphSessions.isOnTrack(zombie)
                    && session.room().phantom(husk.getId()) instanceof Mob,
                    "Setup: the room has lived " + session.room().tick() + " ticks at the press, or a hunter is on no track");
            // Its mind is broken in the room alone: the first thing its phantom asks its goals throws.
            ((Mob) session.room().phantom(husk.getId())).goalSelector.addGoal(0, new Goal() {
                @Override
                public boolean canUse() {
                    throw new IllegalStateException("A14: the tick of this phantom throws");
                }
            });
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (!session.ended()) {
                if (++ticks[0] == 14) {
                    fixture.rule(!EpitaphSessions.isOnTrack(husk) && husk.isAlive(),
                            "the body of a phantom whose tick threw is still held 14 ticks later, or is dead");
                    fixture.rule(EpitaphSessions.isOnTrack(zombie), "the other creature of the prediction is no longer on its track");
                }
                return;
            }
            running[0] = null;
            EntityTrack broken = session.record().track(husk.getId());
            EntityTrack whole = session.record().track(zombie.getId());
            fixture.rule(broken.end() == EntityTrack.End.FAULT && broken.lastTick() <= 2,
                    "the track of the phantom whose tick threw ended as " + broken.end() + " at tick " + broken.lastTick());
            fixture.rule(whole.end() == EntityTrack.End.FORESIGHT && whole.lastTick() == 30,
                    "the track of the other creature ended as " + whole.end() + " at tick " + whole.lastTick() + ", not with the foresight at 30");
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && ticks[0] > 30,
                    "the prediction ended as " + session.endedAs() + " after " + ticks[0] + " server ticks");
            fixture.rule(SandboxGuards.faults(EntityType.HUSK) == faults[0] + 1,
                    "one phantom threw, and " + (SandboxGuards.faults(EntityType.HUSK) - faults[0]) + " faults were counted");
            fixture.finish("A phantom whose tick throws ends alone");
        }));
    }

    // A15
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_copies")
    public static void copiesTouchNoSingleton(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-copies", 0, 12, 8);
        ServerLevel level = fixture.level;
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("kc_ep_copies");
        scoreboard.addPlayerToTeam("kc-ep-copies-member", team);
        fixture.base.onClose(() -> scoreboard.removePlayerTeam(team));
        fixture.base.onClose(SandboxGuards::reset);
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            // A real raid with one real raider in it: a copy that kept the raid of its body would join it.
            Raid raid = level.getRaids().createOrExtendRaid(fixture.user, fixture.absolute(6, 2, 4));
            helper.assertTrue(raid != null, "Setup: no raid could be made");
            Pillager raider = fixture.mob(EntityType.PILLAGER, 6.5D, 4.5D);
            raider.setCurrentRaid(raid);
            raider.setWave(1);
            raid.addWaveMob(1, raider, false);
            fixture.base.onClose(raid::stop);
            helper.assertTrue(raid.getTotalRaidersAlive() == 1 && raider.saveWithoutId(new CompoundTag()).contains("RaidId"),
                    "Setup: the raider is not in the raid, or its saved data does not name it");
            Vec3 at = fixture.spot(8.5D, 2.0D, 4.5D);
            List<String> faulty = new ArrayList<>();
            List<String> uncopied = new ArrayList<>();
            int types = 0;
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (!BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft") || type == EntityType.PLAYER
                        || !DefaultAttributes.hasSupplier(type)) {
                    continue;
                }
                Entity real = type == EntityType.PILLAGER ? raider : type.create(level);
                if (!(real instanceof LivingEntity)) {
                    continue;
                }
                String name = type.toShortString();
                if (real != raider) {
                    real.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                }
                String before = singletons(level, raid);
                int leaks = EpitaphSandbox.leaks();
                int faults = SandboxGuards.faults(type);
                boolean denied = KingCrimsonConfig.epitaphDenies(type);
                EpitaphSession session = EpitaphSessions.predict(fixture.user, List.of(real));
                helper.assertTrue(session != null, "Setup: no prediction began for a " + name);
                boolean copied = session.record().track(real.getId()) != null;
                session.foreseeAll();
                session.end(EpitaphEnd.CANCELLED);
                if (denied) {
                    fixture.rule(!copied && !EpitaphTargeting.select(fixture.user, real, 50.0D, 24).contains(real),
                            "a " + name + ", which is never predicted, got a phantom or is chosen under the crosshair");
                } else if (!copied) {
                    uncopied.add(name);
                }
                String after = singletons(level, raid);
                fixture.rule(after.equals(before), "making and living the phantom of a " + name + " changed " + before + " into " + after);
                fixture.rule(EpitaphSandbox.leaks() == leaks, "the phantom of a " + name + " reached past the room: " + EpitaphSandbox.firstLeak());
                if (SandboxGuards.faults(type) != faults) {
                    faulty.add(name);
                }
                if (real != raider) {
                    real.discard();
                }
                types++;
            }
            fixture.rule(types >= 70, "only " + types + " living types of the game were tried");
            fixture.rule(uncopied.isEmpty(), "no phantom could be made of: " + uncopied);
            fixture.rule(faulty.isEmpty(), "the phantoms of these types threw within their first ten ticks: " + faulty);
            fixture.rule(!EpitaphSessions.any, "a prediction is left running");
            fixture.finish("Copies touch nothing a server has only one of");
        }));
    }

    private static String singletons(ServerLevel level, Raid raid) {
        StringBuilder all = new StringBuilder("teams");
        for (PlayerTeam team : level.getScoreboard().getPlayerTeams()) {
            all.append(" ").append(team.getName()).append(new TreeSet<>(team.getPlayers()));
        }
        all.append(", raids ").append(level.getRaids().save(new CompoundTag(), level.registryAccess()));
        all.append(", raiders ").append(raid.getTotalRaidersAlive()).append(" with ").append(raid.getHealthOfLivingRaiders()).append(" health");
        all.append(", dragon fight ").append(level.getDragonFight() == null ? "none" : level.getDragonFight().saveData().toString());
        return all.toString();
    }

    // What an add-on asked the core to do for a copy each tick, which gets no tick event (EntitySimulations.subscribeTick)
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_tick_listeners")
    public static void aTickListenerOfAnAddOnRunsForEveryPhantomOnceATick(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-listener", 2, 16, 8);
        fixture.budget(0);
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        ResourceLocation owner = KingCrimsonAddon.id("epitaph_tick_listener_test");
        Map<Integer, Integer> calls = new HashMap<>();
        int[] real = new int[1];
        EntitySimulations.subscribeTick(owner, copy -> {
            calls.merge(copy.getId(), 1, Integer::sum);
            if (!EpitaphSessions.isPhantom(copy)) {
                real[0]++;
            }
        });
        fixture.base.onClose(() -> EntitySimulations.unsubscribeTick(owner));
        helper.runAfterDelay(10, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            int lived = session.room().tick();
            EntityTrack track = session.record().track(zombie.getId());
            helper.assertTrue(track != null && track.lastTick() == lived && lived >= 10, "Setup: the room lived " + lived
                    + " ticks and the phantom of the zombie not all of them");
            int ofZombie = calls.getOrDefault(zombie.getId(), 0);
            int ofUser = calls.getOrDefault(fixture.user.getId(), 0);
            fixture.rule(ofZombie == lived && ofUser == lived, "in " + lived + " ticks of the room the tick listener of an add-on was"
                    + " run " + ofZombie + " times for the phantom of the zombie and " + ofUser + " times for that of the user");
            fixture.rule(real[0] == 0, "the tick listener was handed " + real[0] + " entities that are no phantoms");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A tick listener of an add-on runs for every phantom once a tick");
        }));
    }
}
