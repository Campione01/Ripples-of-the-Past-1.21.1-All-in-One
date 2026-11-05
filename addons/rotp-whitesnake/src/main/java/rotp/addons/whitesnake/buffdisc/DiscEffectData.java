package rotp.addons.whitesnake.buffdisc;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * PATCH1 DiscItem NBT (EffectId, Amplifier, Duration; MultiDisc with EffectIds) as one typed item component.
 */
public record DiscEffectData(
        Optional<ResourceLocation> effect,
        int amplifier,
        int duration,
        List<ResourceLocation> effects) {
    public static final ResourceLocation INSTANT_KILL =
            WhitesnakeAddon.id("instant_kill");
    public static final ResourceLocation EXPLOSION =
            WhitesnakeAddon.id("explosion");
    // the effect_disc_speed recipe result: rolled once the disc reaches an inventory
    public static final ResourceLocation RANDOM =
            WhitesnakeAddon.id("random");
    private static final int MAX_EFFECTS = 512;

    public static final Codec<DiscEffectData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("effect")
                            .forGetter(DiscEffectData::effect),
                    Codec.INT.optionalFieldOf("amplifier", 0)
                            .forGetter(DiscEffectData::amplifier),
                    Codec.INT.optionalFieldOf("duration", 0)
                            .forGetter(DiscEffectData::duration),
                    ResourceLocation.CODEC.sizeLimitedListOf(MAX_EFFECTS)
                            .optionalFieldOf("effects", List.of())
                            .forGetter(DiscEffectData::effects))
                    .apply(instance, DiscEffectData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, DiscEffectData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
                    DiscEffectData::effect,
                    ByteBufCodecs.VAR_INT,
                    DiscEffectData::amplifier,
                    ByteBufCodecs.VAR_INT,
                    DiscEffectData::duration,
                    ResourceLocation.STREAM_CODEC.apply(
                            ByteBufCodecs.list(MAX_EFFECTS)),
                    DiscEffectData::effects,
                    DiscEffectData::new);

    public DiscEffectData {
        effect = effect != null ? effect : Optional.empty();
        effects = effects != null ? List.copyOf(effects) : List.of();
    }

    public static DiscEffectData single(
            ResourceLocation effect, int amplifier, int duration) {
        return new DiscEffectData(
                Optional.of(effect), amplifier, duration, List.of());
    }

    public static DiscEffectData multi(List<ResourceLocation> effects) {
        return new DiscEffectData(
                Optional.empty(), 0, 0, effects);
    }

    public boolean isMulti() {
        return effect.isEmpty() && !effects.isEmpty();
    }

    public boolean hasEffect() {
        return effect.isPresent();
    }

    public boolean isRandom() {
        return effect.filter(RANDOM::equals).isPresent();
    }
}
