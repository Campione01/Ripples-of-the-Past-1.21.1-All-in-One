package rotp.addons.sexpistols.content.sexpistols;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 0.1.13 SexPistolsForcedTarget: an entity or a block the members shoot
// at before anything else.
public record SexPistolsForcedTarget(
        @Nullable UUID entityUuid,
        int entityId,
        @Nullable BlockPos blockPos) {
    public static SexPistolsForcedTarget entity(LivingEntity entity) {
        return new SexPistolsForcedTarget(
                entity.getUUID(), entity.getId(), null);
    }

    public static SexPistolsForcedTarget block(BlockPos pos) {
        return new SexPistolsForcedTarget(null, -1, pos.immutable());
    }

    public boolean isEntity() {
        return blockPos == null;
    }

    public boolean matchesEntity(Entity entity) {
        return isEntity()
                && entity != null
                && (entityUuid != null
                        ? entityUuid.equals(entity.getUUID())
                        : entityId == entity.getId());
    }

    public boolean matchesBlock(BlockPos pos) {
        return blockPos != null && blockPos.equals(pos);
    }

    @Nullable
    public LivingEntity living(Level level) {
        if (!isEntity() || level == null) {
            return null;
        }
        Entity entity = level instanceof ServerLevel serverLevel
                && entityUuid != null
                ? serverLevel.getEntity(entityUuid)
                : level.getEntity(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }

    public boolean isValid(Level level) {
        if (isEntity()) {
            LivingEntity living = living(level);
            return living != null && living.isAlive();
        }
        return level != null
                && level.isLoaded(blockPos)
                && !level.getBlockState(blockPos).isAir();
    }

    public Vec3 aimPoint(Level level) {
        if (isEntity()) {
            LivingEntity living = living(level);
            return living != null
                    ? living.getBoundingBox().getCenter()
                    : Vec3.ZERO;
        }
        return Vec3.atCenterOf(blockPos);
    }

    // The client only needs the network id of an entity target.
    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(isEntity());
        if (isEntity()) {
            buffer.writeVarInt(entityId);
        }
        else {
            buffer.writeBlockPos(blockPos);
        }
    }

    public static SexPistolsForcedTarget read(
            RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? new SexPistolsForcedTarget(
                        null, buffer.readVarInt(), null)
                : new SexPistolsForcedTarget(
                        null, -1, buffer.readBlockPos());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (isEntity()) {
            if (entityUuid != null) {
                tag.putUUID("Entity", entityUuid);
            }
        }
        else {
            tag.put("Block", NbtUtils.writeBlockPos(blockPos));
        }
        return tag;
    }

    @Nullable
    public static SexPistolsForcedTarget load(CompoundTag tag) {
        if (tag.hasUUID("Entity")) {
            return new SexPistolsForcedTarget(
                    tag.getUUID("Entity"), -1, null);
        }
        return NbtUtils.readBlockPos(tag, "Block")
                .map(SexPistolsForcedTarget::block)
                .orElse(null);
    }
}
