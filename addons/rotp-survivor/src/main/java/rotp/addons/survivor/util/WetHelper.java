package rotp.addons.survivor.util;

import rotp.addons.survivor.SurvivorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public final class WetHelper {
    public static final String WET = "SurvivorWet";
    public static final String WET_TIMER = "SurvivorWetTimer";

    private WetHelper() {}

    public static boolean isWet(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(WET);
    }

    public static void setWet(LivingEntity entity) {
        entity.getPersistentData().putBoolean(WET, true);
        entity.getPersistentData().putInt(WET_TIMER, SurvivorConfig.WET_DURATION.get());
    }

    public static void tickWet(LivingEntity entity) {
        if (isWet(entity)) {
            int timer = entity.getPersistentData().getInt(WET_TIMER);
            if (--timer <= 0) {
                entity.getPersistentData().remove(WET);
                entity.getPersistentData().remove(WET_TIMER);
            } else {
                entity.getPersistentData().putInt(WET_TIMER, timer);
            }
        }
    }

    /** Water, a waterlogged block, ice or a snow block in the box grown by 0.1, or rain where the entity stands. */
    public static boolean isInWater(LivingEntity entity) {
        AABB box = entity.getBoundingBox().inflate(0.1);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        Level level = entity.level();

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.WATER)) {
                return true;
            }
            if (state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK)) {
                return true;
            }
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED)) {
                return true;
            }
        }

        return level.isRainingAt(entity.blockPosition());
    }

    public static boolean isInRain(LivingEntity entity) {
        return entity.level().isRainingAt(entity.blockPosition());
    }
}
