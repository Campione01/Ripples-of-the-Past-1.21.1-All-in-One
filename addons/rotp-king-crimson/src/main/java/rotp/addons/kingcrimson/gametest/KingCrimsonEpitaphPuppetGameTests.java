package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder;

/**
 * Epitaph, the bodies (EPITAPH-ARCH 2.3 and 2.5): what was foreseen is carried out. A body that is predicted does not
 * tick; it stands where its phantom stood, tick for tick, and what its phantom did to the world happens for real at
 * the tick it was foreseen for, once, and not before. The tests have one player online, so the bodies live a quarter
 * as fast during the phantom phase: every rule is stated in ticks of the foreseen future.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphPuppetGameTests {
    private KingCrimsonEpitaphPuppetGameTests() {}

    // A4
    @GameTest(template = "empty", timeoutTicks = 240, batch = "kc_epitaph_track")
    public static void mobWalksItsForeseenTrack(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-track", 2, 16, 6);
        fixture.budget(0);
        fixture.userKeepsControl();
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] checked = new int[1];
        double[] lastTick = new double[1];
        // By now the zombie is in mid-chase: a new zombie waits a second before it starts.
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            running[0] = fixture.press(null);
            helper.assertTrue(EpitaphSessions.isOnTrack(zombie), "Setup: the zombie that hunts the user is on no track");
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            EntityTrack track = session.record().track(zombie.getId());
            if (!session.ended()) {
                double tick = session.clock().body();
                Vec3 foreseen = KingCrimsonEpitaphFixture.on(track, tick);
                double off = zombie.position().distanceTo(foreseen);
                fixture.rule(off < 1.0E-6D, "at tick " + tick + " of its track the zombie stood " + off
                        + " blocks from where its phantom stood then");
                checked[0]++;
                lastTick[0] = tick;
                return;
            }
            running[0] = null;
            Vec3 start = track.frame(0).position();
            Vec3 end = track.frame(track.lastTick()).position();
            fixture.rule(track.end() == EntityTrack.End.FORESIGHT && track.lastTick() == 30,
                    "the track of the zombie ended as " + track.end() + " at tick " + track.lastTick() + ", not with the foresight at 30");
            fixture.rule(start.distanceTo(end) > 2.0D, "the phantom of a zombie in mid-chase walked only from "
                    + KingCrimsonEpitaphFixture.at(start) + " to " + KingCrimsonEpitaphFixture.at(end));
            fixture.rule(checked[0] >= 40 && lastTick[0] >= 29.0D, "the zombie was carried for " + checked[0]
                    + " server ticks up to tick " + lastTick[0] + " of its track, not through all 30 of them");
            double off = zombie.position().distanceTo(end);
            fixture.rule(zombie.isAlive() && off < 0.5D, "let go, the zombie is " + off + " blocks from where its phantom ended");
            fixture.rule(!EpitaphSessions.isOnTrack(zombie) && !EpitaphSessions.any, "after the end something is still held");
            fixture.finish("A creature walks its foreseen track");
        }));
    }

    // Riders are walkers (EPITAPH-ARCH, rulings after stages A and B, R2).
    @GameTest(template = "empty", timeoutTicks = 240, batch = "kc_epitaph_ride")
    public static void aMountAndItsRiderWalkTheirTracks(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-ride", 2, 16, 6);
        fixture.budget(0);
        Chicken mount = fixture.mob(EntityType.CHICKEN, 12.5D, 2.5D);
        Zombie rider = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        rider.setBaby(true);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] checked = new int[1];
        int[] ticked = new int[2];
        double[] lastTick = new double[1];
        fixture.listen(EntityTickEvent.Post.class, event -> {
            if (running[0] != null && event.getEntity() == mount) {
                ticked[0]++;
            } else if (running[0] != null && event.getEntity() == rider) {
                ticked[1]++;
            }
        });
        helper.runAfterDelay(3, () -> fixture.guarded(
                () -> helper.assertTrue(rider.startRiding(mount, true), "Setup: the zombie did not mount the hen")));
        // By now the rider has the hen in mid-chase: a new zombie waits a second before it starts.
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            helper.assertTrue(rider.getVehicle() == mount, "Setup: the zombie left the hen before the press");
            running[0] = fixture.press(null);
            helper.assertTrue(EpitaphSessions.isOnTrack(mount) && EpitaphSessions.isOnTrack(rider),
                    "Setup: the hen and the zombie that rides it after the user are not both on their tracks");
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            EntityTrack walked = session.record().track(mount.getId());
            EntityTrack ridden = session.record().track(rider.getId());
            if (!session.ended()) {
                double tick = session.clock().body();
                double off = mount.position().distanceTo(KingCrimsonEpitaphFixture.on(walked, tick));
                fixture.rule(off < 1.0E-6D, "at tick " + tick + " of its track the hen stood " + off
                        + " blocks from where its phantom stood then");
                Vec3 seat = mount.getPassengerRidingPosition(rider).subtract(rider.getVehicleAttachmentPoint(mount));
                double unseated = rider.getVehicle() == mount ? rider.position().distanceTo(seat) : Double.MAX_VALUE;
                fixture.rule(unseated < 1.0E-6D, "at tick " + tick + " the zombie was " + unseated
                        + " blocks from its seat on the hen, or rode it no longer");
                // The seat on a hen that is placed between two frames, against the place between two seats.
                double strayed = rider.position().distanceTo(KingCrimsonEpitaphFixture.on(ridden, tick));
                fixture.rule(strayed < 0.05D, "at tick " + tick + " of its track the zombie that rides was " + strayed
                        + " blocks from where its phantom rode then");
                PuppetBinder.Binding binding = session.binder().of(rider);
                fixture.rule(binding != null && binding.offset().equals(Vec3.ZERO), "at tick " + tick + " the zombie that rides is taken to be "
                        + (binding == null ? "unbound" : binding.offset().length() + " blocks off its track by a push") + ": its seat is its place");
                fixture.rule(ticked[0] == 0 && ticked[1] == 0, "on their tracks the hen ticked by itself " + ticked[0]
                        + " times and the zombie that rides it " + ticked[1]);
                if (++checked[0] == 12) {
                    // A push from outside: what a hit does to a creature. A rider is not moved out of its seat by it.
                    rider.knockback(1.0D, 0.0D, -1.0D);
                }
                lastTick[0] = tick;
                return;
            }
            running[0] = null;
            Vec3 start = walked.frame(0).position();
            Vec3 end = walked.frame(walked.lastTick()).position();
            fixture.rule(walked.end() == EntityTrack.End.FORESIGHT && walked.lastTick() == 30 && ridden.end() == EntityTrack.End.FORESIGHT
                    && ridden.lastTick() == 30, "the tracks of the hen and of its rider ended as " + walked.end() + " at tick "
                            + walked.lastTick() + " and as " + ridden.end() + " at tick " + ridden.lastTick() + ", not with the foresight at 30");
            fixture.rule(start.distanceTo(end) > 1.0D, "Setup: the phantom of a hen ridden after the user walked only from "
                    + KingCrimsonEpitaphFixture.at(start) + " to " + KingCrimsonEpitaphFixture.at(end));
            fixture.rule(checked[0] >= 40 && lastTick[0] >= 29.0D, "the hen and its rider were carried for " + checked[0]
                    + " server ticks up to tick " + lastTick[0] + " of their tracks, not through all 30 of them");
            double off = mount.position().distanceTo(end);
            double riderOff = rider.position().distanceTo(ridden.frame(ridden.lastTick()).position());
            fixture.rule(mount.isAlive() && rider.isAlive() && rider.getVehicle() == mount && off < 0.5D && riderOff < 0.5D,
                    "let go, the hen is " + off + " blocks from where its phantom ended and the zombie " + riderOff
                            + " from where its phantom did, or the zombie rides the hen no longer");
            fixture.rule(!EpitaphSessions.isOnTrack(mount) && !EpitaphSessions.isOnTrack(rider) && !EpitaphSessions.any,
                    "after the end something is still held");
            fixture.finish("A mount and its rider walk their foreseen tracks");
        }));
    }

    // A5
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_arrow")
    public static void foreseenArrowFliesAndHitsAtItsTick(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-arrow", 4, 34, 8);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        // Further from the user than a skeleton looks for players, so that it keeps to the golem.
        IronGolem golem = fixture.mob(EntityType.IRON_GOLEM, 30.5D, 3.5D);
        golem.setNoAi(true);
        Skeleton skeleton = fixture.mob(EntityType.SKELETON, 23.5D, 3.5D);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setTarget(golem);
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.Spawn[] shot = new RecordedOp.Spawn[1];
        RecordedOp.Damage[] hit = new RecordedOp.Damage[1];
        int[] ticks = new int[2];
        int[] hits = new int[1];
        int[] passedAtHit = { -1 };
        float[] amountAtHit = new float[1];
        int[] flown = new int[1];
        fixture.listen(LivingIncomingDamageEvent.class, event -> {
            Entity by = event.getSource().getDirectEntity();
            if (event.getEntity() == golem && running[0] != null && shot[0] != null && by != null
                    && by.getId() == shot[0].child().id()) {
                hits[0]++;
                passedAtHit[0] = running[0].passed();
                amountAtHit[0] = event.getAmount();
            }
        });
        helper.runAfterDelay(50, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.predict(skeleton, golem);
            session.foreseeAll();
            for (RecordedOp.Damage damage : KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class)) {
                for (RecordedOp.Spawn spawn : KingCrimsonEpitaphFixture.ops(session, RecordedOp.Spawn.class)) {
                    if (hit[0] == null && damage.victim().id() == golem.getId() && spawn.type() == EntityType.ARROW
                            && spawn.child().equals(damage.actor())) {
                        hit[0] = damage;
                        shot[0] = spawn;
                    }
                }
            }
            helper.assertTrue(hit[0] != null, "Setup: no arrow of the skeleton hit the golem in the room");
            ticks[0] = KingCrimsonEpitaphFixture.tickOf(session, shot[0]);
            ticks[1] = KingCrimsonEpitaphFixture.tickOf(session, hit[0]);
            helper.assertTrue(ticks[0] >= 1 && ticks[1] > ticks[0] && level.getEntity(shot[0].child().id()) == null,
                    "Setup: the arrow is shot at tick " + ticks[0] + " and hits at tick " + ticks[1]);
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            Entity arrow = level.getEntity(shot[0].child().id());
            EntityTrack track = session.record().track(shot[0].child());
            if (!session.ended()) {
                int passed = session.passed();
                if (passed < ticks[0]) {
                    fixture.rule(arrow == null, "the foreseen arrow was in the world at tick " + passed + ", before tick "
                            + ticks[0] + " at which it is shot");
                } else if (passed < ticks[1]) {
                    fixture.rule(arrow instanceof AbstractArrow && !arrow.isRemoved(), "at tick " + passed
                            + " no real arrow flies under the id of the foreseen one, shot at tick " + ticks[0]);
                    if (arrow != null) {
                        double off = arrow.position().distanceTo(KingCrimsonEpitaphFixture.on(track, session.clock().body()));
                        fixture.rule(off < 1.0E-6D, "in flight at tick " + session.clock().body() + " the arrow was " + off
                                + " blocks off its track");
                        flown[0]++;
                    }
                    fixture.rule(hits[0] == 0, "the foreseen hit landed at tick " + passed + ", before its tick " + ticks[1]);
                }
                return;
            }
            running[0] = null;
            fixture.rule(hits[0] == 1, "the foreseen hit of the arrow landed " + hits[0] + " times, not once");
            fixture.rule(passedAtHit[0] == ticks[1] - 1, "the hit landed while tick " + (passedAtHit[0] + 1)
                    + " was carried out, not at its tick " + ticks[1]);
            fixture.rule(amountAtHit[0] == hit[0].amount(), "the real hit was dealt as " + amountAtHit[0] + ", foreseen as "
                    + hit[0].amount());
            fixture.rule(flown[0] >= 2, "the arrow was seen in flight on " + flown[0] + " server ticks");
            fixture.rule(arrow == null || arrow.isRemoved() || track.end() != EntityTrack.End.REMOVED,
                    "the arrow that was foreseen to be gone after its hit is still in the world");
            fixture.finish("A foreseen arrow flies and hits at its tick");
        }));
    }

    // A6
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_anvil")
    public static void fallingBlockLandsAsForeseen(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-anvil", 2, 12, 6);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        EpitaphSession[] running = new EpitaphSession[1];
        FallingBlockEntity[] falling = new FallingBlockEntity[1];
        RecordedOp.SetBlock[] landing = new RecordedOp.SetBlock[1];
        int[] landingTick = new int[1];
        int[] seen = new int[2];
        helper.runAfterDelay(20, () -> fixture.guarded(() -> {
            FallingBlockEntity anvil = fixture.base.track(
                    FallingBlockEntity.fall(level, fixture.absolute(6, 6, 3), Blocks.ANVIL.defaultBlockState()));
            // Under the crosshair: the one entity a press foresees whatever it is.
            EpitaphSession session = fixture.press(anvil);
            session.foreseeAll();
            landing[0] = KingCrimsonEpitaphFixture.ops(session, RecordedOp.SetBlock.class).stream()
                    .filter(set -> set.state().getBlock() instanceof AnvilBlock).findFirst().orElse(null);
            helper.assertTrue(landing[0] != null && EpitaphSessions.isOnTrack(anvil),
                    "Setup: the anvil under the crosshair is on no track, or it did not land in the room");
            landingTick[0] = KingCrimsonEpitaphFixture.tickOf(session, landing[0]);
            falling[0] = anvil;
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            FallingBlockEntity anvil = falling[0];
            EntityTrack track = session.record().track(anvil.getId());
            BlockState there = level.getBlockState(landing[0].pos());
            if (!session.ended()) {
                int passed = session.passed();
                if (passed < landingTick[0]) {
                    fixture.rule(there.isAir(), "at tick " + passed + " the place of the landing held " + there
                            + ", before tick " + landingTick[0] + " at which the anvil lands");
                    double off = anvil.position().distanceTo(KingCrimsonEpitaphFixture.on(track, session.clock().body()));
                    fixture.rule(!anvil.isRemoved() && off < 1.0E-6D, "falling at tick " + session.clock().body()
                            + " the anvil was " + off + " blocks off its track, or gone");
                    seen[0]++;
                } else {
                    fixture.rule(there == landing[0].state(), "at tick " + passed + ", after the landing at tick "
                            + landingTick[0] + ", its place holds " + there);
                    fixture.rule(anvil.isRemoved(), "the falling anvil is still in the world after it landed");
                    seen[1]++;
                }
                return;
            }
            running[0] = null;
            BlockPos last = BlockPos.containing(track.frame(track.lastTick()).position());
            fixture.rule(landing[0].pos().equals(last) && track.lastTick() == landingTick[0],
                    "the anvil was foreseen to land at " + landing[0].pos().toShortString() + " at tick " + landingTick[0]
                            + ", and its track ends at " + last.toShortString() + " at tick " + track.lastTick());
            fixture.rule(seen[0] >= 5 && seen[1] >= 1, "the anvil was seen falling on " + seen[0]
                    + " server ticks and landed on " + seen[1]);
            fixture.rule(level.getBlockState(landing[0].pos()) == landing[0].state(), "at the end no anvil lies where it landed");
            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class,
                    AABB.ofSize(Vec3.atCenterOf(landing[0].pos()), 6.0D, 6.0D, 6.0D));
            fixture.rule(dropped.isEmpty(), "the anvil that landed also dropped as " + dropped.size() + " items");
            level.setBlock(landing[0].pos(), Blocks.AIR.defaultBlockState(), 3);
            fixture.finish("A falling block lands as foreseen");
        }));
    }

    // A7
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_blast")
    public static void foreseenExplosionBreaksTheRecordedBlocks(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-blast", 2, 16, 10);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.Explode[] foreseen = new RecordedOp.Explode[1];
        BlockPos[] replaced = new BlockPos[1];
        int[] blastTick = new int[1];
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        BlockPos from = fixture.absolute(-1, -3, -1);
        BlockPos to = fixture.absolute(16, 7, 10);
        helper.runAfterDelay(20, () -> fixture.guarded(() -> {
            Vec3 at = fixture.spot(8.5D, 2.0D, 5.5D);
            PrimedTnt tnt = fixture.thing(new PrimedTnt(level, at.x, at.y, at.z, null));
            tnt.setFuse(20);
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                before.put(pos.immutable(), level.getBlockState(pos));
            }
            EpitaphSession session = fixture.press(tnt);
            session.foreseeAll();
            List<RecordedOp.Explode> blasts = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Explode.class);
            helper.assertTrue(blasts.size() == 1 && blasts.get(0).blocks().size() >= 3,
                    "Setup: the room saw " + blasts.size() + " explosions, or one that took fewer than three blocks");
            foreseen[0] = blasts.get(0);
            blastTick[0] = KingCrimsonEpitaphFixture.tickOf(session, foreseen[0]);
            // The world changes under the picture: by the time of the blast one of the foreseen blocks is another block.
            replaced[0] = foreseen[0].blocks().keySet().iterator().next();
            level.setBlock(replaced[0], Blocks.GLASS.defaultBlockState(), 3);
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (!session.ended()) {
                if (session.passed() < blastTick[0]) {
                    for (Map.Entry<BlockPos, BlockState> taken : foreseen[0].blocks().entrySet()) {
                        fixture.rule(taken.getKey().equals(replaced[0]) || level.getBlockState(taken.getKey()) == taken.getValue(),
                                "a block of the foreseen crater was gone at tick " + session.passed() + ", before the blast at tick "
                                        + blastTick[0]);
                    }
                }
                return;
            }
            running[0] = null;
            List<String> wrong = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> was : before.entrySet()) {
                BlockPos pos = was.getKey();
                BlockState now = level.getBlockState(pos);
                if (pos.equals(replaced[0])) {
                    if (!now.is(Blocks.GLASS)) {
                        wrong.add("the block that was replaced after the press is " + now + ", not the glass it was replaced by");
                    }
                } else if (foreseen[0].blocks().containsKey(pos)) {
                    if (!now.isAir()) {
                        wrong.add("the foreseen crater takes " + pos.toShortString() + ", which still holds " + now);
                    }
                } else if (now != was.getValue()) {
                    wrong.add(pos.toShortString() + " is no block of the foreseen crater and changed from " + was.getValue() + " to " + now);
                }
            }
            fixture.rule(wrong.isEmpty(), wrong.size() + " blocks differ from the foreseen crater of " + foreseen[0].blocks().size()
                    + " blocks: " + String.join(" | ", wrong.subList(0, Math.min(3, wrong.size()))));
            fixture.rule(session.record().skipped() >= 1, "the block that had changed under the picture was not counted as skipped");
            for (BlockPos pos : before.keySet()) {
                if (level.getBlockState(pos) != before.get(pos)) {
                    level.setBlock(pos, before.get(pos), 3);
                }
            }
            fixture.finish("A foreseen explosion breaks the recorded blocks");
        }));
    }

    // A8
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_crater")
    public static void phantomFallsIntoTheForeseenCrater(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-crater", 3, 18, 10);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        Creeper creeper = fixture.mob(EntityType.CREEPER, 9.5D, 5.5D);
        creeper.setNoAi(true);
        BlockPos under = fixture.absolute(9, 1, 5);
        int ground = fixture.absolute(9, 2, 5).getY();
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.SetBlock[] landing = new RecordedOp.SetBlock[1];
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        helper.runAfterDelay(20, () -> fixture.guarded(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(fixture.absolute(-1, -3, -1), fixture.absolute(18, 7, 10))) {
                before.put(pos.immutable(), level.getBlockState(pos));
            }
            creeper.ignite();
            // High enough to come down on the spot of the creeper only after it has blown.
            FallingBlockEntity anvil = fixture.base.track(
                    FallingBlockEntity.fall(level, fixture.absolute(9, 22, 5), Blocks.ANVIL.defaultBlockState()));
            EpitaphSession session = fixture.predict(creeper, anvil);
            session.foreseeAll();
            List<RecordedOp.Explode> blasts = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Explode.class);
            landing[0] = KingCrimsonEpitaphFixture.ops(session, RecordedOp.SetBlock.class).stream()
                    .filter(set -> set.state().getBlock() instanceof AnvilBlock).findFirst().orElse(null);
            helper.assertTrue(blasts.size() == 1 && blasts.get(0).blocks().containsKey(under) && landing[0] != null,
                    "Setup: the creeper did not take the floor under it in the room, or the anvil did not land there");
            int blast = KingCrimsonEpitaphFixture.tickOf(session, blasts.get(0));
            int landed = KingCrimsonEpitaphFixture.tickOf(session, landing[0]);
            helper.assertTrue(landed > blast, "Setup: the anvil landed at tick " + landed + ", not after the blast at tick " + blast);
            fixture.rule(landing[0].pos().getX() == under.getX() && landing[0].pos().getZ() == under.getZ()
                    && landing[0].pos().getY() < ground, "the phantom of an anvil that came down on the spot of a foreseen blast landed at "
                            + landing[0].pos().toShortString() + ", not below the ground level " + ground + ": it did not see the crater");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || !session.ended()) {
                return;
            }
            running[0] = null;
            // The crater became real at its tick, and the real anvil followed its phantom into it.
            fixture.rule(level.getBlockState(landing[0].pos()) == landing[0].state(), "at the end the place the anvil was foreseen to land in holds "
                    + level.getBlockState(landing[0].pos()));
            fixture.rule(level.getBlockState(under).isAir() || landing[0].pos().equals(under),
                    "the floor under the creeper is still there after the foreseen blast");
            for (BlockPos pos : before.keySet()) {
                if (level.getBlockState(pos) != before.get(pos)) {
                    level.setBlock(pos, before.get(pos), 3);
                }
            }
            fixture.finish("A phantom falls into the foreseen crater");
        }));
    }

    // A9
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_fate")
    public static void outsideDamageCannotKillBeforeTheTrackEnds(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-fate", 2, 16, 6);
        fixture.budget(0);
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] ticks = new int[1];
        int[] deaths = new int[1];
        boolean[] diedOnTrack = new boolean[1];
        fixture.listen(LivingDeathEvent.class, event -> {
            if (event.getEntity() == zombie) {
                deaths[0]++;
                diedOnTrack[0] |= EpitaphSessions.isOnTrack(zombie);
            }
        });
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            running[0] = fixture.press(null);
            helper.assertTrue(EpitaphSessions.isOnTrack(zombie), "Setup: the zombie that hunts the user is on no track");
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (!session.ended()) {
                ticks[0]++;
                if (ticks[0] == 5) {
                    // An outsider: a hit that is no op of the record, for a thousand.
                    zombie.invulnerableTime = 0;
                    boolean landed = zombie.hurt(fixture.level.damageSources().playerAttack(fixture.user), 1000.0F);
                    PuppetBinder.Binding binding = EpitaphSessions.bindingOf(zombie);
                    fixture.rule(landed && binding != null && binding.debt() > 900.0F, "a hit for a thousand on a zombie on its track landed: "
                            + landed + ", and is owed " + (binding == null ? "to nobody" : String.valueOf(binding.debt())));
                }
                if (ticks[0] >= 5) {
                    fixture.rule(zombie.isAlive() && zombie.getHealth() == 1.0F, "hit for a thousand before its track ended, the zombie has "
                            + zombie.getHealth() + " health at tick " + session.clock().body() + " and lives: " + zombie.isAlive());
                    fixture.rule(EpitaphSessions.isOnTrack(zombie), "the zombie that was hit left its track at tick " + session.clock().body());
                }
                return;
            }
            running[0] = null;
            fixture.rule(ticks[0] > 20, "the prediction ran for " + ticks[0] + " server ticks only");
            fixture.rule(!zombie.isAlive(), "after its track ended, the zombie that was hit for a thousand lives on with "
                    + zombie.getHealth() + " health");
            fixture.rule(deaths[0] == 1 && !diedOnTrack[0], "the zombie died " + deaths[0] + " times, on its track: " + diedOnTrack[0]);
            fixture.finish("Outside damage cannot kill before the track ends");
        }));
    }

    // A10
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_death")
    public static void foreseenDeathHappensAtItsTick(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-death", 2, 12, 6);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        Zombie weak = fixture.mob(EntityType.ZOMBIE, 6.5D, 3.5D);
        weak.setNoAi(true);
        weak.setHealth(1.0F);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] deathTick = new int[1];
        int[] deaths = new int[1];
        int[] passedAtDeath = { -1 };
        fixture.listen(LivingDeathEvent.class, event -> {
            if (event.getEntity() == weak) {
                deaths[0]++;
                passedAtDeath[0] = running[0] != null ? running[0].passed() : -2;
            }
        });
        helper.runAfterDelay(20, () -> fixture.guarded(() -> {
            FallingBlockEntity anvil = fixture.base.track(
                    FallingBlockEntity.fall(level, fixture.absolute(6, 6, 3), Blocks.ANVIL.defaultBlockState()));
            anvil.setHurtsEntities(2.0F, 40);
            EpitaphSession session = fixture.predict(weak, anvil);
            session.foreseeAll();
            RecordedOp.Death death = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Death.class).stream()
                    .filter(op -> op.victim().id() == weak.getId()).findFirst().orElse(null);
            helper.assertTrue(death != null, "Setup: the anvil did not kill the zombie under it in the room");
            deathTick[0] = KingCrimsonEpitaphFixture.tickOf(session, death);
            // Something outside healed the body since: the foreseen hit alone no longer kills it.
            weak.setHealth(weak.getMaxHealth());
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (!session.ended()) {
                if (session.passed() < deathTick[0]) {
                    fixture.rule(weak.isAlive() && deaths[0] == 0, "the zombie was dead at tick " + session.passed()
                            + ", before tick " + deathTick[0] + " at which its death was foreseen");
                } else {
                    fixture.rule(!weak.isAlive(), "at tick " + session.passed() + ", after tick " + deathTick[0]
                            + " at which its death was foreseen, the zombie lives with " + weak.getHealth() + " health");
                }
                return;
            }
            running[0] = null;
            fixture.rule(deaths[0] == 1, "the foreseen death happened " + deaths[0] + " times, not once");
            fixture.rule(passedAtDeath[0] == deathTick[0] - 1, "the zombie died while tick " + (passedAtDeath[0] + 1)
                    + " was carried out, not at tick " + deathTick[0]);
            level.setBlock(fixture.absolute(6, 2, 3), Blocks.AIR.defaultBlockState(), 3);
            fixture.finish("A foreseen death happens at its tick");
        }));
    }

    // A11
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_drops")
    public static void futureDropsAreTheRealDrops(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-drops", 2, 12, 6);
        fixture.budget(0);
        ServerLevel level = fixture.level;
        Zombie weak = fixture.mob(EntityType.ZOMBIE, 6.5D, 3.5D);
        // The loot of a dungeon chest: several rolls of many items in random counts, so that no second roll repeats it.
        CompoundTag saved = weak.saveWithoutId(new CompoundTag());
        saved.putString("DeathLootTable", "minecraft:chests/simple_dungeon");
        weak.load(saved);
        weak.setNoAi(true);
        weak.setHealth(1.0F);
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.Death[] foreseen = new RecordedOp.Death[1];
        int[] deathTick = new int[1];
        int[] events = new int[1];
        List<String> dropped = new ArrayList<>();
        boolean[] counted = new boolean[1];
        fixture.listen(LivingDropsEvent.class, event -> {
            if (event.getEntity() == weak) {
                events[0]++;
                for (ItemEntity item : event.getDrops()) {
                    dropped.add(describe(level, item.getItem()));
                }
            }
        });
        helper.runAfterDelay(20, () -> fixture.guarded(() -> {
            FallingBlockEntity anvil = fixture.base.track(
                    FallingBlockEntity.fall(level, fixture.absolute(6, 6, 3), Blocks.ANVIL.defaultBlockState()));
            anvil.setHurtsEntities(2.0F, 40);
            EpitaphSession session = fixture.predict(weak, anvil);
            session.foreseeAll();
            foreseen[0] = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Death.class).stream()
                    .filter(op -> op.victim().id() == weak.getId()).findFirst().orElse(null);
            helper.assertTrue(foreseen[0] != null && foreseen[0].drops().size() >= 3,
                    "Setup: the zombie did not die in the room, or its loot there was fewer than three stacks");
            deathTick[0] = KingCrimsonEpitaphFixture.tickOf(session, foreseen[0]);
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (session.passed() >= deathTick[0] && !counted[0]) {
                counted[0] = true;
                Map<String, Integer> lying = new TreeMap<>();
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, weak.getBoundingBox().inflate(8.0D))) {
                    lying.merge(item.getItem().getItem().toString(), item.getItem().getCount(), Integer::sum);
                }
                Map<String, Integer> expected = new TreeMap<>();
                for (RecordedOp.Drop drop : foreseen[0].drops()) {
                    expected.merge(drop.stack().getItem().toString(), drop.stack().getCount(), Integer::sum);
                }
                fixture.rule(lying.equals(expected), "around the zombie that died as foreseen lie " + lying + ", foreseen were " + expected);
            }
            if (!session.ended()) {
                return;
            }
            running[0] = null;
            List<String> expected = new ArrayList<>();
            for (RecordedOp.Drop drop : foreseen[0].drops()) {
                expected.add(describe(level, drop.stack()));
            }
            expected.sort(null);
            dropped.sort(null);
            fixture.rule(events[0] == 1, "the drops event of the foreseen death was posted " + events[0] + " times, not once");
            fixture.rule(counted[0] && !weak.isAlive(), "the zombie did not die at the tick its death was foreseen for");
            fixture.rule(dropped.equals(expected), "the real death dropped " + dropped.size() + " stacks " + dropped
                    + ", the foreseen death " + expected.size() + " stacks " + expected);
            level.setBlock(fixture.absolute(6, 2, 3), Blocks.AIR.defaultBlockState(), 3);
            fixture.finish("The foreseen drops are the real drops");
        }));
    }

    private static String describe(ServerLevel level, ItemStack stack) {
        return stack.isEmpty() ? "nothing" : stack.save(level.registryAccess()).toString();
    }
}
