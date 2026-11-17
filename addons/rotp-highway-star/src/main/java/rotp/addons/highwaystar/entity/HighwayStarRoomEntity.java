package rotp.addons.highwaystar.entity;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.HighwayStarRoomConfig;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads.LurePayload;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads.ViewEndPayload;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads.ViewStartPayload;
import rotp.addons.highwaystar.room.HighwayStarRoomLure;
import rotp.addons.highwaystar.room.HighwayStarRoomService;
import rotp.addons.highwaystar.room.HighwayStarRoomService.Placement;
import rotp.addons.highwaystar.smell.SmellTargets;

public final class HighwayStarRoomEntity extends Entity {
    public static final int VIEW_TICKS = 60;
    public static final double ESCAPE_RANGE = 3.0;
    public static final int SOLE_COUNT = 8;
    private static final double VIEWER_RANGE = 24.0;
    private static final double MOB_LURE_RANGE = 12.0;
    private static final int VIEWER_REFRESH_TICKS = 40;
    private static final int MOB_REFRESH_TICKS = 10;

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            HighwayStarRoomEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<BlockPos> DOOR_BLOCK_POS = SynchedEntityData.defineId(
            HighwayStarRoomEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Direction> FACING = SynchedEntityData.defineId(
            HighwayStarRoomEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Boolean> CONSUMED = SynchedEntityData.defineId(
            HighwayStarRoomEntity.class, EntityDataSerializers.BOOLEAN);

    private long createdAt = -1L;
    @Nullable private UUID victimId;
    private long releaseAt;
    private boolean viewInterrupted;

    private HighwayStarRoomLure clientLure = HighwayStarRoomLure.CHEST;
    private boolean clientLureKnown;
    @Nullable private transient AutoCloseable clientPreview;

    public HighwayStarRoomEntity(EntityType<? extends HighwayStarRoomEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(DOOR_BLOCK_POS, BlockPos.ZERO);
        builder.define(FACING, Direction.NORTH);
        builder.define(CONSUMED, false);
    }

    public void bind(UUID ownerId, Placement placement, long gameTime) {
        entityData.set(OWNER_UUID, Optional.of(ownerId));
        entityData.set(DOOR_BLOCK_POS, placement.wallPos());
        entityData.set(FACING, placement.face());
        createdAt = gameTime;
        placeAtDoor();
    }

    private void placeAtDoor() {
        BlockPos wall = getDoorBlockPos();
        Direction face = getFacing();
        setPos(wall.getX() + 0.5 + face.getStepX() * 0.501, wall.getY(),
                wall.getZ() + 0.5 + face.getStepZ() * 0.501);
    }

    @Override
    protected AABB makeBoundingBox() {
        double x = getX();
        double y = getY();
        double z = getZ();
        return switch (getFacing()) {
            case EAST -> new AABB(x - 0.06, y, z - 0.5, x + 0.40, y + 2.0, z + 0.5);
            case WEST -> new AABB(x - 0.40, y, z - 0.5, x + 0.06, y + 2.0, z + 0.5);
            case SOUTH -> new AABB(x - 0.5, y, z - 0.06, x + 0.5, y + 2.0, z + 0.40);
            default -> new AABB(x - 0.5, y, z - 0.40, x + 0.5, y + 2.0, z + 0.06);
        };
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (FACING.equals(key)) {
            setBoundingBox(makeBoundingBox());
        }
        if (CONSUMED.equals(key) && isConsumed()) {
            setClientPreview(null);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = HighwayStarRoomService.validOwner(level, getOwnerUUID(), getUUID());
        long gameTime = level.getGameTime();
        if (owner == null || createdAt < 0L || !getFacing().getAxis().isHorizontal()) {
            discard();
            return;
        }
        if (isConsumed()) {
            tickPendingAttack(level, owner, gameTime);
            return;
        }
        if (HighwayStarRoomConfig.isExpired(gameTime - createdAt)) {
            discard();
            return;
        }
        LivingEntity entrant = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox(),
                        candidate -> HighwayStarRoomService.canEnter(owner, candidate))
                .stream().min(Comparator.comparingDouble((LivingEntity candidate) -> candidate.distanceToSqr(this))
                        .thenComparingInt(Entity::getId)).orElse(null);
        if (entrant != null) {
            takeScent(owner, entrant, gameTime);
            return;
        }
        if (tickCount % VIEWER_REFRESH_TICKS == 1) {
            refreshViewerLures(level, gameTime);
        }
        if (tickCount % MOB_REFRESH_TICKS == 1) {
            lureMobs(level, owner);
        }
    }

    private void takeScent(ServerPlayer owner, LivingEntity entrant, long gameTime) {
        UUID target = entrant.getUUID();
        SmellTargets scents = owner.getData(HighwayStarAddon.SMELL_TARGETS);
        scents.addTarget(target);
        scents.setSelectedTrackingTarget(target);
        if (owner.connection != null) {
            owner.sendSystemMessage(Component.translatable("message.highwaystar.room_scent", entrant.getDisplayName()));
        }

        victimId = target;
        releaseAt = gameTime + VIEW_TICKS;
        entityData.set(CONSUMED, true);
        entrant.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, VIEW_TICKS, 0));
        if (entrant instanceof ServerPlayer viewer) {
            HighwayStarRoomPayloads.send(viewer, new ViewStartPayload(getUUID(), level().dimension().location(),
                    getDoorBlockPos(), getFacing(), entrant.position(), VIEW_TICKS,
                    HighwayStarRoomService.chooseLure(viewer, gameTime)));
        }
    }

