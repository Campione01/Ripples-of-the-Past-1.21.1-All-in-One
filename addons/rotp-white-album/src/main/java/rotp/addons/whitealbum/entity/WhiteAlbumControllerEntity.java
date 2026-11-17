package rotp.addons.whitealbum.entity;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Hidden action and synchronized-state owner. It is not White Album's
 * visible manifestation; the canonical visible owner is the player-worn
 * suit rendered by the dedicated client player layers.
 */
public final class WhiteAlbumControllerEntity extends StandEntity {
    private static final EntityDataAccessor<Boolean>
            GENTLY_WEEPS_ACTIVE = SynchedEntityData.defineId(
                    WhiteAlbumControllerEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>
            FREEZE_FLOOR_ACTIVE = SynchedEntityData.defineId(
                    WhiteAlbumControllerEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private boolean wasStandBlocking;
    // The owner's rule of 2026-11-05: both toggles go on through a recall, on the removed entity, until the next summon
    // takes them over or the user dies, leaves, changes level or loses the Stand.
    public static final class Recalled {
        @Nullable
        WhiteAlbumControllerEntity stand;
    }

    /** The recalled controller whose toggles go on, with no controller out. */
    @Nullable
    public static WhiteAlbumControllerEntity recalled(LivingEntity user) {
        WhiteAlbumControllerEntity recalled =
                user.hasData(WhiteAlbumAddon.RECALLED_STAND)
                        ? user.getData(WhiteAlbumAddon.RECALLED_STAND).stand
                        : null;
        if (recalled == null) {
            return null;
        }
        StandPower power = StandPower.get(user);
        if (power == null
                || !power.hasPower()
                || power.getPowerType() != WhiteAlbumAddon.WHITE_ALBUM.get()
                || recalled.getUser() != user
                || !user.isAlive()
                || user.isRemoved()
                || recalled.level() != user.level()) {
            forgetRecalled(user);
            return null;
        }
        return recalled;
    }

    public static void forgetRecalled(LivingEntity user) {
        if (user.hasData(WhiteAlbumAddon.RECALLED_STAND)) {
            user.getData(WhiteAlbumAddon.RECALLED_STAND).stand = null;
        }
    }

    @Override
    public void setUserAndPower(LivingEntity user, StandPower power) {
        super.setUserAndPower(user, power);
        if (user == null || level().isClientSide()) {
            return;
        }
        if (!user.hasData(WhiteAlbumAddon.RECALLED_STAND)) {
            return;
        }
        WhiteAlbumControllerEntity recalled =
                user.getData(WhiteAlbumAddon.RECALLED_STAND).stand;
        forgetRecalled(user);
        if (recalled != null
                && recalled != this
                && recalled.getUser() == user
                && recalled.level() == level()) {
            entityData.set(GENTLY_WEEPS_ACTIVE, recalled.isGentlyWeepsActive());
            entityData.set(FREEZE_FLOOR_ACTIVE, recalled.isFreezeFloorActive());
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        LivingEntity user = getUser();
        if (!level().isClientSide()
                && (isGentlyWeepsActive() || isFreezeFloorActive())
                && user != null
                && user.isAlive()
                && !user.isRemoved()
                && user.level() == level()) {
            user.getData(WhiteAlbumAddon.RECALLED_STAND).stand = this;
        }
        super.remove(reason);
    }

    public WhiteAlbumControllerEntity(
            EntityType<? extends WhiteAlbumControllerEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GENTLY_WEEPS_ACTIVE, false);
        builder.define(FREEZE_FLOOR_ACTIVE, false);
    }

    public boolean isGentlyWeepsActive() {
        return entityData.get(GENTLY_WEEPS_ACTIVE);
    }

    public void setGentlyWeepsActive(boolean active) {
        if (!level().isClientSide()
                && (!active || isAlive() && !isRemoved())) {
            entityData.set(GENTLY_WEEPS_ACTIVE, active);
        }
    }

    public boolean isFreezeFloorActive() {
        return entityData.get(FREEZE_FLOOR_ACTIVE);
    }

    public void setFreezeFloorActive(boolean active) {
        if (!level().isClientSide()
                && (!active || isAlive() && !isRemoved())) {
            entityData.set(FREEZE_FLOOR_ACTIVE, active);
        }
    }

    public boolean pollGuardStarted() {
        boolean blocking = isStandBlocking();
        boolean started = blocking && !wasStandBlocking;
        wasStandBlocking = blocking;
        return started;
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);

        LivingEntity user = getUser();
        if (user == null || !user.isAlive() || user.isRemoved()) {
            if (!level().isClientSide()) {
                setGentlyWeepsActive(false);
                setFreezeFloorActive(false);
            }
            return;
        }

        setPos(user.getX(), user.getY(), user.getZ());
        setYRot(user.getYRot());
        setXRot(user.getXRot());
        yBodyRot = user.yBodyRot;
        yBodyRotO = user.yBodyRotO;
        yHeadRot = user.yHeadRot;
        yHeadRotO = user.yHeadRotO;

        if (!level().isClientSide()) {
            StandPower power = StandPower.get(user);
            if (power == null
                    || power.getPowerType()
                            != WhiteAlbumAddon.WHITE_ALBUM.get()
                    || power.getSummonedStandEntity() != this) {
                setGentlyWeepsActive(false);
                setFreezeFloorActive(false);
            }
        }
    }

    // 1.16 White Album's guard (FrozenBlock) was no block-pose task, so the
    // core never guarded its user's hits; WhiteAlbumGameEvents does that.
    @Override
    protected boolean guardsHitsOnUser() {
        return false;
    }

    // 1.16 WhiteAlbumPunch preTaskInit/onTaskSet: each punch gives the user
    // 40 hidden ticks of integrated_stand.
    @Override
    public boolean onActionSet(EntityActionInstance action) {
        LivingEntity user = getUser();
        if (!level().isClientSide() && action != null && user != null) {
            StandPower power = StandPower.get(user);
            if (power != null
                    && power.getPowerType()
                            == WhiteAlbumAddon.WHITE_ALBUM.get()
                    && action.ability == power.getAbility(
                            WhiteAlbumAddon.PUNCH_NAME)) {
                user.addEffect(new MobEffectInstance(
                        ModStatusEffects.INTEGRATED_STAND,
                        40, 0, false, false, false));
            }
        }
        return super.onActionSet(action);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }
}
