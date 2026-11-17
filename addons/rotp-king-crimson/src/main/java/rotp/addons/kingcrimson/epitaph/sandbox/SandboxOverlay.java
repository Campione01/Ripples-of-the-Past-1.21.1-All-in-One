package rotp.addons.kingcrimson.epitaph.sandbox;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * The blocks the phantoms of a room changed. Nothing is written to the level: the new state is kept here, and block
 * reads made in the room answer from here first, so a phantom falls into the crater a phantom made and walks through
 * the door a phantom broke. Empty for most predictions, and then no read pays for it.
 */
public final class SandboxOverlay {
    private final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();

    public boolean isEmpty() {
        return states.isEmpty();
    }

    @Nullable
    public BlockState get(BlockPos pos) {
        return states.isEmpty() ? null : states.get(pos.asLong());
    }

    public void put(BlockPos pos, BlockState state) {
        states.put(pos.asLong(), state);
    }

    public int size() {
        return states.size();
    }

    /** A chunk as collision code reads it, with the overlay over it. */
    public BlockGetter over(BlockGetter chunk) {
        return new View(chunk);
    }

    private final class View implements BlockGetter {
        private final BlockGetter chunk;

        private View(BlockGetter chunk) {
            this.chunk = chunk;
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState changed = states.get(pos.asLong());
            return changed != null ? changed : chunk.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            BlockState changed = states.get(pos.asLong());
            return changed != null ? changed.getFluidState() : chunk.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return chunk.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return chunk.getMinBuildHeight();
        }
    }
}
