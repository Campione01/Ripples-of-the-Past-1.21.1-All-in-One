package rotp.addons.sexpistols.config;

import net.neoforged.neoforge.common.ModConfigSpec;

// Next Album 0.1.13 NextAlbumConfig.Client "Revolver Display".
public final class SexPistolsClientConfig {
    public static final boolean DEFAULT_GUN_SWAY = true;
    public static final double DEFAULT_GUN_SWAY_SENSITIVITY = 0.55D;
    public static final double MAX_GUN_SWAY_SENSITIVITY = 2.0D;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GUN_SWAY;
    public static final ModConfigSpec.DoubleValue GUN_SWAY_SENSITIVITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("First-person revolver presentation.")
                .push("Revolver Display");
        GUN_SWAY = builder
                .comment("When looking around, the held revolver lags slightly behind the camera.")
                .translation("rotp_sex_pistols.options.gunSway")
                .define("gunSway", DEFAULT_GUN_SWAY);
        GUN_SWAY_SENSITIVITY = builder
                .comment("How strongly look movement sways the revolver. 0.0 = none, 1.0 = default.")
                .translation("rotp_sex_pistols.options.gunSwaySensitivity")
                .defineInRange(
                        "gunSwaySensitivity",
                        DEFAULT_GUN_SWAY_SENSITIVITY,
                        0.0D,
                        MAX_GUN_SWAY_SENSITIVITY);
        builder.pop();
        SPEC = builder.build();
    }

    private SexPistolsClientConfig() {}

    public static boolean gunSway() {
        return SPEC.isLoaded() ? GUN_SWAY.get() : DEFAULT_GUN_SWAY;
    }

    public static double gunSwaySensitivity() {
        return SPEC.isLoaded()
                ? GUN_SWAY_SENSITIVITY.getAsDouble()
                : DEFAULT_GUN_SWAY_SENSITIVITY;
    }
}
