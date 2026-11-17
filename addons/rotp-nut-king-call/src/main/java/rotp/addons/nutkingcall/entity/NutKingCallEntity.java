package rotp.addons.nutkingcall.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class NutKingCallEntity extends StandEntity {
    private static final EntityDataAccessor<Boolean> DISASSEMBLE_MODE =
            SynchedEntityData.defineId(NutKingCallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLYING_KICK_HIT =
            SynchedEntityData.defineId(NutKingCallEntity.class, EntityDataSerializers.BOOLEAN);
    // the Assemble / Disassemble toggle is its user's, through a recall (owner's rule of 2026-11-05)
    private static final String MODE_TAG = "rotp_nut_king_call:disassemble_mode";
    private boolean modeRestored;

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
        rememberMode();
    }

    public void toggleDisassembleMode() {
        setDisassembleMode(!entityData.get(DISASSEMBLE_MODE));
    }

    private void rememberMode() {
        LivingEntity user = getUser();
        if (!level().isClientSide() && user != null) {
            user.getPersistentData().putBoolean(MODE_TAG, entityData.get(DISASSEMBLE_MODE));
        }
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity user = getUser();
        if (!modeRestored && !level().isClientSide() && user != null) {
            modeRestored = true;
            entityData.set(DISASSEMBLE_MODE, user.getPersistentData().getBoolean(MODE_TAG));
        }
    }

    public boolean isFlyingKickHit() {
        return entityData.get(FLYING_KICK_HIT);
    }

    public void setFlyingKickHit(boolean hit) {
        entityData.set(FLYING_KICK_HIT, hit);
    }
}
