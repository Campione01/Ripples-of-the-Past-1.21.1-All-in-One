package rotp.addons.gratefuldead;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Next Album 0.1.13 NextAlbumConfig agingSpeedMultiplier (server) and
 * agingAuraMistParticles (a visual option, here a client config).
 */
public final class GratefulDeadConfig {
    public static final double DEFAULT_AGING_SPEED_MULTIPLIER = 1.0D;
    public static final boolean DEFAULT_AGING_AURA_MIST_PARTICLES = true;

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.DoubleValue AGING_SPEED_MULTIPLIER;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue AGING_AURA_MIST_PARTICLES;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        common.push("Stand Settings");
        AGING_SPEED_MULTIPLIER = common
                .comment("Global multiplier applied to The Grateful Dead aging speed. 1.0 = default. Does not bypass the aging-rate window.")
                .translation("rotp_nextalbum.options.agingSpeedMultiplier")
                .defineInRange("agingSpeedMultiplier", DEFAULT_AGING_SPEED_MULTIPLIER, 0.0D, 10.0D);
        common.pop();
        COMMON_SPEC = common.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("Stand Settings");
        AGING_AURA_MIST_PARTICLES = client
                .comment("Show The Grateful Dead aging aura mist particles.")
                .translation("rotp_nextalbum.options.agingAuraMistParticles")
                .define("agingAuraMistParticles", DEFAULT_AGING_AURA_MIST_PARTICLES);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private GratefulDeadConfig() {}

    public static float agingSpeedMultiplier() {
        return COMMON_SPEC.isLoaded()
                ? AGING_SPEED_MULTIPLIER.get().floatValue()
                : (float) DEFAULT_AGING_SPEED_MULTIPLIER;
    }

    public static boolean agingAuraMistParticles() {
        return CLIENT_SPEC.isLoaded()
                ? AGING_AURA_MIST_PARTICLES.get()
                : DEFAULT_AGING_AURA_MIST_PARTICLES;
    }
}
