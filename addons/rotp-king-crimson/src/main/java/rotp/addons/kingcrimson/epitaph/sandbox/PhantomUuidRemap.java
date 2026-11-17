package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * The UUIDs of a room. Every root phantom has a UUID of its own, so that no phantom shares one with anything real;
 * in the saved data a copy is made from, each UUID of a root is replaced by its phantom's, as an int array and as a
 * whole string, so that anger, owner and thrower references find the phantoms. At the end the same walk puts the real
 * ones back. A UUID kept in another form (two longs, or inside a longer string) is not found.
 */
public final class PhantomUuidRemap {
    private final Map<UUID, UUID> toPhantom = new HashMap<>();
    private final Map<UUID, UUID> toReal = new HashMap<>();

    public UUID assign(UUID real) {
        UUID phantom = toPhantom.get(real);
        if (phantom == null) {
            phantom = UUID.randomUUID();
            assign(real, phantom);
        }
        return phantom;
    }

    public void assign(UUID real, UUID phantom) {
        toPhantom.put(real, phantom);
        toReal.put(phantom, real);
    }

    @Nullable
    public UUID phantomOf(UUID real) {
        return toPhantom.get(real);
    }

    @Nullable
    public UUID realOf(UUID phantom) {
        return toReal.get(phantom);
    }

    public void toPhantoms(CompoundTag tag) {
        walk(tag, toPhantom);
    }

    public void toReals(CompoundTag tag) {
        walk(tag, toReal);
    }

    private static void walk(CompoundTag tag, Map<UUID, UUID> map) {
        if (map.isEmpty()) {
            return;
        }
        for (String key : tag.getAllKeys().toArray(new String[0])) {
            Tag value = tag.get(key);
            Tag mapped = mapped(value, map);
            if (mapped != null) {
                tag.put(key, mapped);
            }
        }
    }

    // The replacement of a value that is a UUID of the map, null when it stays; compounds and lists are walked in place.
    @Nullable
    private static Tag mapped(@Nullable Tag value, Map<UUID, UUID> map) {
        if (value instanceof CompoundTag compound) {
            walk(compound, map);
        } else if (value instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag mapped = mapped(list.get(i), map);
                if (mapped != null) {
                    list.set(i, mapped);
                }
            }
        } else if (value instanceof IntArrayTag ints && ints.size() == 4) {
            UUID other = map.get(NbtUtils.loadUUID(ints));
            return other != null ? NbtUtils.createUUID(other) : null;
        } else if (value instanceof StringTag string && string.getAsString().length() == 36) {
            try {
                UUID other = map.get(UUID.fromString(string.getAsString()));
                return other != null ? StringTag.valueOf(other.toString()) : null;
            }
            catch (IllegalArgumentException notAUuid) {
                return null;
            }
        }
        return null;
    }
}
