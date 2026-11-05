package rotp.addons.echoes.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.state.EchoesOnomatopoeia;
import rotp.addons.echoes.state.EchoesOnomatopoeiaService;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * R772 F11: ACT2's tail shot as a needle carrying the chosen word (the master's OnomatopoeiaTailNeedleEntity): it
 * flies for at most 200 ticks; a living target takes 2 magic damage and keeps the word; a block keeps it as a mark on
 * the face hit, or as a thing on the block in the entity form (Youhuo, or a face that cannot take a mark, sends it
 * back); recalled, it flies back to its owner at 2.5 blocks a tick through anything, and the tail is in again when it
 * arrives.
 */
public final class EchoesTailNeedleEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<String> WORD =
            SynchedEntityData.defineId(EchoesTailNeedleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(EchoesTailNeedleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RETURNING =
            SynchedEntityData.defineId(EchoesTailNeedleEntity.class, EntityDataSerializers.BOOLEAN);
    public static final double SPEED = 3.0D;
    private static final int MAX_LIFE = 200;
    private static final float DIRECT_DAMAGE = 2.0F;

    private int lifeTicks;
    private boolean hitProcessed;
    // master ENTITY_FORM: the user's form when the tail was shot
    private boolean entityForm;

    public EchoesTailNeedleEntity(EntityType<? extends EchoesTailNeedleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WORD, "");
        builder.define(VARIANT, 0);
        builder.define(RETURNING, false);
    }

    public void setWord(EchoesOnomatopoeia word, int variant) {
        entityData.set(WORD, word.id());
        entityData.set(VARIANT, variant);
    }

    @Nullable
    public EchoesOnomatopoeia word() {
        return EchoesOnomatopoeia.byId(entityData.get(WORD));
    }

    public void setEntityForm(boolean entityForm) {
        this.entityForm = entityForm;
    }

    public boolean isReturning() {
        return entityData.get(RETURNING);
    }

    public void startReturning() {
        entityData.set(RETURNING, true);
        noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        lifeTicks++;
        if (level().isClientSide()) {
            return;
        }
        if (lifeTicks > MAX_LIFE * 2) {
            releaseTail();
            return;
        }
        if (isReturning()) {
            tickReturning();
        }
        else if (lifeTicks > MAX_LIFE || isInWater() || isInLava()) {
            releaseTail();
        }
    }

    private void tickReturning() {
        ServerPlayer owner = ownerPlayer();
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            releaseTail();
            return;
        }
        Vec3 dir = owner.position().add(0.0D, owner.getBbHeight() * 0.5D, 0.0D).subtract(position());
        if (dir.length() < 1.5D) {
            releaseTail();
            return;
        }
        setDeltaMovement(dir.normalize().scale(2.5D));
    }

    @Override
    protected double getDefaultGravity() {
        return isReturning() ? 0.0D : super.getDefaultGravity();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (isReturning() || !super.canHitEntity(target)) {
            return false;
        }
        ServerPlayer owner = ownerPlayer();
        StandPower power = owner != null ? StandPower.get(owner) : null;
        return target != owner && (power == null || target != power.getSummonedStandEntity());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide() || isReturning() || hitProcessed) {
            return;
        }
        hitProcessed = true;
        EchoesOnomatopoeia word = word();
        ServerPlayer owner = ownerPlayer();
        if (result.getEntity() instanceof LivingEntity living && word != null && owner != null) {
            living.hurt(damageSources().magic(), DIRECT_DAMAGE);
            EchoesOnomatopoeiaService.attachFromNeedle(living, owner, word, entityData.get(VARIANT));
            discard();
        }
        else {
            startReturning();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide() || isReturning() || hitProcessed) {
            return;
        }
        hitProcessed = true;
        EchoesOnomatopoeia word = word();
        ServerPlayer owner = ownerPlayer();
        if (word != null && owner != null && level() instanceof ServerLevel serverLevel
                && EchoesOnomatopoeiaService.markBlock(serverLevel, owner.getUUID(), result.getBlockPos(),
                        result.getDirection(), word, entityData.get(VARIANT), entityForm)) {
            discard();
        }
        else {
            startReturning();
        }
    }

    private void releaseTail() {
        ServerPlayer owner = ownerPlayer();
        if (owner != null) {
            RotpEchoes.userState(owner).setTailOut(false);
        }
        discard();
    }

    @Nullable
    private ServerPlayer ownerPlayer() {
        UUID owner = getOwner() != null ? getOwner().getUUID() : null;
        return owner != null && level() instanceof ServerLevel serverLevel
                ? serverLevel.getServer().getPlayerList().getPlayer(owner)
                : null;
    }
}