    private void refreshViewerLures(ServerLevel level, long gameTime) {
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(this) <= VIEWER_RANGE * VIEWER_RANGE) {
                HighwayStarRoomPayloads.send(viewer, new LurePayload(getUUID(), level.dimension().location(),
                        HighwayStarRoomService.chooseLure(viewer, gameTime)));
            }
        }
    }

    private void lureMobs(ServerLevel level, ServerPlayer owner) {
        BlockPos approach = getDoorBlockPos().relative(getFacing());
        for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(MOB_LURE_RANGE),
                candidate -> !candidate.isNoAi() && candidate.distanceToSqr(this) <= MOB_LURE_RANGE * MOB_LURE_RANGE
                        && HighwayStarRoomService.canEnter(owner, candidate))) {
            var navigation = mob.getNavigation();
            if (navigation.isDone() && mob.blockPosition().equals(approach)) {
                // Vanilla considers the approach node reached before small mobs touch the door.
                mob.getMoveControl().setWantedPosition(getX() + getFacing().getStepX() * 0.20,
                        getY(), getZ() + getFacing().getStepZ() * 0.20, 1.0);
            } else if (navigation.isDone() || !approach.equals(navigation.getTargetPos())) {
                navigation.moveTo(approach.getX() + 0.5, approach.getY(), approach.getZ() + 0.5, 0, 1.0);
            }
        }
    }

    public boolean hasPendingVictim(UUID target) {
        return isConsumed() && !isRemoved() && target.equals(victimId);
    }

    public void interruptView() {
        viewInterrupted = true;
    }

    private void tickPendingAttack(ServerLevel level, ServerPlayer owner, long gameTime) {
        Entity target = victimId != null ? level.getEntity(victimId) : null;
        if (!(target instanceof LivingEntity victim) || !HighwayStarRoomService.canEnter(owner, victim)) {
            discard();
            return;
        }
        if (gameTime < releaseAt && !viewInterrupted && victim.distanceToSqr(this) <= ESCAPE_RANGE * ESCAPE_RANGE) {
            return;
        }

        discard();
        collapse(level);
        Direction face = getFacing();
        for (int i = 0; i < SOLE_COUNT; i++) {
            HighwayStarSoleEntity sole = HighwayStarAddon.SOLE_ENTITY.get().create(level);
            if (sole == null) {
                continue;
            }
            double side = (i - (SOLE_COUNT - 1) / 2.0) * 0.045;
            sole.moveTo(getX() + face.getStepX() * 0.28 + face.getStepZ() * side, getY(),
                    getZ() + face.getStepZ() * 0.28 - face.getStepX() * side, face.toYRot(), 0.0F);
            sole.setOwnerUUID(owner.getUUID());
            sole.attachTo(victim.getUUID(), i);
            level.addFreshEntity(sole);
        }
    }

    @Override
    public boolean isPickable() {
        return !isConsumed() && !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel level) || !isPickable() || !(amount > 0.0F)
                || (source.getEntity() == null && source.getDirectEntity() == null) || isInvulnerableTo(source)) {
            return false;
        }
        ServerPlayer owner = HighwayStarRoomService.validOwner(level, getOwnerUUID(), getUUID());
        if (owner == null) {
            discard();
            return false;
        }
        discard();
        collapse(level);
        if (owner.connection != null) {
            owner.sendSystemMessage(Component.translatable("message.highwaystar.room_broken"));
        }
        return true;
    }

    private void collapse(ServerLevel level) {
        Direction face = getFacing();
        level.sendParticles(HighwayStarAddon.ROOM_FOOTPRINT.get(), getX(), getY() + 1.0, getZ(), 22,
                face.getAxis() == Direction.Axis.X ? 0.12 : 0.40, 0.65,
                face.getAxis() == Direction.Axis.Z ? 0.12 : 0.40, 0.03);
        level.playSound(null, getX(), getY(), getZ(), HighwayStarAddon.DRAIN_VOICE.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !isRemoved() && level() instanceof ServerLevel level) {
            HighwayStarRoomService.clearIfCurrent(level, getOwnerUUID(), getUUID());
            if (victimId != null && level.getEntity(victimId) instanceof ServerPlayer victim) {
                HighwayStarRoomPayloads.send(victim, new ViewEndPayload(getUUID(), level.dimension().location()));
            }
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID ownerId = getOwnerUUID();
        if (ownerId != null) {
            tag.putUUID("RoomOwner", ownerId);
        }
        tag.putLong("DoorPos", getDoorBlockPos().asLong());
        tag.putInt("DoorFace", getFacing().get3DDataValue());
        tag.putLong("CreatedAt", createdAt);
        tag.putBoolean("Consumed", isConsumed());
        if (victimId != null) {
            tag.putUUID("Victim", victimId);
            tag.putLong("ReleaseAt", releaseAt);
            tag.putBoolean("ViewInterrupted", viewInterrupted);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.hasUUID("RoomOwner") || !tag.contains("DoorPos", Tag.TAG_LONG)
                || !tag.contains("DoorFace", Tag.TAG_INT) || !tag.contains("CreatedAt", Tag.TAG_LONG)) {
            return;
        }
        int faceId = tag.getInt("DoorFace");
        Direction face = Direction.from3DDataValue(faceId);
        if (face.get3DDataValue() != faceId || !face.getAxis().isHorizontal()) {
            return;
        }
        bind(tag.getUUID("RoomOwner"), new Placement(BlockPos.of(tag.getLong("DoorPos")), face),
                tag.getLong("CreatedAt"));
        entityData.set(CONSUMED, tag.getBoolean("Consumed"));
        if (isConsumed() && tag.hasUUID("Victim") && tag.contains("ReleaseAt", Tag.TAG_LONG)) {
            victimId = tag.getUUID("Victim");
            releaseAt = Math.min(tag.getLong("ReleaseAt"), level().getGameTime() + VIEW_TICKS);
            viewInterrupted = tag.getBoolean("ViewInterrupted");
        }
    }

    @Nullable
    public UUID getOwnerUUID() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public BlockPos getDoorBlockPos() {
        return entityData.get(DOOR_BLOCK_POS);
    }

    public Direction getFacing() {
        return entityData.get(FACING);
    }

    public boolean isConsumed() {
        return entityData.get(CONSUMED);
    }

    public boolean hasClientLure() {
        return clientLureKnown;
    }

    public HighwayStarRoomLure getClientLure() {
        return clientLure;
    }

    public void setClientLure(HighwayStarRoomLure lure) {
        if (!clientLure.equals(lure)) {
            setClientPreview(null);
        }
        clientLure = lure;
        clientLureKnown = true;
    }

    @Nullable
    public AutoCloseable getClientPreview() {
        return clientPreview;
    }

    public void setClientPreview(@Nullable AutoCloseable preview) {
        AutoCloseable previous = clientPreview;
        if (previous == preview) return;
        clientPreview = preview;
        if (previous != null) {
            try {
                previous.close();
            } catch (Exception error) {
                LogUtils.getLogger().warn("Could not release a Highway Star room preview", error);
            }
        }
    }
}
