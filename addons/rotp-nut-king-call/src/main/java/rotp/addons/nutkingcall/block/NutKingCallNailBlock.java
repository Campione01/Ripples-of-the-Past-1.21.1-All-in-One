package rotp.addons.nutkingcall.block;

import javax.annotation.Nullable;

import rotp.addons.nutkingcall.NutKingCallAddon;
import rotp.addons.nutkingcall.NutKingCallService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The nails and nuts a nail or nut leaves in a block face; one with both is climbable. */
public final class NutKingCallNailBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty NAILS = IntegerProperty.create("nails", 0, 4);
    public static final IntegerProperty NUTS = IntegerProperty.create("nuts", 0, 4);
    private static final VoxelShape SHAPE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);

    public NutKingCallNailBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(0.5F)
                .sound(SoundType.METAL)
                .noCollission()
                .noOcclusion()
                .isViewBlocking((state, level, pos) -> false));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(NAILS, 1)
                .setValue(NUTS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NAILS, NUTS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
            LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!state.canSurvive(level, currentPos)) {
            level.destroyBlock(currentPos, true);
        }
        return state;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NutKingCallNailBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != NutKingCallAddon.NAIL_BLOCK_ENTITY.get()
                ? null
                : (tickLevel, pos, tickState, blockEntity) ->
                        ((NutKingCallNailBlockEntity) blockEntity).serverTick();
    }

    public static void placeNail(Level level, BlockPos supportPos, Direction face) {
        BlockPos targetPos = supportPos.relative(face);
        BlockState existing = level.getBlockState(targetPos);
        if (existing.getBlock() instanceof NutKingCallNailBlock) {
            int nails = existing.getValue(NAILS);
            if (nails < 4) {
                level.setBlockAndUpdate(targetPos, existing.setValue(NAILS, nails + 1));
            }
        }
        else if (level.getBlockState(supportPos).isFaceSturdy(level, supportPos, face)
                && existing.isAir()) {
            level.setBlockAndUpdate(targetPos, NutKingCallAddon.NAIL_BLOCK.get().defaultBlockState()
                    .setValue(FACING, face)
                    .setValue(NAILS, 1)
                    .setValue(NUTS, 0));
        }
    }

    // 1.16: unlike a nail, a nut placed into air did not ask for a sturdy face
    public static void placeNut(Level level, BlockPos supportPos, Direction face) {
        BlockPos targetPos = supportPos.relative(face);
        BlockState existing = level.getBlockState(targetPos);
        if (existing.getBlock() instanceof NutKingCallNailBlock) {
            int nuts = existing.getValue(NUTS);
            if (nuts < 4) {
                level.setBlockAndUpdate(targetPos, existing.setValue(NUTS, nuts + 1));
            }
        }
        else if (existing.isAir()) {
            level.setBlockAndUpdate(targetPos, NutKingCallAddon.NAIL_BLOCK.get().defaultBlockState()
                    .setValue(FACING, face)
                    .setValue(NAILS, 0)
                    .setValue(NUTS, 1));
        }
    }

    public static boolean isClimbable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof NutKingCallNailBlock
                && state.getValue(NAILS) >= 1 && state.getValue(NUTS) >= 1;
    }

    /**
     * Takes the block the nail is in apart, dropping part of what crafts it, with an explosion's sound and puff; a
     * block the breaker may not destroy by the core's rule stays as it is.
     */
    public static void dismantleNail(Level level, BlockPos nailPos, LivingEntity breaker) {
        BlockState nailState = level.getBlockState(nailPos);
        if (!(nailState.getBlock() instanceof NutKingCallNailBlock)) {
            return;
        }
        Direction facing = nailState.getValue(FACING);
        BlockPos supportPos = nailPos.relative(facing.getOpposite());
        BlockState supportState = level.getBlockState(supportPos);
        if (supportState.getDestroySpeed(level, supportPos) < 0.0F) {
            level.removeBlock(nailPos, false);
            return;
        }
        if (!NutKingCallService.canBreakBlock(level, supportPos, breaker)) {
            return;
        }
        dropMaterials(level, supportState, supportPos);
        double x = supportPos.getX() + 0.5D;
        double y = supportPos.getY() + 0.5D;
        double z = supportPos.getZ() + 0.5D;
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.removeBlock(supportPos, false);
        level.removeBlock(nailPos, false);
    }

    // 1.16: the first crafting recipe (here in id order) that makes the block's item drops each ingredient at a half
    // chance, else the item
    private static void dropMaterials(Level level, BlockState supportState, BlockPos supportPos) {
        ItemStack blockItem = new ItemStack(supportState.getBlock().asItem());
        if (blockItem.isEmpty()) {
            return;
        }
        RandomSource random = level.random;
        double x = supportPos.getX() + 0.5D;
        double y = supportPos.getY() + 0.5D;
        double z = supportPos.getZ() + 0.5D;
        for (RecipeHolder<?> holder : NutKingCallService.craftingRecipesById(level)) {
            if (holder.value() instanceof CraftingRecipe crafting) {
                ItemStack result = crafting.getResultItem(level.registryAccess());
                if (!result.isEmpty() && ItemStack.isSameItemSameComponents(result, blockItem)) {
                    for (Ingredient ingredient : crafting.getIngredients()) {
                        if (ingredient.hasNoItems()) {
                            continue;
                        }
                        ItemStack[] matching = ingredient.getItems();
                        if (matching.length != 0 && !(random.nextFloat() < 0.5F)) {
                            ItemStack give = matching[random.nextInt(matching.length)].copy();
                            give.setCount(1);
                            level.addFreshEntity(new ItemEntity(level, x, y, z, give));
                        }
                    }
                    return;
                }
            }
        }
        level.addFreshEntity(new ItemEntity(level, x, y, z, blockItem));
    }
}
