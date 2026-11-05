package rotp.addons.beachboy.util;

import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// 0.1.13 RevolverBulletRedstoneUtil.interactAlong, which the Beach Boy hook
// also ran on its way: it flips levers and presses buttons, pressure plates
// and tripwire hooks it passes. Kept as the Sex Pistols port wrote it.
public final class LineRedstone {
    private LineRedstone() {}

    // Blocks with no collision box: the bullet keeps flying through them.
    public static boolean isPassThroughDevice(BlockState state) {
        Block block = state.getBlock();
        return block instanceof LeverBlock
                || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock
                || block instanceof TripWireHookBlock;
    }

    public static boolean isHitDevice(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof NoteBlock
                || block instanceof BellBlock;
    }

    @Nullable
    public static ServerPlayer resolvePlayer(@Nullable Entity owner) {
        if (owner instanceof ServerPlayer player) {
            return player;
        }
        if (owner instanceof StandEntity stand
                && stand.getUser() instanceof ServerPlayer user) {
            return user;
        }
        return null;
    }

    public static void activateAlongPath(
            ServerLevel level,
            Vec3 start,
            Vec3 end,
            ServerPlayer owner,
            Set<BlockPos> alreadyActivated) {
        activateAlongPath(level, start, end, end.add(end.subtract(start)),
                owner, alreadyActivated);
    }

    public static void activateAlongPath(
            ServerLevel level,
            Vec3 start,
            Vec3 end,
            Vec3 reference,
            ServerPlayer owner,
            Set<BlockPos> alreadyActivated) {
        if (start.distanceToSqr(end) <= 1.0E-8D) {
            return;
        }
        double pad = 0.12D;
        int minX = Mth.floor(Math.min(start.x, end.x) - pad);
        int minY = Mth.floor(Math.min(start.y, end.y) - pad);
        int minZ = Mth.floor(Math.min(start.z, end.z) - pad);
        int maxX = Mth.floor(Math.max(start.x, end.x) + pad);
        int maxY = Mth.floor(Math.max(start.y, end.y) + pad);
        int maxZ = Mth.floor(Math.max(start.z, end.z) + pad);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (alreadyActivated.contains(cursor)
                            || !level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!isPassThroughDevice(state)
                            || !segmentTouches(
                                    level, start, end, cursor, state)) {
                        continue;
                    }
                    BlockPos pos = cursor.immutable();
                    if (tryActivate(
                            level,
                            pos,
                            state,
                            owner,
                            guessFace(start, pos),
                            reference)) {
                        alreadyActivated.add(pos);
                    }
                }
            }
        }
    }

    public static boolean tryActivate(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable ServerPlayer owner,
            Direction face,
            @Nullable Vec3 panelReference) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock) {
            return togglePanel(
                    level, pos, state, owner, face, panelReference);
        }
        // A null player: the shooter hears the click too.
        if (block instanceof LeverBlock lever) {
            lever.pull(state, level, pos, null);
            return true;
        }
        if (block instanceof ButtonBlock button) {
            if (state.getValue(BlockStateProperties.POWERED)) {
                return false;
            }
            button.press(state, level, pos, null);
            return true;
        }
        if (block instanceof BasePressurePlateBlock
                || block instanceof TripWireHookBlock) {
            return pressPowered(
                    level,
                    pos,
                    state,
                    block,
                    block instanceof TripWireHookBlock ? 10 : 20);
        }
        if (block instanceof NoteBlock) {
            level.blockEvent(pos, block, 0, 0);
            return true;
        }
        if (block instanceof BellBlock bell) {
            return bell.attemptToRing(owner, level, pos, face);
        }
        return false;
    }

    // Only flips a panel whose flipped shape lies further along the shot.
    private static boolean togglePanel(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable ServerPlayer owner,
            Direction face,
            @Nullable Vec3 panelReference) {
        if (owner == null
                || !state.hasProperty(BlockStateProperties.OPEN)) {
            return false;
        }
        if (panelReference != null) {
            Vec3 now = shapeCenter(level, pos, state);
            Vec3 flipped = shapeCenter(
                    level,
                    pos,
                    state.cycle(BlockStateProperties.OPEN));
            if (flipped.distanceToSqr(panelReference) + 1.0E-6D
                    >= now.distanceToSqr(panelReference)) {
                return false;
            }
        }
        // The block's own use: iron doors and trapdoors stay shut.
        return state.useWithoutItem(
                        level,
                        owner,
                        new BlockHitResult(
                                Vec3.atCenterOf(pos), face, pos, false))
                .consumesAction();
    }

    private static boolean pressPowered(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Block block,
            int ticks) {
        if (!state.hasProperty(BlockStateProperties.POWERED)
                || state.getValue(BlockStateProperties.POWERED)) {
            return false;
        }
        level.setBlock(
                pos,
                state.setValue(BlockStateProperties.POWERED, true),
                Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.below(), block);
        level.scheduleTick(pos, block, ticks);
        level.playSound(
                null,
                pos,
                SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON,
                SoundSource.BLOCKS,
                0.3F,
                0.6F);
        return true;
    }

    private static Vec3 shapeCenter(
            ServerLevel level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            shape = state.getShape(level, pos);
        }
        if (shape.isEmpty()) {
            return Vec3.atCenterOf(pos);
        }
        return shape.bounds().move(pos).getCenter();
    }

    private static boolean segmentTouches(
            ServerLevel level,
            Vec3 start,
            Vec3 end,
            BlockPos pos,
            BlockState state) {
        VoxelShape outline = state.getShape(level, pos);
        if (!outline.isEmpty()) {
            if (outline.clip(start, end, pos) != null) {
                return true;
            }
            AABB bounds = outline.bounds().move(pos);
            return bounds.contains(start) || bounds.contains(end);
        }
        AABB cube = new AABB(pos);
        return cube.clip(start, end).isPresent()
                || cube.contains(start)
                || cube.contains(end);
    }

    private static Direction guessFace(Vec3 start, BlockPos pos) {
        Vec3 from = start.subtract(Vec3.atCenterOf(pos));
        return Direction.getNearest(from.x, from.y, from.z);
    }
}
