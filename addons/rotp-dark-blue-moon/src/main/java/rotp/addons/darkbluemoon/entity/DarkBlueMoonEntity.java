package rotp.addons.darkbluemoon.entity;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 1.16 DarkBlueMoonEntity, with the whirlpool stay of 1.16 WhirlpoolStandStayHandler kept on the Stand itself. */
public class DarkBlueMoonEntity extends StandEntity {
    private static final EntityDataAccessor<Boolean> HAS_BARNACLE_TRAP =
            SynchedEntityData.defineId(DarkBlueMoonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> TRAP_POS =
            SynchedEntityData.defineId(DarkBlueMoonEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> STAYING =
            SynchedEntityData.defineId(DarkBlueMoonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> STAY_POS =
            SynchedEntityData.defineId(DarkBlueMoonEntity.class, EntityDataSerializers.VECTOR3);

    /** 1.16 WhirlpoolStandStayHandler.STAY_DURATION. */
    public static final int STAY_DURATION = 200;

    // The owner's rule of 2026-11-05: a placed trap stays through a recall, on the removed entity, until the next summon
    // takes it over or its user dies, leaves, changes level or loses the Stand.
    public static final class Recalled {
        @Nullable
        DarkBlueMoonEntity stand;
    }

    private int stayTicksLeft;
    private int barnacleTrapDelay;

    public DarkBlueMoonEntity(EntityType<? extends DarkBlueMoonEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HAS_BARNACLE_TRAP, false);
        builder.define(TRAP_POS, BlockPos.ZERO);
        builder.define(STAYING, false);
        builder.define(STAY_POS, new Vector3f());
    }

    /** The user's summoned Dark Blue Moon, or the recalled one whose trap stays. */
    @Nullable
    public static DarkBlueMoonEntity current(@Nullable LivingEntity user) {
        if (user == null) {
            return null;
        }
        StandPower power = StandPower.get(user);
        boolean holdsTheStand = power != null && power.hasPower()
                && power.getPowerType() == DarkBlueMoonAddon.DARK_BLUE_MOON.get();
        if (holdsTheStand && power.getSummonedStandEntity() instanceof DarkBlueMoonEntity dbm) {
            return dbm;
        }
        DarkBlueMoonEntity recalled = user.hasData(DarkBlueMoonAddon.RECALLED_STAND)
                ? user.getData(DarkBlueMoonAddon.RECALLED_STAND).stand
                : null;
        if (recalled != null && (!holdsTheStand || recalled.getUser() != user || !user.isAlive() || user.isRemoved()
                || recalled.level() != user.level())) {
            forgetRecalled(user);
            return null;
        }
        return recalled;
    }

    public static void forgetRecalled(LivingEntity user) {
        if (user.hasData(DarkBlueMoonAddon.RECALLED_STAND)) {
            user.getData(DarkBlueMoonAddon.RECALLED_STAND).stand = null;
        }
    }

    @Override
    public void setUserAndPower(LivingEntity user, StandPower power) {
        super.setUserAndPower(user, power);
        if (user == null || level().isClientSide()) {
            return;
        }
        if (!user.hasData(DarkBlueMoonAddon.RECALLED_STAND)) {
            return;
        }
        DarkBlueMoonEntity recalled = user.getData(DarkBlueMoonAddon.RECALLED_STAND).stand;
        forgetRecalled(user);
        if (recalled != null && recalled != this && recalled.getUser() == user && recalled.level() == level()) {
            entityData.set(TRAP_POS, recalled.getBarnacleTrapPos());
            entityData.set(HAS_BARNACLE_TRAP, recalled.hasBarnacleTrap());
            barnacleTrapDelay = recalled.barnacleTrapDelay;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        LivingEntity user = getUser();
        if (!level().isClientSide() && hasBarnacleTrap() && user != null && user.isAlive() && !user.isRemoved()
                && user.level() == level()) {
            user.getData(DarkBlueMoonAddon.RECALLED_STAND).stand = this;
        }
        super.remove(reason);
    }

    public boolean hasBarnacleTrap() {
        return entityData.get(HAS_BARNACLE_TRAP);
    }

    public void setBarnacleTrapPos(BlockPos pos) {
        entityData.set(TRAP_POS, pos.immutable());
        entityData.set(HAS_BARNACLE_TRAP, true);
    }

    public void clearBarnacleTrap() {
        entityData.set(HAS_BARNACLE_TRAP, false);
    }

    public BlockPos getBarnacleTrapPos() {
        return entityData.get(TRAP_POS);
    }

    /** 1.16 kept this count in the Stand's persistent data ("BarnacleTrapDelay"). */
    public int incBarnacleTrapDelay() {
        return ++barnacleTrapDelay;
    }

    public void resetBarnacleTrapDelay() {
        barnacleTrapDelay = 0;
    }

    /** 1.16 WhirlpoolStandStayHandler.startStay: the Stand stays where it is for 200 ticks. */
    public void startStay() {
        if (!level().isClientSide()) {
            setDeltaMovement(Vec3.ZERO);
            Vec3 pos = position();
            entityData.set(STAY_POS, new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
            entityData.set(STAYING, true);
            stayTicksLeft = STAY_DURATION;
        }
    }

    public boolean isStaying() {
        return entityData.get(STAYING);
    }

    @Override
    public void updatePosition(LivingEntity user) {
        if (isStaying()) {
            Vector3f stay = entityData.get(STAY_POS);
            setDeltaMovement(Vec3.ZERO);
            setPos(stay.x(), stay.y(), stay.z());
            xo = stay.x();
            yo = stay.y();
            zo = stay.z();
            xOld = stay.x();
            yOld = stay.y();
            zOld = stay.z();
            return;
        }
        super.updatePosition(user);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (stayTicksLeft > 0 && --stayTicksLeft <= 0) {
            entityData.set(STAYING, false);
            retract();
        }
        LivingEntity user = getUser();
        boolean inWater = user != null && user.isAlive() && DbmWater.isInWater(user);
        if (inWater) {
            addEffect(new MobEffectInstance(DarkBlueMoonAddon.DARK_BLUE_MOON_WATER_STAND, 40, 0, false, false, true));
        }
        else {
            removeEffect(DarkBlueMoonAddon.DARK_BLUE_MOON_WATER_STAND);
        }
    }
}
