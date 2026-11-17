package rotp.addons.weatherreport.block;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandDamageContext;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.WeatherReportDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BloodSpikesBlock extends BloodPuddleBlock {
    private static final Vec3 STUCK_MULTIPLIER =
            new Vec3(0.8D, 0.75D, 0.8D);
    private static final double USER_RANGE = 64.0D;

    public BloodSpikesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos position,
            Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        living.makeStuckInBlock(state, STUCK_MULTIPLIER);
        if (!level.isClientSide()) {
            // the spikes keep no owner: the Weather Report user nearest to them stands for the Stand's damage share
            try (var running = StandDamageContext.enter(nearestUser(level, position))) {
                living.hurt(
                        DamageUtil.make(
                                level,
                                WeatherReportDamageTypes.SPIKES),
                        (state.getValue(STAGE) + 1) / 2.0F);
            }
        }
    }

    @Nullable
    private static StandPower nearestUser(Level level, BlockPos position) {
        Player user = level.getNearestPlayer(
                position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D, USER_RANGE,
                entity -> {
                    StandPower power = entity instanceof LivingEntity living ? StandPower.get(living) : null;
                    return power != null && power.getPowerType() == WeatherReportAddon.WEATHER_REPORT.get();
                });
        return user != null ? StandPower.get(user) : null;
    }
}
