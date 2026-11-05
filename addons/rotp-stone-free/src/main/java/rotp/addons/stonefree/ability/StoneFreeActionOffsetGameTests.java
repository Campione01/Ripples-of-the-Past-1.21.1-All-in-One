package rotp.addons.stonefree.ability;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.impl.stands._entitybase.StandMeleeReach;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

import rotp.addons.stonefree.StoneFreeAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeActionOffsetGameTests {
    private static final double EPS = 1.0E-4D;

    private StoneFreeActionOffsetGameTests() {}

    /**
     * 1.16 InitStands.java:166 and :172: stone_free_extended_punch
     * standOffsetFront() and stone_free_grapple_entity
     * standOffsetFromUser(-0.5, 0.25), full mode only, at the default y;
     * StoneFreeStringPunch.getOffsetFromUser noYOffset(0, 0.5) in either mode,
     * the Stand moving by itself from the end of its windup. All are (left,
     * up, forward) from the user's feet, turned by the view yaw.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void stoneFreeActionsPutTheStandWhereTheDonorDid(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "sf-action-offsets")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = StoneFreeAddon.STONE_FREE.get();
        StandPower power = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the Stone Free user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Stone Free");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon Stone Free");
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "setup: Stone Free is not a full Stand following its user");

            start(helper, user, power, stand, StoneFreeAddon.EXTENDED_PUNCH_NAME);
            assertTurned(helper, user, stand, 0.0D, 0.2D, 0.5D, "the extended punch");
            stop(user, stand);

            start(helper, user, power, stand, StoneFreeAddon.GRAPPLE_ENTITY_NAME);
            assertTurned(helper, user, stand, -0.5D, 0.2D, 0.25D, "the Stand's grapple");
            stop(user, stand);

            EntityActionInstance punch = start(helper, user, power, stand,
                    StoneFreeAddon.STRING_PUNCH_NAME);
            assertTurned(helper, user, stand, 0.0D, 0.2D, 0.5D, "the string punch windup");
            punch.setPhase(ActionPhase.WINDUP, 8);
            helper.assertTrue(!stand.isFollowingUser(),
                    "1.16 let the string punch's Stand move by itself in the last two windup ticks");
            punch.setPhase(ActionPhase.RECOVERY, 0);
            helper.assertTrue(!stand.isFollowingUser(),
                    "1.16 let the string punch's Stand stay where it punched through the recovery");
            stop(user, stand);

            // each action replaces the one before: clearing it would unsummon the arms
            stand.setArmsOnlyMode();
            start(helper, user, power, stand, StoneFreeAddon.GRAPPLE_ENTITY_NAME);
            turn(user, 0.0F, 50.0F);
            assertAt(helper, user, stand, 0.0D, 0.0D, 0.15D,
                    "the arms-only Stand's grapple (the arms-only default), view yaw 0");
            start(helper, user, power, stand, StoneFreeAddon.STRING_PUNCH_NAME);
            assertTurned(helper, user, stand, 0.0D, 0.0D, 0.5D, "the arms-only string punch windup");
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntity.taskOffset: stone_free_attack_binding (StoneFreeStringBind.standKeepsTarget true) kept what
     * the Stand aimed at as it was pressed, and while it ran the default offset turned by the yaw from the user's
     * eyes to that target's eyes, wherever the user looked.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void stringBindTurnsTheOffsetToItsTarget(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> stringBindTurnsTheOffsetToItsTargetBody(h));
    }

    private static void stringBindTurnsTheOffsetToItsTargetBody(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "sf-bind-target")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = StoneFreeAddon.STONE_FREE.get();
        StandPower power = null;
        Cow cow = EntityType.COW.create(helper.getLevel());
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the Stone Free user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Stone Free");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon Stone Free");
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);
            helper.assertTrue(cow != null, "setup: could not make a cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(pos.x + 3.0D, pos.y, pos.z + 2.0D, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "setup: could not add the cow");
            // what the Stand's aim met, as its user's client sent it
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));

            EntityActionInstance bind = start(helper, user, power, stand, StoneFreeAddon.STRING_BIND_NAME);
            helper.assertTrue(stand.isFollowingUser()
                            && bind.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "1.16 StoneFreeStringBind.standKeepsTarget kept the cow it was pressed at; the task holds "
                            + bind.getTaskTarget(helper.getLevel()));
            // the user looks west, away from the cow east of it
            turn(user, 90.0F, 90.0F);
            Vec3 toCow = cow.getEyePosition().subtract(user.getEyePosition());
            Vec3 expected = new Vec3(-0.75D, 0.2D, -0.75D)
                    .yRot(-MathUtil.yRotDegFromVec(toCow) * MathUtil.DEG_TO_RAD);
            assertAt(helper, user, stand, expected.x, expected.y, expected.z,
                    "the Stand's string bind at a cow east of its user who looks west (the default offset turned"
                            + " to the cow)");
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    /**
     * 1.16 StoneFreeStringPunch.standTickWindup: with 2 windup ticks left the
     * Stand's velocity is set once to the vector toward its task target's eyes
     * (clamped to the distance less the Stand's width, at most 1.5), or the
     * look vector times 1.5 without a target; with 1 tick left it is zero.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void stringPunchSlidesToItsTargetAndStops(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> stringPunchSlidesToItsTargetAndStopsBody(h));
    }

    private static void stringPunchSlidesToItsTargetAndStopsBody(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "sf-string-slide")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = StoneFreeAddon.STONE_FREE.get();
        StandPower power = null;
        Cow near = EntityType.COW.create(helper.getLevel());
        Cow far = EntityType.COW.create(helper.getLevel());
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the Stone Free user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Stone Free");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon Stone Free");
            StandEntity stand = power.getSummonedStandEntity();
            stand.setNoGravity(true);
            stand.updatePosition(user);
            helper.assertTrue(near != null && far != null, "setup: could not make the cows");
            near.setNoAi(true);
            near.setNoGravity(true);
            Vec3 eyes = stand.getEyePosition();
            near.moveTo(eyes.x, eyes.y - near.getEyeHeight(),
                    eyes.z + stand.getBbWidth() + 0.6D, 0.0F, 0.0F);
            far.setNoAi(true);
            far.setNoGravity(true);
            // The plot's chunk is the one that ticks entities, and a cow across its border cannot be held as a task
            // target: the far cow stands 6 blocks off on the side that stays inside it.
            Vec3 farPos = null;
            for (Vec3 side : new Vec3[] {new Vec3(0.0D, 0.0D, 6.0D), new Vec3(0.0D, 0.0D, -6.0D),
                    new Vec3(6.0D, 0.0D, 0.0D), new Vec3(-6.0D, 0.0D, 0.0D)}) {
                Vec3 candidate = new Vec3(eyes.x, eyes.y - far.getEyeHeight(), eyes.z).add(side);
                if (helper.getLevel().isPositionEntityTicking(BlockPos.containing(candidate))) {
                    farPos = candidate;
                    break;
                }
            }
            helper.assertTrue(farPos != null, "setup: no side of the Stand is 6 blocks of entity-ticking ground");
            far.moveTo(farPos.x, farPos.y, farPos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(near)
                            && helper.getLevel().addFreshEntity(far),
                    "setup: could not add the cows");

            EntityActionInstance punch = start(helper, user, power, stand,
                    StoneFreeAddon.STRING_PUNCH_NAME);
            punch.setTaskTarget(new ActionTarget(near));
            helper.assertTrue(punch.getTaskTarget(helper.getLevel()).getEntity() == near,
                    "setup: the string punch holds " + punch.getTaskTarget(helper.getLevel())
                            + ", not the near cow");

            stand.setDeltaMovement(Vec3.ZERO);
            slideTick(punch, 7);
            assertSlide(helper, stand, Vec3.ZERO, "3 windup ticks left");

            Vec3 toNear = near.getEyePosition().subtract(stand.getEyePosition());
            double nearDistance = Math.max(toNear.length() - stand.getBbWidth(), 0.0D);
            helper.assertTrue(nearDistance > 0.0D && nearDistance < 1.5D,
                    "setup: the near cow is " + nearDistance + " blocks short of the Stand, not 0 to 1.5");
            slideTick(punch, 8);
            assertSlide(helper, stand, toNear.normalize().scale(nearDistance),
                    "2 windup ticks left toward a cow 0.6 blocks short of the Stand's face (its distance less the Stand's width)");
            slideTick(punch, 9);
            assertSlide(helper, stand, Vec3.ZERO, "1 windup tick left");

            punch.setTaskTarget(new ActionTarget(far));
            helper.assertTrue(punch.getTaskTarget(helper.getLevel()).getEntity() == far,
                    "setup: the string punch holds " + punch.getTaskTarget(helper.getLevel())
                            + ", not the far cow");
            Vec3 toFar = far.getEyePosition().subtract(stand.getEyePosition());
            slideTick(punch, 8);
            assertSlide(helper, stand, toFar.normalize().scale(1.5D),
                    "2 windup ticks left toward a cow 6 blocks away (at most 1.5)");
            slideTick(punch, 9);
            assertSlide(helper, stand, Vec3.ZERO, "1 windup tick left after the far slide");

            punch.setTaskTarget(null);
            helper.assertTrue(punch.getTaskTarget(helper.getLevel()).getType()
                            == ActionTarget.TargetType.EMPTY,
                    "setup: the string punch still holds " + punch.getTaskTarget(helper.getLevel()));
            slideTick(punch, 8);
            assertSlide(helper, stand, stand.getLookAngle().scale(1.5D),
                    "2 windup ticks left without a target (the look times 1.5)");
            slideTick(punch, 9);
            assertSlide(helper, stand, Vec3.ZERO, "1 windup tick left without a target");
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            near.discard();
            far.discard();
            user.discard();
        }
    }

    /**
     * Owner T2: pressed at a mob on its user's look past what the windup slide reaches (6.2 blocks; base reach 5,
     * reach out to 6.25), the string punch flies out in front of it as the core heavy punch does, follows its user
     * through the strike instead of moving by itself, and strikes weaker past the base reach.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void stringPunchFliesOutToAMobPastItsSlideAndStrikesWeaker(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> stringPunchFliesOutBody(h));
    }

    private static void stringPunchFliesOutBody(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "sf-string-reach")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = StoneFreeAddon.STONE_FREE.get();
        StandPower power = null;
        Cow cow = EntityType.COW.create(helper.getLevel());
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the Stone Free user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Stone Free");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon Stone Free");
            StandEntity stand = power.getSummonedStandEntity();
            stand.setNoGravity(true);
            helper.assertTrue(cow != null, "setup: could not make a cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            Vec3 cowPos = null;
            float yaw = 0.0F;
            for (float side : new float[] {0.0F, 180.0F, -90.0F, 90.0F}) {
                Vec3 look = Vec3.directionFromRotation(0.0F, side);
                Vec3 candidate = pos.add(look.scale(6.2D));
                if (helper.getLevel().isPositionEntityTicking(BlockPos.containing(candidate))) {
                    cowPos = candidate;
                    yaw = side;
                    break;
                }
            }
            helper.assertTrue(cowPos != null, "setup: no side of the user is 6.2 blocks of entity-ticking ground");
            turn(user, yaw, yaw);
            user.setXRot(0.0F);
            stand.updatePosition(user);
            cow.moveTo(cowPos.x, cowPos.y, cowPos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "setup: could not add the cow");
            // what the Stand's aim met, as its user's client sent it
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));

            EntityActionInstance punch = start(helper, user, power, stand,
                    StoneFreeAddon.STRING_PUNCH_NAME);
            helper.assertTrue(punch instanceof StoneFreeStringPunchAbility.StringPunchAction string
                            && string.fliesOut(),
                    "The string punch pressed at a cow 6.2 blocks away did not fly out; it holds "
                            + punch.getTaskTarget(helper.getLevel()));
            double forward = stand.offsetFromUser.getRelativeOffset().z;
            helper.assertTrue(forward > 4.0D && forward < 5.0D,
                    "The string punch did not put Stone Free in front of the cow 6.2 blocks away (4.75 forward):"
                            + " offset " + stand.offsetFromUser.getRelativeOffset());
            punch.setPhase(ActionPhase.WINDUP, 8);
            helper.assertTrue(stand.isFollowingUser(),
                    "A string punch that flew out moved by itself in its last windup ticks");
            punch.setPhase(ActionPhase.PERFORM, 0);
            float factor = StandMeleeReach.strikeFactor(stand, cow);
            helper.assertTrue(stand.isFollowingUser() && StandMeleeReach.reachable(stand, cow)
                            && factor > 0.5F && factor < 0.6F,
                    "A string punch that flew out to a cow 5.75 blocks from its user's eyes did not strike it"
                            + " at about 0.55 of its strength (base reach 5, margin 1.25): following "
                            + stand.isFollowingUser() + ", reachable " + StandMeleeReach.reachable(stand, cow)
                            + ", factor " + factor);
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    private static void slideTick(EntityActionInstance punch, int windupTick) {
        punch.setPhase(ActionPhase.WINDUP, windupTick);
        punch.actionTick();
    }

    private static void assertSlide(GameTestHelper helper, StandEntity stand, Vec3 expected,
            String when) {
        Vec3 actual = stand.getDeltaMovement();
        helper.assertTrue(actual.distanceTo(expected) < EPS,
                "1.16 StoneFreeStringPunch.standTickWindup left the Stand's velocity at " + expected
                        + " with " + when + ", got " + actual);
    }

    private static EntityActionInstance start(GameTestHelper helper, Player user,
            StandPower power, StandEntity stand, String name) {
        helper.assertTrue(power.getAbility(name) instanceof EntityActionType,
                "Stone Free has no action " + name);
        EntityActionType type = (EntityActionType) power.getAbility(name);
        EntityActionInstance action = type.createActionObj();
        type.initActionFromConfig(action, helper.getLevel(), user, stand);
        action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0.0F);
        action.phasesLength.put(ActionPhase.WINDUP, 10.0F);
        action.phasesLength.put(ActionPhase.PERFORM, 5.0F);
        action.phasesLength.put(ActionPhase.RECOVERY, 10.0F);
        action.setStartingPhase();
        LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action,
                "setup: " + name + " did not become the Stand's action");
        return action;
    }

    // a Stand left far from its user is retracted when its action ends
    private static void stop(Player user, StandEntity stand) {
        stand.setPos(user.position());
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
    }

    private static void assertTurned(GameTestHelper helper, Player user, StandEntity stand,
            double left, double y, double forward, String what) {
        // the body is turned away from the view: a body-yaw offset misses
        turn(user, 0.0F, 50.0F);
        assertAt(helper, user, stand, left, y, forward, what + ", view yaw 0, body yaw 50");
        // facing west the forward is -x and the left is -z
        turn(user, 90.0F, 0.0F);
        assertAt(helper, user, stand, -forward, y, left, what + ", view yaw 90, body yaw 0");
    }

    private static void turn(Player user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertAt(GameTestHelper helper, Player user, StandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < EPS
                        && Math.abs(actual.y - dy) < EPS
                        && Math.abs(actual.z - dz) < EPS,
                "1.16 put Stone Free at its user's feet + (" + dx + ", " + dy + ", " + dz
                        + ") during " + when + ", got " + actual + " (offset "
                        + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }
}
