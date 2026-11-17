package rotp.addons.kingcrimson.state;

import java.util.List;

import javax.annotation.Nullable;

import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where a player's shifted image may stand (owner 2026-11-07): somewhere he could have walked to. The image is walked
 * over the ground in short steps, up a block and down as far as three ("若移動方向有高於三格的落差，則停在邊緣"), and stops at
 * a wall, at such an edge, at a hazard and at unloaded ground; a body in the air or in water is moved in a straight
 * line through free space. The line from the real body to the image must be clear, so nobody is ever put through a
 * wall.
 */
public final class KingCrimsonShiftWalk {
    private static final double STEP = 0.25D;
    private static final double STEP_UP = 1.0D;
    private static final double MAX_DROP = KingCrimsonPhase5Spec.TIME_ERASE_SHIFT_MAX_DROP;
    private static final double EPSILON = 1.0E-4D;
    private static final int MAX_STEPS = 512;

    private KingCrimsonShiftWalk() {}

    /** Walks from a standing place toward a column; the feet's place where the walk ended, at most the column. */
    static Vec3 walk(ServerLevel level, Entity body, EntityDimensions size, Vec3 from, double toX, double toZ) {
        double dx = toX - from.x;
        double dz = toZ - from.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < EPSILON) {
            return from;
        }
        int steps = Math.min(Mth.ceil(distance / STEP), MAX_STEPS);
        Vec3 stride = new Vec3(dx / steps, 0.0D, dz / steps);
        AABB box = size.makeBoundingBox(from);
        for (int step = 0; step < steps; step++) {
            AABB next = stride(level, body, box, stride);
            if (next == null) {
                break;
            }
            box = next;
        }
        return feet(box);
    }

    /** Moves in a straight line through free space, for a body that is not on the ground. */
    static Vec3 fly(ServerLevel level, Entity body, EntityDimensions size, Vec3 from, Vec3 to) {
        Vec3 way = to.subtract(from);
        double distance = way.length();
        if (distance < EPSILON) {
            return from;
        }
        int steps = Math.min(Mth.ceil(distance / STEP), MAX_STEPS);
        Vec3 stride = way.scale(1.0D / steps);
        AABB box = size.makeBoundingBox(from);
        for (int step = 0; step < steps; step++) {
            AABB next = box.move(stride);
            if (!isLoaded(level, next) || !level.noCollision(body, next) || isHazardAt(level, next)) {
                break;
            }
            box = next;
        }
        return feet(box);
    }

    // One short step as a player takes it: straight on, else up a block and over, then down to the ground.
    @Nullable
    private static AABB stride(ServerLevel level, Entity body, AABB box, Vec3 stride) {
        AABB moved;
        double raised = 0.0D;
        if (isWhole(Entity.collideBoundingBox(body, stride, box, level, List.of()), stride)) {
            moved = box.move(stride);
        } else {
            raised = Entity.collideBoundingBox(body, new Vec3(0.0D, STEP_UP, 0.0D), box, level, List.of()).y;
            AABB lifted = box.move(0.0D, raised, 0.0D);
            if (raised < EPSILON
                    || !isWhole(Entity.collideBoundingBox(body, stride, lifted, level, List.of()), stride)) {
                return null;
            }
            moved = lifted.move(stride);
        }
        if (!isLoaded(level, moved)) {
            return null;
        }
        double reach = raised + MAX_DROP + 0.01D;
        double fell = -Entity.collideBoundingBox(body, new Vec3(0.0D, -(reach + 0.5D), 0.0D), moved, level, List.of()).y;
        if (fell > reach) {
            return null;
        }
        AABB stood = moved.move(0.0D, -fell, 0.0D);
        return isHazardAt(level, stood) || isUnderLiquid(level, stood) ? null : stood;
    }

    // A walker stops at the shore: he would swim on from there, and the image only walks.
    private static boolean isUnderLiquid(ServerLevel level, AABB box) {
        double knee = box.minY + 0.4D;
        BlockPos pos = BlockPos.containing(box.getCenter().x, knee, box.getCenter().z);
        FluidState fluid = level.getFluidState(pos);
        return !fluid.isEmpty() && pos.getY() + fluid.getHeight(level, pos) > knee;
    }

    /** How far below a box the ground is, or a negative number when there is none within three blocks. */
    public static double groundBelow(ServerLevel level, Entity body, AABB box) {
        double reach = MAX_DROP + 0.01D;
        double fell = -Entity.collideBoundingBox(body, new Vec3(0.0D, -(reach + 0.5D), 0.0D), box, level, List.of()).y;
        return fell > reach ? -1.0D : fell;
    }

    /** Whether nothing solid lies between two places of a body, at its middle and at its eyes. */
    static boolean isLineClear(ServerLevel level, Entity body, EntityDimensions size, Vec3 from, Vec3 to) {
        for (double height : new double[] {size.height() * 0.5D, size.eyeHeight()}) {
            Vec3 start = from.add(0.0D, height, 0.0D);
            Vec3 end = to.add(0.0D, height, 0.0D);
            if (start.distanceToSqr(end) > EPSILON && level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body)).getType() != HitResult.Type.MISS) {
                return false;
            }
        }
        return true;
    }

    static boolean canStand(ServerLevel level, Entity body, EntityDimensions size, Vec3 at) {
        AABB box = size.makeBoundingBox(at);
        return isLoaded(level, box) && level.noCollision(body, box) && !isHazardAt(level, box);
    }

    private static boolean isLoaded(ServerLevel level, AABB box) {
        BlockPos pos = BlockPos.containing(box.getCenter().x, box.minY, box.getCenter().z);
        return level.hasChunkAt(pos)
                && level.getWorldBorder().isWithinBounds(pos)
                && box.minY >= level.getMinBuildHeight()
                && box.maxY < level.getMaxBuildHeight();
    }

    /** Whether what a body touches there, or stands on, would hurt it. */
    public static boolean isHazardAt(ServerLevel level, AABB box) {
        AABB touched = box.deflate(1.0E-3D).expandTowards(0.0D, -0.2D, 0.0D);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(touched.minX), Mth.floor(touched.minY), Mth.floor(touched.minZ),
                Mth.floor(touched.maxX), Mth.floor(touched.maxY), Mth.floor(touched.maxZ))) {
            if (isHazard(level, pos, level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHazard(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getFluidState().is(FluidTags.LAVA)
                || state.is(BlockTags.FIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)) {
            return true;
        }
        // A mod's own dangerous block says so through its path type.
        PathType type = state.getBlockPathType(level, pos, null);
        return type == PathType.LAVA || type == PathType.DAMAGE_FIRE || type == PathType.DANGER_FIRE
                || type == PathType.DAMAGE_OTHER || type == PathType.DANGER_OTHER
                || type == PathType.DAMAGE_CAUTIOUS || type == PathType.POWDER_SNOW;
    }

    private static boolean isWhole(Vec3 moved, Vec3 wanted) {
        return moved.distanceToSqr(wanted) < EPSILON * EPSILON;
    }

    private static Vec3 feet(AABB box) {
        return new Vec3((box.minX + box.maxX) * 0.5D, box.minY, (box.minZ + box.maxZ) * 0.5D);
    }
}
