package rotp.addons.cmoon.field;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A falling block lands only when it is on the ground and only on what is under it, so on the field's new floor it would
 * hang until it drops as an item after 600 ticks. This is FallingBlockEntity.tick's landing turned along the field.
 */
final class FallingBlockLanding {
    // further than the client's rounding of 1/4096 and the step out of a surface leave between the box and its floor
    private static final double CONTACT = 1.0E-3D;
    private static final double SIDE_TOLERANCE = 1.0E-3D;

    private FallingBlockLanding() {}

    static void landAfterTick(FallingBlockEntity falling, Direction direction) {
        if (!restsOn(falling, direction)) {
            return;
        }
        ServerLevel level = (ServerLevel) falling.level();
        BlockPos pos = BlockPos.containing(falling.getBoundingBox().getCenter());
        BlockState there = level.getBlockState(pos);
        if (there.is(Blocks.MOVING_PISTON)) {
            return;
        }
        BlockState state = falling.getBlockState();
        Block block = state.getBlock();
        falling.setDeltaMovement(Vec3.ZERO);
        if (cancelsDrop(falling)) {
            falling.discard();
            falling.callOnBrokenAfterFall(block, pos);
            return;
        }
        boolean replaceable = there.canBeReplaced(
                new DirectionalPlaceContext(level, pos, direction, ItemStack.EMPTY, direction.getOpposite()));
        boolean supported = !FallingBlock.isFree(level.getBlockState(pos.relative(direction)));
        if (replaceable && supported && state.canSurvive(level, pos)) {
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && level.getFluidState(pos).getType() == Fluids.WATER) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, Boolean.TRUE);
            }
            if (level.setBlock(pos, state, Block.UPDATE_ALL)) {
                level.getChunkSource().chunkMap.broadcast(
                        falling, new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));
                falling.discard();
                if (block instanceof Fallable fallable) {
                    fallable.onLand(level, pos, state, there, falling);
                }
                loadBlockData(falling, level, pos, state);
                return;
            }
        }
        falling.discard();
        if (falling.dropItem && level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            falling.callOnBrokenAfterFall(block, pos);
            falling.spawnAtLocation(block);
        }
    }

    private static boolean restsOn(FallingBlockEntity falling, Direction direction) {
        Direction.Axis axis = direction.getAxis();
        AABB probe = falling.getBoundingBox()
                .deflate(axis == Direction.Axis.X ? 0.0D : SIDE_TOLERANCE,
                        axis == Direction.Axis.Y ? 0.0D : SIDE_TOLERANCE,
                        axis == Direction.Axis.Z ? 0.0D : SIDE_TOLERANCE)
                .move(Vec3.atLowerCornerOf(direction.getNormal()).scale(CONTACT));
        return !falling.level().noBlockCollision(falling, probe);
    }

    // FallingBlockEntity keeps it private; its own save is the one way to read it
    private static boolean cancelsDrop(FallingBlockEntity falling) {
        return falling.saveWithoutId(new CompoundTag()).getBoolean("CancelDrop");
    }

    private static void loadBlockData(FallingBlockEntity falling, ServerLevel level, BlockPos pos, BlockState state) {
        if (falling.blockData == null || !state.hasBlockEntity()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
        for (String key : falling.blockData.getAllKeys()) {
            tag.put(key, falling.blockData.get(key).copy());
        }
        try {
            blockEntity.loadWithComponents(tag, level.registryAccess());
        }
        catch (Exception exception) {
            LogUtils.getLogger().error("Failed to load block entity from falling block", exception);
        }
        blockEntity.setChanged();
    }
}
