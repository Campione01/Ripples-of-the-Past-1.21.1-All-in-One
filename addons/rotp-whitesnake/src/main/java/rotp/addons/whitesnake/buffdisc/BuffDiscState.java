package rotp.addons.whitesnake.buffdisc;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandDamageContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * The per-entity values PATCH1 kept in raw persistent data: the delayed disc explosion (PendingExplosion,
 * ExplosionTimer, ExplosionPower), and for players UnlockedEffects and EnlightenmentCount.
 */
public final class BuffDiscState
        implements INBTSerializable<CompoundTag> {
    private static final String EXPLOSION_TIMER_TAG = "ExplosionTimer";
    private static final String EXPLOSION_POWER_TAG = "ExplosionPower";
    private static final String UNLOCKED_EFFECTS_TAG = "UnlockedEffects";
    private static final String ENLIGHTENMENT_COUNT_TAG =
            "EnlightenmentCount";

    private boolean pendingExplosion;
    private int explosionTimer;
    private float explosionPower;
    // not saved: the explosion of a world reloaded in between is nobody's
    @Nullable
    private UUID explosionOwner;
    private final Set<String> unlockedEffects = new LinkedHashSet<>();
    private int enlightenmentCount;
    // not saved: the one injection the last Inject Effect action allows
    private long injectPermitExpiry = Long.MIN_VALUE;

    public void grantInjectPermit(long expiryGameTime) {
        injectPermitExpiry = expiryGameTime;
    }

    public boolean consumeInjectPermit(long gameTime) {
        boolean valid = gameTime <= injectPermitExpiry;
        injectPermitExpiry = Long.MIN_VALUE;
        return valid;
    }

    public void scheduleExplosion(int ticks, float power) {
        pendingExplosion = true;
        explosionTimer = ticks;
        explosionPower = power;
        StandDamageContext.Owner running = StandDamageContext.running();
        explosionOwner = running != null && running.power().getUser() != null ? running.power().getUser().getUUID() : null;
    }

    /** The user whose Stand's disc set the explosion going (not saved), once; null when none did. */
    @Nullable
    public UUID takeExplosionOwner() {
        UUID owner = explosionOwner;
        explosionOwner = null;
        return owner;
    }

    public boolean hasPendingExplosion() {
        return pendingExplosion;
    }

    /** Counts the timer down; the power once it has run out, else a negative value. */
    public float tickExplosion() {
        if (!pendingExplosion) {
            return -1.0F;
        }
        if (explosionTimer > 0) {
            explosionTimer--;
            return -1.0F;
        }
        pendingExplosion = false;
        explosionTimer = 0;
        float power = explosionPower;
        explosionPower = 0.0F;
        return power;
    }

    public Set<String> unlockedEffects() {
        return unlockedEffects;
    }

    public int enlightenmentCount() {
        return enlightenmentCount;
    }

    public void setEnlightenmentCount(int count) {
        enlightenmentCount = count;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (pendingExplosion) {
            tag.putInt(EXPLOSION_TIMER_TAG, explosionTimer);
            tag.putFloat(EXPLOSION_POWER_TAG, explosionPower);
        }
        if (!unlockedEffects.isEmpty()) {
            ListTag list = new ListTag();
            unlockedEffects.forEach(id -> list.add(StringTag.valueOf(id)));
            tag.put(UNLOCKED_EFFECTS_TAG, list);
        }
        if (enlightenmentCount > 0) {
            tag.putInt(ENLIGHTENMENT_COUNT_TAG, enlightenmentCount);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        pendingExplosion = tag.contains(EXPLOSION_TIMER_TAG);
        explosionTimer = tag.getInt(EXPLOSION_TIMER_TAG);
        explosionPower = tag.getFloat(EXPLOSION_POWER_TAG);
        unlockedEffects.clear();
        ListTag list = tag.getList(UNLOCKED_EFFECTS_TAG, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            unlockedEffects.add(list.getString(i));
        }
        enlightenmentCount = tag.getInt(ENLIGHTENMENT_COUNT_TAG);
    }
}
