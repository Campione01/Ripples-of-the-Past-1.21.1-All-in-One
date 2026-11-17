package rotp.addons.spin.power;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Player attachment, independent of the current power, as in the donor. */
public final class SpinEnergy implements INBTSerializable<CompoundTag> {
    private float energy;

    public float get() {
        return energy;
    }

    public void set(float value, float max) {
        energy = Float.isFinite(value) ? Mth.clamp(value, 0.0F, Math.max(0.0F, max)) : 0.0F;
    }

    public boolean consume(float cost) {
        if (!Float.isFinite(cost)) return false;
        if (cost <= 0.0F) return true;
        if (energy < cost) return false;
        energy -= cost;
        return true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Energy", energy);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        set(tag.getFloat("Energy"), Float.MAX_VALUE);
    }
}
