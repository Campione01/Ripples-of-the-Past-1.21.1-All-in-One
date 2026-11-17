package rotp.addons.weatherreport.gametest;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.block.BloodPuddleBlock;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandTuning;

/**
 * R772.1 EV-STANDCFG item3: the blood spikes hurt from the block, with no Stand code running and no owner kept, so the
 * nearest Weather Report user stands for the Stand: a damage share of 50 % to mobs halves what a creature takes in them.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportSpikesDamageGameTests {
    private WeatherReportSpikesDamageGameTests() {}

    @GameTest(template = "empty", batch = "wr_spikes_share", timeoutTicks = 40)
    public static void theBloodSpikesTakeTheStandsDamageShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StandTuning tuning = WeatherReportAddon.WEATHER_REPORT.get().getTuning();
        BlockPos spikes = helper.absolutePos(new BlockPos(2, 2, 2));
        FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "wr-spikes-share"));
        user.setPos(spikes.getX() + 6.5D, spikes.getY(), spikes.getZ() + 0.5D);
        user.setNoGravity(true);
        level.addNewPlayer(user);
        Cow cow = EntityType.COW.create(level);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(spikes.getX() + 0.5D, spikes.getY(), spikes.getZ() + 0.5D, 0.0F, 0.0F);
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(WeatherReportAddon.WEATHER_REPORT.get());
            helper.assertTrue(level.addFreshEntity(cow), "Fixture: the cow was not added");
            BlockState state = WeatherReportAddon.BLOOD_SPIKES.get().defaultBlockState().setValue(BloodPuddleBlock.STAGE, 3);
            level.setBlockAndUpdate(spikes, state);
            float whole = spikesDamage(level, state, spikes, cow);
            helper.assertTrue(Math.abs(whole - 2.0F) < 1.0E-4F,
                    "At the default share the spikes of stage 3 deal (3 + 1) / 2 = 2; the cow took " + whole);
            JsonObject half = new JsonObject();
            half.addProperty("damageVsMobs", 50);
            tuning.applyConfig(half);
            float halved = spikesDamage(level, state, spikes, cow);
            helper.assertTrue(Math.abs(halved - 1.0F) < 1.0E-4F,
                    "At 50 % damage to mobs the spikes deal half of 2; the cow took " + halved);
            LogUtils.getLogger().info("R772_WR_SPIKES_PASS spikes_share");
        }
        finally {
            tuning.restoreDefaults();
            level.removeBlock(spikes, false);
            cow.discard();
            level.removePlayerImmediately(user, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    // the cow standing in the spikes for one tick; what it lost
    private static float spikesDamage(ServerLevel level, BlockState state, BlockPos spikes, Cow cow) {
        cow.setHealth(cow.getMaxHealth());
        cow.invulnerableTime = 0;
        float before = cow.getHealth();
        state.entityInside(level, spikes, cow);
        return before - cow.getHealth();
    }
}
