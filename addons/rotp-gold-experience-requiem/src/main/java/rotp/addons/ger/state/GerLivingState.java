package rotp.addons.ger.state;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class GerLivingState
        implements INBTSerializable<CompoundTag> {
    /** Where an Undie loop death stands; NONE while the victim is alive. */
    public enum UndiePhase {
        NONE,
        /** The lethal hit was taken; the dying starts on the next tick. */
        PENDING,
        DYING,
        PULLING;

        private static UndiePhase byOrdinal(int ordinal) {
            UndiePhase[] values = values();
            return ordinal >= 0 && ordinal < values.length
                    ? values[ordinal]
                    : NONE;
        }
    }

    @Nullable
    private UUID disablerCaster;
    private int disablerTicks;

    @Nullable
    private UUID undieCaster;
    @Nullable
    private UUID undieSoulCaster;
    private int undieDeaths;
    private int rekillDelay;
    private UndiePhase undiePhase = UndiePhase.NONE;
    @Nullable
    private transient Boolean undieStandSummoned;
    @Nullable
    private transient Integer undieStandArms;
    private final Map<UUID, ReturnToZeroDisable>
            returnToZeroDisables = new LinkedHashMap<>();
    private boolean actionsDisabledSent;
    private boolean disablerSent;
    private transient int untouchableTicks;
    private transient int fireSample;
    private transient long answeredTick = Long.MIN_VALUE;
    private transient int answeredCause;
    private transient Map<Holder<MobEffect>, Long> answeredEffects;

    /** The Disabler's user for its 9 seconds: every hit on him is returned to zero. */
    public boolean untouchable() {
        return untouchableTicks > 0;
    }

    public void startUntouchable(int ticks) {
        untouchableTicks = Math.max(untouchableTicks, ticks);
    }

    /** @return the ticks left */
    public int tickUntouchable() {
        return untouchableTicks > 0 ? --untouchableTicks : 0;
    }

    /** His fire ticks when his last tick ended: what a cause of fire found before it set him alight. */
    public int fireSample() {
        return fireSample;
    }

    public void sampleFire(int fireTicks) {
        fireSample = fireTicks;
    }

    public void markAnswered(long gameTime, int causeId) {
        answeredTick = gameTime;
        answeredCause = causeId;
    }

    /** Whether a hit of this cause was returned to zero in this tick already. */
    public boolean answeredNow(long gameTime, int causeId) {
        return answeredTick == gameTime && answeredCause == causeId;
    }

    public boolean answeredAt(long gameTime) {
        return answeredTick == gameTime;
    }

    public void markEffectAnswered(Holder<MobEffect> effect, long until) {
        if (answeredEffects == null) {
            answeredEffects = new HashMap<>();
        }
        answeredEffects.values().removeIf(end -> end < until - 200L);
        answeredEffects.put(effect, until);
    }

    /** Whether the same effect was refused so lately that it is still the same harm. */
    public boolean effectAnswered(Holder<MobEffect> effect, long gameTime) {
        Long until = answeredEffects != null
                ? answeredEffects.get(effect)
                : null;
        return until != null && gameTime <= until;
    }

    /**
     * 1.16's ger_disabler tag, which the Disabler and Return to Zero's
     * lockdown gave: every Stand and non-Stand action of its holder refused.
     */
    public boolean actionsDisabled() {
        return disablerActive() || returnToZeroDisabled();
    }

    /** @return whether the holder's client has to be told the new state */
    public boolean markActionsDisabledSent(
            boolean disabled, boolean byDisabler) {
        if (actionsDisabledSent == disabled
                && disablerSent == byDisabler) {
            return false;
        }
        actionsDisabledSent = disabled;
        disablerSent = byDisabler;
        return true;
    }

    public void applyDisabler(UUID caster, int ticks) {
        disablerCaster = caster;
        disablerTicks = Math.max(disablerTicks, ticks);
    }

    public boolean disablerActive() {
        return disablerCaster != null && disablerTicks > 0;
    }

    @Nullable
    public UUID disablerCaster() {
        return disablerCaster;
    }

    public void tickDisabler() {
        if (disablerTicks > 0 && --disablerTicks == 0) {
            disablerCaster = null;
        }
    }

    /**
     * Starts the loop for the victim of a killing Finisher Punch. 1.16
     * UndiePunch only rewrote the caster and the tag: a strike on a victim
     * already in the loop keeps its death count and rekill timer.
     */
    public void armUndie(UUID caster) {
        boolean looping = undieActive();
        undieCaster = caster;
        undieSoulCaster = caster;
        if (!looping) {
            undieDeaths = 0;
            rekillDelay = 0;
            undiePhase = UndiePhase.NONE;
        }
    }

    /** Out of the loop for good, with nothing kept of it: a victim the loop may not hold. */
    public void clearUndie() {
        markFinalDeath();
        undieSoulCaster = null;
        undieDeaths = 0;
    }

    public boolean undieActive() {
        return undieCaster != null;
    }

    @Nullable
    public UUID undieCaster() {
        return undieCaster;
    }

    /** The only Stand user the soul of the final death may give Resolve to. */
    @Nullable
    public UUID undieSoulCaster() {
        return undieSoulCaster;
    }

    public int undieDeaths() {
        return undieDeaths;
    }

    public UndiePhase undiePhase() {
        return undiePhase;
    }

    public boolean inUndieDeath() {
        return undiePhase != UndiePhase.NONE;
    }

    public void acceptUndieDeath() {
        undiePhase = UndiePhase.PENDING;
        rekillDelay = 0;
    }

    public void startUndieDying() {
        undiePhase = UndiePhase.DYING;
    }

    public void startUndiePull() {
        undiePhase = UndiePhase.PULLING;
    }


    /**
     * Whether the victim's Stand was out when this loop death's window was
     * first seen; the window keeps it that way.
     */
    public boolean undieStandSummoned(boolean summonedNow) {
        if (undieStandSummoned == null) {
            undieStandSummoned = summonedNow;
        }
        return undieStandSummoned;
    }

    /**
     * Which arms the victim's arms-only Stand showed when this loop death's
     * window was first seen (bit 1 the main arm, bit 2 the off arm), or -1
     * when the Stand was not arms-only.
     */
    public int undieStandArms(int armsNow) {
        if (undieStandArms == null) {
            undieStandArms = armsNow;
        }
        return undieStandArms;
    }

    public void recordUndieRevival(int delay) {
        undieStandSummoned = null;
        undieStandArms = null;
        undiePhase = UndiePhase.NONE;
        undieDeaths++;
        rekillDelay = Math.max(1, delay);
    }

    public boolean tickRekillDelay() {
        return rekillDelay > 0 && --rekillDelay == 0;
    }

    public void markFinalDeath() {
        undieCaster = null;
        rekillDelay = 0;
        undiePhase = UndiePhase.NONE;
        undieStandSummoned = null;
        undieStandArms = null;
    }

    public void startReturnToZeroDisable(
            UUID owner, LivingEntity entity, int ticks) {
        returnToZeroDisables.remove(owner);
        // This rewind moved the entity: an older lockdown that outlasts this
        // one must not pull it back to where that one held it.
        returnToZeroDisables.replaceAll((other, disable) ->
                new ReturnToZeroDisable(
                        disable.ticks(),
                        entity.position(),
                        entity.getYRot(),
                        entity.getXRot()));
        returnToZeroDisables.put(
                owner,
                new ReturnToZeroDisable(
                        Math.max(1, ticks),
                        entity.position(),
                        entity.getYRot(),
                        entity.getXRot()));
    }

    public boolean returnToZeroDisabled() {
        return !returnToZeroDisables.isEmpty();
    }

    public void tickReturnToZeroDisable() {
        Iterator<Map.Entry<UUID, ReturnToZeroDisable>>
                iterator =
                        returnToZeroDisables.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ReturnToZeroDisable> entry =
                    iterator.next();
            ReturnToZeroDisable disable = entry.getValue();
            int remaining = disable.ticks() - 1;
            if (remaining <= 0) {
                iterator.remove();
            }
            else {
                entry.setValue(disable.withTicks(remaining));
            }
        }
    }

    public void enforceReturnToZeroDisable(
            LivingEntity entity) {
        ReturnToZeroDisable active = null;
        for (ReturnToZeroDisable disable :
                returnToZeroDisables.values()) {
            active = disable;
        }
        if (active != null) {
            entity.teleportTo(
                    active.position().x,
                    active.position().y,
                    active.position().z);
            entity.setYRot(active.yRot());
            entity.setXRot(active.xRot());
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
        entity.fallDistance = 0.0F;
    }

    public void clearReturnToZeroDisable(UUID owner) {
        returnToZeroDisables.remove(owner);
    }

    public void copyFrom(GerLivingState other) {
        // 1.16 kept a lockdown's end in persistent data, which neither a
        // respawn nor an End return carried over: the new player was free.
        disablerCaster = null;
        disablerTicks = 0;
        untouchableTicks = 0;
        undieCaster = other.undieCaster;
        // The restriction belongs to the body that died its final death.
        undieSoulCaster = other.undieCaster != null
                ? other.undieSoulCaster
                : null;
        undieDeaths = other.undieDeaths;
        rekillDelay = other.rekillDelay;
        undiePhase = other.undiePhase;
        returnToZeroDisables.clear();
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (disablerCaster != null) {
            tag.putUUID("DisablerCaster", disablerCaster);
        }
        tag.putInt("DisablerTicks", disablerTicks);
        if (undieCaster != null) {
            tag.putUUID("UndieCaster", undieCaster);
        }
        if (undieSoulCaster != null) {
            tag.putUUID("UndieSoulCaster", undieSoulCaster);
        }
        tag.putInt("UndieDeaths", undieDeaths);
        tag.putInt("RekillDelay", rekillDelay);
        tag.putByte("UndiePhase", (byte) undiePhase.ordinal());
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        disablerCaster = tag.hasUUID("DisablerCaster")
                ? tag.getUUID("DisablerCaster")
                : null;
        disablerTicks = Math.max(0, tag.getInt("DisablerTicks"));
        undieCaster = tag.hasUUID("UndieCaster")
                ? tag.getUUID("UndieCaster")
                : null;
        undieSoulCaster = tag.hasUUID("UndieSoulCaster")
                ? tag.getUUID("UndieSoulCaster")
                : undieCaster;
        undieDeaths = Math.max(0, tag.getInt("UndieDeaths"));
        rekillDelay = Math.max(0, tag.getInt("RekillDelay"));
        undiePhase = UndiePhase.byOrdinal(tag.getByte("UndiePhase"));
        returnToZeroDisables.clear();
    }

    private record ReturnToZeroDisable(
            int ticks,
            Vec3 position,
            float yRot,
            float xRot) {
        private ReturnToZeroDisable withTicks(int ticks) {
            return new ReturnToZeroDisable(
                    ticks, position, yRot, xRot);
        }
    }
}
