package rotp.addons.whitesnake.buffdisc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * PATCH1 EffectRegistry: every registered mob effect plus the disc-only explosion can be injected, each at
 * amplifier 2 for 100 ticks (the explosion has no duration). PATCH1 built the list once in a static block; the
 * registry is frozen before any disc is used, so it is read when asked.
 */
public final class EffectRegistry {
    public static final int DEFAULT_AMPLIFIER = 2;
    public static final int DEFAULT_DURATION = 100;

    private EffectRegistry() {}

    public static List<ResourceLocation> getAllEffects() {
        List<ResourceLocation> effects =
                new ArrayList<>(BuiltInRegistries.MOB_EFFECT.keySet());
        effects.add(DiscEffectData.EXPLOSION);
        return effects;
    }

    public static boolean isEffectExists(ResourceLocation id) {
        return id != null
                && (DiscEffectData.EXPLOSION.equals(id)
                        || BuiltInRegistries.MOB_EFFECT.containsKey(id));
    }

    public static int getDefaultAmplifier(ResourceLocation id) {
        return DEFAULT_AMPLIFIER;
    }

    public static int calculateDuration(
            ResourceLocation id, int amplifier) {
        return DiscEffectData.EXPLOSION.equals(id)
                ? 0
                : DEFAULT_DURATION;
    }
}
