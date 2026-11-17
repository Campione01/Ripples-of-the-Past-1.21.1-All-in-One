package rotp.addons.cmoonreimagined.entity;

import rotp.addons.cmoonreimagined.CMoonAddon;
import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravitySource;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class CMoonStandEntity extends StandEntity
        implements DirectionalGravitySource {
    private static final ResourceLocation GRAVITY_SOURCE_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "cmoon_rm", "c_moon");
    private static final int GRAVITY_PRIORITY = 100;
    private static final EntityDataAccessor<Integer>
            GRAVITY_DIRECTION = SynchedEntityData.defineId(
                    CMoonStandEntity.class,
                    EntityDataSerializers.INT);

    // The owner's rule of 2026-11-05: a changed gravity goes on through a recall, bound to the removed entity, until the
    // next summon takes it over, a press on the variation key ends it or its user dies, leaves, changes level or loses the Stand.
    public static final class Recalled {
        CMoonStandEntity stand;
    }

    private LivingEntity boundGravityTarget;
    private Direction boundGravityDirection = Direction.DOWN;
    private boolean carryEnded;

    public CMoonStandEntity(
            EntityType<? extends CMoonStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(
                GRAVITY_DIRECTION,
                Direction.DOWN.get3DDataValue());
    }

    public Direction getGravityDirection() {
        return Direction.from3DDataValue(
                entityData.get(GRAVITY_DIRECTION));
    }

    @Override
    public Direction gravityDirection(Entity entity) {
        return entity == boundGravityTarget && (!isRemoved() || userStillHolds(boundGravityTarget))
                ? getGravityDirection()
                : Direction.DOWN;
    }

    // Once it has failed it stays failed: a way back to this level must not bring the gravity back.
    private boolean userStillHolds(LivingEntity user) {
        if (carryEnded) {
            return false;
        }
        StandPower power = StandPower.get(user);
        carryEnded = !(user.isAlive()
                && !user.isRemoved()
                && user.level() == level()
                && power != null
                && power.hasPower()
                && power.getPowerType() == CMoonAddon.C_MOON.get());
        return !carryEnded;
    }

    public void setGravityDirection(Direction direction) {
        if (!level().isClientSide()
                && direction != null
                && (direction == Direction.DOWN
                        || direction.getAxis().isHorizontal())) {
            entityData.set(
                    GRAVITY_DIRECTION,
                    direction.get3DDataValue(),
                    true);
            reconcileGravityBinding(getUser());
        }
    }

    @Override
    public void setUserAndPower(
            LivingEntity user, StandPower power) {
        clearGravityBinding();
        super.setUserAndPower(user, power);
        if (user != null
                && !level().isClientSide()
                && user.hasData(CMoonAddon.RECALLED_STAND)) {
            takeOverRecalled(user);
        }
        reconcileGravityBinding(user);
    }

    private void takeOverRecalled(LivingEntity user) {
        Recalled carry = user.getData(CMoonAddon.RECALLED_STAND);
        CMoonStandEntity recalled = carry.stand;
        carry.stand = null;
        if (recalled == null || recalled == this) {
            return;
        }
        if (recalled.getUser() == user
                && recalled.level() == level()
                && recalled.userStillHolds(user)) {
            entityData.set(
                    GRAVITY_DIRECTION,
                    recalled.entityData.get(GRAVITY_DIRECTION),
                    true);
            // this one's binding replaces it under the same source id
            recalled.boundGravityTarget = null;
        }
        else {
            recalled.clearGravityBinding();
        }
    }

    @Override
    public void onSyncedDataUpdated(
            EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        reconcileGravityBinding(getUser());
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            clearGravityBinding();
        }
        else {
            reconcileGravityBinding(getUser());
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()
                && boundGravityTarget != null
                && userStillHolds(boundGravityTarget)) {
            boundGravityTarget.getData(CMoonAddon.RECALLED_STAND).stand = this;
        }
        else {
            clearGravityBinding();
        }
        super.remove(reason);
    }

    private void reconcileGravityBinding(LivingEntity user) {
        Direction direction = getGravityDirection();
        if (boundGravityTarget != user
                || direction == Direction.DOWN) {
            clearGravityBinding();
        }
        if (user == null || direction == Direction.DOWN) {
            return;
        }
        if (boundGravityTarget == null) {
            boundGravityTarget = user;
            boundGravityDirection = direction;
            DirectionalGravityApi.bind(
                    user,
                    GRAVITY_SOURCE_ID,
                    GRAVITY_PRIORITY,
                    this);
        }
        else if (boundGravityDirection != direction) {
            boundGravityDirection = direction;
            DirectionalGravityApi.directionChanged(
                    user, GRAVITY_SOURCE_ID, this);
        }
    }

    private void clearGravityBinding() {
        if (boundGravityTarget != null) {
            DirectionalGravityApi.unbind(
                    boundGravityTarget,
                    GRAVITY_SOURCE_ID,
                    this);
            boundGravityTarget = null;
        }
        boundGravityDirection = Direction.DOWN;
    }
}
