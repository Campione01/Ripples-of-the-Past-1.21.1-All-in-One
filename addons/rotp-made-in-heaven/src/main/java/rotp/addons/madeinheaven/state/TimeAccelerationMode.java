package rotp.addons.madeinheaven.state;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * The two readings of the acceleration the owner keeps (2026-11-04): the
 * server config chooses one, and an acceleration keeps the one in force when
 * it started.
 */
public enum TimeAccelerationMode implements TranslatableEnum {
    /** Mode A: the owner and non-living things keep normal speed, every other living being falls behind by m. */
    OTHERS_FALL_BEHIND("others_fall_behind"),
    /** Mode B: living beings keep their own time, non-living things and the weather run m times faster, the owner adapts. */
    WORLD_ACCELERATES("world_accelerates");

    public static final String TRANSLATION_PREFIX =
            "rotp_mih.config.server.accelerationMode.";

    private final String key;

    TimeAccelerationMode(String key) {
        this.key = key;
    }

    public String translationKey() {
        return TRANSLATION_PREFIX + key;
    }

    @Override
    public Component getTranslatedName() {
        return Component.translatable(translationKey());
    }

    public static TimeAccelerationMode byId(int id) {
        TimeAccelerationMode[] modes = values();
        return id >= 0 && id < modes.length ? modes[id] : WORLD_ACCELERATES;
    }
}
