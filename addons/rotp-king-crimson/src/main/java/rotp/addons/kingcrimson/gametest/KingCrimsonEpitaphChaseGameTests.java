package rotp.addons.kingcrimson.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.network.EpitaphShortcutPayload;

/**
 * Epitaph, the chase (EPITAPH-ARCH 2.3): a body on its track is placed, not steered, and the world can still get in
 * its way. Knocked off its track it comes back, the faster the further off it is. It is never put inside blocks: where
 * something was built across its track it waits, and appears at the first later place of its track that is free.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphChaseGameTests {
    private KingCrimsonEpitaphChaseGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_knock")
    public static void aKnockedBodyComesBackToItsTrack(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-knock", 3, 16, 8);
        fixture.budget(0);
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 3.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] ticks = new int[1];
        double[] furthest = new double[1];
        int[] backAt = { -1 };
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
                ticks[0]++;
                double off = zombie.position().distanceTo(KingCrimsonEpitaphFixture.on(track, session.clock().body()));
                if (ticks[0] == 6) {
                    // A push from outside, across its way: what a hit does to a creature.
                    zombie.knockback(1.0D, 0.0D, -1.0D);
                } else if (ticks[0] > 6) {
                    furthest[0] = Math.max(furthest[0], off);
                    if (backAt[0] < 0 && furthest[0] > 0.3D && off < 1.0E-6D) {
                        backAt[0] = ticks[0];
                    }
                    fixture.rule(backAt[0] < 0 || off < 1.0E-6D, "back on its track at server tick " + backAt[0]
                            + ", the zombie was off it again by " + off + " at server tick " + ticks[0]);
                } else {
                    fixture.rule(off < 1.0E-6D, "before anything pushed it the zombie was " + off + " blocks off its track");
                }
                return;
            }
            running[0] = null;
            fixture.rule(furthest[0] > 0.3D && furthest[0] < 6.0D, "a knock across its way took the zombie " + furthest[0]
                    + " blocks off its track at most");
            fixture.rule(backAt[0] > 6 && backAt[0] <= 40, "knocked off its track at server tick 6, the zombie was back on it at server tick "
                    + backAt[0]);
            // Let go, it has lived a tick of its own since, with the motion its phantom ended with: a blow of the phantom of
            // the user in the last ticks of the foresight throws it half a block.
            double carried = track.frame(track.lastTick()).motion().length();
            double off = zombie.position().distanceTo(track.frame(track.lastTick()).position());
            fixture.rule(zombie.isAlive() && off < 0.5D + carried, "let go, the zombie is " + off + " blocks from where its phantom"
                    + " ended, which moved " + carried + " blocks a tick then");
            fixture.finish("A body knocked off its track comes back to it");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_wall")
    public static void aWallAcrossTheTrackIsPassedByTheShortCut(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-wall", 3, 16, 6);
        fixture.budget(0);
        fixture.userKeepsControl();
        ServerLevel level = fixture.level;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        // Somebody who sees the zombie: he is shown the flash of its short cut.
        ServerPlayer watcher = fixture.witness("kc-ep-wall-by", 0.5D, 5.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] wallX = new int[1];
        int[] waited = new int[1];
        double[] leap = new double[1];
        Vec3[] last = new Vec3[1];
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            EntityTrack track = session.record().track(zombie.getId());
            helper.assertTrue(track != null && EpitaphSessions.isOnTrack(zombie), "Setup: the zombie that hunts the user is on no track");
            Vec3 start = track.frame(0).position();
            Vec3 end = track.frame(track.lastTick()).position();
            helper.assertTrue(start.x - end.x > 4.5D, "Setup: the phantom of the zombie walked only from x " + start.x + " to x " + end.x);
            // Built after the press, across the way its phantom walked: a wall two blocks ahead of the body.
            wallX[0] = BlockPos.containing(start.x - 2.5D, start.y, start.z).getX();
            int floor = BlockPos.containing(start).getY();
            for (int z = fixture.absolute(0, 0, -1).getZ(); z <= fixture.absolute(0, 0, 6).getZ(); z++) {
                for (int y = floor; y <= floor + 2; y++) {
                    level.setBlock(new BlockPos(wallX[0], y, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
            last[0] = zombie.position();
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            EntityTrack track = session.record().track(zombie.getId());
            fixture.rule(level.noBlockCollision(zombie, zombie.getBoundingBox().deflate(0.05D)),
                    "the zombie on its track was put inside blocks at " + KingCrimsonEpitaphFixture.at(zombie.position()));
            leap[0] = Math.max(leap[0], last[0].distanceTo(zombie.position()));
            last[0] = zombie.position();
            if (!session.ended()) {
                if (zombie.position().distanceTo(KingCrimsonEpitaphFixture.on(track, session.clock().body())) > 0.5D) {
                    waited[0]++;
                }
                return;
            }
            running[0] = null;
            Vec3 end = track.frame(track.lastTick()).position();
            fixture.rule(waited[0] >= 5, "the zombie stood away from its track, which ran through the wall, on " + waited[0]
                    + " server ticks only");
            fixture.rule(leap[0] > 1.2D, "the furthest the zombie moved in one server tick is " + leap[0]
                    + " blocks: it took no short cut past the wall");
            List<EpitaphShortcutPayload> flashes = KingCrimsonEraseFixture.listener(watcher).payloads(EpitaphShortcutPayload.class);
            fixture.rule(flashes.size() == 1 && flashes.get(0).entity() == zombie.getId() && flashes.get(0).x() > wallX[0],
                    "a player who sees the zombie was shown the flash of a short cut " + flashes.size() + " times, not once for it"
                            + " at the place before the wall that it left");
            fixture.rule(zombie.isAlive() && zombie.getX() < wallX[0] && zombie.position().distanceTo(end) < 0.5D,
                    "at the end the zombie is at " + KingCrimsonEpitaphFixture.at(zombie.position()) + ", its phantom ended at "
                            + KingCrimsonEpitaphFixture.at(end) + ", beyond the wall at x " + wallX[0]);
            fixture.finish("A wall across the track is passed by the short cut");
        }));
    }
}
