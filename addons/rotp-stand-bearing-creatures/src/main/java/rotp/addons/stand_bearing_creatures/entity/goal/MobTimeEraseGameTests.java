package rotp.addons.stand_bearing_creatures.entity.goal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.gametest.TemplateChunkLayout;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

/**
 * A creature that carries King Crimson uses its Time Erase in a fight with a player (the owner, 2026-11-11): right
 * after he hits it, and by itself with a chance each second, high for the first erase of a fight within 10 blocks
 * ("最好是和玩家相距10m內優先高概率發動一次，防止發動晚了生物替身使者死亡"). King Crimson is in the test server for these
 * tests (build.gradle, localRuntime); nothing here is compiled against it, so whether the creature erases is asked of
 * King Crimson by name. Each test has a batch of its own: the end of an erase stops the thinking of every creature
 * around for a moment.
 */
@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobTimeEraseGameTests {
    private static final String ERASE = "kingcrimson_timeerase";

    private MobTimeEraseGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200, batch = "mwp_time_erase_first")
    public static void aKingCrimsonMobErasesTimeSoonAfterItClosesToTenBlocksOfThePlayerItFights(GameTestHelper helper) {
        Scene scene = new Scene(helper, 20.0D, true);
        int[] erasedAt = {-1};
        int[] tick = new int[1];
        helper.onEachTick(() -> scene.guarded(() -> {
            tick[0]++;
            if (tick[0] == 30) {
                helper.assertTrue(scene.mob.getTarget() == scene.player && !scene.erased(),
                        "A King Crimson mob erased time for a player 20 blocks away, or does not fight him: beyond 16"
                                + " blocks it never does.");
                helper.assertTrue(scene.goal.timeErase.askedNear() == 0, "Setup: the mob has asked itself for a first"
                        + " erase within 10 blocks with the player 20 blocks away");
                // He closes to 9 blocks.
                scene.place(9.0D);
            } else if (tick[0] > 30 && erasedAt[0] < 0 && scene.erased()) {
                erasedAt[0] = tick[0] - 30;
            }
            if (tick[0] == 32) {
                helper.assertTrue(scene.goal.timeErase.askedNear() == 1, "Two ticks after the player it fights came"
                        + " within 10 blocks, a King Crimson mob has asked itself " + scene.goal.timeErase.askedNear()
                        + " times whether to erase time: it asks at once as he closes in, not at its next second.");
            }
            // Asked as he closes in and once a second after, at 0.8 each time; its fourth asking, 60 ticks after he
            // closed in, is certain, and no other move of its Stand comes first. The press becomes an erase in the
            // Stand's next ticks.
            if (tick[0] == 30 + 66) {
                helper.assertTrue(erasedAt[0] >= 0, "66 ticks after the player it fights came within 10 blocks, a King"
                        + " Crimson mob with its erase ready has not erased time.");
                helper.assertTrue(scene.goal.timeErase.pressedInThisFight() > 0, "A King Crimson mob that erased time does"
                        + " not know that it has erased in this fight: its next erase would be a first one again.");
                scene.close();
                helper.succeed();
            }
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "mwp_time_erase_hit")
    public static void aKingCrimsonMobAnswersAHitOfThePlayerItFightsWithItsEraseAtOnce(GameTestHelper helper) {
        // Out where it asks itself only once a second, at 0.25: its first asking is 20 ticks into the fight.
        Scene scene = new Scene(helper, 14.0D, true);
        int[] tick = new int[1];
        helper.onEachTick(() -> scene.guarded(() -> {
            tick[0]++;
            if (tick[0] == 6) {
                helper.assertTrue(scene.mob.getTarget() == scene.player && !scene.erased(),
                        "Setup: the mob does not fight the player, or erased time in its first ticks.");
                scene.hit();
            } else if (tick[0] == 12) {
                helper.assertTrue(scene.erased(), "Six ticks after the player it fights hit it, a King Crimson mob with"
                        + " its erase ready has not erased time.");
                scene.close();
                helper.succeed();
            }
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "mwp_time_erase_chances")
    public static void theEraseOfAKingCrimsonMobIsNoPickAndItsChancesGoByDistanceAndByTheFight(GameTestHelper helper) {
        Scene scene = new Scene(helper, 30.0D, true);
        helper.runAfterDelay(5, () -> scene.guarded(() -> {
            Ability erase = MobPowerUtil.timeErase(scene.power);
            helper.assertTrue(erase != null && scene.power.updateAvailableMoves().inMovesetAndCanBeUsed.get(ERASE) == erase,
                    "Setup: a King Crimson mob at Resolve 4 cannot use its Time Erase.");
            helper.assertTrue(!MobPowerUtil.isMobSafeAbility(scene.power, erase)
                            && !MobPowerUtil.availableMobAbilities(scene.power).contains(erase),
                    "The Time Erase is among the moves the goal of a King Crimson mob picks at random.");
            helper.assertTrue(TimeEraseTactic.chance(true, 9.0D * 9.0D, 0) == 0.8F
                            && TimeEraseTactic.chance(true, 9.0D * 9.0D, 2) == 0.8F
                            && TimeEraseTactic.chance(true, 9.0D * 9.0D, 3) == 1.0F
                            && TimeEraseTactic.chance(false, 9.0D * 9.0D, 3) == 0.5F
                            && TimeEraseTactic.chance(true, 12.0D * 12.0D, 3) == 0.25F
                            && TimeEraseTactic.chance(false, 12.0D * 12.0D, 0) == 0.25F
                            && TimeEraseTactic.chance(true, 16.5D * 16.5D, 3) == 0.0F
                            && TimeEraseTactic.ASK_EVERY_TICKS == 20,
                    "The chances of a King Crimson mob's erase are not 0.8 for the first of a fight within 10 blocks"
                            + " (its fourth asking certain), 0.5 for a later one, 0.25 out to 16 blocks and none beyond,"
                            + " asked once a second.");
            scene.close();
            helper.succeed();
        }));
    }

    /**
     * The owner's "優先": within 10 blocks the first erase of a fight comes before the Stand's other moves. Here every
     * asking that goes by chance is made to fail, tick by tick of the goal: three of them, 20 ticks apart, and in all
     * that time the goal picks nothing; the fourth is certain. Afterwards the goal picks again, and a hit that comes
     * while the key cannot be used (its erase runs) is let be.
     */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "mwp_time_erase_fourth")
    public static void theFirstEraseOfAKingCrimsonMobComesBeforeItsOtherMovesAndItsFourthAskingIsCertain(GameTestHelper helper) {
        Scene scene = new Scene(helper, 9.0D, false);
        TimeEraseTactic tactic = scene.goal.timeErase;
        scene.guarded(() -> {
            for (int tick = 1; tick <= 60; tick++) {
                scene.nextRollFails();
                scene.goal.tick();
                int asked = 1 + (tick - 1) / 20;
                helper.assertTrue(tactic.askedNear() == asked && !tactic.wants() && tactic.pressedInThisFight() == 0,
                        "In tick " + tick + " of a fight within 10 blocks, with every roll failed, a King Crimson mob has"
                                + " asked itself " + tactic.askedNear() + " times for its first erase (" + asked
                                + " as he closes in and every 20 ticks), wants it " + tactic.wants() + ", pressed it "
                                + tactic.pressedInThisFight() + " times");
                helper.assertTrue(scene.goal.pool(scene.power).isEmpty() && scene.standAction() == null,
                        "In tick " + tick + " of a fight within 10 blocks, its first erase still before it and its key"
                                + " ready, the goal of a King Crimson mob picks other moves " + scene.goal.pool(scene.power).keySet()
                                + "; its Stand runs " + scene.standAction());
            }
            scene.nextRollFails();
            scene.goal.tick();
            helper.assertTrue(tactic.pressedInThisFight() == 1, "The fourth asking of a first erase within 10 blocks is"
                    + " certain: 60 ticks after the player came within 10 blocks, three askings failed, a King Crimson"
                    + " mob with its erase ready did not press it.");
        });
        helper.runAfterDelay(4, () -> scene.guarded(() -> {
            helper.assertTrue(scene.erased(), "Four ticks after it pressed its key a King Crimson mob does not erase time.");
            scene.hit();
            scene.nextRollFails();
            scene.goal.tick();
            helper.assertTrue(!tactic.wants() && tactic.pressedInThisFight() == 1, "A King Crimson mob that is hit while its key"
                    + " cannot be used (its erase runs) wants an erase " + tactic.wants() + " and has pressed its key "
                    + tactic.pressedInThisFight() + " times: such a hit is let be.");
            helper.assertTrue(!scene.goal.pool(scene.power).isEmpty(), "After its first erase the goal of a King Crimson"
                    + " mob still picks no other move.");
            scene.close();
            helper.succeed();
        }));
    }

    /**
     * A move of its Stand that holds the key back does not cost the creature its erase: it waits for the move to end,
     * picks no other move meanwhile, and presses the key in the first tick its Stand lets it through. Out between 10
     * and 12.5 blocks, where the goal picks moves and no first erase comes before them.
     */
    @GameTest(template = "empty", timeoutTicks = 40, batch = "mwp_time_erase_busy")
    public static void aKingCrimsonMobWhoseStandIsInAMoveErasesAsSoonAsTheMoveIsOverAndPicksNoOtherMoveUntilThen(
            GameTestHelper helper) {
        Scene scene = new Scene(helper, 11.5D, false);
        TimeEraseTactic tactic = scene.goal.timeErase;
        // Once the husk has lived a tick: a hit in the tick it was made bears the time its goal began with.
        helper.runAfterDelay(2, () -> scene.guarded(() -> {
            Ability erase = MobPowerUtil.timeErase(scene.power);
            Map<String, Ability> moves = scene.goal.pool(scene.power);
            String heavy = List.of("heavy_punch", "heavy_charged", "kingcrimson_impale", "kingcrimson_chop").stream()
                    .filter(moves::containsKey).findFirst().orElse(null);
            helper.assertTrue(heavy != null && scene.goal.performNamed(scene.power, moves, heavy, null, scene.player),
                    "Setup: the husk did not press a heavy move; it can pick " + moves.keySet());
            EntityActionInstance running = scene.standAction();
            helper.assertTrue(running != null && !running.isOver() && erase instanceof EntityActionAbility key
                            && key.wouldBufferInput(scene.mob),
                    "Setup: the running " + heavy + " does not hold the erase back; the Stand runs " + running);
            scene.hit();
            scene.nextRollFails();
            scene.goal.tick();
            helper.assertTrue(tactic.wants() && tactic.pressedInThisFight() == 0 && scene.standAction() == running,
                    "A King Crimson mob that is hit while a move of its Stand holds its key back wants its erase "
                            + tactic.wants() + ", pressed it " + tactic.pressedInThisFight() + " times" + "; its Stand runs "
                            + scene.standAction() + ": it waits for the move to end.");
            helper.assertTrue(scene.goal.pool(scene.power).isEmpty(), "While a King Crimson mob waits for its Stand to let"
                    + " its erase through, its goal picks other moves: " + scene.goal.pool(scene.power).keySet());
            // The move is over.
            LivingComponentAction.getComponent(scene.power.getSummonedStandEntity()).setAction(null, scene.mob, SyncType.NO_SYNC);
            scene.nextRollFails();
            scene.goal.tick();
            helper.assertTrue(tactic.pressedInThisFight() == 1 && !tactic.wants(), "In the first tick its Stand let the key"
                    + " through, a King Crimson mob that wanted its erase did not press it.");
        }));
        helper.runAfterDelay(6, () -> scene.guarded(() -> {
            helper.assertTrue(scene.erased(), "Four ticks after it pressed its key a King Crimson mob does not erase time.");
            scene.close();
            helper.succeed();
        }));
    }

    /**
     * A husk with King Crimson that cannot walk, and the player it fights, so many blocks east of it. One that thinks
     * runs the goal the add-on gave it; one that does not has its Stand out, and the test ticks a goal of its own.
     */
    private static final class Scene {
        private final Mob mob;
        private final Player player;
        private final StandPower power;
        private final StandPowerMobGoal goal;
        private final boolean thinks;
        private final Vec3 at;
        private boolean closed;
        private boolean erased;

        private Scene(GameTestHelper helper, double blocks, boolean thinks) {
            this.thinks = thinks;
            ServerLevel level = helper.getLevel();
            mob = EntityType.HUSK.create(level);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MobTimeEraseTarget"));
            helper.assertTrue(mob != null, "Missing mob fixture");
            // The husk and its Stand must tick: only the chunk of the template is sure to.
            at = TemplateChunkLayout.absolute(helper, new Vec3(1.5D, 2.0D, 1.5D), 0.0D, 3.0D, 0.0D, 3.0D);
            mob.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
            mob.setNoAi(!thinks);
            mob.setNoGravity(true);
            mob.setPersistenceRequired();
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            player.setNoGravity(true);
            place(blocks);
            level.addFreshEntity(mob);
            StandType kingCrimson = MobPowerUtil.findStand("rotp_kingcrimson:kingcrimson");
            helper.assertTrue(kingCrimson != null, "Setup: King Crimson is not in the test server (build.gradle, localRuntime).");
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob) && MobPowerUtil.grantStand(mob, kingCrimson, true),
                    "Could not give the husk King Crimson.");
            power = StandPower.get(mob);
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            mob.setTarget(player);
            helper.assertTrue(level.isPositionEntityTicking(mob.blockPosition()), "Setup: the husk stands where nothing ticks.");
            StandPowerMobGoal given = null;
            for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
                if (wrapped.getGoal() instanceof StandPowerMobGoal found) {
                    given = found;
                }
            }
            helper.assertTrue(given != null, "Setup: the husk was given no Stand goal.");
            if (thinks) {
                goal = given;
            } else {
                helper.assertTrue(power.getPowerType().summon(mob, power) && power.getSummonedStandEntity() != null,
                        "Setup: the husk's King Crimson did not come out.");
                power.getSummonedStandEntity().summonLockTicks = 0;
                goal = new StandPowerMobGoal(mob);
            }
        }

        private void place(double blocks) {
            player.moveTo(at.x + blocks, at.y, at.z, 90.0F, 0.0F);
        }

        private void hit() {
            mob.hurt(mob.damageSources().playerAttack(player), 1.0F);
        }

        // The next roll of the husk fails whatever its chance below 1 is: its random starts where its first float is high.
        private void nextRollFails() {
            RandomSource random = mob.getRandom();
            long seed = 0L;
            do {
                random.setSeed(++seed);
            } while (random.nextFloat() < 0.9F);
            random.setSeed(seed);
        }

        private EntityActionInstance standAction() {
            StandEntity stand = power.getSummonedStandEntity();
            return stand != null ? LivingComponentAction.getComponent(stand).getAction() : null;
        }

        // Asked of King Crimson by name: KingCrimsonCreatureErase.isErasing(creature). Once it erased, it has erased.
        private boolean erased() {
            erased |= Boolean.TRUE.equals(kingCrimson("isErasing", Entity.class));
            return erased;
        }

        private Object kingCrimson(String method, Class<?> argument) {
            try {
                return Class.forName("rotp.addons.kingcrimson.state.KingCrimsonCreatureErase").getMethod(method, argument)
                        .invoke(null, mob);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Setup: King Crimson's creature erase is not in the test server", failure);
            }
        }

        private void guarded(Runnable check) {
            if (closed) {
                return;
            }
            try {
                check.run();
            } catch (RuntimeException | AssertionError failure) {
                close();
                throw failure;
            }
        }

        private void close() {
            closed = true;
            if (!thinks) {
                goal.stop();
            }
            // Its erase ends here, in this test's own batch, and not a tick after the next test began.
            kingCrimson("end", Mob.class);
            StandEntity stand = power.getSummonedStandEntity();
            if (stand != null) {
                LivingComponentAction.getComponent(stand).setAction(null, mob, SyncType.NO_SYNC);
            }
            if (power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            player.discard();
        }
    }
}
