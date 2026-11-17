package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.GoalWarmStart;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;

/**
 * Epitaph, the warm start (EPITAPH-ARCH 7.3, stage G): the goals of a copy run on from where those of its body are.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphWarmStartGameTests {
    private static final int PRESS_AT = 5;
    /** The press comes this many ticks after a blow: past the moment in which a struck player cannot be struck again. */
    private static final int AFTER_A_BLOW = 11;
    /** What the attack of a zombie waits between two blows. */
    private static final int COOLDOWN = 20;

    private KingCrimsonEpitaphWarmStartGameTests() {}

    /** A goal of a test: one field of each kind the warm start carries, and what it must leave alone. */
    private static final class TestGoal extends Goal {
        int count;
        boolean wanted;
        Direction facing = Direction.NORTH;
        BlockPos spot;
        Vec3 at;
        Entity quarry;
        Path way;
        List<String> notes = new ArrayList<>();
        final int made;

        TestGoal(int made, Goal.Flag flag) {
            this.made = made;
            setFlags(EnumSet.of(flag));
        }

        @Override
        public boolean canUse() {
            return wanted;
        }
    }

    private static final class OtherGoal extends Goal {
        @Override
        public boolean canUse() {
            return false;
        }
    }

    private static boolean runs(GoalSelector selector, Goal goal) {
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() == goal) {
                return wrapped.isRunning();
            }
        }
        return false;
    }

    // The first tick of the future at which the phantom of this creature strikes that player, -1 when it never does.
    private static int firstBlow(EpitaphSession session, Entity striker, ServerPlayer struck) {
        ActorKey by = ActorKey.of(striker);
        ActorKey on = ActorKey.of(struck);
        for (RecordedOp.Damage hit : KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class)) {
            if (hit.victim().equals(on) && by.equals(ActorKey.of(hit.source().getEntity()))) {
                return KingCrimsonEpitaphFixture.tickOf(session, hit);
            }
        }
        return -1;
    }

    // G1
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_warm_cooldown")
    public static void warmStartKeepsAnAttackCooldown(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-warm", 2, 16, 8);
        // The user is not walked: his phantom stands where he stands, and the zombie strikes a still man in both worlds.
        fixture.userKeepsControl();
        fixture.steadyHealth();
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        ServerPlayer user = fixture.user;
        // The zombie stands in a stall that opens on the user: it is in reach of him at every tick, here and in the room.
        List<BlockPos> stall = List.of(fixture.absolute(4, 2, 2), fixture.absolute(4, 3, 2), fixture.absolute(3, 2, 1),
                fixture.absolute(3, 3, 1), fixture.absolute(3, 2, 3), fixture.absolute(3, 3, 3));
        for (BlockPos pos : stall) {
            fixture.level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        }
        fixture.base.onClose(() -> {
            for (BlockPos pos : stall) {
                fixture.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        });
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 3.5D, 2.5D));
        int[] blow = {-1, -1};
        int[] age = new int[1];
        float[] health = {-1.0F};
        boolean[] done = new boolean[1];
        helper.onEachTick(() -> fixture.guarded(() -> {
            if (done[0]) {
                return;
            }
            age[0]++;
            if (health[0] >= 0.0F && user.getHealth() < health[0] - 0.01F) {
                // The control: what the zombie itself does, left alone. Two blows at its own pace, and the press after them.
                blow[1] = blow[0] >= 0 && age[0] - blow[0] >= COOLDOWN - 1 && age[0] - blow[0] <= COOLDOWN + 1 ? age[0] : -1;
                blow[0] = age[0];
                user.setHealth(user.getMaxHealth());
            }
            health[0] = user.getHealth();
            if (blow[1] < 0 || age[0] != blow[1] + AFTER_A_BLOW) {
                return;
            }
            done[0] = true;
            helper.assertTrue(zombie.getTarget() == user && zombie.isWithinMeleeAttackRange(user), "Setup: at the press the zombie is after "
                    + zombie.getTarget() + " and stands " + zombie.distanceTo(user) + " blocks from the user, " + (zombie.isWithinMeleeAttackRange(user) ? "in" : "out of")
                    + " reach");
            EpitaphSession session = fixture.predict(zombie);
            session.foreseeAll();
            int first = firstBlow(session, zombie, user);
            int due = COOLDOWN - AFTER_A_BLOW;
            fixture.rule(first >= due - 2 && first <= due + 3, "a zombie that strikes every " + COOLDOWN + " ticks and struck "
                    + AFTER_A_BLOW + " ticks before the press: its phantom strikes first at tick " + first
                    + " of the future, and the zombie itself would have struck at about tick " + due);
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The warm start keeps an attack cooldown");
        }));
    }

    // G2, and point 10 of the plan after stage D: by class and order among the goals of that class
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_warm_object")
    public static void warmStartCopiesNoObject(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-warm-own", 2, 16, 8);
        // The room waits: its phantoms are asked for before it has lived a tick.
        fixture.budget(0);
        ServerPlayer user = fixture.user;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 10.5D, 2.5D);
        Cow outsider = fixture.mob(EntityType.COW, 10.5D, 6.5D);
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.predict(zombie);
            SandboxRoom room = session.room();
            Entity copy = room.phantom(zombie.getId());
            helper.assertTrue(copy != null && room.phantom(outsider.getId()) == null, "Setup: the zombie has no phantom, or the cow"
                    + " that is not foreseen has one");
            GoalSelector from = new GoalSelector(fixture.level.getProfilerSupplier());
            GoalSelector to = new GoalSelector(fixture.level.getProfilerSupplier());
            TestGoal a1 = new TestGoal(1, Goal.Flag.JUMP);
            TestGoal a2 = new TestGoal(2, Goal.Flag.MOVE);
            TestGoal a3 = new TestGoal(3, Goal.Flag.LOOK);
            TestGoal b1 = new TestGoal(1, Goal.Flag.JUMP);
            TestGoal b2 = new TestGoal(2, Goal.Flag.MOVE);
            TestGoal b3 = new TestGoal(3, Goal.Flag.LOOK);
            TestGoal b4 = new TestGoal(4, Goal.Flag.MOVE);
            from.addGoal(1, a1);
            from.addGoal(2, a2);
            from.addGoal(3, a3);
            // On the copy the same goals stand in other places: one of its own comes first, and one more wants to move.
            to.addGoal(0, new OtherGoal());
            to.addGoal(1, b1);
            to.addGoal(2, b2);
            to.addGoal(3, b3);
            to.addGoal(4, b4);
            a2.wanted = true;
            a3.wanted = true;
            from.tick();
            helper.assertTrue(!runs(from, a1) && runs(from, a2) && runs(from, a3), "Setup: of the three goals of the body the second"
                    + " and the third do not run alone");
            Path way = zombie.getNavigation().createPath(user.blockPosition(), 0);
            helper.assertTrue(way != null && way.getNodeCount() > 1, "Setup: the zombie finds no path to the user");
            // The first does not run: what it counted is carried all the same.
            a1.count = 3;
            a2.count = 17;
            a2.facing = Direction.WEST;
            a2.spot = zombie.blockPosition();
            a2.at = zombie.position();
            a2.quarry = zombie;
            a2.way = way;
            a2.notes.add("of the body");
            // The third holds a creature that has no phantom in this room.
            a3.count = 5;
            a3.quarry = outsider;
            GoalWarmStart.carry(room, from, to);
            fixture.rule(runs(to, b2) && b2.count == 17 && b2.wanted && b2.facing == Direction.WEST && a2.spot.equals(b2.spot)
                    && a2.at.equals(b2.at), "the twin of a running goal " + (runs(to, b2) ? "runs" : "does not run") + " and has count "
                            + b2.count + ", facing " + b2.facing + ", spot " + b2.spot + " and place " + b2.at + ": its body has 17, west, "
                            + a2.spot + " and " + a2.at);
            fixture.rule(b2.quarry == copy, "the creature a goal holds is, on its twin, "
                    + (b2.quarry == zombie ? "the real creature" : b2.quarry == null ? "nothing" : "something else") + ", not its phantom");
            fixture.rule(b2.way != null && b2.way != way && b2.way.getNodeCount() == way.getNodeCount(), "the path a goal holds is, on"
                    + " its twin, " + (b2.way == way ? "the very path of the body" : b2.way == null ? "nothing" : "a path of "
                            + b2.way.getNodeCount() + " nodes for " + way.getNodeCount()));
            fixture.rule(b2.notes != a2.notes && b2.notes.isEmpty(), "an object a goal holds is, on its twin, "
                    + (b2.notes == a2.notes ? "the very object of the body" : "an object with " + b2.notes));
            fixture.rule(!runs(to, b1) && b1.count == 3, "the first goal of a class on the copy, whose twin waits with a count of 3, "
                    + (runs(to, b1) ? "runs" : "does not run") + " and has count " + b1.count + ": 17 is the count of the second of that class");
            fixture.rule(!runs(to, b3) && b3.count == 0 && b3.quarry == null, "a goal that holds a creature with no phantom "
                    + (runs(to, b3) ? "runs" : "does not run") + " on the copy, with count " + b3.count + " and "
                    + (b3.quarry == null ? "nothing" : "something") + " in the place of the creature");
            // The flag a running goal holds is held by its twin: a goal below it that wants the same does not start beside it.
            b4.wanted = true;
            to.tick();
            fixture.rule(runs(to, b2) && !runs(to, b4), "after a tick of the goals of the copy the twin "
                    + (runs(to, b2) ? "runs on" : "has stopped") + " and a goal below it that wants the same flag "
                    + (runs(to, b4) ? "runs beside it" : "waits"));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The warm start carries values, maps creatures, copies paths and takes no object");
        }));
    }
}
