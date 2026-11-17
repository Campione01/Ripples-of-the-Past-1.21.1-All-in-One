package rotp.addons.spin.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.power.SpinData;

public final class SpunBlockEntity extends SpunProjectileEntity {
    private static final EntityDataAccessor<BlockState> BLOCK =
            SynchedEntityData.defineId(SpunBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private boolean payloadCommitted;
    private boolean canDropLoot;

    public SpunBlockEntity(EntityType<? extends SpunBlockEntity> type, Level level) {
        super(type, level);
    }

    private SpunBlockEntity(ServerLevel level, LivingEntity thrower, BlockState state) {
        super(SpinAddon.SPUN_BLOCK.get(), level, thrower, new ItemStack(state.getBlock()), SpinConfig.BLOCK_SPIN_DAMAGE.get());
        entityData.set(BLOCK, state);
        pickup = Pickup.DISALLOWED;
    }

    public static SpunBlockEntity prepare(ServerLevel level, LivingEntity thrower, BlockState state) {
        SpunBlockEntity projectile = new SpunBlockEntity(level, thrower, state);
        projectile.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0,
                SpinConfig.BLOCK_SPIN_VELOCITY.get().floatValue(), SpinConfig.BALL_INACCURACY.get().floatValue());
        return projectile;
    }

    /** The source is no longer available to be thrown a second time. */
    public void commitPayload(boolean spentSource) {
        if (level().isClientSide() || payloadCommitted || isRemoved()) {
            return;
        }
        payloadCommitted = true;
        canDropLoot = spentSource;
        if (spentSource && getOwner() instanceof Player player && !new ItemStack(getBlockState().getBlock()).isEmpty()) {
            pickup = player.hasInfiniteMaterials() ? Pickup.CREATIVE_ONLY : Pickup.ALLOWED;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BLOCK, Blocks.AIR.defaultBlockState());
    }

    public BlockState getBlockState() {
        return entityData.get(BLOCK);
    }

    @Override
    protected void onSuccessfulHit(Entity target, Entity owner) {
        if (payloadCommitted && owner instanceof LivingEntity living) {
            SpinData.practiceHit(living, target);
        }
    }

    @Override
    protected void changeMovementAfterHit() {
        dropAndDiscard(blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        dropAndDiscard(hit.getBlockPos().relative(hit.getDirection()));
    }

    private void dropAndDiscard(BlockPos pos) {
        if (level() instanceof ServerLevel server && !isRemoved()) {
            if (payloadCommitted && canDropLoot) {
                Block.dropResources(getBlockState(), server, pos);
            }
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Block", NbtUtils.writeBlockState(getBlockState()));
        tag.putBoolean("PayloadCommitted", payloadCommitted);
        tag.putBoolean("CanDropLoot", canDropLoot);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Block", CompoundTag.TAG_COMPOUND)) {
            entityData.set(BLOCK, NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), tag.getCompound("Block")));
        }
        payloadCommitted = tag.getBoolean("PayloadCommitted");
        canDropLoot = payloadCommitted && tag.getBoolean("CanDropLoot");
        if (!canDropLoot) {
            pickup = Pickup.DISALLOWED;
        }
    }
}
