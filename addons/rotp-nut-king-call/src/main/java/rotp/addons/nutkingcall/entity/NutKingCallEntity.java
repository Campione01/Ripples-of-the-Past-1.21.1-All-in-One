package rotp.addons.nutkingcall.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class NutKingCallEntity extends StandEntity {
    private static final EntityDataAccessor<Boolean> DISASSEMBLE_MODE =
            SynchedEntityData.defineId(NutKingCallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLYING_KICK_HIT =
            SynchedEntityData.defineId(NutKingCallEntity.class, EntityDataSerializers.BOOLEAN);

    public NutKingCallEntity(EntityType<? extends NutKingCallEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISASSEMBLE_MODE, false);
        builder.define(FLYING_KICK_HIT, false);
    }

    public boolean isDisassembleMode() {
        return entityData.get(DISASSEMBLE_MODE);
    }

    public void setDisassembleMode(boolean disassemble) {
        entityData.set(DISASSEMBLE_MODE, disassemble);
    }

    public void toggleDisassembleMode() {
        entityData.set(DISASSEMBLE_MODE, !entityData.get(DISASSEMBLE_MODE));
    }

    public boolean isFlyingKickHit() {
        return entityData.get(FLYING_KICK_HIT);
    }

    public void setFlyingKickHit(boolean hit) {
        entityData.set(FLYING_KICK_HIT, hit);
    }
}
