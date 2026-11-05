package rotp.addons.echoes.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.echoes.network.EchoesSoundPayloads;
import net.minecraft.resources.ResourceLocation;

/**
 * R772 F11: what the user's client knows of ACT1's sounds (the master's client copies in EchoesSoundSelection,
 * SoundNameOverrides, ClientSoundAttachmentHandler and SoundGiveEffectHandler.CLIENT_TARGETS). Plain data, so that the
 * actions' checks can read it on either side.
 */
public final class EchoesSoundClient {
    @Nullable private static ResourceLocation sound;
    private static boolean selfSound;
    private static final Set<ResourceLocation> FAVORITES = new LinkedHashSet<>();
    private static final Map<ResourceLocation, String> NAMES = new HashMap<>();
    private static int marks;
    private static final Set<UUID> GIVE_TARGETS = new HashSet<>();

    private EchoesSoundClient() {}

    public static void onSync(EchoesSoundPayloads.Sync payload) {
        sound = EchoesSoundPayloads.id(payload.sound());
        selfSound = payload.selfSound();
        FAVORITES.clear();
        for (String favorite : payload.favorites()) {
            ResourceLocation id = EchoesSoundPayloads.id(favorite);
            if (id != null) {
                FAVORITES.add(id);
            }
        }
        NAMES.clear();
        payload.names().forEach((key, name) -> {
            ResourceLocation id = EchoesSoundPayloads.id(key);
            if (id != null) {
                NAMES.put(id, name);
            }
        });
        marks = payload.marks();
    }

    public static void onGiveTargets(EchoesSoundPayloads.GiveTargets payload) {
        GIVE_TARGETS.clear();
        GIVE_TARGETS.addAll(payload.targets());
    }

    public static void clear() {
        sound = null;
        selfSound = false;
        FAVORITES.clear();
        NAMES.clear();
        marks = 0;
        GIVE_TARGETS.clear();
    }

    @Nullable
    public static ResourceLocation sound() {
        return sound;
    }

    public static boolean selfSound() {
        return selfSound;
    }

    public static boolean isFavorite(ResourceLocation id) {
        return FAVORITES.contains(id);
    }

    @Nullable
    public static String name(ResourceLocation id) {
        return NAMES.get(id);
    }

    // the picker shows a change at once; the server's Sync confirms it
    public static void putName(ResourceLocation id, String name) {
        if (name.isBlank()) {
            NAMES.remove(id);
        }
        else {
            NAMES.put(id, name.strip());
        }
    }

    public static void putFavorite(ResourceLocation id, boolean favorite) {
        if (favorite) {
            FAVORITES.add(id);
        }
        else {
            FAVORITES.remove(id);
        }
    }

    public static int marks() {
        return marks;
    }

    public static boolean gaveTo(UUID target) {
        return GIVE_TARGETS.contains(target);
    }

    public static boolean hasGiveTargets() {
        return !GIVE_TARGETS.isEmpty();
    }

    public static Set<UUID> giveTargets() {
        return GIVE_TARGETS;
    }
}
