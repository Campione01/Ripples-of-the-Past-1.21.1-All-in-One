package rotp.addons.echoes.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.echoes.state.EchoesOnomatopoeia;
import rotp.addons.echoes.state.EchoesOnomatopoeiaService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * R772 F11: ACT2's word made a thing on a block, its entity form (the master's OnomatopoeiaBlockEntity): it stands
 * at the centre of the block it was put on, others bump into it, and every half second it acts on each living entity
 * within 0.6 blocks of it; it is saved with the world.
 */
public final class EchoesOnomatopoeiaWordEntity extends Entity {
    private static final EntityDataAccessor<String> WORD =
            SynchedEntityData.defineId(EchoesOnomatopoeiaWordEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(EchoesOnomatopoeiaWordEntity.class, EntityDataSerializers.INT);
    private static final int TRIGGER_INTERVAL_TICKS = 10;
    private static final double TRIGGER_RANGE = 0.6D;

    @Nullable private UUID owner;

    public EchoesOnomatopoeiaWordEntity(EntityType<? extends EchoesOnomatopoeiaWordEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WORD, "");
        builder.define(VARIANT, 0);
    }

    public void set(UUID owner, EchoesOnomatopoeia word, int variant) {
        this.owner = owner;
        entityData.set(WORD, word.id());
        entityData.set(VARIANT, variant);
    }

    @Nullable
    public EchoesOnomatopoeia word() {
        return EchoesOnomatopoeia.byId(entityData.get(WORD));
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Override
    public void tick() {
        super.tick();
        EchoesOnomatopoeia word = word();
        if (level().isClientSide() || word == null || level().getGameTime() % TRIGGER_INTERVAL_TICKS != 0L) {
            return;
        }
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(TRIGGER_RANGE), LivingEntity::isAlive)) {
            EchoesOnomatopoeiaService.apply(word, entity);
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(WORD, tag.getString("Word"));
        entityData.set(VARIANT, tag.getInt("Variant"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Word", entityData.get(WORD));
        tag.putInt("Variant", entityData.get(VARIANT));
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }
}
