package rotp.addons.cmoon.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The owner's rule of 2026-11-05 (13.png), against 1.16, whose toggles died with the Stand entity: Gravity Shift goes on
 * through a recall, the next summon takes it over, and it ends with its user's loss of the Stand. Gravity Counter is a
 * stance of the Stand's, which ends with its action, so it ends here (EV-REVIEW-RECALL).
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonRecallGameTests {
    private CMoonRecallGameTests() {}

    @GameTest(template = "empty", batch = "cm_recall", timeoutTicks = 60)
    public static void shiftGoesOnThroughARecallAndCounterEnds(GameTestHelper helper) {
        try (GravityTestSupport.Actor actor = new GravityTestSupport.Actor(helper, false, false, false)) {
            CMoonStandEntity first = actor.stand;
            actor.power.setStamina(actor.power.getMaxStamina());
            first.setShiftActive(true);
            first.setCounterActive(true);
            Direction direction = first.getGravityDirection();
            first.tick();

            CMoonAddon.C_MOON.get().forceUnsummon(actor.user, actor.power);
            helper.assertTrue(!actor.power.isSummoned() && first.isRemoved(), "setup: the Stand was not recalled");
            helper.assertTrue(CMoonStandEntity.current(actor.user) == first && first.isShiftActive(),
                    "Shift ended with the recall");
            helper.assertTrue(!first.isCounterActive(), "Gravity Counter went on with no Stand to hold its stance");
            actor.user.removeEffect(CMoonAddon.CM_SHIFT);
            EventHooks.fireEntityTickPost(actor.user);
            helper.assertTrue(actor.user.hasEffect(CMoonAddon.CM_SHIFT),
                    "the user's tick must keep Shift's effect up with the Stand recalled");
            helper.assertTrue(DirectionalGravityApi.getDirection(actor.user) == direction,
                    "the changed gravity ended with the recall: " + DirectionalGravityApi.getDirection(actor.user));

            helper.assertTrue(CMoonAddon.C_MOON.get().summon(actor.user, actor.power), "setup: the next summon was refused");
            CMoonStandEntity next = (CMoonStandEntity) actor.power.getSummonedStandEntity();
            helper.assertTrue(next != first && next.isShiftActive() && !next.isCounterActive()
                            && CMoonStandEntity.current(actor.user) == next
                            && DirectionalGravityApi.getDirection(actor.user) == direction,
                    "the next summon must take Shift over, so its own key can end it, and leave Counter off");

            CMoonAddon.C_MOON.get().forceUnsummon(actor.user, actor.power);
            actor.power.setStand(null);
            EventHooks.fireEntityTickPost(actor.user);
            helper.assertTrue(CMoonStandEntity.current(actor.user) == null && !next.isShiftActive()
                            && DirectionalGravityApi.getDirection(actor.user) == Direction.DOWN,
                    "Shift must end when its user loses the Stand");
        }
        helper.succeed();
    }

    /** The player list saves him right after this event: nothing of it may be left to wake up with. */
    @GameTest(template = "empty", batch = "cm_recall_logout", timeoutTicks = 60)
    public static void aLogoutEndsARecalledShift(GameTestHelper helper) {
        try (GravityTestSupport.Actor actor = new GravityTestSupport.Actor(helper, false, false, false)) {
            CMoonStandEntity stand = actor.stand;
            actor.power.setStamina(actor.power.getMaxStamina());
            stand.setShiftActive(true);
            CMoonAddon.C_MOON.get().forceUnsummon(actor.user, actor.power);
            helper.assertTrue(CMoonStandEntity.current(actor.user) == stand, "setup: Shift did not go on");

            EventHooks.firePlayerLoggedOut(actor.user);
            helper.assertTrue(CMoonStandEntity.current(actor.user) == null && !stand.isShiftActive()
                            && DirectionalGravityApi.getDirection(actor.user) == Direction.DOWN,
                    "Shift outlived its user's logout: " + DirectionalGravityApi.getDirection(actor.user));
        }
        helper.succeed();
    }

    /** A portal between the recall and the next summon: that Stand and its level are not this one's to take over. */
    @GameTest(template = "empty", batch = "cm_recall_level", timeoutTicks = 60)
    public static void theNextSummonInAnotherLevelTakesNothingOver(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "setup: the server has no Nether");
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO).above(8));
        RecordingServerPlayer recorded = new RecordingServerPlayer(helper, "cm-recall-level", at);
        try {
            ServerPlayer user = recorded.player;
            StandPower power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(CMoonAddon.C_MOON.get())).status()
                            == StandPowerTransitions.Status.APPLIED && CMoonAddon.C_MOON.get().summon(user, power),
                    "setup: C-Moon was not summoned");
            CMoonStandEntity first = (CMoonStandEntity) power.getSummonedStandEntity();
            first.setShiftActive(true);
            CMoonAddon.C_MOON.get().forceUnsummon(user, power);
            helper.assertTrue(first.isShiftActive() && first.isRemoved(), "setup: Shift did not go on");

            user.changeDimension(new DimensionTransition(nether, new Vec3(0.5D, 200.0D, 0.5D), Vec3.ZERO, 0.0F, 0.0F,
                    DimensionTransition.DO_NOTHING));
            helper.assertTrue(CMoonAddon.C_MOON.get().summon(user, power), "setup: the next summon was refused");
            CMoonStandEntity next = (CMoonStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(!next.isShiftActive() && DirectionalGravityApi.getDirection(user) == Direction.DOWN,
                    "The next summon took Shift over from another level");
            CMoonAddon.C_MOON.get().forceUnsummon(user, power);
        }
        finally {
            recorded.close();
        }
        helper.succeed();
    }
}
