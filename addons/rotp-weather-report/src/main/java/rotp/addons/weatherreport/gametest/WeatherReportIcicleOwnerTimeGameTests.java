package rotp.addons.weatherreport.gametest;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.entity.WeatherReportIcicleEntity;
import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportIcicleOwnerTimeGameTests {
    private static final String SLOWED_USER = "r772_wr_icicle_slowed_user";
    private static final String FAST_USER = "r772_wr_icicle_fast_user";
    private static final String FAST_PROJECTILE = "r772_wr_icicle_fast_projectile";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private WeatherReportIcicleOwnerTimeGameTests() {}

    @GameTest(template = "empty", batch = "wr_icicle_owner_time", timeoutTicks = 40)
    public static void icicleUsesItsStandUsersClock(GameTestHelper helper) {
        registerRates();
        ServerLevel level = helper.getLevel();
        BlockPos template = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(template);
        Vec3 at = Vec3.atCenterOf(new BlockPos(chunk.getMinBlockX() + 7, template.getY() + 8,
                chunk.getMinBlockZ() + 4));
        Cow user = EntityType.COW.create(level);
        helper.assertTrue(user != null, "Could not create the Icicle user");
        user.setNoAi(true);
        user.setNoGravity(true);
        user.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(user), "Could not add the Icicle user");
        StandPower power = PowerClass.STAND.attachGet(user);
        var type = WeatherReportAddon.WEATHER_REPORT.get();
        try {
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED && type.summon(user, power),
                    "Could not grant and summon Weather Report");
            helper.assertTrue(power.getSummonedStandEntity() instanceof WeatherReportStandEntity,
                    "The summoned Weather Report is missing");
            WeatherReportStandEntity stand = (WeatherReportStandEntity) power.getSummonedStandEntity();
            stand.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            stand.yBodyRot = 0.0F;
            helper.assertTrue(stand.getUser() == user, "The Icicle's Stand has no matching user");

            checkRow(helper, stand, "rate 1", 1.0D, 1);
            user.addTag(SLOWED_USER);
            checkRow(helper, stand, "rate 1/2", 0.5D, 1);
            user.removeTag(SLOWED_USER);
            user.addTag(FAST_USER);
            checkRow(helper, stand, "rate 4, one entity tick", 4.0D, 1);
            checkRow(helper, stand, "rate 4, four entity ticks", 4.0D, 4);
        }
        finally {
            if (power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
        LogUtils.getLogger().info("R772_WR_OWNER_TIME_PASS icicle_owner_time normal=19/21 half=38/41 fast=5/6 mode_b=5/6");
        helper.succeed();
    }

    private static void registerRates() {
        if (REGISTERED.compareAndSet(false, true)) {
            EntityTimeRates.register(ResourceLocation.fromNamespaceAndPath("rotp_wr_reach", "icicle_owner_time"),
                    (entity, channel) -> {
                        if (channel == EntityTimeRates.Channel.STAND_ACTIONS && entity instanceof Cow) {
                            if (entity.getTags().contains(SLOWED_USER)) {
                                return 0.5F;
                            }
                            if (entity.getTags().contains(FAST_USER)) {
                                return 4.0F;
                            }
                        }
                        if (channel == EntityTimeRates.Channel.ENTITY_TICK && entity instanceof WeatherReportIcicleEntity
                                && entity.getTags().contains(FAST_PROJECTILE)) {
                            return 4.0F;
                        }
                        return EntityTimeRates.NO_RATE;
                    });
        }
    }

    private static void checkRow(GameTestHelper helper, WeatherReportStandEntity stand, String row,
            double agePerServerTick, int callsPerServerTick) {
        checkFlight(helper, stand, row, agePerServerTick, callsPerServerTick, false);
        checkFlight(helper, stand, row, agePerServerTick, callsPerServerTick, true);
    }

    private static void checkFlight(GameTestHelper helper, WeatherReportStandEntity stand, String row,
            double agePerServerTick, int callsPerServerTick, boolean lifespanProbe) {
        ServerLevel level = helper.getLevel();
        WeatherReportIcicleEntity icicle = new WeatherReportIcicleEntity(stand, level);
        icicle.setShootingPosOf(stand);
        Vec3 origin = icicle.position();
        if (callsPerServerTick > 1) {
            icicle.addTag(FAST_PROJECTILE);
        }
        helper.assertTrue(level.addFreshEntity(icicle), "Could not add the Icicle: " + row);
        stand.activateIcicle(icicle);
        try {
            int lastTick = lifespanProbe ? (int) (20.0D / agePerServerTick) + 1
                    : (int) Math.ceil(19.0D / agePerServerTick);
            for (int serverTick = 1; serverTick <= lastTick; serverTick++) {
                level.tickNonPassenger(icicle);
                double age = serverTick * agePerServerTick;
                String at = row + " at owner age " + age + (lifespanProbe ? " (lifespan probe)" : "");
                if (serverTick == lastTick) {
                    helper.assertTrue(icicle.isRemoved() && !stand.isIcicleArmHidden(),
                            "Expected the Icicle to be removed and clear the active arm at " + at);
                    break;
                }
                helper.assertTrue(!icicle.isRemoved() && stand.getActiveIcicle() == icicle,
                        "The active Icicle disappeared early at " + at);
                helper.assertTrue(icicle.tickCount == serverTick * callsPerServerTick,
                        "The server did not execute the expected entity ticks at " + at + ": " + icicle.tickCount);
                double expected = 0.75D * Math.min(6.0D, age) - 0.5D * Math.max(0.0D, age - 10.0D);
                if (lifespanProbe && age > 8.0D) {
                    expected = 4.5D - 0.0625D * (age - 8.0D);
                }
                double actual = origin.distanceTo(icicle.position());
                helper.assertTrue(Math.abs(actual - expected) < 1.0E-6D,
                        "Wrong Icicle tip distance at " + at + ": expected " + expected + ", got " + actual);
                if (lifespanProbe && age == 8.0D) {
                    // Keep a positive length beyond age 20 so the lifespan, not reaching the origin, ends this shot.
                    icicle.setSpeedFactor(0.125D);
                    icicle.beginRetracting();
                }
            }
        }
        finally {
            if (!icicle.isRemoved()) {
                icicle.discard();
            }
        }
    }
}
