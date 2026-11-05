package rotp.addons.nutkingcall.state;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.nutkingcall.NutKingCallAddon;
import rotp.addons.nutkingcall.network.NailStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

/** 1.16 NailCapability: the nails, nuts and pin on a living entity, synced to its trackers and itself. */
public final class NailState implements INBTSerializable<CompoundTag> {
    @Nullable private LivingEntity entity;
    private int entityId = -1;
    private boolean isInGround = false;
    private boolean wallClimbing = false;
    private BlockPos climbStartPos = BlockPos.ZERO;
    private int nailCount = 0;
    private int nutCount = 0;
    private int nailedCount = 0;
    private boolean pinned = false;
    private BlockPos pinPos = BlockPos.ZERO;
    private Direction pinFace = Direction.DOWN;
    private int struggleCount = 0;
    private int pinStartTick = 0;
    @Nullable private UUID pinOwnerUUID = null;

    public NailState(@Nullable LivingEntity entity) {
        setEntity(entity);
    }

    public static NailState get(LivingEntity entity) {
        return entity.getData(NutKingCallAddon.NAIL_STATE);
    }

    @Nullable
    public static NailState getExisting(LivingEntity entity) {
        return entity.getExistingData(NutKingCallAddon.NAIL_STATE).orElse(null);
    }

    public static boolean isPinned(LivingEntity entity) {
        NailState state = getExisting(entity);
        return state != null && state.isPinned();
    }

    @Nullable
    public LivingEntity getEntity() {
        return entity;
    }

    public void setEntity(@Nullable LivingEntity entity) {
        this.entity = entity;
        if (entity != null) {
            this.entityId = entity.getId();
        }
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public boolean isInGround() {
        return isInGround;
    }

    public void setInGround(boolean inGround) {
        this.isInGround = inGround;
        syncData();
    }

    public boolean isWallClimbing() {
        return wallClimbing;
    }

    public void setWallClimbing(boolean wallClimbing) {
        this.wallClimbing = wallClimbing;
        syncData();
    }

    public BlockPos getClimbStartPos() {
        return climbStartPos;
    }

    public void setClimbStartPos(BlockPos climbStartPos) {
        this.climbStartPos = climbStartPos;
        syncData();
    }

    public int getNailCount() {
        return nailCount;
    }

    public void setNailCount(int nailCount) {
        this.nailCount = nailCount;
        updateNailed();
        syncData();
    }

    public void addNail() {
        nailCount++;
        updateNailed();
        syncData();
    }

    public void removeNail() {
        if (nailCount > 0) {
            nailCount--;
            updateNailed();
            syncData();
        }
    }

    public int getNutCount() {
        return nutCount;
    }

    public void setNutCount(int nutCount) {
        this.nutCount = nutCount;
        updateNailed();
        syncData();
    }

    public void addNut() {
        nutCount++;
        updateNailed();
        syncData();
    }

    public void removeNut() {
        if (nutCount > 0) {
            nutCount--;
            updateNailed();
            syncData();
        }
    }

    public int getNailedCount() {
        return nailedCount;
    }

    public void setNailedCount(int nailedCount) {
        this.nailedCount = nailedCount;
        syncData();
    }

    private void updateNailed() {
        nailedCount = Math.min(nailCount, nutCount);
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
        if (pinned && entity != null) {
            pinStartTick = entity.tickCount;
        }
        syncData();
    }

    public BlockPos getPinPos() {
        return pinPos;
    }

    public void setPinPos(BlockPos pinPos) {
        this.pinPos = pinPos;
        syncData();
    }

    public Direction getPinFace() {
        return pinFace;
    }

    public void setPinFace(Direction pinFace) {
        this.pinFace = pinFace;
        syncData();
    }

    public int getStruggleCount() {
        return struggleCount;
    }

    public void setStruggleCount(int struggleCount) {
        this.struggleCount = struggleCount;
        syncData();
    }

    public void incrementStruggle() {
        struggleCount++;
        syncData();
    }

    public int getPinStartTick() {
        return pinStartTick;
    }

    public void setPinStartTick(int pinStartTick) {
        this.pinStartTick = pinStartTick;
        syncData();
    }

    @Nullable
    public UUID getPinOwnerUUID() {
        return pinOwnerUUID;
    }

    public void setPinOwnerUUID(@Nullable UUID pinOwnerUUID) {
        this.pinOwnerUUID = pinOwnerUUID;
        syncData();
    }

    public void clearPinned() {
        pinned = false;
        pinPos = BlockPos.ZERO;
        pinFace = Direction.DOWN;
        struggleCount = 0;
        pinStartTick = 0;
        pinOwnerUUID = null;
        syncData();
    }

    public void syncData() {
        if (entity != null && !entity.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, NailStatePayload.of(this));
        }
    }

