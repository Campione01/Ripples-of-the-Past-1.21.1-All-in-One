package rotp.addons.bastet.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import rotp.addons.bastet.BastetAddon;
import rotp.addons.bastet.magnet.MagnetizedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 1.16 BastetOutletBlock: an invisible, unbreakable outlet on a wall, drawn by its block entity's renderer. */
public final class BastetOutletBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BastetOutletBlock> CODEC = simpleCodec(BastetOutletBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** 1.16 BastetPlaceOutlet: the user's pick reach. */
    public static final double PLACE_REACH = 5.0D;
    /** 1.16 BastetPlaceOutlet and BastetRemoveOutlet: the cube around the user searched for its outlets. */
    public static final int OWN_OUTLET_RADIUS = 64;

    public BastetOutletBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // 1.16 Material.BARRIER, hardnessAndResistance(3500000), SoundType.METAL, notSolid, doesNotBlockMovement
    public static BlockBehaviour.Properties outletProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .pushReaction(PushReaction.BLOCK)
                .strength(3500000.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .noCollission();
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction dir : context.getNearestLookingDirections()) {
            if (dir.getAxis().isHorizontal()) {
                BlockState state = defaultBlockState().setValue(FACING, dir.getOpposite());
                if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return state;
                }
            }
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof BastetOutletBlockEntity outlet) {
            outlet.setOwnerUUID(placer.getUUID());
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behind = pos.relative(facing);
        return level.getBlockState(behind).isFaceSturdy(level, behind, facing.getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean isMoving) {
        if (!level.isClientSide() && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, false);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BastetOutletBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != BastetAddon.OUTLET_BLOCK_ENTITY.get()
                ? null
                : (tickLevel, pos, tickState, blockEntity) -> ((BastetOutletBlockEntity) blockEntity).serverTick();
    }

    @Nullable
    public static UUID getOwner(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BastetOutletBlockEntity outlet ? outlet.getOwnerUUID() : null;
    }

    /**
     * 1.16 BastetPlaceOutlet.standPerform: the block the user looks at within 5 blocks, on a side face, with air in
     * front of it and a sturdy face to hang on, gets an outlet facing it; the user's other outlets go first.
     */
    public static void placeOutlet(Level level, Player user) {
        HitResult ray = user.pick(PLACE_REACH, 1.0F, false);
        if (ray.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockHitResult blockHit = (BlockHitResult) ray;
        Direction side = blockHit.getDirection();
        if (!side.getAxis().isHorizontal()) {
            return;
        }
        BlockPos placePos = blockHit.getBlockPos().relative(side);
        if (!level.isEmptyBlock(placePos)) {
            return;
        }
        BlockState outlet = BastetAddon.OUTLET_BLOCK.get().defaultBlockState().setValue(FACING, side.getOpposite());
        if (!outlet.canSurvive(level, placePos)) {
            return;
        }
        removeOwnOutlets(level, user);
        level.setBlockAndUpdate(placePos, outlet);
        if (level.getBlockEntity(placePos) instanceof BastetOutletBlockEntity outletEntity) {
            outletEntity.setOwnerUUID(user.getUUID());
        }
    }

    /** 1.16 BastetRemoveOutlet.standPerform: the user's outlets go, and every magnetized creature near the user is freed. */
    public static void removeOutlets(Level level, Player user) {
        removeOwnOutlets(level, user);
        AABB area = user.getBoundingBox().inflate(OWN_OUTLET_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (MagnetizedData.isMagnetized(entity)) {
                MagnetizedData.clearMagnetization(entity);
            }
        }
    }

    /*
     * 1.16 visited every block of the 129-block cube around the user (loading any chunk not loaded) and removed the
     * outlets the user owns. Outlets always have their block entity, so this reads the block entities of the loaded
     * chunks in that cube instead.
     */
    private static void removeOwnOutlets(Level level, Player user) {
        BlockPos center = user.blockPosition();
        UUID uuid = user.getUUID();
        List<BlockPos> owned = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - OWN_OUTLET_RADIUS);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + OWN_OUTLET_RADIUS);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - OWN_OUTLET_RADIUS);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + OWN_OUTLET_RADIUS);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (blockEntity instanceof BastetOutletBlockEntity outlet
                            && Math.abs(pos.getX() - center.getX()) <= OWN_OUTLET_RADIUS
                            && Math.abs(pos.getY() - center.getY()) <= OWN_OUTLET_RADIUS
                            && Math.abs(pos.getZ() - center.getZ()) <= OWN_OUTLET_RADIUS
                            && uuid.equals(outlet.getOwnerUUID())) {
                        owned.add(pos.immutable());
                    }
                }
            }
        }
        for (BlockPos pos : owned) {
            if (level.getBlockState(pos).getBlock() instanceof BastetOutletBlock) {
                level.removeBlock(pos, false);
            }
        }
    }
}
