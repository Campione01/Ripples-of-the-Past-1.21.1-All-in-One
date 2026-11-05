package rotp.addons.bastet.block;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.bastet.BastetAddon;
import rotp.addons.bastet.magnet.MagnetizedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** 1.16 BastetOutletTileEntity, with the 1.16 BastetOutletRangeHandler check as its ticker. */
public final class BastetOutletBlockEntity extends BlockEntity {
    public static final int CHECK_INTERVAL_TICKS = 20;
    /** 1.16 BastetOutletRangeHandler.clearMagnetizedAround: the box around a removed outlet whose creatures are freed. */
    public static final double CLEAR_RADIUS = 20.0D;
    private static final String OWNER_TAG = "Owner";

    @Nullable
    private UUID ownerUUID;

    public BastetOutletBlockEntity(BlockPos pos, BlockState state) {
        super(BastetAddon.OUTLET_BLOCK_ENTITY.get(), pos, state);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
        setChanged();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BastetOutletBlock.FACING) ? state.getValue(BastetOutletBlock.FACING) : Direction.NORTH;
    }

    /*
     * 1.16 BastetOutletRangeHandler: every 20 game ticks an outlet without an owner, whose owner is offline, or whose
     * owner no longer has Bastet is removed and frees the magnetized creatures around it. 1.16 kept the outlets in one
     * static set for every dimension, which neither survived a restart nor told dimensions apart; each outlet checks
     * itself here instead.
     */
    void serverTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide() || level.getGameTime() % CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        if (!(getBlockState().getBlock() instanceof BastetOutletBlock) || ownerKeepsOutlet(level)) {
            return;
        }
        BlockPos pos = getBlockPos();
        level.removeBlock(pos, false);
        clearMagnetizedAround(level, pos);
    }

    private boolean ownerKeepsOutlet(Level level) {
        if (ownerUUID == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        ServerPlayer owner = server != null ? server.getPlayerList().getPlayer(ownerUUID) : null;
        return owner != null && BastetAddon.isBastetUser(owner);
    }

    private static void clearMagnetizedAround(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(CLEAR_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (MagnetizedData.isMagnetized(entity)) {
                MagnetizedData.clearMagnetization(entity);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUUID != null) {
            tag.putUUID(OWNER_TAG, ownerUUID);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(OWNER_TAG)) {
            ownerUUID = tag.getUUID(OWNER_TAG);
        }
    }
}
