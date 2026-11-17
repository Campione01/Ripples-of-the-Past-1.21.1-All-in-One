package rotp.addons.d4c.block;

import javax.annotation.Nullable;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The destination of a portal to a dimension that has no portal block of
 * its own; empty on portals whose block names their destination.
 */
public final class D4CPortalBlockEntity extends BlockEntity {
    private static final String TARGET = "target";
    @Nullable
    private ResourceLocation target;

    public D4CPortalBlockEntity(BlockPos position, BlockState state) {
        super(D4CAddon.PORTAL_TARGET.get(), position, state);
    }

    @Nullable
    public ResourceLocation target() {
        return target;
    }

    public void setTarget(@Nullable ResourceLocation target) {
        this.target = target;
        setChanged();
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (target != null) {
            tag.putString(TARGET, target.toString());
        }
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        target = tag.contains(TARGET)
                ? ResourceLocation.tryParse(tag.getString(TARGET))
                : null;
    }
}
