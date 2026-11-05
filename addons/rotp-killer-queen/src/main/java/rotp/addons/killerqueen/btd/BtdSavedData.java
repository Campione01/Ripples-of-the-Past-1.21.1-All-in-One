package rotp.addons.killerqueen.btd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** 1.6.10 (2026-10-20) com.zeml.rotp_zkq.btd.BtdSavedData: the running Bite the Dust instances, kept with the world. */
public final class BtdSavedData extends SavedData {
    private static final String NAME = "rotp_zkq_bite_the_dust";
    private static final SavedData.Factory<BtdSavedData> FACTORY =
            new SavedData.Factory<>(BtdSavedData::new, BtdSavedData::load);

    private final Map<UUID, BtdInstance> byOwner = new LinkedHashMap<>();
    private final Set<UUID> pendingKills = new HashSet<>();

    public static BtdSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(FACTORY, NAME);
    }

    @Nullable
    public BtdInstance getByOwner(UUID owner) {
        return byOwner.get(owner);
    }

    @Nullable
    public BtdInstance getByCarrier(UUID carrier) {
        for (BtdInstance inst : byOwner.values()) {
            if (inst.carrier.equals(carrier)) {
                return inst;
            }
        }
        return null;
    }

    public void add(BtdInstance inst) {
        byOwner.put(inst.owner, inst);
        setDirty();
    }

    public void remove(BtdInstance inst) {
        if (byOwner.get(inst.owner) == inst) {
            byOwner.remove(inst.owner);
            setDirty();
        }
    }

    public List<BtdInstance> all() {
        return new ArrayList<>(byOwner.values());
    }

    public Set<UUID> getPendingKills() {
        return pendingKills;
    }

    public static BtdSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        BtdSavedData data = new BtdSavedData();
        for (Tag value : tag.getList("Instances", Tag.TAG_COMPOUND)) {
            BtdInstance inst = value instanceof CompoundTag entry
                    ? BtdInstance.load(entry)
                    : null;
            if (inst != null && inst.state.locksOwnerStand()) {
                data.byOwner.put(inst.owner, inst);
            }
        }
        for (Tag value : tag.getList("PendingKills", Tag.TAG_INT_ARRAY)) {
            data.pendingKills.add(NbtUtils.loadUUID(value));
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BtdInstance inst : byOwner.values()) {
            list.add(inst.save());
        }
        tag.put("Instances", list);
        ListTag kills = new ListTag();
        for (UUID uuid : pendingKills) {
            kills.add(NbtUtils.createUUID(uuid));
        }
        tag.put("PendingKills", kills);
        return tag;
    }
}
