package rotp.addons.highwaystar;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HighwayStarRoomConfig {
    public static final int DEFAULT_EXPIRY_TICKS = 12_000;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue EXPIRY_ENABLED;
    public static final ModConfigSpec.IntValue EXPIRY_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("room");
        EXPIRY_ENABLED = builder.comment("Expire an unused Illusory Room even while its chunk is unloaded")
                .translation("highwaystar.configuration.room.expiryEnabled").define("expiryEnabled", true);
        EXPIRY_TICKS = builder.comment("Age in game ticks at which an unused Illusory Room expires")
                .translation("highwaystar.configuration.room.expiryTicks")
                .defineInRange("expiryTicks", DEFAULT_EXPIRY_TICKS, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private HighwayStarRoomConfig() {}

    public static boolean isExpired(long elapsedTicks) {
        return SPEC.isLoaded() ? EXPIRY_ENABLED.get() && elapsedTicks >= EXPIRY_TICKS.get()
                : elapsedTicks >= DEFAULT_EXPIRY_TICKS;
    }
}
