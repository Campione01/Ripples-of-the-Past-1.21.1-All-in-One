package rotp.addons.echoes.stand;

import java.util.Locale;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesAttachments;
import rotp.addons.echoes.init.EchoesEntities;
import rotp.addons.echoes.init.EchoesSounds;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class EchoesStandEntity extends StandEntity {
    private static final EntityDataAccessor<Float> USER_HEAD_YAW =
            SynchedEntityData.defineId(
                    EchoesStandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> USER_HEAD_PITCH =
            SynchedEntityData.defineId(
                    EchoesStandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> TAIL_OUT =
            SynchedEntityData.defineId(
                    EchoesStandEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double EASTER_EGG_ORIGIN_RADIUS = 300.0D;

    // The donors keep the 1.16 default offset, so the core default stays.
    public EchoesStandEntity(
            EntityType<? extends EchoesStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(USER_HEAD_YAW, 0.0F);
        builder.define(USER_HEAD_PITCH, 0.0F);
        builder.define(TAIL_OUT, false);
    }

    // R772 F11, master EchoesEntity.tick and EchoesACT2Model.setupAnim: ACT2 is drawn without its tail tip while the
    // tail is out (on a target, a block or in flight)
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && getType() == EchoesEntities.ACT2.get()) {
            entityData.set(TAIL_OUT, getUser() instanceof Player player
                    && player.hasData(EchoesAttachments.USER_STATE)
                    && RotpEchoes.userState(player).tailOut());
        }
    }

    public boolean isTailOut() {
        return entityData.get(TAIL_OUT);
    }

    @Override
    public void copyStandUserRotation(LivingEntity user) {
        super.copyStandUserRotation(user);
        if (!level().isClientSide()) {
            // Relative to the Stand's own body, which an action can turn
            // away from the user's.
            entityData.set(USER_HEAD_YAW, Mth.wrapDegrees(
                    user.yHeadRot - yBodyRot));
            entityData.set(USER_HEAD_PITCH, user.getXRot());
        }
    }

    // R772 F11, master EchoesEntity.playEasterEggSound: the author's greeting for a user named after him near the origin
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        LivingEntity user = getUser();
        if (!level().isClientSide() && user != null && user.position().length() <= EASTER_EGG_ORIGIN_RADIUS) {
            String name = user.getName().getString();
            if (name.contains("康神") || name.toLowerCase(Locale.ROOT).contains("kangshen")) {
                level().playSound(null, user.getX(), user.getY(), user.getZ(),
                        EchoesSounds.EASTER_EGG_SUMMON.get(), user.getSoundSource(), 1.0F, 1.0F);
            }
        }
    }

    public float getSyncedHeadYaw() {
        return entityData.get(USER_HEAD_YAW);
    }

    public float getSyncedHeadPitch() {
        return entityData.get(USER_HEAD_PITCH);
    }
}