    public void deserializeFromPacket(NailStatePayload packet) {
        this.isInGround = packet.inGround();
        this.wallClimbing = packet.wallClimbing();
        this.climbStartPos = packet.climbStartPos();
        this.nailCount = packet.nailCount();
        this.nutCount = packet.nutCount();
        this.nailedCount = Math.min(packet.nailCount(), packet.nutCount());
        this.pinned = packet.pinned();
        this.pinPos = packet.pinPos();
        this.pinFace = packet.pinFace();
        this.struggleCount = packet.struggleCount();
        this.pinStartTick = packet.pinStartTick();
        this.pinOwnerUUID = packet.pinOwner().orElse(null);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("EntityId", entityId);
        nbt.putBoolean("IsInGround", isInGround);
        nbt.putBoolean("IsWallClimbing", wallClimbing);
        nbt.putInt("ClimbX", climbStartPos.getX());
        nbt.putInt("ClimbY", climbStartPos.getY());
        nbt.putInt("ClimbZ", climbStartPos.getZ());
        nbt.putInt("NailCount", nailCount);
        nbt.putInt("NutCount", nutCount);
        nbt.putInt("NailedCount", nailedCount);
        nbt.putBoolean("Pinned", pinned);
        nbt.putInt("PinX", pinPos.getX());
        nbt.putInt("PinY", pinPos.getY());
        nbt.putInt("PinZ", pinPos.getZ());
        nbt.putInt("PinFace", pinFace.get3DDataValue());
        nbt.putInt("StruggleCount", struggleCount);
        // an entity's tick counter restarts when it is loaded, so the save keeps how long the pin has run
        nbt.putInt("PinElapsedTicks", pinned && entity != null ? Math.max(0, entity.tickCount - pinStartTick) : 0);
        if (pinOwnerUUID != null) {
            nbt.putUUID("PinOwner", pinOwnerUUID);
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        entityId = nbt.getInt("EntityId");
        isInGround = nbt.getBoolean("IsInGround");
        wallClimbing = nbt.getBoolean("IsWallClimbing");
        climbStartPos = new BlockPos(nbt.getInt("ClimbX"), nbt.getInt("ClimbY"), nbt.getInt("ClimbZ"));
        nailCount = nbt.getInt("NailCount");
        nutCount = nbt.getInt("NutCount");
        nailedCount = nbt.getInt("NailedCount");
        pinned = nbt.getBoolean("Pinned");
        pinPos = new BlockPos(nbt.getInt("PinX"), nbt.getInt("PinY"), nbt.getInt("PinZ"));
        pinFace = Direction.from3DDataValue(nbt.getInt("PinFace"));
        struggleCount = nbt.getInt("StruggleCount");
        // a save without PinElapsedTicks held a tick of an earlier session: its pin runs its 600 ticks from now
        pinStartTick = pinned && entity != null ? entity.tickCount - nbt.getInt("PinElapsedTicks") : 0;
        pinOwnerUUID = nbt.hasUUID("PinOwner") ? nbt.getUUID("PinOwner") : null;
        // the saved id belonged to the entity's last session
        if (entity != null) {
            entityId = entity.getId();
        }
    }
}
