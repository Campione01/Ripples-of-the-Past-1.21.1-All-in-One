package rotp.addons.wonderofu.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The owner's rule of 2026-11-05 (13.png), against 1.16, whose toggles died with the Stand entity: Calamity goes on through a
 * recall, the next summon takes it over, and it ends with its user's loss of the Stand.
 */
@GameTestHolder("rotp_wou_car")
@PrefixGameTestTemplate(false)
public final class WonderOfURecallGameTests {
    private WonderOfURecallGameTests() {}

    @GameTest(template = "empty", batch = "wou_recall", timeoutTicks = 40)
    public static void calamityGoesOnThroughARecall(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "wou-recall")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the Wonder of U user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WonderOfUAddon.WONDER_OF_YOU.get().summon(user, power),
                    "setup: could not summon Wonder of U");
            WonderOfUStandEntity first = (WonderOfUStandEntity) power.getSummonedStandEntity();
            first.setPassiveEnabled(true);
            first.setCarEnabled(true);

            WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(user, power);
            helper.assertTrue(!power.isSummoned() && first.isRemoved(), "setup: the Stand was not recalled");
            WonderOfUStandEntity carried = WonderOfUStandEntity.current(user);
            helper.assertTrue(carried == first && carried.isPassiveEnabled() && carried.isCarEnabled(),
                    "Calamity's toggles ended with the recall");

            user.setHealth(user.getMaxHealth());
            user.hurt(helper.getLevel().damageSources().generic(), 10.0F);
            float taken = user.getMaxHealth() - user.getHealth();
            helper.assertTrue(taken > 0.0F && taken < 10.0F,
                    "the passive must still lessen a hit with the Stand recalled: took " + taken + " of 10");

            helper.assertTrue(WonderOfUAddon.WONDER_OF_YOU.get().summon(user, power), "setup: the next summon was refused");
            WonderOfUStandEntity next = (WonderOfUStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(next != first && next.isPassiveEnabled() && next.isCarEnabled()
                            && WonderOfUStandEntity.current(user) == next,
                    "the next summon must take the toggles over, so their own keys can end them");

            WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(user, power);
            helper.assertTrue(WonderOfUStandEntity.current(user) == next, "the second recall lost the toggles");
            power.setStand(null);
            helper.assertTrue(WonderOfUStandEntity.current(user) == null && !next.isPassiveEnabled() && !next.isCarEnabled(),
                    "Calamity must end when its user loses the Stand");
        }
        finally {
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
        System.out.println("R772_WOU_RECALL_PASS recall");
    }
}
