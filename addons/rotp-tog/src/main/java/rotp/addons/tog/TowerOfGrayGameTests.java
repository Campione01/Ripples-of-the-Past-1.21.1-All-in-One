package rotp.addons.tog;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.tog.TowerOfGrayAbility.Move;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(TowerOfGrayAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TowerOfGrayGameTests {
    private static final short KEY = 1;
    private static final short OTHER_KEY = 2;
    private static final PowerSkillUnlocks.Owner UNLOCKS =
            PowerSkillUnlocks.register(TowerOfGrayAddon.id("gametest"));

    private TowerOfGrayGameTests() {}

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 60)
    public static void dashAndRepositionBothHit(GameTestHelper helper) {
        Scene scene = new Scene(helper, 32);
        Cow ahead = scene.cow(0, 0, 3);
        scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, ahead.position().add(0, 0.9, 0));
        float health = ahead.getHealth();
        float stamina = scene.power.getStamina();
        scene.press(Move.DASH);
        scene.near(1.8, scene.stand.getDeltaMovement().length(), "Dash initial speed");
        scene.check(scene.power.getAbilityCooldown("dash") == 40, "Dash cooldown");
        scene.release();
        helper.runAfterDelay(4, () -> {
            scene.check(ahead.getHealth() < health, "Click Dash did not hit in actual entity travel");
            scene.near(stamina - 10, scene.power.getStamina(), "Dash one-time stamina");
            ahead.discard();
            scene.stand.setDeltaMovement(Vec3.ZERO);
        });
        helper.runAfterDelay(13, () -> {
            scene.stand.setPos(scene.origin.add(0, 0, 4));
            scene.stand.setDeltaMovement(Vec3.ZERO);
            Cow between = scene.cow(0, 0, 1.5);
            float before = between.getHealth();
            scene.press(Move.QUICK_REPOSITION);
            scene.check(scene.stand.getDeltaMovement().z < -1.6
                    && scene.power.getAbilityCooldown("quick_reposition") == 20,
                    "Reposition did not dash back at the owner or set its cooldown");
            scene.release();
            helper.runAfterDelay(4, () -> {
                scene.check(between.getHealth() < before, "Reposition lost the donor's contact damage");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 40)
    public static void flyMovesWithoutDamage(GameTestHelper helper) {
        Scene scene = new Scene(helper, 44);
        Cow ahead = scene.cow(0, 0, 3);
        float health = ahead.getHealth();
        float stamina = scene.power.getStamina();
        scene.press(Move.FLY);
        scene.near(1.8, scene.stand.getDeltaMovement().length(), "Fly initial speed");
        scene.release();
        helper.runAfterDelay(4, () -> {
            scene.check(scene.stand.getZ() - scene.origin.z > 4, "Fly did not travel with the actual world ticks");
            scene.near(health, ahead.getHealth(), "Fly must not deal contact damage");
            scene.near(stamina - 5, scene.power.getStamina(), "Fly one-time stamina");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 40)
    public static void stingerUsesItsDonorDamageAndTwoSpeeds(GameTestHelper helper) {
        Scene scene = new Scene(helper, 56);
        Cow ahead = scene.cow(0, 0, 4);
        scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, ahead.position().add(0, 0.9, 0));
        float stamina = scene.power.getStamina();
        float health = ahead.getHealth();
        scene.press(Move.GRAY_STINGER);
        scene.near(3.2, scene.stand.getDeltaMovement().length(), "Stinger launch speed");
        scene.release();
        helper.runAfterDelay(1, () -> {
            scene.near(1.8, scene.stand.getDeltaMovement().length(), "Stinger shared dash step speed");
        });
        helper.runAfterDelay(3, () -> {
            scene.near(health - 8, ahead.getHealth(), "Stinger hits once for eight");
            scene.near(stamina - 50, scene.power.getStamina(), "Stinger one-time stamina");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 70)
    public static void pursuitWaitsForReleaseThenKeepsPerforming(GameTestHelper helper) {
        Scene scene = new Scene(helper, 68);
        Cow target = scene.cow(0, 0, 4);
        scene.aim(target);
        float health = target.getHealth();
        float stamina = scene.power.getStamina();
        EntityActionInstance action = scene.press(Move.NEEDLE_PURSUIT);
        scene.near(1.8, scene.stand.getDeltaMovement().length(), "Pursuit starts homing on press");
        helper.runAfterDelay(25, () -> {
            scene.check(action.getPhase() == ActionPhase.BUTTON_CHARGE && action.getPhaseTick() >= 20
                    && scene.power.getAbilityCooldown("needle_pursuit") == 0,
                    "Pursuit auto-fired or spent its cooldown before release");
            scene.near(health, target.getHealth(), "Homing alone must not hit");
            scene.near(stamina, scene.power.getStamina(), "Homing alone must not spend stamina");
            scene.release();
            scene.check(action.getPhase() == ActionPhase.PERFORM
                    && scene.power.getAbilityCooldown("needle_pursuit") == 80,
                    "An eligible release did not enter perform with donor cooldown");
            helper.runAfterDelay(3, () -> {
                scene.check(target.getHealth() < health, "Released pursuit never hit its kept target");
                scene.near(stamina - 30, scene.power.getStamina(), "Pursuit one-time stamina");
            });
            helper.runAfterDelay(14, () -> {
                scene.check(scene.stand.getCurStandAction() == action && action.getPhase() == ActionPhase.PERFORM,
                        "Pursuit incorrectly ended after a fixed perform length");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 75)
    public static void shortAndStunnedPursuitReleaseDoNotFire(GameTestHelper helper) {
        Scene scene = new Scene(helper, 80);
        Cow target = scene.cow(0, 0, 4);
        scene.aim(target);
        float stamina = scene.power.getStamina();
        float health = target.getHealth();
        EntityActionInstance shortHold = scene.press(Move.NEEDLE_PURSUIT);
        helper.runAfterDelay(3, () -> {
            scene.release();
            scene.check(shortHold.isOver() && scene.power.getAbilityCooldown("needle_pursuit") == 0,
                    "A short pursuit release fired or took cooldown");
        });
        helper.runAfterDelay(6, () -> {
            EntityActionInstance fullHold = scene.press(Move.NEEDLE_PURSUIT);
            helper.runAfterDelay(23, () -> {
                scene.check(fullHold.getPhase() == ActionPhase.BUTTON_CHARGE && fullHold.getPhaseTick() >= 20,
                        "The forced-end control never reached an armed hold");
                scene.check(scene.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 20)),
                        "Could not stun the actual Stand");
                scene.release();
                scene.check(fullHold.isOver() && scene.power.getAbilityCooldown("needle_pursuit") == 0,
                        "A stunned pursuit release fired or took cooldown");
                helper.runAfterDelay(3, () -> {
                    scene.near(stamina, scene.power.getStamina(), "Neither failed hold spends stamina");
                    scene.near(health, target.getHealth(), "Neither failed hold damages the target");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 80)
    public static void dodgeAndSwarmHoldAndKeepDonorMotion(GameTestHelper helper) {
        Scene scene = new Scene(helper, 92);
        float stamina = scene.power.getStamina();
        EntityActionInstance dodge = scene.press(Move.DODGE);
        scene.near(0.0375, scene.stand.getDeltaMovement().length(), "Dodge launch magnitude");
        scene.check(scene.stand.getDeltaMovement().y > 0, "Dodge lost its upward bias");
        helper.runAfterDelay(12, () -> {
            scene.check(dodge.getPhase() == ActionPhase.PERFORM, "Dodge's old constructor length became a hold limit");
            scene.near(stamina - 35, scene.power.getStamina(), "Dodge one-time stamina");
            scene.release();
            scene.check(dodge.isOver(), "Dodge did not end on release");
        });
        helper.runAfterDelay(14, () -> {
            scene.check(scene.stand.getCurStandAction() == null, "Released Dodge did not clear on the next world tick");
            scene.stand.setPos(scene.origin.add(1, 0, 0));
            scene.stand.setDeltaMovement(Vec3.ZERO);
            Cow nearby = scene.cow(0, 0, 2);
            float health = nearby.getHealth();
            float swarmStamina = scene.power.getStamina();
            EntityActionInstance swarm = scene.press(Move.GRAY_SWARM);
            scene.near(health - 4, nearby.getHealth(), "Swarm's actual initial generic damage");
            scene.near(0.3, scene.stand.getDeltaMovement().length(), "Swarm initial tangent speed");
            helper.runAfterDelay(2, () -> {
                scene.near(swarmStamina - 40, scene.power.getStamina(), "Swarm one-time stamina");
                scene.release();
                scene.check(swarm.isOver(), "Swarm hold did not end on release");
                helper.runAfterDelay(3, () -> {
                    scene.near(2.5 * Math.PI * 2 / 30, scene.stand.getDeltaMovement().length(),
                            "Swarm's body orbit did not continue after release");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 40)
    public static void dodgeHoldGivesWayToDashAtOnce(GameTestHelper helper) {
        holdGivesWay(helper, Move.DODGE, Move.DASH, 104);
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 40)
    public static void swarmHoldGivesWayToFlyAtOnce(GameTestHelper helper) {
        holdGivesWay(helper, Move.GRAY_SWARM, Move.FLY, 116);
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 40)
    public static void stingerRefusesItsOwnPressWhileItRuns(GameTestHelper helper) {
        refusesOwnPress(helper, Move.GRAY_STINGER, 128);
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 40)
    public static void flyRefusesItsOwnPressWhileItRuns(GameTestHelper helper) {
        refusesOwnPress(helper, Move.FLY, 140);
    }

    @GameTest(template = "empty", templateNamespace = TowerOfGrayAddon.MOD_ID, timeoutTicks = 40)
    public static void pursuitTurnsTheBeetleToItsTarget(GameTestHelper helper) {
        Scene scene = new Scene(helper, 152);
        Cow target = scene.cow(4, 0, 0);
        scene.aim(target);
        scene.check(Math.abs(Mth.wrapDegrees(scene.stand.getYRot())) < 1, "Setup: the beetle must start facing south");
        scene.press(Move.NEEDLE_PURSUIT);
        helper.runAfterDelay(1, () -> {
            Vec3 toTarget = target.getEyePosition().subtract(scene.stand.getEyePosition());
            float expected = Mth.wrapDegrees((float) (Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0F);
            float yaw = scene.stand.getYRot();
            scene.check(Math.abs(Mth.wrapDegrees(yaw - expected)) < 10,
                    "Needle Pursuit did not turn the beetle to its target: yaw " + yaw + ", expected " + expected);
            helper.succeed();
        });
    }

    // 1.16 holdType(): a move pressed under a held Dodge or Gray Swarm replaces it on that tick.
    private static void holdGivesWay(GameTestHelper helper, Move hold, Move next, int height) {
        Scene scene = new Scene(helper, height);
        float stamina = scene.power.getStamina();
        EntityActionInstance held = scene.press(hold);
        scene.check(!scene.power.getAbility(next.id).isHeldBackByHeldAction(scene.user),
                "The HUD draws " + next.id + " unavailable under the held " + hold.id);
        helper.runAfterDelay(2, () -> {
            scene.check(!held.isOver() && held.getPhase() == ActionPhase.PERFORM,
                    hold.id + " was not held when " + next.id + " was pressed");
            EntityActionInstance replacement = scene.press(next, OTHER_KEY);
            scene.check(held.isOver() && replacement != held && scene.stand.getCurStandAction() == replacement,
                    next.id + " did not replace the held " + hold.id + " at once");
            helper.runAfterDelay(1, () -> {
                scene.near(stamina - hold.stamina - next.stamina, scene.power.getStamina(),
                        "Stamina of the held " + hold.id + " and the move that replaced it");
                helper.succeed();
            });
        });
    }

    // 1.16 canClickDuringTask: no donor move is chainable, so its own press is refused, not queued behind it.
    private static void refusesOwnPress(GameTestHelper helper, Move move, int height) {
        Scene scene = new Scene(helper, height);
        float stamina = scene.power.getStamina();
        EntityActionInstance first = scene.press(move);
        scene.check(!scene.gateAdmits(move), "A second press of " + move.id + " was admitted as it started");
        helper.runAfterDelay(3, () -> {
            scene.check(scene.stand.getCurStandAction() == first && !first.isOver(), move.id + " stopped too early");
            scene.check(!scene.gateAdmits(move), "A second press of " + move.id + " was admitted while it ran");
            scene.near(stamina - move.stamina, scene.power.getStamina(), move.id + " stamina while it ran");
            // The beetle may be out of the loaded chunks by now: end the move as its length would, so that a queued press would start.
            first.forceStop();
        });
        helper.runAfterDelay(6, () -> {
            scene.check(scene.stand.getCurStandAction() == null,
                    "A refused second press of " + move.id + " ran after the first ended: " + scene.stand.getCurStandAction());
            // Stamina only comes back once the move is over; a second run would have spent it again.
            scene.check(scene.power.getStamina() >= stamina - move.stamina - 0.005F,
                    "The refused second press of " + move.id + " spent stamina: " + scene.power.getStamina());
            helper.succeed();
        });
    }

    private static final class Scene implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final Vec3 origin;
        final Player user;
        final StandPower power;
        final TowerOfGrayEntity stand;
        final List<Cow> cows = new ArrayList<>();
        boolean closed;

        Scene(GameTestHelper helper, int height) {
            this.helper = helper;
            level = helper.getLevel();
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(base);
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5, base.getY() + height, chunk.getMiddleBlockZ() + 0.5);
            user = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "TowerTest")) {
                @Override public boolean isCreative() { return false; }
                @Override public boolean isSpectator() { return false; }
            };
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            helper.testInfo.addListener(this);
            check(level.addFreshEntity(user), "Could not add survival owner");
            power = PowerClass.STAND.attachGet(user);
            var type = TowerOfGrayAddon.STAND.get();
            check(StandPowerTransitions.insert(power, new StandInstance(type)).applied(), "Could not grant registered Stand");
            power.setResolveLevel(4);
            for (Move move : Move.values()) {
                if (move.resolve > 0) {
                    UNLOCKS.forceUnlock(power, move.id);
                }
            }
            check(type.summon(user, power), "Could not summon registered Tower of Gray");
            stand = (TowerOfGrayEntity) power.getSummonedStandEntity();
            check(stand != null && stand.getUser() == user, "Wrong summoned body or owner");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            power.setStamina(power.getMaxStamina());
        }

        Cow cow(double x, double y, double z) {
            Cow cow = EntityType.COW.create(level);
            check(cow != null, "Could not create target");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(origin.x + x, origin.y + y, origin.z + z, 0, 0);
            cows.add(cow);
            check(level.addFreshEntity(cow), "Could not add target");
            return cow;
        }

        void aim(Cow cow) {
            ActionTarget target = new ActionTarget(cow);
            LivingComponentAction.getComponent(user).entityAim.setTarget(target);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
            user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
        }

        EntityActionInstance press(Move move) {
            return press(move, KEY);
        }

        EntityActionInstance press(Move move, short key) {
            var ability = power.getAbility(move.id);
            var method = move.hold() ? InputMethod.HOLD : InputMethod.CLICK;
            var selected = resolve(move);
            check(selected != null && AbilityInput.withConditionCheck(selected, user, method),
                    "Registered input was refused: " + move.id);
            BufferingState state = BufferingState.clickOnly();
            AbilityInput.keyPress(key, selected.ability, user, null, method, 0, state, ability.getAbilityId());
            EntityActionInstance action = stand.getCurStandAction();
            check(state.isActionSuccess && action != null && action.ability == selected.ability,
                    "Registered input did not install its real action: " + move.id
                            + ", current=" + (action == null ? "none" : action.ability + " " + action.getPhase()
                            + "/" + action.getPhaseTick()));
            return action;
        }

        // The server's input gate alone, for a press that must be refused before anything is installed or queued.
        boolean gateAdmits(Move move) {
            var selected = resolve(move);
            return selected != null
                    && AbilityInput.withConditionCheck(selected, user, move.hold() ? InputMethod.HOLD : InputMethod.CLICK);
        }

        // The power resolves its keys once a tick (Power.updateAvailableMoves); a press made in the tick of another needs a fresh one.
        private AbilityConditionCheck resolve(Move move) {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(move.id));
        }

        void release() {
            AbilityInput.keyRelease(KEY, user);
            AbilityInput.keyRelease(OTHER_KEY, user);
        }

        void near(double expected, double actual, String detail) {
            check(Math.abs(expected - actual) <= 0.005, detail + ": expected " + expected + ", got " + actual);
        }

        void check(boolean ok, String detail) {
            helper.assertTrue(ok, detail);
        }

        private void close() {
            if (closed) return;
            closed = true;
            release();
            if (power != null && power.isSummoned()) {
                TowerOfGrayAddon.STAND.get().forceUnsummon(user, power);
            }
            cows.forEach(Cow::discard);
            user.discard();
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) { close(); }
    }
}
