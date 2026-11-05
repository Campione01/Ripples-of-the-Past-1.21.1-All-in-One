package rotp.addons.darkbluemoon.util;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class DbmWater {
    private DbmWater() {}

    /** 1.16 isInWater() || canSwim() (eyes in water). */
    public static boolean isInWater(Entity entity) {
        return entity.isInWater() || entity.isUnderWater();
    }

    /** 1.16 Material.WATER: the water block itself, not a waterlogged block or a bubble column. */
    public static boolean isWater(BlockState state) {
        return state.is(Blocks.WATER);
    }

    /** 1.16 Material.isLiquid: the water and lava blocks. */
    public static boolean isLiquid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock;
    }

    public static boolean hasWaterNearby(Level level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (isWater(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    /** Water blocks in the cube of this radius, counting stops at the limit (no limit for a negative one). */
    public static int countWater(Level level, BlockPos center, int radius, int limit) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (isWater(level.getBlockState(pos))) {
                count++;
                if (limit >= 0 && count >= limit) {
                    return count;
                }
            }
        }
        return count;
    }

    public static SoundEvent randomWaterUseSound(RandomSource random) {
        return switch (random.nextInt(3)) {
            case 0 -> DarkBlueMoonAddon.CHOP_USE_WATER_1.get();
            case 1 -> DarkBlueMoonAddon.CHOP_USE_WATER_2.get();
            default -> DarkBlueMoonAddon.CHOP_USE_WATER_3.get();
        };
    }

    public static SoundEvent randomLandUseSound(RandomSource random) {
        return switch (random.nextInt(3)) {
            case 0 -> DarkBlueMoonAddon.CHOP_USE_LAND_1.get();
            case 1 -> DarkBlueMoonAddon.CHOP_USE_LAND_2.get();
            default -> DarkBlueMoonAddon.CHOP_USE_LAND_3.get();
        };
    }
}
