package rotp.addons.kingcrimson.entity;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * What a phantom of Epitaph is drawn through. The phantom itself is a client entity kept outside the level; this
 * stands in the level where the phantom stands, with an id no server entity has, so that the game, and a shader pack,
 * find an entity to draw there. Client only: it is never sent, saved or summoned, and nothing can touch it.
 */
public final class KingCrimsonPhantomCarrierEntity extends Entity {
    @Nullable
    private Entity puppet;

    public KingCrimsonPhantomCarrierEntity(EntityType<? extends KingCrimsonPhantomCarrierEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** The phantom this stands for, from now on. */
    public void carry(Entity phantom) {
        puppet = phantom;
        setPos(phantom.getX(), phantom.getY(), phantom.getZ());
        setOldPosAndRot();
    }

    @Nullable
    public Entity puppet() {
        return puppet;
    }

    @Override
    public void tick() {
        if (puppet == null || puppet.isRemoved()) {
            discard();
            return;
        }
        setPos(puppet.getX(), puppet.getY(), puppet.getZ());
    }

    // Its box is its phantom's, so that it is drawn and culled as the phantom would be.
    @Override
    protected AABB makeBoundingBox() {
        return puppet != null ? puppet.getBoundingBox() : super.makeBoundingBox();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return puppet != null ? puppet.getBoundingBoxForCulling() : super.getBoundingBoxForCulling();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return puppet != null ? puppet.shouldRenderAtSqrDistance(distance) : super.shouldRenderAtSqrDistance(distance);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
