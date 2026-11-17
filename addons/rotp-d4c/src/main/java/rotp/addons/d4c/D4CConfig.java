package rotp.addons.d4c;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

// COMMON, not SERVER: a server config is corrected through a relative
// world path, and NightConfig 3.8.2 then fails to write it (seen in the
// game-test server); read once per world start either way
public final class D4CConfig {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            PARALLEL_DIMENSIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PARALLEL_DIMENSIONS = builder
                .comment(
                        "Dimensions that get a parallel world, by id. The "
                                + "Overworld's is rotp_d4c:parallel_world; "
                                + "each other dimension listed gets one more "
                                + "world, made at the next start, which is "
                                + "ticked and saved like any dimension. "
                                + "Removing an id never deletes what was "
                                + "saved. Mods that act on a dimension by "
                                + "its id may not treat its parallel the "
                                + "same way.")
                .translation("rotp_d4c.configuration.parallelDimensions")
                .worldRestart()
                .defineListAllowEmpty(
                        "parallelDimensions",
                        List.of(OVERWORLD),
                        () -> OVERWORLD,
                        D4CConfig::isDimensionId);
        SPEC = builder.build();
    }

    private D4CConfig() {}

    static boolean isDimensionId(Object value) {
        return value instanceof String text
                && text.indexOf(':') > 0
                && ResourceLocation.tryParse(text) != null;
    }

    /** The ticked ids as written; the default list where none is loaded. */
    public static List<String> parallelDimensions() {
        if (!SPEC.isLoaded()) {
            return List.of(OVERWORLD);
        }
        return List.copyOf(PARALLEL_DIMENSIONS.get());
    }
}
