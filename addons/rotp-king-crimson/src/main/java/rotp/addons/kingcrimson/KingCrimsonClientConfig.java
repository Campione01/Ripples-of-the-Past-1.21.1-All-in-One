package rotp.addons.kingcrimson;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The module's own client settings (CLIENT, rotp_kingcrimson-client.toml); 1.16 had none. */
public final class KingCrimsonClientConfig {
    public static final String FILE_NAME = "rotp_kingcrimson-client.toml";

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue TIME_ERASE_CAMERA_SHAKE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        TIME_ERASE_CAMERA_SHAKE = builder
                .comment("A slight shake of your own camera while the ground breaks at the start of your Time Erase.",
                        "It scales with Screen Effects in the Accessibility settings.")
                .define("timeEraseCameraShake", true);
        SPEC = builder.build();
    }

    private KingCrimsonClientConfig() {}

    public static boolean timeEraseCameraShake() {
        return !SPEC.isLoaded() || TIME_ERASE_CAMERA_SHAKE.get();
    }
}
