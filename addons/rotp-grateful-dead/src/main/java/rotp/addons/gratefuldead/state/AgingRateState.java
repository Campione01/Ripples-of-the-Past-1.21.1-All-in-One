package rotp.addons.gratefuldead.state;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Next Album 0.1.13 AgingRateState: a player's own heat on the aging rate
 * (sprinting, mining, jumping and spending Stand stamina; digesting food)
 * and an ice cube's cooling, all decaying with time. Server only.
 */
public final class AgingRateState implements INBTSerializable<CompoundTag> {
    public static final float MOVE_HEAT_CAP = 0.4F;
    public static final float STAND_HEAT_CAP = 0.7F;
    public static final float DIGESTION_CAP = 0.18F;
    public static final float PRECISE_DIGESTION_CAP = 0.09F;
    public static final float SPRINT_HEAT_PER_TICK = 0.0035F;
    public static final float JUMP_HEAT = 0.05F;
    public static final float MINE_HEAT_PER_TICK = 0.0025F;
    public static final float DECAY_PER_TICK = 0.006F;
    public static final int IDLE_BEFORE_DECAY_TICKS = 30;
    public static final float DIGESTION_DECAY_PER_TICK = 7.5E-4F;
    public static final float ICE_COOL_CAP = 0.55F;
    // 0.1.13 AgingRateState: a body downed in Resolve cools down.
    public static final float DOWNED_COOL_PER_TICK = 0.0035F;
    public static final float DOWNED_COOL_CAP = 1.0F;
    public static final String RESOLVE_DOWNED_KEY = "RotpNextAlbumResolveDowned";

    private float exertion;
    private float digestion;
    private int idleTicks;
    private float lastStamina;
    private boolean hasLastStamina;
    private int iceCoolTicks;
    private float iceCoolAmount;
    private float downedCool;

    public static AgingRateState get(LivingEntity entity) {
        return entity.getData(GratefulDeadAddon.AGING_RATE);
    }

    public static AgingRateState getIfPresent(LivingEntity entity) {
        return entity.hasData(GratefulDeadAddon.AGING_RATE)
                ? entity.getData(GratefulDeadAddon.AGING_RATE)
                : null;
    }

    public float exertion() {
        return exertion;
    }

    public float digestion() {
        return digestion;
    }

    public float downedCool() {
        return downedCool;
    }

    /** rotp_nextalbum's downed Resolve, read from the player's persistent data by its key. */
    public static boolean isResolveDowned(LivingEntity entity) {
        return entity.getPersistentData().getCompound(RESOLVE_DOWNED_KEY)
                .getBoolean("Downed");
    }

    // 0.1.13 cleared the cooling when the downed state ended.
    public void tickDownedCool(boolean downed) {
        downedCool = downed
                ? Math.min(DOWNED_COOL_CAP, downedCool + DOWNED_COOL_PER_TICK)
                : 0.0F;
    }

    public float currentIceCool() {
        return iceCoolTicks > 0 ? iceCoolAmount : 0.0F;
    }

    public void addMoveHeat(float amount) {
        if (amount <= 0.0F) {
            return;
        }
        idleTicks = 0;
        if (exertion < MOVE_HEAT_CAP) {
            exertion = Math.min(MOVE_HEAT_CAP, exertion + amount);
        }
    }

    public void addStandHeat(float amount) {
        if (amount <= 0.0F) {
            return;
        }
        idleTicks = 0;
        exertion = Math.min(STAND_HEAT_CAP, exertion + amount);
    }

    public void trackStamina(float stamina, float maxStamina) {
        if (hasLastStamina && maxStamina > 0.0F && stamina < lastStamina) {
            addStandHeat(STAND_HEAT_CAP * (lastStamina - stamina) / maxStamina);
        }
        lastStamina = stamina;
        hasLastStamina = true;
    }

    public void addDigestion(float amount, float cap) {
        if (amount <= 0.0F) {
            return;
        }
        digestion += (cap - digestion) * Mth.clamp(amount, 0.0F, 1.0F);
        digestion = Mth.clamp(digestion, 0.0F, cap);
    }

    public void startIceCool(float amount, int ticks) {
        iceCoolAmount = Math.min(iceCoolAmount + amount, ICE_COOL_CAP);
        iceCoolTicks = Math.max(iceCoolTicks, ticks);
    }

    public void tickDecay(float digestionCap) {
        idleTicks++;
        if (idleTicks > IDLE_BEFORE_DECAY_TICKS && exertion > 0.0F) {
            exertion = Math.max(0.0F, exertion - DECAY_PER_TICK);
        }
        if (digestion > 0.0F) {
            digestion = Math.max(
                    0.0F,
                    digestion - DIGESTION_DECAY_PER_TICK * (digestionCap / DIGESTION_CAP));
        }
        if (iceCoolTicks > 0 && --iceCoolTicks <= 0) {
            iceCoolAmount = 0.0F;
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Exertion", exertion);
        tag.putFloat("Digestion", digestion);
        tag.putInt("IdleTicks", idleTicks);
        if (hasLastStamina) {
            tag.putFloat("LastStamina", lastStamina);
        }
        tag.putInt("IceCoolTicks", iceCoolTicks);
        tag.putFloat("IceCoolAmount", iceCoolAmount);
        tag.putFloat("DownedCool", downedCool);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        exertion = Mth.clamp(tag.getFloat("Exertion"), 0.0F, STAND_HEAT_CAP);
        digestion = Mth.clamp(tag.getFloat("Digestion"), 0.0F, DIGESTION_CAP);
        idleTicks = Math.max(0, tag.getInt("IdleTicks"));
        hasLastStamina = tag.contains("LastStamina");
        lastStamina = tag.getFloat("LastStamina");
        iceCoolTicks = Math.max(0, tag.getInt("IceCoolTicks"));
        iceCoolAmount = Mth.clamp(tag.getFloat("IceCoolAmount"), 0.0F, ICE_COOL_CAP);
        downedCool = Mth.clamp(tag.getFloat("DownedCool"), 0.0F, DOWNED_COOL_CAP);
    }
}
