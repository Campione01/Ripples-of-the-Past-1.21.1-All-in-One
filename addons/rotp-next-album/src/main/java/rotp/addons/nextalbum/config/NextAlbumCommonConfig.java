package rotp.addons.nextalbum.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Next Album 0.1.13 NextAlbumConfig "Stand Settings" gameplay option
 * lastActionForAllStands (saved as unyieldingForSoulUsers), read on the
 * server only: the clients get its effect with the downed state.
 */
public final class NextAlbumCommonConfig {
    public static final boolean DEFAULT_UNYIELDING_FOR_SOUL_USERS = false;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue UNYIELDING_FOR_SOUL_USERS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Stand gameplay.").push("Stand Settings");
        UNYIELDING_FOR_SOUL_USERS = builder
                .comment("When enabled, any Stand user with a Resolve level above 0 whose soul would rise can be downed instead of dying"
                        + " (Resolve delayed death); skills stay usable and the timer drains quickly with the stamina they spend."
                        + " Sex Pistols keep their skills locked. Off: only The Grateful Dead's user, with only its aging aura usable.")
                .translation("rotp_nextalbum.options.lastActionForAllStands")
                .define("unyieldingForSoulUsers",
                        DEFAULT_UNYIELDING_FOR_SOUL_USERS);
        builder.pop();
        SPEC = builder.build();
    }

    private NextAlbumCommonConfig() {}

    public static boolean unyieldingForSoulUsers() {
        return SPEC.isLoaded()
                ? UNYIELDING_FOR_SOUL_USERS.get()
                : DEFAULT_UNYIELDING_FOR_SOUL_USERS;
    }
}
