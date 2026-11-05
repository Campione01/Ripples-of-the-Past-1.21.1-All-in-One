package rotp.addons.nextalbum.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Next Album 0.1.13 NextAlbumConfig "Stand Settings" visual options,
 * shown by its NextAlbum settings screen; in the port a client config in
 * the NeoForge config screen.
 */
public final class NextAlbumClientConfig {
    public static final boolean DEFAULT_ONOMATOPOEIA_PARTICLES = true;
    public static final boolean DEFAULT_RESOLVE_AURA_FOR_ALL_STANDS = false;
    public static final boolean DEFAULT_STAND_RESOLVE_AURA = true;
    public static final boolean DEFAULT_RESOLVE_AURA_ON_SUMMON = false;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ONOMATOPOEIA_PARTICLES;
    public static final ModConfigSpec.BooleanValue RESOLVE_AURA_FOR_ALL_STANDS;
    public static final ModConfigSpec.BooleanValue STAND_RESOLVE_AURA;
    public static final ModConfigSpec.BooleanValue RESOLVE_AURA_ON_SUMMON;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Stand visuals.").push("Stand Settings");
        RESOLVE_AURA_FOR_ALL_STANDS = builder
                .comment("Show the Resolve aura for every Stand; off: only Next Album's Stands (Sex Pistols, The Grateful Dead, Beach Boy).")
                .translation("rotp_nextalbum.options.resolveAuraForAll")
                .define("resolveAuraForAllStands",
                        DEFAULT_RESOLVE_AURA_FOR_ALL_STANDS);
        STAND_RESOLVE_AURA = builder
                .comment("Show the Resolve aura on the Stand too, not only on its user.")
                .translation("rotp_nextalbum.options.resolveAura")
                .define("standResolveAura", DEFAULT_STAND_RESOLVE_AURA);
        ONOMATOPOEIA_PARTICLES = builder
                .comment("Show onomatopoeia particles (for now on footstep sounds). Only Stand users see them.")
                .translation("rotp_nextalbum.options.onomatopoeiaParticles")
                .define("onomatopoeiaParticlesEnabled",
                        DEFAULT_ONOMATOPOEIA_PARTICLES);
        RESOLVE_AURA_ON_SUMMON = builder
                .comment("Show the Resolve aura while the Stand is summoned, even without the Resolve effect.")
                .translation("rotp_nextalbum.options.resolveAuraOnSummon")
                .define("resolveAuraOnSummon",
                        DEFAULT_RESOLVE_AURA_ON_SUMMON);
        builder.pop();
        SPEC = builder.build();
    }

    private NextAlbumClientConfig() {}

    public static boolean resolveAuraForAllStands() {
        return SPEC.isLoaded()
                ? RESOLVE_AURA_FOR_ALL_STANDS.get()
                : DEFAULT_RESOLVE_AURA_FOR_ALL_STANDS;
    }

    public static boolean standResolveAura() {
        return SPEC.isLoaded()
                ? STAND_RESOLVE_AURA.get()
                : DEFAULT_STAND_RESOLVE_AURA;
    }

    public static boolean resolveAuraOnSummon() {
        return SPEC.isLoaded()
                ? RESOLVE_AURA_ON_SUMMON.get()
                : DEFAULT_RESOLVE_AURA_ON_SUMMON;
    }

    public static boolean onomatopoeiaParticles() {
        return SPEC.isLoaded()
                ? ONOMATOPOEIA_PARTICLES.get()
                : DEFAULT_ONOMATOPOEIA_PARTICLES;
    }
}
