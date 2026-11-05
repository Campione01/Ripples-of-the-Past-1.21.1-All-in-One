package rotp.addons.ger.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.state.GerRtzRewindService;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * Return to Zero restores a mob by loading its saved data, which puts a new Stand power on it. A Stand that outlives its
 * user's removal (a King Crimson time erase decoy's, EV2 lane C's ownerless Stand) was left to nobody by that.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerRtzStandLinkGameTests {
    private static final int MARK_CHUNK_RADIUS = 2;
    private static final float STAMINA_AFTER_MARK = 1;

    private GerRtzStandLinkGameTests() {}

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_rtz_stand_link")
    public static void aRestoredMobKeepsItsStandAndLosesOneItsSavedDataDoesNotHave(GameTestHelper helper) {
        new StandLinkSequence(helper).start();
    }

    private static final class StandLinkSequence extends Sequence {
        private Actor owner;
        private LivingEntity keeper;
        private LivingEntity gainer;
        private StandEntity keptStand;
        private StandEntity gainedStand;
        private StandPower keeperPowerAtRewind;

        StandLinkSequence(GameTestHelper helper) {
            super(helper, "rtz/stand-link", 150);
        }

        private EntityStandType starPlatinum() {
            return ModStands.STAR_PLATINUM.get();
        }

        private StandEntity grantAndSummon(LivingEntity user) {
            StandPower power = PowerClass.STAND.attachGet(user);
            scene.premise(StandPowerTransitions.insert(power, new StandInstance(starPlatinum())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "the mob got its Stand");
            scene.premise(starPlatinum().summon(user, power), "the mob summoned its Stand");
            StandEntity stand = power.getSummonedStandEntity();
            scene.premise(stand != null && stand.getUser() == user, "the Stand is the mob's");
            // The decoy's Stand is this way: only its user's removal code takes it away.
            stand.healthLinkedWithUser = false;
            stand.setNoGravity(true);
            stand.noPhysics = true;
            scene.onClose(stand::discard);
            return stand;
        }

        private void rewindOverBothMobs() {
            Vec3 at = scene.origin;
            owner = scene.actor();
            keeper = scene.cow(at.add(2, 0, 0));
            gainer = scene.cow(at.add(-2, 0, 0));
            keptStand = grantAndSummon(keeper);
            StandPower keeperPower = StandPower.get(keeper);
            float markedStamina = keeperPower.getMaxStamina() / 2;
            scene.premise(markedStamina > STAMINA_AFTER_MARK, "the Stand has stamina");
            keeperPower.setStamina(markedStamina);
            StandPower gainerPower = PowerClass.STAND.attachGet(gainer);
            scene.premise(gainerPower.getPowerType() == null, "the other mob has no Stand at the mark");

            scene.premise(GerRtzRewindService.setMark(scene.level, owner.user(), MARK_CHUNK_RADIUS)
                            == GerRtzRewindService.Result.SUCCESS,
                    "the mark is set");
            keeperPower.setStamina(STAMINA_AFTER_MARK);
            gainedStand = grantAndSummon(gainer);
            helper.assertTrue(
                    GerRtzRewindService.rewind(scene.level, owner.user(), owner.user(), owner.power(), 0)
                            == GerRtzRewindService.Result.SUCCESS,
                    "Return to Zero ran");

            keeperPowerAtRewind = StandPower.get(keeper);
            helper.assertTrue(keeperPowerAtRewind != keeperPower,
                    "the reload is what this test is about: the mob has a new power");
            helper.assertTrue(keeperPowerAtRewind.getStamina() == markedStamina,
                    "the mob's own state came back: stamina " + keeperPowerAtRewind.getStamina()
                            + ", at the mark " + markedStamina);
            StandPower restoredGainer = StandPower.get(gainer);
            helper.assertTrue(restoredGainer != null && restoredGainer.getPowerType() == null && !restoredGainer.isSummoned(),
                    "the other mob has no Stand again");
            helper.assertTrue(gainedStand.isRemoved(),
                    "a Stand the restored mob does not have is not left behind");
        }

        @Override
        boolean step() {
            if (owner == null) {
                rewindOverBothMobs();
            }
            helper.assertTrue(!keptStand.isRemoved(), "the mob's own Stand stays");
            helper.assertTrue(keeperPowerAtRewind.getSummonedStandEntity() == keptStand,
                    "the restored power knows the mob's Stand");
            helper.assertTrue(keptStand.getUserPower() == keeperPowerAtRewind,
                    "the Stand follows the restored power");
            helper.assertTrue(StandPower.get(keeper) == keeperPowerAtRewind, "the mob keeps the restored power");
            if (GerRtzRewindService.hasActivePresentation(owner.user())) {
                return false;
            }
            starPlatinum().forceUnsummon(keeper, keeperPowerAtRewind);
            helper.assertTrue(keptStand.isRemoved(), "the mob's own removal path reaches its Stand");
            return true;
        }
    }
}
